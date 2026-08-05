package com.bencodez.votingplugin.listeners;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.listeners.PlayerRewardEvent;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.user.VotingPluginUser;

// Rewards given outside survival in the main worlds get wiped by
// GameModeInventories' per-gamemode XP/inventory swap or land in the wrong
// context, so bank them until the player is eligible again.
//
// A minigame counts as ineligible even though it runs in survival in a main
// world: Spleef, Splegg and Duels each swap the player's inventory for a match
// kit and put the real one back from a snapshot afterwards, so anything handed
// over in between is destroyed by that restore while the bank has already been
// marked as delivered.
public class SurvivalRewardGate implements Listener {

	private static final Set<String> REWARD_WORLDS = Set.of("world", "world_nether", "world_the_end");
	private static final long NOTICE_THROTTLE_MILLIS = 30000L;
	private static final long SWEEP_INTERVAL_TICKS = 200L;

	static boolean eligibleForRewards(Player player) {
		return player.getGameMode() == GameMode.SURVIVAL && REWARD_WORLDS.contains(player.getWorld().getName())
				&& !MinigameCheck.inMinigame(player);
	}

	private final VotingPluginMain plugin;
	private final ConcurrentHashMap<UUID, Long> lastBankNotice = new ConcurrentHashMap<>();
	private final Set<UUID> banked = ConcurrentHashMap.newKeySet();
	private int sweepTask = -1;

	public SurvivalRewardGate(VotingPluginMain plugin) {
		this.plugin = plugin;
	}

	// Leaving a minigame does not always change gamemode or world -- a duel or a spleef match a player walks out of
	// can end in survival in the same world it started in -- so a slow sweep backstops the event triggers. It only
	// looks at players who actually have something banked, so an idle server does no work.
	public void start() {
		sweepTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
			if (banked.isEmpty()) {
				return;
			}
			for (UUID uuid : Set.copyOf(banked)) {
				Player player = Bukkit.getPlayer(uuid);
				if (player == null || !player.isOnline()) {
					banked.remove(uuid);
					continue;
				}
				if (eligibleForRewards(player)) {
					scheduleReplay(uuid);
				}
			}
		}, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
	}

	public void stop() {
		if (sweepTask != -1) {
			Bukkit.getScheduler().cancelTask(sweepTask);
			sweepTask = -1;
		}
	}

	// Fires on an async thread; PlayerRewardEvent does not implement Cancellable
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerReward(PlayerRewardEvent event) {
		if (event.isCancelled()) {
			return;
		}
		AdvancedCoreUser user = event.getPlayer();
		if (user == null) {
			return;
		}
		Player player = user.getPlayer();
		if (player == null) {
			// truly offline: stock offline handling applies
			return;
		}
		if (eligibleForRewards(player)) {
			return;
		}
		event.setCancelled(true);
		user.addOfflineRewards(event.getReward(), event.getRewardOptions().getPlaceholders());
		plugin.debug("Banked reward " + event.getReward().getRewardName() + " for " + user.getPlayerName()
				+ " until survival in a main world");
		banked.add(player.getUniqueId());
		notifyBanked(player);
	}

	private void notifyBanked(Player player) {
		long now = System.currentTimeMillis();
		Long last = lastBankNotice.put(player.getUniqueId(), now);
		if (last != null && now - last < NOTICE_THROTTLE_MILLIS) {
			return;
		}
		plugin.getLogger().info(player.getName() + " has vote rewards banked until survival in a main world");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onGameModeChange(PlayerGameModeChangeEvent event) {
		if (event.getNewGameMode() != GameMode.SURVIVAL) {
			return;
		}
		scheduleReplay(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		scheduleReplay(event.getPlayer().getUniqueId());
	}

	private void scheduleReplay(UUID uuid) {
		// 1 tick later the change is applied and GameModeInventories' restore has settled
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline() || !eligibleForRewards(player)) {
				return;
			}
			banked.remove(uuid);
			// reward replay fires the async PlayerRewardEvent, so hop off the main thread
			plugin.getLoginTimer().execute(() -> {
				VotingPluginUser user = plugin.getVotingPluginUserManager().getVotingPluginUser(player);
				user.offVote();
				user.checkOfflineRewards();
			});
		}, 1L);
	}

}
