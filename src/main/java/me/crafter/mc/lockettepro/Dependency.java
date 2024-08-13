package me.crafter.mc.lockettepro;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public class Dependency {

    protected static WorldGuardPlugin worldguard = null;
    protected static Plugin vault = null;
    protected static Permission permission = null;
    private static CoreProtectAPI coreProtectAPI;

    public Dependency(Plugin plugin) {
        // WorldGuard
        Plugin worldguardplugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (!Config.worldguard || !(worldguardplugin instanceof WorldGuardPlugin)) {
            worldguard = null;
        } else {
            worldguard = (WorldGuardPlugin) worldguardplugin;
        }
        // Vault
        vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault != null) {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            permission = rsp.getProvider();
        }

        var coreprotect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (Config.coreprotect && coreprotect != null && coreprotect.isEnabled() && CoreProtect.getInstance().getAPI().APIVersion() >= 6) {
            coreProtectAPI = CoreProtect.getInstance().getAPI();
            if (!coreProtectAPI.isEnabled()) {
                coreProtectAPI = null;
                plugin.getLogger().warning("CoreProtect API is not enabled!");
            }
        }
    }

    public static boolean isProtectedFrom(Block block, Player player) {
        if (worldguard != null) {
            return !worldguard.createProtectionQuery().testBlockPlace(player, block.getLocation(), block.getType());
        }
        return false;
    }

    public static boolean isPermissionGroupOf(String line, Player player) {
        if (vault != null) {
            try {
                String[] groups = permission.getPlayerGroups(player);
                for (String group : groups) {
                    if (line.equals("[" + group + "]")) return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean isScoreboardTeamOf(String line, Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) return false;
        Team team = scoreboardManager.getMainScoreboard().getEntryTeam(player.getName());
        if (team != null) {
            return line.equals("[" + team.getName() + "]");
        }
        return false;
    }

    public static void logPlacement(Player player, Block block) {
        if (coreProtectAPI != null && coreProtectAPI.isEnabled()) {
            coreProtectAPI.logPlacement(player.getName(), block.getLocation(), block.getType(), block.getBlockData());
        }
    }
}
