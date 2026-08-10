package com.dobeshadow.dsteam;

import com.dobeshadow.dsteam.commands.TeamCommand;
import com.dobeshadow.dsteam.listener.TeamListener;
import com.dobeshadow.dsteam.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DsTeam - Lightweight temporary team plugin for Paper 1.21+
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
public final class DsTeam extends JavaPlugin {

    private static DsTeam instance;
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

        getLogger().info("DsTeam v" + getPluginMeta().getVersion() + " 已启动！");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.cleanup();
        }
        instance = null;
        getLogger().info("DsTeam 已卸载！");
    }

    // ---- Static accessors ----

    public static DsTeam getInstance() {
        return instance;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
}
