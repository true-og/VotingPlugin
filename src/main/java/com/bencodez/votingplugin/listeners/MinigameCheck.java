package com.bencodez.votingplugin.listeners;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

// Reports whether a player is currently inside a minigame.
//
// A minigame owns the player's inventory for the duration of a match and puts it back afterwards from a snapshot
// it took on entry. A reward delivered in between lands in the match kit and is destroyed by that restore, with
// the bank already marked as delivered, so rewards have to wait until the player is back on the SMP.
//
// Everything here is reflective and optional: no minigame is a dependency, and an absent or changed plugin simply
// reports "not in a minigame" rather than breaking vote delivery.
final class MinigameCheck {

    private static final String SPLEEF_PLUGIN = "Spleef-OG";
    private static final String SPLEEF_API = "net.trueog.spleefog.api.SpleefAPI";
    private static final String SPLEGG_PLUGIN = "Splegg-OG";
    private static final String DUELS_PLUGIN = "Duels-OG";

    private MinigameCheck() {
    }

    static boolean inMinigame(Player player) {
        return player != null && (inSpleef(player) || inSplegg(player) || inDuel(player));
    }

    private static boolean inSpleef(Player player) {
        Plugin plugin = enabled(SPLEEF_PLUGIN);
        if (plugin == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName(SPLEEF_API, true, plugin.getClass().getClassLoader());
            Method query = api.getMethod("isInSpleef", Player.class);
            return Boolean.TRUE.equals(query.invoke(null, player));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    // Splegg keeps its games in worlds it owns rather than in the SMP worlds, so the player's world answers this.
    private static boolean inSplegg(Player player) {
        Plugin plugin = enabled(SPLEGG_PLUGIN);
        if (plugin == null) {
            return false;
        }
        String world = player.getWorld().getName();
        try {
            Object configured = plugin.getClass().getMethod("getInGameWorlds").invoke(plugin);
            if (configured instanceof List<?> worlds) {
                for (Object name : worlds) {
                    if (name != null && world.equalsIgnoreCase(name.toString())) {
                        return true;
                    }
                }
            }
            Object prefix = plugin.getClass().getMethod("getGameWorldPrefix").invoke(plugin);
            // Per-game worlds are named <prefix><gameId>-<map>.
            return prefix != null && !prefix.toString().isEmpty()
                    && world.toLowerCase(Locale.ROOT).startsWith(prefix.toString().toLowerCase(Locale.ROOT));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean inDuel(Player player) {
        Plugin plugin = enabled(DUELS_PLUGIN);
        if (plugin == null) {
            return false;
        }
        try {
            Object arenaManager = plugin.getClass().getMethod("getArenaManager").invoke(plugin);
            if (arenaManager == null) {
                return false;
            }
            Method query = arenaManager.getClass().getMethod("isInMatch", Player.class);
            query.setAccessible(true);
            return Boolean.TRUE.equals(query.invoke(arenaManager, player));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static Plugin enabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

}
