package com.minemc.teamplugin.team;

import com.minemc.teamplugin.TeamPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all team lifecycle: creation, invites, join requests, summoning, PvP, and GUI.
 */
public class TeamManager {

    private final TeamPlugin plugin;

    // player UUID -> Team (only one team per player)
    private final Map<UUID, Team> playerTeamMap = new ConcurrentHashMap<>();

    // Pending invites: invited UUID -> TeamInvite
    private final Map<UUID, TeamInvite> pendingInvites = new ConcurrentHashMap<>();

    // Pending join requests: requester UUID -> TeamInvite (waiting for leader)
    private final Map<UUID, TeamInvite> pendingJoinRequests = new ConcurrentHashMap<>();

    // Active summon requests: team leader UUID -> SummonRequest
    private final Map<UUID, SummonRequest> activeSummons = new ConcurrentHashMap<>();

    // Settings loaded from config
    private int maxTeamSize;
    private int inviteTimeout;
    private int summonTimeout;
    private String chatPrefix;
    private boolean summonTitle;
    private boolean consoleLog;

    public TeamManager(TeamPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load/reload settings from config.yml.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var config = plugin.getConfig();

        maxTeamSize = config.getInt("max-team-size", 6);
        inviteTimeout = config.getInt("invite-timeout", 60);
        summonTimeout = config.getInt("summon-timeout", 30);
        chatPrefix = colorize(config.getString("chat-prefix", "&8[&b组队&8] &7"));
        summonTitle = config.getBoolean("summon-title", true);
        consoleLog = config.getBoolean("console-log", true);

        if (consoleLog) {
            plugin.getLogger().info("配置已加载: max-team-size=" + maxTeamSize
                    + " invite-timeout=" + inviteTimeout + " summon-timeout=" + summonTimeout);
        }
    }

    // ==================== Team CRUD ====================

    /**
     * Create a new team with the given player as leader.
     * Returns false if the player is already in a team.
     */
    public boolean createTeam(Player leader) {
        if (playerTeamMap.containsKey(leader.getUniqueId())) {
            return false;
        }
        Team team = new Team(leader);
        playerTeamMap.put(leader.getUniqueId(), team);
        if (consoleLog) {
            plugin.getLogger().info("Team created by " + leader.getName());
        }
        return true;
    }

    /**
     * Disband the team led by the given player.
     * Returns false if the player is not a leader.
     */
    public boolean disbandTeam(Player leader) {
        Team team = getTeam(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            return false;
        }
        disbandTeamInternal(team);
        return true;
    }

    private void disbandTeamInternal(Team team) {
        // Notify online members
        Component msg = component(chatPrefix + "&c队伍已解散！");
        for (UUID uuid : team.getMembers()) {
            playerTeamMap.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(msg);
            }
        }
        // Cancel any active summon
        activeSummons.remove(team.getLeaderUuid());
        if (consoleLog) {
            plugin.getLogger().info("Team disbanded: " + team);
        }
    }

    // ==================== Member Management ====================

    /**
     * Add a player to the team directly (used for accept invite / accept join request).
     */
    public boolean addToTeam(Team team, Player player) {
        if (playerTeamMap.containsKey(player.getUniqueId())) {
            return false;
        }
        if (team.getSize() >= maxTeamSize) {
            return false;
        }
        team.addMember(player);
        playerTeamMap.put(player.getUniqueId(), team);

        // Notify team
        Component msg = component(chatPrefix + "&a" + player.getName() + " &7加入了队伍！ &7(&b" + team.getSize() + "&7/&b" + maxTeamSize + "&7)");
        for (Player p : team.getOnlineMembers()) {
            p.sendMessage(msg);
        }
        if (consoleLog) {
            plugin.getLogger().info(player.getName() + " joined team of " + team.getLeaderName());
        }
        return true;
    }

    /**
     * Remove a player from their team (kick or voluntary leave).
     */
    public boolean removeFromTeam(Player target, Player remover) {
        Team team = getTeam(target);
        if (team == null) return false;

        // If target is the leader, disband instead
        if (team.isLeader(target.getUniqueId())) {
            if (remover != null && remover.getUniqueId().equals(target.getUniqueId())) {
                // Leader is leaving — disband the team
                disbandTeamInternal(team);
                return true;
            }
            // Can't kick the leader
            return false;
        }

        String removerName = remover != null ? remover.getName() : "控制台";
        team.removeMember(target.getUniqueId());
        playerTeamMap.remove(target.getUniqueId());

        Component targetMsg = component(chatPrefix + "&e你已被 &f" + removerName + " &e移出队伍！");
        if (target.isOnline()) {
            target.sendMessage(targetMsg);
        }

        Component teamMsg = component(chatPrefix + "&e" + target.getName() + " &7离开了队伍！ &7(&b" + team.getSize() + "&7/&b" + maxTeamSize + "&7)");
        for (Player p : team.getOnlineMembers()) {
            p.sendMessage(teamMsg);
        }
        if (consoleLog) {
            plugin.getLogger().info(target.getName() + " removed from team of " + team.getLeaderName());
        }
        return true;
    }

    /**
     * Player voluntarily leaves their team.
     */
    public boolean leaveTeam(Player player) {
        Team team = getTeam(player);
        if (team == null) return false;

        if (team.isLeader(player.getUniqueId())) {
            // Transfer leadership or disband
            disbandTeamInternal(team);
            return true;
        }
        return removeFromTeam(player, player);
    }

    // ==================== Invites ====================

    /**
     * Leader invites a player to join their team.
     */
    public boolean invitePlayer(Player leader, Player target) {
        Team team = getTeam(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(component(chatPrefix + "&c你不是队长，无法邀请！"));
            return false;
        }
        if (team.getSize() >= maxTeamSize) {
            leader.sendMessage(component(chatPrefix + "&c队伍已满！(&b" + team.getSize() + "&7/&b" + maxTeamSize + "&7)"));
            return false;
        }
        if (playerTeamMap.containsKey(target.getUniqueId())) {
            leader.sendMessage(component(chatPrefix + "&c该玩家已在其他队伍中！"));
            return false;
        }
        if (pendingInvites.containsKey(target.getUniqueId())) {
            leader.sendMessage(component(chatPrefix + "&c已有一个待处理的邀请发送给该玩家，请等待回应。"));
            return false;
        }

        TeamInvite invite = new TeamInvite(team, leader.getUniqueId(), target.getUniqueId());
        pendingInvites.put(target.getUniqueId(), invite);

        leader.sendMessage(component(chatPrefix + "&a已向 &f" + target.getName() + " &a发送组队邀请！&7(&b" + inviteTimeout + "秒&7内有效)"));

        // Send invite message to target with clickable + hoverable accept/deny buttons
        String cmdBase = "/team";
        target.sendMessage(Component.empty());
        target.sendMessage(component("&8&m-----------------------------------"));
        target.sendMessage(component(chatPrefix + "&f" + leader.getName() + " &e邀请你加入队伍！"));
        target.sendMessage(Component.empty());
        target.sendMessage(
                hoverButton("  &a[✔ 接受] ", "&a点击接受邀请", cmdBase + " accept " + leader.getName())
                        .append(component("&8│ "))
                        .append(hoverButton("&c[✘ 拒绝]", "&c点击拒绝邀请", cmdBase + " deny " + leader.getName()))
        );
        target.sendMessage(component("&8&m-----------------------------------"));

        // Auto-expire
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeamInvite expired = pendingInvites.remove(target.getUniqueId());
            if (expired != null) {
                if (leader.isOnline()) {
                    leader.sendMessage(component(chatPrefix + "&7对 &f" + target.getName() + " &7的邀请已过期。"));
                }
                if (target.isOnline()) {
                    target.sendMessage(component(chatPrefix + "&7来自 &f" + leader.getName() + " &7的组队邀请已过期。"));
                }
            }
        }, inviteTimeout * 20L);

        return true;
    }

    /**
     * Player accepts a team invitation.
     */
    public boolean acceptInvite(Player player, String leaderName) {
        TeamInvite invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(component(chatPrefix + "&c你没有待处理的组队邀请！"));
            return false;
        }

        Player leader = Bukkit.getPlayer(invite.leaderUuid());
        if (leaderName != null && !leaderName.isEmpty() && leader != null
                && !leader.getName().equalsIgnoreCase(leaderName)) {
            player.sendMessage(component(chatPrefix + "&c未找到来自 &f" + leaderName + " &c的邀请。"));
            return false;
        }

        Team team = invite.team();
        // Verify team still exists and leader is still the leader
        Team currentTeam = getTeam(leader);
        if (currentTeam == null || !currentTeam.equals(team)) {
            player.sendMessage(component(chatPrefix + "&c该队伍已不存在或队长已变更！"));
            return false;
        }

        return addToTeam(team, player);
    }

    /**
     * Player denies a team invitation.
     */
    public boolean denyInvite(Player player, String leaderName) {
        TeamInvite invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(component(chatPrefix + "&c你没有待处理的组队邀请！"));
            return false;
        }

        Player leader = Bukkit.getPlayer(invite.leaderUuid());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(component(chatPrefix + "&f" + player.getName() + " &7拒绝了你的组队邀请。"));
        }
        player.sendMessage(component(chatPrefix + "&7你拒绝了来自 &f" + invite.leaderName() + " &7的组队邀请。"));
        return true;
    }

    // ==================== Join Requests ====================

    /**
     * Player requests to join a team by leader name.
     */
    public boolean requestJoin(Player player, Player leader) {
        Team team = getTeam(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            player.sendMessage(component(chatPrefix + "&c该玩家不是队长！"));
            return false;
        }
        if (playerTeamMap.containsKey(player.getUniqueId())) {
            player.sendMessage(component(chatPrefix + "&c你已在队伍中！请先离开当前队伍。"));
            return false;
        }
        if (team.getSize() >= maxTeamSize) {
            player.sendMessage(component(chatPrefix + "&c该队伍已满！"));
            return false;
        }
        if (pendingJoinRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(component(chatPrefix + "&c你已有一个待处理的申请，请等待回应。"));
            return false;
        }

        TeamInvite request = new TeamInvite(team, player.getUniqueId(), leader.getUniqueId());
        pendingJoinRequests.put(player.getUniqueId(), request);

        player.sendMessage(component(chatPrefix + "&a已向队长 &f" + leader.getName() + " &a发送加入申请！"));

        // Notify leader
        String cmdBase = "/team";
        leader.sendMessage(Component.empty());
        leader.sendMessage(component("&8&m-----------------------------------"));
        leader.sendMessage(component(chatPrefix + "&f" + player.getName() + " &e申请加入队伍！"));
        leader.sendMessage(Component.empty());
        leader.sendMessage(
                hoverButton("  &a[✔ 接受] ", "&a点击接受申请", cmdBase + " accept " + player.getName())
                        .append(component("&8│ "))
                        .append(hoverButton("&c[✘ 拒绝]", "&c点击拒绝申请", cmdBase + " deny " + player.getName()))
        );
        leader.sendMessage(component("&8&m-----------------------------------"));

        // Auto-expire
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeamInvite expired = pendingJoinRequests.remove(player.getUniqueId());
            if (expired != null) {
                if (player.isOnline()) {
                    player.sendMessage(component(chatPrefix + "&7你对 &f" + leader.getName() + " &7的加入申请已过期。"));
                }
                if (leader.isOnline()) {
                    leader.sendMessage(component(chatPrefix + "&7来自 &f" + player.getName() + " &7的加入申请已过期。"));
                }
            }
        }, inviteTimeout * 20L);

        return true;
    }

    /**
     * Leader accepts a join request from a player.
     */
    public boolean acceptJoinRequest(Player leader, String requesterName) {
        Team team = getTeam(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(component(chatPrefix + "&c你不是队长！"));
            return false;
        }
        if (team.getSize() >= maxTeamSize) {
            leader.sendMessage(component(chatPrefix + "&c队伍已满！"));
            return false;
        }

        // Find the join request by player name
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null) {
            // Try to find by name in pending requests
            for (Map.Entry<UUID, TeamInvite> entry : pendingJoinRequests.entrySet()) {
                TeamInvite req = entry.getValue();
                if (req.targetName().equalsIgnoreCase(requesterName)) {
                    pendingJoinRequests.remove(entry.getKey());
                    leader.sendMessage(component(chatPrefix + "&c该玩家已离线，无法加入。"));
                    return false;
                }
            }
            leader.sendMessage(component(chatPrefix + "&c未找到来自 &f" + requesterName + " &c的申请。"));
            return false;
        }

        TeamInvite request = pendingJoinRequests.remove(requester.getUniqueId());
        if (request == null) {
            leader.sendMessage(component(chatPrefix + "&c未找到来自 &f" + requesterName + " &c的申请。"));
            return false;
        }

        return addToTeam(team, requester);
    }

    /**
     * Leader denies a join request.
     */
    public boolean denyJoinRequest(Player leader, String requesterName) {
        // Find the join request by player name
        Player requester = Bukkit.getPlayer(requesterName);
        UUID requesterUuid = requester != null ? requester.getUniqueId() : null;

        if (requesterUuid != null) {
            TeamInvite request = pendingJoinRequests.remove(requesterUuid);
            if (request == null) {
                leader.sendMessage(component(chatPrefix + "&c未找到来自 &f" + requesterName + " &c的申请。"));
                return false;
            }
            if (requester.isOnline()) {
                requester.sendMessage(component(chatPrefix + "&7你加入 &f" + leader.getName() + " &7队伍的申请已被拒绝。"));
            }
        } else {
            // Player is offline, find by name
            UUID toRemove = null;
            for (Map.Entry<UUID, TeamInvite> entry : pendingJoinRequests.entrySet()) {
                if (entry.getValue().targetName().equalsIgnoreCase(requesterName)) {
                    toRemove = entry.getKey();
                    break;
                }
            }
            if (toRemove != null) {
                pendingJoinRequests.remove(toRemove);
            } else {
                leader.sendMessage(component(chatPrefix + "&c未找到来自 &f" + requesterName + " &c的申请。"));
                return false;
            }
        }

        leader.sendMessage(component(chatPrefix + "&7你拒绝了 &f" + requesterName + " &7的加入申请。"));
        return true;
    }

    // ==================== Summon ====================

    /**
     * Leader summons all team members to their location.
     * Each member gets an accept/deny GUI.
     */
    public boolean summonTeam(Player leader) {
        Team team = getTeam(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(component(chatPrefix + "&c你不是队长，无法召集！"));
            return false;
        }

        // Cancel any existing summon
        SummonRequest old = activeSummons.remove(leader.getUniqueId());
        if (old != null) {
            old.cancel();
        }

        Set<Player> onlineMembers = team.getOnlineMembers();
        // Exclude the leader
        onlineMembers.remove(leader);

        if (onlineMembers.isEmpty()) {
            leader.sendMessage(component(chatPrefix + "&7没有在线的队员可以召集。"));
            return false;
        }

        SummonRequest summon = new SummonRequest(team, leader);
        activeSummons.put(leader.getUniqueId(), summon);

        leader.sendMessage(component(chatPrefix + "&a已向 &b" + onlineMembers.size() + " &a名队员发送召集请求！&7(&b" + summonTimeout + "秒&7内有效)"));

        for (Player member : onlineMembers) {
            // Send title
            if (summonTitle) {
                member.sendTitle(
                        colorize("&b&l队长召集"),
                        colorize("&7" + leader.getName() + " 召唤你传送过去！"),
                        10, 70, 20
                );
            }

            // Send chat message with hoverable buttons
            String cmdBase = "/team";
            member.sendMessage(Component.empty());
            member.sendMessage(component("&8&m-----------------------------------"));
            member.sendMessage(component(chatPrefix + "&f" + leader.getName() + " &e召集所有队员传送！"));
            member.sendMessage(Component.empty());
            member.sendMessage(
                    hoverButton("  &a[✔ 接受召集] ", "&a点击传送到队长身边", cmdBase + " s a")
                            .append(component("&8│ "))
                            .append(hoverButton("&c[✘ 拒绝召集]", "&c点击拒绝召集请求", cmdBase + " s d"))
            );
            member.sendMessage(component("&8&m-----------------------------------"));

            // Open summon GUI for the member
            openSummonGUI(member, leader);
        }

        // Auto-expire
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            SummonRequest expired = activeSummons.remove(leader.getUniqueId());
            if (expired != null) {
                expired.cancel();
                if (leader.isOnline()) {
                    leader.sendMessage(component(chatPrefix + "&7召集请求已过期。"));
                }
                for (Player member : onlineMembers) {
                    if (member.isOnline()) {
                        member.sendMessage(component(chatPrefix + "&7来自 &f" + leader.getName() + " &7的召集请求已过期。"));
                    }
                }
            }
        }, summonTimeout * 20L);

        return true;
    }

    /**
     * Player accepts a summon request.
     */
    public boolean acceptSummon(Player player) {
        Team team = getTeam(player);
        if (team == null) {
            player.sendMessage(component(chatPrefix + "&c你不在队伍中！"));
            return false;
        }

        SummonRequest summon = activeSummons.get(team.getLeaderUuid());
        if (summon == null) {
            player.sendMessage(component(chatPrefix + "&c没有待处理的召集请求！"));
            return false;
        }

        if (summon.hasAccepted(player.getUniqueId())) {
            player.sendMessage(component(chatPrefix + "&7你已经接受过召集了！"));
            return false;
        }

        // Check if player is in the team
        if (!team.isMember(player.getUniqueId())) {
            return false;
        }

        Player leader = Bukkit.getPlayer(team.getLeaderUuid());
        if (leader == null || !leader.isOnline()) {
            player.sendMessage(component(chatPrefix + "&c队长已离线！"));
            summon.cancel();
            activeSummons.remove(team.getLeaderUuid());
            return false;
        }

        // Teleport the player
        player.teleport(leader.getLocation());
        summon.markAccepted(player.getUniqueId());
        player.sendMessage(component(chatPrefix + "&a你已传送到队长 &f" + leader.getName() + " &a身边！"));
        leader.sendMessage(component(chatPrefix + "&f" + player.getName() + " &a接受了召集并已传送！"));

        return true;
    }

    /**
     * Player denies a summon request.
     */
    public boolean denySummon(Player player) {
        Team team = getTeam(player);
        if (team == null) {
            player.sendMessage(component(chatPrefix + "&c你不在队伍中！"));
            return false;
        }

        SummonRequest summon = activeSummons.get(team.getLeaderUuid());
        if (summon == null) {
            player.sendMessage(component(chatPrefix + "&c没有待处理的召集请求！"));
            return false;
        }

        Player leader = Bukkit.getPlayer(team.getLeaderUuid());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(component(chatPrefix + "&f" + player.getName() + " &7拒绝了召集。"));
        }
        player.sendMessage(component(chatPrefix + "&7你拒绝了召集请求。"));
        player.closeInventory();
        return true;
    }

    // ==================== GUI ====================

    /**
     * Open the team info GUI for a player.
     */
    public void openTeamGUI(Player player) {
        Team team = getTeam(player);
        if (team == null) {
            player.sendMessage(component(chatPrefix + "&c你不在队伍中！使用 &f/team create &c创建队伍，"
                    + "或 &f/team join <队长名> &c申请加入。"));
            return;
        }

        boolean isLeader = team.isLeader(player.getUniqueId());
        Set<Player> onlineMembers = team.getOnlineMembers();
        Set<UUID> offlineMembers = team.getOfflineMembers();

        int totalSlots = Math.max(9, ((onlineMembers.size() + offlineMembers.size() + 3) / 9 + 1) * 9);
        totalSlots = Math.min(totalSlots, 54); // max 6 rows

        String title = isLeader ? "§8队伍管理 §7(队长)" : "§8队伍信息";
        Inventory gui = Bukkit.createInventory(null, totalSlots, title);

        // Fill online members as player heads
        int slot = 0;
        for (Player member : onlineMembers) {
            ItemStack head = createPlayerHead(member, team);
            gui.setItem(slot++, head);
        }

        // Fill offline members
        for (UUID uuid : offlineMembers) {
            OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(uuid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offPlayer);
                meta.setDisplayName(colorize("&7" + (offPlayer.getName() != null ? offPlayer.getName() : "未知玩家") + " &8[离线]"));
                List<String> lore = new ArrayList<>();
                if (team.isLeader(uuid)) {
                    lore.add(colorize("&6⚔ 队长"));
                }
                lore.add(colorize("&8&o离线"));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        // Separator
        slot = placeSeparators(gui, slot, totalSlots);

        // Leader-only buttons
        if (isLeader) {
            // Invite button
            ItemStack inviteItem = createMenuItem(Material.WRITABLE_BOOK,
                    "&a&l邀请玩家",
                    "&7点击邀请玩家加入队伍",
                    "&7命令: &f/team invite <玩家名>");
            gui.setItem(slot++, inviteItem);

            // Summon button
            ItemStack summonItem = createMenuItem(Material.ENDER_PEARL,
                    "&b&l召集队员",
                    "&7点击召集所有在线队员",
                    "&7传送至你的位置");
            gui.setItem(slot++, summonItem);

            // Disband button
            ItemStack disbandItem = createMenuItem(Material.BARRIER,
                    "&c&l解散队伍",
                    "&7点击解散当前队伍",
                    "&c⚠ 此操作不可撤销");
            gui.setItem(slot++, disbandItem);
        } else {
            // Leave button for non-leaders
            ItemStack leaveItem = createMenuItem(Material.OAK_DOOR,
                    "&e&l离开队伍",
                    "&7点击离开当前队伍");
            gui.setItem(slot++, leaveItem);
        }

        // Info item
        ItemStack infoItem = createMenuItem(Material.PAPER,
                "&b&l队伍信息",
                "&7队长: &f" + team.getLeaderName(),
                "&7人数: &b" + team.getSize() + "&7/&b" + maxTeamSize,
                "&7创建时间: &f" + formatTime(team.getCreatedAt()));
        gui.setItem(slot, infoItem);

        player.openInventory(gui);
    }

    /**
     * Open a summon accept/deny GUI for a team member.
     */
    public void openSummonGUI(Player member, Player leader) {
        Inventory gui = Bukkit.createInventory(null, 9, "§8队长召集");

        // Info item
        ItemStack info = createMenuItem(Material.ENDER_PEARL,
                "&b&l队长召集",
                "&7队长 &f" + leader.getName() + " &7召集你传送！",
                "&7请选择接受或拒绝：");
        gui.setItem(3, info);

        // Accept button
        ItemStack accept = createMenuItem(Material.LIME_WOOL,
                "&a&l✔ 接受召集",
                "&7点击传送到队长身边");
        gui.setItem(4, accept);

        // Deny button
        ItemStack deny = createMenuItem(Material.RED_WOOL,
                "&c&l✘ 拒绝召集",
                "&7点击拒绝召集请求");
        gui.setItem(5, deny);

        member.openInventory(gui);
    }

    // ==================== PvP Check ====================

    /**
     * Check whether two players are in the same team (for PvP protection).
     */
    public boolean areInSameTeam(Player a, Player b) {
        Team teamA = getTeam(a);
        Team teamB = getTeam(b);
        return teamA != null && teamA.equals(teamB);
    }

    /**
     * Check if two entities (players) are in the same team.
     */
    public boolean areInSameTeam(UUID a, UUID b) {
        Team teamA = playerTeamMap.get(a);
        Team teamB = playerTeamMap.get(b);
        return teamA != null && teamA.equals(teamB);
    }

    // ==================== Queries ====================

    /**
     * Get the team a player belongs to, or null.
     */
    public Team getTeam(Player player) {
        return playerTeamMap.get(player.getUniqueId());
    }

    /**
     * Get the team a player UUID belongs to, or null.
     */
    public Team getTeam(UUID uuid) {
        return playerTeamMap.get(uuid);
    }

    /**
     * Get all active teams.
     */
    public Collection<Team> getAllTeams() {
        Set<Team> teams = new HashSet<>(playerTeamMap.values());
        return Collections.unmodifiableCollection(teams);
    }

    /**
     * Get the number of active teams.
     */
    public int getTeamCount() {
        return (int) playerTeamMap.values().stream().distinct().count();
    }

    /**
     * Get total number of players in teams.
     */
    public int getTotalPlayers() {
        return playerTeamMap.size();
    }

    // ==================== Cleanup ====================

    /**
     * Clean up on plugin disable - disband all teams.
     */
    public void cleanup() {
        // Copy to avoid concurrent modification
        Set<Team> teams = new HashSet<>(playerTeamMap.values());
        for (Team team : teams) {
            // Notify online members
            for (UUID uuid : team.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(component(chatPrefix + "&c插件重载，队伍已解散！"));
                }
            }
        }
        playerTeamMap.clear();
        pendingInvites.clear();
        pendingJoinRequests.clear();
        activeSummons.clear();
    }

    // ==================== Helpers ====================

    private ItemStack createPlayerHead(Player player, Team team) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            String leaderMark = team.isLeader(player.getUniqueId()) ? " &6[队长]" : "";
            meta.setDisplayName(colorize("&f" + player.getName() + leaderMark));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7生命: &c" + String.format("%.0f", player.getHealth()) + "&7/&c" + String.format("%.0f", player.getMaxHealth())));
            lore.add(colorize("&7等级: &e" + player.getLevel()));
            if (team.isLeader(player.getUniqueId())) {
                lore.add(colorize("&6⚔ 队长"));
            }
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private int placeSeparators(Inventory gui, int startSlot, int totalSlots) {
        // Place glass pane separators when needed
        int rowStart = (startSlot / 9 + 1) * 9;
        if (rowStart < totalSlots && startSlot % 9 != 0) {
            // Fill current row to end with glass
            for (int i = startSlot; i < rowStart && i < totalSlots; i++) {
                gui.setItem(i, createGlassPane());
            }
            startSlot = rowStart;
        }
        return startSlot;
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack createMenuItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(name));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(component(line));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new java.util.Date(millis));
    }

    // ==================== Static utilities ====================

    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static Component component(String text) {
        return Component.text(ChatColor.translateAlternateColorCodes('&', text))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Create a clickable + hoverable chat button (e.g. [✔ 接受]).
     * @param text    display text (with & color codes)
     * @param hover   tooltip text shown on mouse hover
     * @param command command to run on click (e.g. "/team accept Player")
     */
    public static Component hoverButton(String text, String hover, String command) {
        return Component.text(ChatColor.translateAlternateColorCodes('&', text))
                .decoration(TextDecoration.ITALIC, false)
                .hoverEvent(HoverEvent.showText(
                        Component.text(ChatColor.translateAlternateColorCodes('&', hover))
                                .decoration(TextDecoration.ITALIC, false)
                ))
                .clickEvent(ClickEvent.runCommand(command));
    }

    // ==================== Inner Classes ====================

    /**
     * Tracks a pending invite or join request.
     */
    record TeamInvite(Team team, UUID requesterUuid, UUID targetUuid) {
        String requesterName() {
            Player p = Bukkit.getPlayer(requesterUuid);
            return p != null ? p.getName() : "未知玩家";
        }
        String targetName() {
            Player p = Bukkit.getPlayer(targetUuid);
            return p != null ? p.getName() : "未知玩家";
        }
        UUID leaderUuid() {
            return team.getLeaderUuid();
        }
        String leaderName() {
            return team.getLeaderName();
        }
    }

    /**
     * Tracks an active summon request.
     */
    static class SummonRequest {
        private final Team team;
        private final UUID leaderUuid;
        private final Set<UUID> acceptedPlayers = new HashSet<>();

        SummonRequest(Team team, Player leader) {
            this.team = team;
            this.leaderUuid = leader.getUniqueId();
        }

        boolean hasAccepted(UUID uuid) {
            return acceptedPlayers.contains(uuid);
        }

        void markAccepted(UUID uuid) {
            acceptedPlayers.add(uuid);
        }

        void cancel() {
            acceptedPlayers.clear();
        }
    }
}
