package com.domitg.dragonescape.manager;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.model.DragonEscapePlayer;
import com.domitg.dragonescape.model.GameState;
import com.domitg.dragonescape.model.PlayerClass;
import com.domitg.dragonescape.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central game controller. Manages:
 * <ul>
 *   <li>Player registration (join/leave)</li>
 *   <li>Game state transitions (WAITING → STARTING → IN_GAME → ENDING)</li>
 *   <li>Countdown and game-tick loops</li>
 *   <li>Win/lose detection</li>
 *   <li>Player ability execution</li>
 * </ul>
 */
public class GameManager {

    private final DragonEscapePlugin plugin;
    private final MapManager mapManager;
    private final DragonManager dragonManager;
    private final ScoreboardManager scoreboardManager;

    private GameState state = GameState.WAITING;

    /** All registered players, including eliminated/winners. */
    private final Map<UUID, DragonEscapePlayer> players = new LinkedHashMap<>();

    private BukkitRunnable countdownTask;
    private BukkitRunnable gameTickTask;
    private BukkitRunnable scoreboardTask;
    private BukkitRunnable particleTask;

    private int countdownSeconds;
    private int gameTimeSeconds = 0;
    private int maxGameDurationSeconds;
    private int minPlayers;
    private int maxPlayers;

    public GameManager(DragonEscapePlugin plugin, MapManager mapManager,
                       DragonManager dragonManager, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.dragonManager = dragonManager;
        this.scoreboardManager = scoreboardManager;
        loadConfig();
    }

    private void loadConfig() {
        countdownSeconds = plugin.getConfig().getInt("start-countdown", 30);
        maxGameDurationSeconds = plugin.getConfig().getInt("game-max-duration", 600);
        minPlayers = plugin.getConfig().getInt("min-players", 2);
        maxPlayers = plugin.getConfig().getInt("max-players", 10);
    }

    // =========================================================================
    // Join / Leave
    // =========================================================================

    public boolean joinGame(Player player) {
        if (state == GameState.IN_GAME || state == GameState.ENDING) {
            player.sendMessage(msg("already-in-game"));
            return false;
        }
        if (players.containsKey(player.getUniqueId())) {
            player.sendMessage(msg("already-in-game"));
            return false;
        }
        if (players.size() >= maxPlayers) {
            player.sendMessage(msg("game-full"));
            return false;
        }
        if (!mapManager.isFullyConfigured()) {
            player.sendMessage(msg("no-arena-configured"));
            return false;
        }

        DragonEscapePlayer dep = new DragonEscapePlayer(player);
        players.put(player.getUniqueId(), dep);

        broadcastGame(MessageUtil.format(msg("player-joined"),
                "player", player.getName(),
                "current", players.size(),
                "max", maxPlayers));

        mapManager.sendToWaitingRoom(player);
        giveClassSelectorItem(player);

        if (state == GameState.WAITING && players.size() >= minPlayers) {
            startCountdown();
        }
        return true;
    }

    public void leaveGame(Player player) {
        DragonEscapePlayer dep = players.remove(player.getUniqueId());
        if (dep == null) return;

        restorePlayer(player);

        broadcastGame(MessageUtil.format(msg("player-left"), "player", player.getName()));

        if (state == GameState.IN_GAME) {
            checkEndConditions();
        } else if (state == GameState.STARTING && players.size() < minPlayers) {
            cancelCountdown();
        }
    }

    // =========================================================================
    // Countdown
    // =========================================================================

    private void startCountdown() {
        if (state == GameState.STARTING) return;
        state = GameState.STARTING;
        int[] remaining = {countdownSeconds};

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (players.size() < minPlayers) {
                    cancelCountdown();
                    return;
                }
                if (remaining[0] <= 0) {
                    cancel();
                    startGame();
                    return;
                }
                if (remaining[0] <= 5 || remaining[0] % 10 == 0) {
                    broadcastGame(MessageUtil.format(msg("game-starting"),
                            "time", remaining[0]));
                    broadcastTitle(ChatColor.GREEN + "Starting in",
                            "" + ChatColor.WHITE + remaining[0] + "s",
                            0, 22, 5);
                }
                remaining[0]--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = GameState.WAITING;
        broadcastGame(MessageUtil.colorize("&cNot enough players – countdown cancelled."));
    }

    // =========================================================================
    // Game start / tick / end
    // =========================================================================

    private void startGame() {
        if (state == GameState.IN_GAME) return;
        state = GameState.IN_GAME;
        gameTimeSeconds = 0;

        // Init scoreboard first, then teleport players
        scoreboardManager.init();

        // Teleport all players to spawn and prepare them
        Location spawn = mapManager.getSpawnLocation();
        for (DragonEscapePlayer dep : players.values()) {
            Player p = dep.getPlayer();
            p.teleport(spawn);
            preparePlayer(p);
            scoreboardManager.showToPlayer(p);
        }

        broadcastGame(msg("game-started"));
        broadcastTitle(ChatColor.DARK_RED + "DRAGON ESCAPE!",
                ChatColor.RED + "Run for your life!", 10, 60, 20);

        // Dragon starts after a short delay
        int dragonDelay = plugin.getConfig().getInt("dragon-start-delay", 100);
        Bukkit.getScheduler().runTaskLater(plugin, dragonManager::startDragon, dragonDelay);

        startGameTickTask();
        startScoreboardTask();
        startParticleTask();
    }

    private void startParticleTask() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.IN_GAME) {
                    cancel();
                    return;
                }
                spawnMarkerParticles();
            }
        };
        particleTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnMarkerParticles() {
        // Checkpoint markers: cyan END_ROD ring
        final int checkpointRingCount = 8;
        final int finishRingCount = 12;

        List<Location> checkpoints = mapManager.getCheckpoints();
        for (Location cp : checkpoints) {
            World w = cp.getWorld();
            if (w == null) continue;
            for (int i = 0; i < checkpointRingCount; i++) {
                double angle = (2 * Math.PI / checkpointRingCount) * i;
                double ox = Math.cos(angle) * 1.5;
                double oz = Math.sin(angle) * 1.5;
                w.spawnParticle(Particle.END_ROD, cp.clone().add(ox, 1, oz), 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.VILLAGER_HAPPY, cp.clone().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
        }

        // Finish marker: firework-like TOTEM ring + beacon column
        Location finish = mapManager.getFinishLocation();
        if (finish != null && finish.getWorld() != null) {
            World w = finish.getWorld();
            for (int i = 0; i < finishRingCount; i++) {
                double angle = (2 * Math.PI / finishRingCount) * i;
                double ox = Math.cos(angle) * 2.0;
                double oz = Math.sin(angle) * 2.0;
                w.spawnParticle(Particle.FIREWORKS_SPARK, finish.clone().add(ox, 1, oz), 2, 0, 0.2, 0, 0.05);
            }
            // Rising column above finish
            for (int y = 0; y <= 4; y++) {
                w.spawnParticle(Particle.TOTEM, finish.clone().add(0, y, 0), 2, 0.3, 0, 0.3, 0);
            }
        }
    }

    private void startGameTickTask() {
        gameTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.IN_GAME) {
                    cancel();
                    return;
                }
                gameTimeSeconds++;
                tickGame();
                if (gameTimeSeconds >= maxGameDurationSeconds) {
                    endGame(Collections.emptyList(), false);
                }
            }
        };
        gameTickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startScoreboardTask() {
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.IN_GAME) {
                    cancel();
                    return;
                }
                updateScoreboard();
                tickPlayerAbilityBar();
            }
        };
        scoreboardTask.runTaskTimer(plugin, 0L, 5L);
    }

    private void tickGame() {
        if (!dragonManager.isActive()) return;

        Location dragonLoc = dragonManager.getDragonLocation();

        for (DragonEscapePlayer dep : new ArrayList<>(players.values())) {
            if (!dep.isActive()) continue;

            Player p = dep.getPlayer();

            // Update checkpoint progress
            int cpIdx = mapManager.getNearestCheckpointIndex(p.getLocation());
            if (cpIdx > dep.getCheckpointIndex()) {
                dep.setCheckpointIndex(cpIdx);
                dep.setLastSafeLocation(p.getLocation());
            }

            // Check if dragon has caught the player
            double distToDragon = dragonManager.distanceTo(p.getLocation());
            if (distToDragon < 4.0) {
                eliminatePlayer(dep, "dragon");
                continue;
            }

            // If player is behind the dragon (dragon has passed their position)
            // by checking dragon path index vs player checkpoint
            if (dragonManager.getPathIndex() > dep.getCheckpointIndex() + 2) {
                eliminatePlayer(dep, "left_behind");
            }

            // Check win condition
            if (mapManager.isAtFinish(p.getLocation())) {
                winPlayer(dep);
            }

            // Reset Scout double jump when player hits ground
            if (dep.getPlayerClass() == PlayerClass.SCOUT
                    && p.isOnGround() && !dep.isDoubleJumpAvailable()) {
                dep.setDoubleJumpAvailable(true);
            }
        }

        checkEndConditions();
    }

    private void updateScoreboard() {
        double dragonDist = players.values().stream()
                .filter(DragonEscapePlayer::isActive)
                .mapToDouble(dep -> dragonManager.distanceTo(dep.getPlayer().getLocation()))
                .min()
                .orElse(0);

        scoreboardManager.update(players.values(), -1, gameTimeSeconds, dragonDist);
    }

    private void tickPlayerAbilityBar() {
        for (DragonEscapePlayer dep : players.values()) {
            if (!dep.isActive()) continue;
            Player p = dep.getPlayer();
            PlayerClass pc = dep.getPlayerClass();
            if (pc == PlayerClass.WARRIOR) continue;

            String bar;
            if (dep.isAbilityOnCooldown()) {
                long remainSec = dep.getRemainingCooldownMillis() / 1000L;
                bar = ChatColor.GRAY + "Ability cooldown: " + ChatColor.RED + remainSec + "s";
            } else {
                bar = pc.getColor() + pc.getDisplayName() + " ability " + ChatColor.GREEN + "READY"
                        + ChatColor.GRAY + " [Sneak + Jump]";
            }
            p.sendActionBar(Component.text(bar));
        }
    }

    // =========================================================================
    // Win / Elimination
    // =========================================================================

    private void winPlayer(DragonEscapePlayer dep) {
        dep.setWinner(true);
        Player p = dep.getPlayer();

        broadcastGame(MessageUtil.format(msg("player-won"), "player", p.getName()));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendTitle(ChatColor.GOLD + "YOU ESCAPED!", ChatColor.GREEN + "You survived the dragon!");

        checkEndConditions();
    }

    public void eliminatePlayer(DragonEscapePlayer dep, String reason) {
        if (!dep.isActive()) return;
        dep.setEliminated(true);

        Player p = dep.getPlayer();
        broadcastGame(MessageUtil.format(msg("player-eliminated"), "player", p.getName()));

        p.setGameMode(GameMode.SPECTATOR);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.5f);
        p.sendTitle(ChatColor.DARK_RED + "ELIMINATED!", ChatColor.RED + "The dragon got you!");

        // Particle burst at player location
        p.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                p.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1, 0f);

        checkEndConditions();
    }

    private void checkEndConditions() {
        if (state != GameState.IN_GAME) return;

        List<DragonEscapePlayer> active = players.values().stream()
                .filter(DragonEscapePlayer::isActive)
                .collect(Collectors.toList());

        List<DragonEscapePlayer> winners = players.values().stream()
                .filter(DragonEscapePlayer::isWinner)
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            // All players are either winners or eliminated
            endGame(winners, true);
        }
    }

    public void endGame(List<DragonEscapePlayer> winners, boolean natural) {
        if (state == GameState.ENDING) return;
        state = GameState.ENDING;

        stopTasks();
        dragonManager.stopDragon();

        if (winners.isEmpty()) {
            broadcastGame(msg("game-ended-no-winner"));
            broadcastTitle(ChatColor.DARK_RED + "GAME OVER",
                    ChatColor.RED + "No one escaped!", 10, 80, 20);
        } else {
            String winnerNames = winners.stream()
                    .map(dep -> dep.getPlayer().getName())
                    .collect(Collectors.joining(", "));
            broadcastGame(MessageUtil.format(msg("game-ended-winners"), "winners", winnerNames));
            broadcastTitle(ChatColor.GOLD + "GAME OVER",
                    ChatColor.GREEN + "Winners: " + winnerNames, 10, 80, 20);
        }

        // Clean up after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 100L);
    }

    private void resetGame() {
        for (DragonEscapePlayer dep : players.values()) {
            restorePlayer(dep.getPlayer());
            mapManager.sendToWaitingRoom(dep.getPlayer());
            scoreboardManager.resetPlayerScoreboard(dep.getPlayer());
        }
        players.clear();
        scoreboardManager.destroy();
        state = GameState.WAITING;
        gameTimeSeconds = 0;
    }

    // =========================================================================
    // Player abilities
    // =========================================================================

    /**
     * Triggers the active ability for a player (called on sneak+jump).
     */
    public void useAbility(Player player) {
        DragonEscapePlayer dep = players.get(player.getUniqueId());
        if (dep == null || !dep.isActive()) return;
        if (state != GameState.IN_GAME) return;
        if (dep.isAbilityOnCooldown()) {
            long sec = dep.getRemainingCooldownMillis() / 1000L;
            player.sendMessage(msg("prefix") + ChatColor.RED + "Ability on cooldown for " + sec + "s!");
            return;
        }

        switch (dep.getPlayerClass()) {
            case SCOUT -> executeDoubleJump(player, dep);
            case MAGE -> executeLeap(player, dep);
            case ROGUE -> executeSpeedBoost(player, dep);
            default -> { }
        }
    }

    /**
     * Scout double jump – triggered on second jump in air.
     */
    public void tryDoubleJump(Player player) {
        DragonEscapePlayer dep = players.get(player.getUniqueId());
        if (dep == null || !dep.isActive()) return;
        if (dep.getPlayerClass() != PlayerClass.SCOUT) return;
        if (!dep.isDoubleJumpAvailable()) return;

        dep.setDoubleJumpAvailable(false);
        dep.startAbilityCooldown();

        Vector vel = player.getLocation().getDirection().normalize().multiply(1.0);
        vel.setY(0.7);
        player.setVelocity(vel);

        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);
    }

    private void executeDoubleJump(Player player, DragonEscapePlayer dep) {
        // Scout's item ability: high vertical jump with a forward burst
        dep.startAbilityCooldown();

        Vector direction = player.getLocation().getDirection().normalize().multiply(0.8);
        direction.setY(1.2); // strong upward boost
        player.setVelocity(direction);

        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().add(0, 1, 0), 20, 0.3, 0.1, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);

        player.sendMessage(msg("prefix") + dep.getPlayerClass().getColor() + "High Jump!");
    }

    private void executeLeap(Player player, DragonEscapePlayer dep) {
        dep.startAbilityCooldown();

        Vector direction = player.getLocation().getDirection().normalize();
        direction.multiply(2.2).setY(0.6);
        player.setVelocity(direction);

        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 1.0);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 0.8f);

        player.sendMessage(msg("prefix") + dep.getPlayerClass().getColor() + "Leap!");
    }

    private void executeSpeedBoost(Player player, DragonEscapePlayer dep) {
        dep.startAbilityCooldown();

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, false, true));

        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);

        player.sendMessage(msg("prefix") + dep.getPlayerClass().getColor() + "Speed Boost!");
    }

    // =========================================================================
    // Player preparation / cleanup
    // =========================================================================

    private void preparePlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExp(0f);
        player.setLevel(0);
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

        // Give ability item
        DragonEscapePlayer dep = players.get(player.getUniqueId());
        if (dep != null && dep.getPlayerClass() != PlayerClass.WARRIOR) {
            ItemStack abilityItem = createAbilityItem(dep.getPlayerClass());
            player.getInventory().setItem(0, abilityItem);
        }
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.setHealth(20.0);
        player.setFoodLevel(20);
    }

    private void giveClassSelectorItem(Player player) {
        // Compass opens class selection GUI
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("Class Selector", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Right-click to choose your class", NamedTextColor.GRAY)
        ));
        compass.setItemMeta(meta);
        player.getInventory().setItem(4, compass);
    }

    private ItemStack createAbilityItem(PlayerClass pc) {
        Material mat = switch (pc) {
            case SCOUT -> Material.FEATHER;
            case MAGE -> Material.BLAZE_ROD;
            case ROGUE -> Material.SUGAR;
            default -> Material.STICK;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(pc.getDisplayName() + " Ability", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text(pc.getDescription(), NamedTextColor.GRAY),
                Component.text("Right-click to activate", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // Class selection
    // =========================================================================

    public void openClassSelector(Player player) {
        // Simple chat-based class selection
        DragonEscapePlayer dep = players.get(player.getUniqueId());
        if (dep == null) return;

        player.sendMessage(MessageUtil.colorize("&8&m------------------------------"));
        player.sendMessage(MessageUtil.colorize("&6&lChoose Your Class:"));
        player.sendMessage("");
        for (PlayerClass pc : PlayerClass.values()) {
            player.sendMessage(pc.getColor() + pc.getDisplayName() + ChatColor.GRAY + " – " + pc.getDescription());
            player.sendMessage(ChatColor.GRAY + "  /de class " + pc.name().toLowerCase());
        }
        player.sendMessage(MessageUtil.colorize("&8&m------------------------------"));
    }

    public void selectClass(Player player, String className) {
        DragonEscapePlayer dep = players.get(player.getUniqueId());
        if (dep == null) {
            player.sendMessage(msg("not-in-game"));
            return;
        }
        if (state == GameState.IN_GAME) {
            player.sendMessage(msg("prefix") + ChatColor.RED + "You cannot change class during the game!");
            return;
        }
        try {
            PlayerClass pc = PlayerClass.valueOf(className.toUpperCase());
            dep.setPlayerClass(pc);
            player.sendMessage(msg("prefix") + pc.getColor() + "Selected class: " + pc.getDisplayName());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        } catch (IllegalArgumentException e) {
            player.sendMessage(msg("prefix") + ChatColor.RED + "Unknown class: " + className);
        }
    }

    // =========================================================================
    // Admin: force start / stop
    // =========================================================================

    public void forceStart() {
        if (state == GameState.STARTING && countdownTask != null) {
            countdownTask.cancel();
        }
        startGame();
    }

    public void forceStop() {
        if (state == GameState.IN_GAME || state == GameState.STARTING) {
            endGame(Collections.emptyList(), false);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stopTasks() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (gameTickTask != null) { gameTickTask.cancel(); gameTickTask = null; }
        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
    }

    private void broadcastGame(String message) {
        String colored = MessageUtil.colorize(message);
        for (DragonEscapePlayer dep : players.values()) {
            dep.getPlayer().sendMessage(colored);
        }
    }

    private void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (DragonEscapePlayer dep : players.values()) {
            dep.getPlayer().sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private String msg(String key) {
        String prefix = MessageUtil.colorize(
                plugin.getConfig().getString("messages.prefix", "&8[&cDragonEscape&8] &r"));
        String val = plugin.getConfig().getString("messages." + key, "");
        return prefix + MessageUtil.colorize(val);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public GameState getState() { return state; }

    public Map<UUID, DragonEscapePlayer> getPlayers() { return Collections.unmodifiableMap(players); }

    public DragonEscapePlayer getPlayer(UUID uuid) { return players.get(uuid); }

    public boolean isInGame(Player player) { return players.containsKey(player.getUniqueId()); }

    public int getPlayerCount() { return players.size(); }

    public int getMinPlayers() { return minPlayers; }

    public int getMaxPlayers() { return maxPlayers; }

    public int getGameTimeSeconds() { return gameTimeSeconds; }
}
