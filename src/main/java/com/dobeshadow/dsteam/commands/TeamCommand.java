package com.dobeshadow.dsteam.commands;

import com.dobeshadow.dsteam.DsTeam;
import com.dobeshadow.dsteam.team.Team;
import com.dobeshadow.dsteam.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles all /team subcommands with tab completion.
 *
 * Subcommands:
 *   create              - Create a new team
 *   invite <player>     - Invite a player to your team (leader only)
 *   accept <name>       - Accept an invite or join request
 *   deny <name>         - Deny an invite or join request
 *   join <leader>       - Request to join a team
 *   kick <player>       - Kick a member from your team (leader only)
 *   leave               - Leave your current team
 *   disband             - Disband your team (leader only)
 *   summon [accept|deny] - Summon team / accept or deny summon
 *   gui                 - Open team GUI
 *   info                - Show team info
 *   help                - Show help
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    private final DsTeam plugin;
    private final TeamManager teamManager;

    private static final String NO_PERM = "&c你没有权限使用此命令！";
    private static final String PLAYER_ONLY = "&c此命令只能由玩家执行！";

    public TeamCommand(DsTeam plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            // Default: open GUI if in team, else show help
            if (sender instanceof Player player) {
                Team team = teamManager.getTeam(player);
                if (team != null) {
                    teamManager.openTeamGUI(player);
                } else {
                    sendHelp(sender);
                }
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "create", "c"       -> handleCreate(sender);
            case "invite", "inv"     -> handleInvite(sender, args);
            case "accept", "acc"     -> handleAccept(sender, args);
            case "deny", "den", "dec"-> handleDeny(sender, args);
            case "join", "j"         -> handleJoin(sender, args);
            case "kick", "k"         -> handleKick(sender, args);
            case "leave", "l", "lv"  -> handleLeave(sender);
            case "disband", "dis"    -> handleDisband(sender);
            case "summon", "s", "sum"-> handleSummon(sender, args);
            case "gui", "menu", "g"  -> handleGUI(sender);
            case "info", "i"         -> handleInfo(sender);
            case "list"              -> handleList(sender);
            case "help", "h"         -> handleHelp(sender);
            default -> {
                sendMsg(sender, TeamManager.colorize("&c未知子命令: &f/" + label + " " + sub + " &c使用 /" + label + " help 查看帮助"));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "create", "c", "invite", "inv", "accept", "acc", "deny", "den", "dec",
                    "join", "j", "kick", "k", "leave", "l", "lv", "disband", "dis",
                    "summon", "s", "sum", "gui", "g", "info", "i", "list", "help", "h"
            ));
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "invite", "inv" -> {
                    // Suggest online players not in sender's team
                    if (sender instanceof Player player) {
                        Team team = teamManager.getTeam(player);
                        return Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> {
                                    if (team == null) return true;
                                    // Exclude members of the same team
                                    Player p = Bukkit.getPlayer(name);
                                    return p != null && !team.isMember(p.getUniqueId());
                                })
                                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "accept", "acc", "deny", "den", "dec", "join", "j" -> {
                    // Suggest online players
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "kick", "k" -> {
                    // Suggest team members
                    if (sender instanceof Player player) {
                        Team team = teamManager.getTeam(player);
                        if (team != null && team.isLeader(player.getUniqueId())) {
                            return team.getOnlineMemberNames().stream()
                                    .filter(n -> !n.equalsIgnoreCase(player.getName()))
                                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList());
                        }
                    }
                }
                case "summon", "s", "sum" -> {
                    return List.of("accept", "a", "deny", "d").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }

    // ==================== Command Handlers ====================

    private boolean handleCreate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        boolean success = teamManager.createTeam(player);
        if (success) {
            player.sendMessage(TeamManager.component("&8&m-----------------------------------"));
            sendMsg(player, TeamManager.colorize("&a✅ 队伍创建成功！你现在是队长。"));
            sendMsg(player, TeamManager.colorize("&7命令速查:"));
            sendMsg(player, TeamManager.colorize("  &f/team invite <玩家> &7- 邀请玩家加入"));
            sendMsg(player, TeamManager.colorize("  &f/team kick <玩家> &7- 踢出队员"));
            sendMsg(player, TeamManager.colorize("  &f/team summon &7- 召集所有队员"));
            sendMsg(player, TeamManager.colorize("  &f/team disband &7- 解散队伍"));
            sendMsg(player, TeamManager.colorize("  &f/team gui &7- 打开队伍界面"));
            player.sendMessage(TeamManager.component("&8&m-----------------------------------"));
        } else {
            sendMsg(player, TeamManager.colorize("&c你已在队伍中！请先离开或解散当前队伍。"));
        }
        return true;
    }

    private boolean handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }
        if (args.length < 2) {
            sendMsg(player, TeamManager.colorize("&c用法: /team invite <玩家名>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMsg(player, TeamManager.colorize("&c玩家 &f" + args[1] + " &c不在线！"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendMsg(player, TeamManager.colorize("&c你不能邀请自己！"));
            return true;
        }

        teamManager.invitePlayer(player, target);
        return true;
    }

    private boolean handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        String targetName = args.length >= 2 ? args[1] : null;

        // First, try accepting an invite (player is the invitee)
        Team team = teamManager.getTeam(player);
        if (team == null) {
            // Player is not in a team — must be accepting an invite
            if (teamManager.acceptInvite(player, targetName)) {
                return true;
            }
            // If no invite pending, show error
            sendMsg(player, TeamManager.colorize("&c你没有待处理的组队邀请！使用 &f/team join <队长名> &c申请加入。"));
            return true;
        }

        // Player IS in a team — if they're a leader, try accepting a join request
        if (team.isLeader(player.getUniqueId()) && targetName != null) {
            if (teamManager.acceptJoinRequest(player, targetName)) {
                return true;
            }
        }

        sendMsg(player, TeamManager.colorize("&c用法: &f/team accept <玩家名>"));
        return true;
    }

    private boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        String targetName = args.length >= 2 ? args[1] : null;

        // Try denying an invite first
        if (teamManager.getTeam(player) == null) {
            if (teamManager.denyInvite(player, targetName)) {
                return true;
            }
            sendMsg(player, TeamManager.colorize("&c你没有待处理的组队邀请！"));
            return true;
        }

        // Try denying a join request (if player is leader)
        Team team = teamManager.getTeam(player);
        if (team.isLeader(player.getUniqueId()) && targetName != null) {
            if (teamManager.denyJoinRequest(player, targetName)) {
                return true;
            }
        }

        sendMsg(player, TeamManager.colorize("&c用法: &f/team deny <玩家名>"));
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }
        if (args.length < 2) {
            sendMsg(player, TeamManager.colorize("&c用法: /team join <队长名>"));
            return true;
        }

        Player leader = Bukkit.getPlayer(args[1]);
        if (leader == null) {
            sendMsg(player, TeamManager.colorize("&c玩家 &f" + args[1] + " &c不在线！"));
            return true;
        }
        if (leader.getUniqueId().equals(player.getUniqueId())) {
            sendMsg(player, TeamManager.colorize("&c你不能申请加入自己的队伍！使用 &f/team create &c创建队伍。"));
            return true;
        }

        teamManager.requestJoin(player, leader);
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }
        if (args.length < 2) {
            sendMsg(player, TeamManager.colorize("&c用法: /team kick <玩家名>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMsg(player, TeamManager.colorize("&c玩家 &f" + args[1] + " &c不在线！"));
            return true;
        }

        Team team = teamManager.getTeam(player);
        if (team == null || !team.isLeader(player.getUniqueId())) {
            sendMsg(player, TeamManager.colorize("&c你不是队长，无法踢出队员！"));
            return true;
        }
        if (!team.isMember(target.getUniqueId())) {
            sendMsg(player, TeamManager.colorize("&c该玩家不在你的队伍中！"));
            return true;
        }
        if (team.isLeader(target.getUniqueId())) {
            sendMsg(player, TeamManager.colorize("&c你不能踢出你自己！使用 &f/team disband &c解散队伍。"));
            return true;
        }

        teamManager.removeFromTeam(target, player);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        Team team = teamManager.getTeam(player);
        if (team == null) {
            sendMsg(player, TeamManager.colorize("&c你不在队伍中！"));
            return true;
        }

        teamManager.leaveTeam(player);
        return true;
    }

    private boolean handleDisband(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        boolean success = teamManager.disbandTeam(player);
        if (success) {
            sendMsg(player, TeamManager.colorize("&a队伍已解散！"));
        } else {
            sendMsg(player, TeamManager.colorize("&c你不是队长，无法解散队伍！"));
        }
        return true;
    }

    private boolean handleSummon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        // Subcommand: summon accept/a / summon deny/d
        if (args.length >= 2) {
            String sub = args[1].toLowerCase();
            if (sub.equals("accept") || sub.equals("a")) {
                teamManager.acceptSummon(player);
                player.closeInventory();
                return true;
            }
            if (sub.equals("deny") || sub.equals("d")) {
                teamManager.denySummon(player);
                return true;
            }
        }

        // No subcommand: leader initiates summon
        teamManager.summonTeam(player);
        return true;
    }

    private boolean handleGUI(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }
        if (!player.hasPermission("dsteam.use")) {
            sendMsg(player, TeamManager.colorize(NO_PERM));
            return true;
        }

        teamManager.openTeamGUI(player);
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }

        Team team = teamManager.getTeam(player);
        if (team == null) {
            sendMsg(player, TeamManager.colorize("&c你不在队伍中！"));
            sendMsg(player, TeamManager.colorize("&7使用 &f/team create &7创建队伍，或 &f/team join <队长名> &7申请加入。"));
            return true;
        }

        sendMsg(player, TeamManager.colorize("&8&m-----------------------------------"));
        sendMsg(player, TeamManager.colorize("&b&l队伍信息"));
        sendMsg(player, TeamManager.colorize("&7队长: &f" + team.getLeaderName() + (team.isLeader(player.getUniqueId()) ? " &6[你]" : "")));
        sendMsg(player, TeamManager.colorize("&7人数: &b" + team.getSize() + "&7/&b" + plugin.getConfig().getInt("max-team-size", 6)));
        sendMsg(player, TeamManager.colorize("&7在线队员:"));
        for (String name : team.getOnlineMemberNames()) {
            String marker = name.equalsIgnoreCase(team.getLeaderName()) ? " &6[队长]" : "";
            sendMsg(player, TeamManager.colorize("  &f- " + name + marker));
        }
        sendMsg(player, TeamManager.colorize("&8&m-----------------------------------"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, TeamManager.colorize(PLAYER_ONLY));
            return true;
        }

        var teams = teamManager.getAllTeams();
        if (teams.isEmpty()) {
            sendMsg(player, TeamManager.colorize("&7当前没有活跃的队伍。"));
            return true;
        }

        sendMsg(player, TeamManager.colorize("&8&m-----------------------------------"));
        sendMsg(player, TeamManager.colorize("&b&l活跃队伍列表 &7(共 " + teams.size() + " 队, " + teamManager.getTotalPlayers() + " 人)"));
        for (Team team : teams) {
            sendMsg(player, TeamManager.colorize("&f" + team.getLeaderName() + " &7的队伍 &8| &b" + team.getSize() + "人 &8| &7在线: " + String.join(", ", team.getOnlineMemberNames())));
        }
        sendMsg(player, TeamManager.colorize("&8&m-----------------------------------"));
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sendHelp(sender);
        return true;
    }

    // ==================== Help ====================

    private void sendHelp(CommandSender sender) {
        sendMsg(sender, TeamManager.colorize("  &8Ds&fTeam &8- &7组队系统"));
        sendMsg(sender, TeamManager.colorize("  &7命令: &f/team &8[...]"));
        sendMsg(sender, TeamManager.colorize("  &7参数:"));
        sendMsg(sender, TeamManager.colorize("    &8- &fcreate &8(c)   &7创建队伍"));
        sendMsg(sender, TeamManager.colorize("    &8- &finvite <玩家> &8(inv)   &7邀请玩家加入 &8(队长)"));
        sendMsg(sender, TeamManager.colorize("    &8- &faccept <玩家> &8(acc)   &7接受邀请/加入申请"));
        sendMsg(sender, TeamManager.colorize("    &8- &fdeny <玩家> &8(den)   &7拒绝邀请/加入申请"));
        sendMsg(sender, TeamManager.colorize("    &8- &fjoin <队长> &8(j)   &7申请加入队伍"));
        sendMsg(sender, TeamManager.colorize("    &8- &fkick <玩家> &8(k)   &7踢出队员 &8(队长)"));
        sendMsg(sender, TeamManager.colorize("    &8- &fleave &8(l)   &7离开队伍"));
        sendMsg(sender, TeamManager.colorize("    &8- &fdisband &8(dis)   &7解散队伍 &8(队长)"));
        sendMsg(sender, TeamManager.colorize("    &8- &fsummon &8(s)   &7召集队员 &8(队长)"));
        sendMsg(sender, TeamManager.colorize("    &8- &fsummon accept/deny   &7接受/拒绝召集"));
        sendMsg(sender, TeamManager.colorize("    &8- &fgui &8(g)   &7打开队伍界面"));
        sendMsg(sender, TeamManager.colorize("    &8- &finfo &8(i)   &7查看队伍信息"));
        sendMsg(sender, TeamManager.colorize("    &8- &flist   &7查看所有活跃队伍"));
    }

    // ==================== Utilities ====================

    private void sendMsg(CommandSender sender, String msg) {
        sender.sendMessage(TeamManager.colorize(msg));
    }
}
