package com.domitg.dragonescape.model;

import org.bukkit.ChatColor;

/**
 * Player classes available in Dragon Escape, each with unique abilities.
 */
public enum PlayerClass {

    WARRIOR(
        "Warrior",
        ChatColor.RED,
        "No special ability. Strong and resilient.",
        "none",
        0
    ),

    SCOUT(
        "Scout",
        ChatColor.YELLOW,
        "Double Jump – press jump while in the air to leap again.",
        "double_jump",
        2
    ),

    MAGE(
        "Mage",
        ChatColor.AQUA,
        "Leap – launch yourself forward with great force.",
        "leap",
        20 // cooldown in seconds
    ),

    ROGUE(
        "Rogue",
        ChatColor.DARK_GREEN,
        "Speed Boost – burst of speed for a few seconds.",
        "speed_boost",
        15
    );

    private final String displayName;
    private final ChatColor color;
    private final String description;
    private final String abilityKey;
    private final int cooldownSeconds;

    PlayerClass(String displayName, ChatColor color, String description,
                String abilityKey, int cooldownSeconds) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.abilityKey = abilityKey;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public String getAbilityKey() {
        return abilityKey;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getFormattedName() {
        return color + displayName;
    }
}
