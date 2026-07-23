package com.minemc.teamplugin.listener;

import com.minemc.teamplugin.TeamPlugin;
import com.minemc.teamplugin.team.Team;
import com.minemc.teamplugin.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Handles:
 * - PvP protection between teammates (including projectile damage)
 * - GUI click events for team menu and summon accept/deny
 * - Player quit cleanup
 */
public class TeamListener implements Listener {

    private final TeamPlugin plugin;
    private final TeamManager teamManager;

    public TeamListener(TeamPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    // ==================== PvP Protection ====================

    /**
     * Cancel damage between players in the same team.
     * Also handles projectile damage (arrows, tridents, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Player victim = null;
        Player attacker = null;

        // Direct melee attack
        if (event.getDamager() instanceof Player directAttacker) {
            attacker = directAttacker;
        }
        // Projectile attack
        else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                attacker = shooter;
            }
        }

        if (event.getEntity() instanceof Player damaged) {
            victim = damaged;
        }

        if (attacker == null || victim == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Check if same team — cancel damage
        if (teamManager.areInSameTeam(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    // ==================== GUI Clicks ====================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        Inventory inv = event.getInventory();

        // Handle team GUI clicks
        if (title.contains("§8队伍管理") || title.contains("§8队伍信息")) {
            event.setCancelled(true); // prevent item stealing

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;

            String displayName = meta.getDisplayName();
            String stripped = org.bukkit.ChatColor.stripColor(displayName);
            if (stripped == null) return;

            // Detect button by material + display name
            if (clicked.getType() == Material.WRITABLE_BOOK && stripped.contains("邀请")) {
                player.closeInventory();
                player.sendMessage(TeamManager.component("&7使用 &f/team invite <玩家名> &7邀请玩家加入队伍。"));
            } else if (clicked.getType() == Material.ENDER_PEARL && stripped.contains("召集")) {
                player.closeInventory();
                teamManager.summonTeam(player);
            } else if (clicked.getType() == Material.BARRIER && stripped.contains("解散")) {
                player.closeInventory();
                teamManager.disbandTeam(player);
            } else if (clicked.getType() == Material.OAK_DOOR && stripped.contains("离开")) {
                player.closeInventory();
                teamManager.leaveTeam(player);
            }
            // Player heads — clicking a team member does nothing special
            return;
        }

        // Handle summon GUI clicks
        if (title.equals("§8队长召集")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.LIME_WOOL) {
                player.closeInventory();
                teamManager.acceptSummon(player);
            } else if (clicked.getType() == Material.RED_WOOL) {
                player.closeInventory();
                teamManager.denySummon(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("§8队伍管理") || title.contains("§8队伍信息") || title.equals("§8队长召集")) {
            event.setCancelled(true);
        }
    }

    // ==================== Player Quit ====================

    /**
     * When a player quits, if they are the leader and have no other online members,
     * don't auto-disband — just let them stay offline. But if they want to leave
     * gracefully, they should use /team leave or disband first.
     *
     * We just log and notify the team that the player went offline.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Team team = teamManager.getTeam(player);
        if (team == null) return;

        // Notify online team members
        for (Player member : team.getOnlineMembers()) {
            if (!member.getUniqueId().equals(player.getUniqueId())) {
                member.sendMessage(TeamManager.component(
                        "&7[&b组队&7] &e" + player.getName() + " &7已离线。"
                                + (team.isLeader(player.getUniqueId()) ? " &c⚠ 队长离线！" : "")
                ));
            }
        }
    }
}
