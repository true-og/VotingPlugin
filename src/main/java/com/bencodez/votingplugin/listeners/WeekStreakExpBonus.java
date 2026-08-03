package com.bencodez.votingplugin.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.listeners.PlayerRewardEvent;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.user.VotingPluginUser;

import net.kyori.adventure.text.minimessage.MiniMessage;

// Each full week of voting every day adds a bonus level on top of the per-site
// EXPLevels reward, capping the streak credit at MAX_WEEKS; missing a day
// resets the day streak and with it the bonus.
public class WeekStreakExpBonus implements Listener {

	private static final String SITE_REWARD_PREFIX = "VoteSites.";
	private static final String SITE_REWARD_SUFFIX = ".Rewards";
	private static final int BASE_LEVELS = 15;
	private static final int MAX_WEEKS = 52;
	private static final int DAYS_PER_WEEK = 7;

	private final VotingPluginMain plugin;

	public WeekStreakExpBonus(VotingPluginMain plugin) {
		this.plugin = plugin;
	}

	// Runs after SurvivalRewardGate (HIGHEST); a banked reward arrives cancelled
	// here and earns its bonus when the replay passes the gate
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerReward(PlayerRewardEvent event) {
		if (event.isCancelled()) {
			return;
		}
		Reward reward = event.getReward();
		if (reward == null || reward.getRewardName() == null) {
			return;
		}
		String name = reward.getRewardName();
		if (!name.startsWith(SITE_REWARD_PREFIX) || !name.endsWith(SITE_REWARD_SUFFIX)) {
			return;
		}
		AdvancedCoreUser acUser = event.getPlayer();
		if (acUser == null) {
			return;
		}
		Player player = acUser.getPlayer();
		if (player == null || !SurvivalRewardGate.eligibleForRewards(player)) {
			return;
		}
		final UUID uuid = player.getUniqueId();
		// async event; XP must change on the main thread
		Bukkit.getScheduler().runTask(plugin, () -> {
			Player target = Bukkit.getPlayer(uuid);
			if (target == null || !target.isOnline() || !SurvivalRewardGate.eligibleForRewards(target)) {
				return;
			}
			VotingPluginUser user = plugin.getVotingPluginUserManager().getVotingPluginUser(target);
			int dayStreak = user.getDayVoteStreak();
			int weeks = dayStreak / DAYS_PER_WEEK;
			int bonus = Math.min(weeks, MAX_WEEKS);
			if (bonus > 0) {
				target.giveExpLevels(bonus);
				plugin.debug("Week streak bonus: +" + bonus + " levels for " + target.getName());
			}
			target.sendMessage(MiniMessage.miniMessage().deserialize(buildMessage(dayStreak, weeks, bonus)));
		});
	}

	private String buildMessage(int dayStreak, int weeks, int bonus) {
		int total = BASE_LEVELS + bonus;
		if (bonus > 0) {
			return "<green>+" + total + " XP levels <gray>(" + weeks
					+ " week streak <dark_gray>-</dark_gray> vote every day to keep it)";
		}
		if (dayStreak > 0) {
			return "<green>+" + total + " XP levels <gray>(" + dayStreak + "/" + DAYS_PER_WEEK
					+ " days to your first bonus level)";
		}
		return "<green>+" + total + " XP levels <gray>(vote every day for a week to earn bonus XP)";
	}

}
