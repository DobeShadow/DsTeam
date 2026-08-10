package com.dobeshadow.dsteam.team;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single team with a leader and members.
 * Thread-safe for Bukkit main-thread usage.
 */
public class Team {

    private final UUID leaderUuid;
    private final String leaderName;
    private final Set<UUID> members; // includes leader
    private final long createdAt;

    public Team(Player leader) {
        this.leaderUuid = leader.getUniqueId();
        this.leaderName = leader.getName();
        this.members = new LinkedHashSet<>();
        this.members.add(leader.getUniqueId());
        this.createdAt = System.currentTimeMillis();
    }

    // ---- Member management ----

    public boolean addMember(Player player) {
        return members.add(player.getUniqueId());
    }

    public boolean removeMember(UUID uuid) {
        if (uuid.equals(leaderUuid)) {
            return false; // can't remove leader this way — use disband
        }
        return members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    // ---- Getters ----

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public String getLeaderName() {
        // Resolve live name if player is online
        Player leader = Bukkit.getPlayer(leaderUuid);
        return leader != null ? leader.getName() : leaderName;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public int getSize() {
        return members.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * @return a set of online player names in this team.
     */
    public Set<String> getOnlineMemberNames() {
        Set<String> names = new LinkedHashSet<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                names.add(player.getName());
            }
        }
        return names;
    }

    /**
     * @return a set of online players in this team.
     */
    public Set<Player> getOnlineMembers() {
        Set<Player> online = new LinkedHashSet<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * @return all member UUIDs that are currently offline.
     */
    public Set<UUID> getOfflineMembers() {
        Set<UUID> offline = new LinkedHashSet<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                offline.add(uuid);
            }
        }
        return offline;
    }

    @Override
    public String toString() {
        return "Team{leader=" + getLeaderName() + ", size=" + members.size() + "}";
    }
}
