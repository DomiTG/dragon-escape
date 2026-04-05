package com.domitg.dragonescape.listener;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.manager.GameManager;
import com.domitg.dragonescape.manager.MapManager;
import com.domitg.dragonescape.model.DragonEscapePlayer;
import com.domitg.dragonescape.model.GameState;
import com.domitg.dragonescape.model.PlayerClass;
import com.domitg.dragonescape.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Listens to in-game events: damage, movement, quit, ability usage, etc.
 */
public class GameListener implements Listener {

    private final DragonEscapePlugin plugin;
    private final GameManager gameManager;
    private final MapManager mapManager;

    public GameListener(DragonEscapePlugin plugin, GameManager gameManager, MapManager mapManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.mapManager = mapManager;
    }

    // -------------------------------------------------------------------------
    // Prevent friendly fire / external damage to game players
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!gameManager.isInGame(player)) return;
        if (gameManager.getState() != GameState.IN_GAME) {
            event.setCancelled(true);
            return;
        }

        DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
        if (dep == null || dep.isEliminated()) {
            event.setCancelled(true);
            return;
        }

        // Only dragon breath / dragon fireball hurts in game
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            // Check if it's from the dragon
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                if (byEntity.getDamager() instanceof org.bukkit.entity.EnderDragon) {
                    // Dragon touch → eliminate player
                    event.setCancelled(true);
                    gameManager.eliminatePlayer(dep, "dragon_hit");
                    return;
                }
            }
        }

        // Cancel all other damage (void, lava, etc.) but track fall damage
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            // Teleport player to last safe location or spawn
            Location safe = dep.getLastSafeLocation();
            if (safe == null) safe = mapManager.getSpawnLocation();
            if (safe != null) player.teleport(safe);
            player.sendMessage(MessageUtil.colorize(
                    "&8[&cDragonEscape&8] &cCareful! Teleported back to last checkpoint."));
            return;
        }

        // Prevent all other damage
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent players from hitting each other in the lobby
        if (event.getDamager() instanceof Player attacker
                && event.getEntity() instanceof Player victim) {
            if (gameManager.isInGame(attacker) || gameManager.isInGame(victim)) {
                event.setCancelled(true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Prevent block breaking / placing during game
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Handle item interaction (ability items & class selector)
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isInGame(player)) return;

        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Class selector compass
        if (item.getType() == Material.COMPASS) {
            event.setCancelled(true);
            if (gameManager.getState() != GameState.IN_GAME) {
                gameManager.openClassSelector(player);
            }
            return;
        }

        // Ability items
        if (gameManager.getState() == GameState.IN_GAME) {
            DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
            if (dep == null || dep.isEliminated()) return;

            boolean isAbilityItem = switch (item.getType()) {
                case FEATHER, BLAZE_ROD, SUGAR -> true;
                default -> false;
            };

            if (isAbilityItem) {
                event.setCancelled(true);
                gameManager.useAbility(player);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scout double jump via jump event
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
        if (dep == null) return;
        if (dep.getPlayerClass() != PlayerClass.SCOUT) return;
        if (gameManager.getState() != GameState.IN_GAME) return;
        if (dep.isEliminated()) return;

        // Cancel flight toggle (adventure mode) and use as double jump trigger
        event.setCancelled(true);
        gameManager.tryDoubleJump(player);
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
        if (dep == null) return;
        if (dep.getPlayerClass() != PlayerClass.SCOUT) return;
        if (gameManager.getState() != GameState.IN_GAME) return;
        if (dep.isEliminated()) return;

        // When on ground, restore double jump
        if (player.isOnGround()) {
            dep.setDoubleJumpAvailable(true);
        }
        // Allow flight ability (double jump) when airborne and ability is available
        if (!player.isOnGround() && dep.isDoubleJumpAvailable()) {
            player.setAllowFlight(true);
        }
    }

    // -------------------------------------------------------------------------
    // Player disconnect / quit
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            gameManager.leaveGame(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            gameManager.leaveGame(event.getPlayer());
        }
    }

    // -------------------------------------------------------------------------
    // Item drop prevention
    // -------------------------------------------------------------------------

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Food loss prevention
    // -------------------------------------------------------------------------

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (gameManager.isInGame(player)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Death during game
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!gameManager.isInGame(player)) return;

        DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
        if (dep != null && dep.isActive()) {
            gameManager.eliminatePlayer(dep, "death");
        }
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isInGame(player)) return;

        // Respawn in spectator mode at spawn
        DragonEscapePlayer dep = gameManager.getPlayer(player.getUniqueId());
        if (dep != null) {
            Location spawn = mapManager.getSpawnLocation();
            if (spawn != null) event.setRespawnLocation(spawn);
        }
    }
}
