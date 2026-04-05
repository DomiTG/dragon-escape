package com.domitg.dragonescape.command;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.manager.GameManager;
import com.domitg.dragonescape.manager.MapManager;
import com.domitg.dragonescape.model.GameState;
import com.domitg.dragonescape.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles the /dragonescape (alias /de) command.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>/de join                   – join the lobby</li>
 *   <li>/de leave                  – leave the game</li>
 *   <li>/de class <name>           – pick your player class</li>
 *   <li>/de start                  – (admin) force start</li>
 *   <li>/de stop                   – (admin) force stop</li>
 *   <li>/de setlobby               – (admin) set waiting room</li>
 *   <li>/de setspawn               – (admin) set arena spawn</li>
 *   <li>/de setfinish              – (admin) set finish portal</li>
 *   <li>/de addcheckpoint          – (admin) add a path checkpoint</li>
 *   <li>/de clearcheckpoints       – (admin) remove all checkpoints</li>
 *   <li>/de info                   – show current game info</li>
 * </ul>
 */
public class DragonEscapeCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "dragonescape.admin";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED
            + "DragonEscape" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private final DragonEscapePlugin plugin;
    private final GameManager gameManager;
    private final MapManager mapManager;

    public DragonEscapeCommand(DragonEscapePlugin plugin, GameManager gameManager,
                               MapManager mapManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.mapManager = mapManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "class" -> handleClass(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "setlobby" -> handleSetLobby(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "setfinish" -> handleSetFinish(sender);
            case "addcheckpoint" -> handleAddCheckpoint(sender);
            case "clearcheckpoints" -> handleClearCheckpoints(sender);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private void handleJoin(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        gameManager.joinGame(player);
    }

    private void handleLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!gameManager.isInGame(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You are not in a game.");
            return;
        }
        gameManager.leaveGame(player);
        player.sendMessage(PREFIX + ChatColor.GREEN + "You left the game.");
    }

    private void handleClass(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            gameManager.openClassSelector(player);
            return;
        }
        gameManager.selectClass(player, args[1]);
    }

    private void handleStart(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return;
        }
        if (gameManager.getState() == GameState.IN_GAME) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Game is already running.");
            return;
        }
        if (!mapManager.isFullyConfigured()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Arena is not fully configured!");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Force starting the game...");
        gameManager.forceStart();
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Stopping the game...");
        gameManager.forceStop();
    }

    private void handleSetLobby(CommandSender sender) {
        Player player = requireAdmin(sender);
        if (player == null) return;
        mapManager.setWaitingRoom(player.getLocation());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Waiting room set to your current location.");
    }

    private void handleSetSpawn(CommandSender sender) {
        Player player = requireAdmin(sender);
        if (player == null) return;
        mapManager.setSpawnLocation(player.getLocation());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Arena spawn set to your current location.");
    }

    private void handleSetFinish(CommandSender sender) {
        Player player = requireAdmin(sender);
        if (player == null) return;
        mapManager.setFinishLocation(player.getLocation());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Finish location set to your current location.");
    }

    private void handleAddCheckpoint(CommandSender sender) {
        Player player = requireAdmin(sender);
        if (player == null) return;
        mapManager.addCheckpoint(player.getLocation());
        int total = mapManager.getCheckpoints().size();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Checkpoint #" + total + " added at your location.");
    }

    private void handleClearCheckpoints(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return;
        }
        mapManager.clearCheckpoints();
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "All checkpoints cleared.");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "--- Dragon Escape Info ---");
        sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + gameManager.getState());
        sender.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.WHITE
                + gameManager.getPlayerCount() + "/" + gameManager.getMaxPlayers());
        sender.sendMessage(ChatColor.YELLOW + "Arena configured: " + ChatColor.WHITE
                + mapManager.isFullyConfigured());
        sender.sendMessage(ChatColor.YELLOW + "Checkpoints: " + ChatColor.WHITE
                + mapManager.getCheckpoints().size());
        if (gameManager.getState() == GameState.IN_GAME) {
            sender.sendMessage(ChatColor.YELLOW + "Game time: " + ChatColor.WHITE
                    + MessageUtil.formatDuration(gameManager.getGameTimeSeconds()));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return;
        }
        plugin.reloadConfig();
        mapManager.loadFromConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "=== Dragon Escape Commands ===");
        sendCmd(sender, "/de join", "Join the game lobby");
        sendCmd(sender, "/de leave", "Leave the current game");
        sendCmd(sender, "/de class <name>", "Choose your class");
        sendCmd(sender, "/de info", "Show game information");
        if (sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(ChatColor.RED + "--- Admin ---");
            sendCmd(sender, "/de start", "Force start the game");
            sendCmd(sender, "/de stop", "Force stop the game");
            sendCmd(sender, "/de setlobby", "Set the waiting room");
            sendCmd(sender, "/de setspawn", "Set player spawn");
            sendCmd(sender, "/de setfinish", "Set the finish location");
            sendCmd(sender, "/de addcheckpoint", "Add a dragon path checkpoint");
            sendCmd(sender, "/de clearcheckpoints", "Remove all checkpoints");
            sendCmd(sender, "/de reload", "Reload config");
        }
    }

    private void sendCmd(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(ChatColor.GOLD + cmd + ChatColor.GRAY + " – " + desc);
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of("join", "leave", "class", "info"));
            if (sender.hasPermission(ADMIN_PERM)) {
                completions.addAll(List.of("start", "stop", "setlobby", "setspawn",
                        "setfinish", "addcheckpoint", "clearcheckpoints", "reload"));
            }
            return filter(completions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("class")) {
            List<String> classes = new ArrayList<>();
            for (com.domitg.dragonescape.model.PlayerClass pc
                    : com.domitg.dragonescape.model.PlayerClass.values()) {
                classes.add(pc.name().toLowerCase());
            }
            return filter(classes, args[1]);
        }
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "This command is player-only.");
            return null;
        }
        return player;
    }

    private Player requireAdmin(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return null;
        if (!player.hasPermission(ADMIN_PERM)) {
            player.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return null;
        }
        return player;
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) result.add(s);
        }
        return result;
    }
}
