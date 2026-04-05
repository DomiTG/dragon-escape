package com.domitg.dragonescape.util;

import org.bukkit.ChatColor;

/**
 * Utility class for message formatting and colorization.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /**
     * Translates color codes using '&' as the color char.
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Formats a message by replacing placeholders.
     * Example: format("Hello {name}", "name", "World") → "Hello World"
     */
    public static String format(String template, Object... args) {
        String result = template;
        for (int i = 0; i + 1 < args.length; i += 2) {
            result = result.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return result;
    }

    /**
     * Formats seconds into a human-readable duration string (e.g., "1m 30s").
     */
    public static String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }
}
