package com.domitg.dragonescape.model;

import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * Holds per-player game data for a Dragon Escape session.
 */
public class DragonEscapePlayer {

    private final Player player;
    private PlayerClass playerClass;
    private boolean eliminated;
    private boolean winner;
    private long abilityCooldownEnd;
    private boolean doubleJumpAvailable;
    private int checkpointIndex;
    private Location lastSafeLocation;

    public DragonEscapePlayer(Player player) {
        this.player = player;
        this.playerClass = PlayerClass.WARRIOR;
        this.eliminated = false;
        this.winner = false;
        this.abilityCooldownEnd = 0L;
        this.doubleJumpAvailable = false;
        this.checkpointIndex = 0;
        this.lastSafeLocation = player.getLocation().clone();
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(PlayerClass playerClass) {
        this.playerClass = playerClass;
        // Scout always has double jump available when class is set
        this.doubleJumpAvailable = (playerClass == PlayerClass.SCOUT);
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

    public boolean isAbilityOnCooldown() {
        return System.currentTimeMillis() < abilityCooldownEnd;
    }

    public long getRemainingCooldownMillis() {
        long remaining = abilityCooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void startAbilityCooldown() {
        long cooldownMs = (long) playerClass.getCooldownSeconds() * 1000L;
        this.abilityCooldownEnd = System.currentTimeMillis() + cooldownMs;
    }

    public boolean isDoubleJumpAvailable() {
        return doubleJumpAvailable;
    }

    public void setDoubleJumpAvailable(boolean doubleJumpAvailable) {
        this.doubleJumpAvailable = doubleJumpAvailable;
    }

    public int getCheckpointIndex() {
        return checkpointIndex;
    }

    public void setCheckpointIndex(int checkpointIndex) {
        this.checkpointIndex = checkpointIndex;
    }

    public Location getLastSafeLocation() {
        return lastSafeLocation;
    }

    public void setLastSafeLocation(Location lastSafeLocation) {
        this.lastSafeLocation = lastSafeLocation.clone();
    }

    public boolean isActive() {
        return !eliminated && !winner;
    }
}
