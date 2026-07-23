package com.minemc.teamplugin;

import com.minemc.teamplugin.commands.TeamCommand;
import com.minemc.teamplugin.listener.TeamListener;
import com.minemc.teamplugin.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TeamPlugin - Lightweight temporary team plugin for Paper 1.21+
 *
 * Features:
 * - Temporary team creation and disbanding
 * - PvP protection between teammates
 * - Team summon with accept/deny mechanic
 * - GUI showing team members and controls
 * - Invite and join-request system
 * - Kick and leave functionality
 * - Lightweight, in-memory only, no persistence
 */
public final class TeamPlugin extends JavaPlugin {

    private static TeamPlugin instance;
    private TeamManager teamManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize team manager
        teamManager = new TeamManager(this);
        teamManager.loadConfig();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new TeamListener(this, teamManager), this);

        // Register commands
        TeamCommand cmd = new TeamCommand(this, teamManager);
        var teamCmd = getCommand("team");
        if (teamCmd != null) {
            teamCmd.setExecutor(cmd);
            teamCmd.setTabCompleter(cmd);
        } else {
            getLogger().severe("Command 'team' not found in plugin.yml! Check your configuration.");
        }

        getLogger().info("TeamPlugin v" + getPluginMeta().getVersion() + " 已启动！");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.cleanup();
        }
        instance = null;
        getLogger().info("TeamPlugin 已卸载！");
    }

    // ---- Static accessors ----

    public static TeamPlugin getInstance() {
        return instance;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
}
