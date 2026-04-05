package com.domitg.dragonescape;

import com.domitg.dragonescape.command.DragonEscapeCommand;
import com.domitg.dragonescape.listener.GameListener;
import com.domitg.dragonescape.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dragon Escape – a fast-paced parkour minigame where players must outrun
 * the destructive Ender Dragon.
 *
 * <p>Plugin entry point. Wires together all managers and registers
 * commands and event listeners.
 */
public final class DragonEscapePlugin extends JavaPlugin {

    private MapManager mapManager;
    private DragonManager dragonManager;
    private ScoreboardManager scoreboardManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialise managers
        mapManager = new MapManager(this);
        dragonManager = new DragonManager(this, mapManager);
        scoreboardManager = new ScoreboardManager(this);
        gameManager = new GameManager(this, mapManager, dragonManager, scoreboardManager);

        // Register event listeners
        getServer().getPluginManager().registerEvents(
                new GameListener(this, gameManager, mapManager), this);

        // Register commands
        DragonEscapeCommand cmdExecutor = new DragonEscapeCommand(this, gameManager, mapManager);
        getCommand("dragonescape").setExecutor(cmdExecutor);
        getCommand("dragonescape").setTabCompleter(cmdExecutor);

        getLogger().info("DragonEscape has been enabled! May your legs be swift.");
    }

    @Override
    public void onDisable() {
        // Stop any active game cleanly
        if (gameManager != null) {
            gameManager.forceStop();
        }
        if (dragonManager != null) {
            dragonManager.stopDragon();
        }
        if (scoreboardManager != null) {
            scoreboardManager.destroy();
        }
        getLogger().info("DragonEscape has been disabled.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public MapManager getMapManager() { return mapManager; }
    public DragonManager getDragonManager() { return dragonManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public GameManager getGameManager() { return gameManager; }
}
