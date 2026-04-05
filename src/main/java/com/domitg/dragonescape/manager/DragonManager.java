package com.domitg.dragonescape.manager;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.model.DragonEscapePlayer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Manages the Ender Dragon entity that chases players and destroys the terrain.
 *
 * <p>The dragon follows a path built from the map checkpoints. It advances
 * along the path at a configurable speed and, within a configurable radius,
 * removes non-indestructible blocks (simulating the dragon tearing the map apart).
 */
public class DragonManager {

    // Blocks that the dragon cannot destroy (bedrock, barriers, etc.)
    private static final Set<Material> INDESTRUCTIBLE = EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.END_PORTAL_FRAME
    );

    // Blocks the dragon skips (air, liquids)
    private static final Set<Material> SKIP_DESTROY = EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.WATER,
            Material.LAVA
    );

    private final DragonEscapePlugin plugin;
    private final MapManager mapManager;

    private EnderDragon dragon;
    private final List<Location> path = new ArrayList<>();
    private int pathIndex = 0;
    private double distanceTravelled = 0;
    private BukkitRunnable moveTask;
    private BukkitRunnable destroyTask;

    private double speedBlocksPerSecond;
    private int destroyRadius;
    private int destroyIntervalTicks;

    public DragonManager(DragonEscapePlugin plugin, MapManager mapManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Spawns the dragon and starts its movement along the arena path.
     */
    public void startDragon() {
        loadConfig();
        buildPath();

        if (path.isEmpty()) {
            plugin.getLogger().warning("[DragonManager] No path defined – dragon will not move.");
            return;
        }

        Location startLoc = path.get(0).clone().add(0, 10, 0);
        dragon = (EnderDragon) startLoc.getWorld().spawnEntity(startLoc, EntityType.ENDER_DRAGON);
        dragon.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE DRAGON");
        dragon.setCustomNameVisible(true);
        dragon.setPhase(EnderDragon.Phase.HOVER);
        dragon.setAI(false);
        org.bukkit.attribute.AttributeInstance maxHealthAttr = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(1024.0);
        }
        dragon.setHealth(1024.0);
        dragon.setGlowing(true);

        pathIndex = 0;
        distanceTravelled = 0;

        startMovementTask();
        startDestroyTask();
    }

    /**
     * Stops the dragon and cleans up all tasks.
     */
    public void stopDragon() {
        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }
        if (destroyTask != null) {
            destroyTask.cancel();
            destroyTask = null;
        }
        if (dragon != null && !dragon.isDead()) {
            dragon.remove();
            dragon = null;
        }
        path.clear();
        pathIndex = 0;
    }

    // -------------------------------------------------------------------------
    // Dragon movement
    // -------------------------------------------------------------------------

    private void startMovementTask() {
        // Run every tick (20 times/sec), move dragon toward next waypoint
        moveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead()) {
                    cancel();
                    return;
                }
                tickMovement();
            }
        };
        moveTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickMovement() {
        if (pathIndex >= path.size() - 1) return;

        Location current = dragon.getLocation();
        Location target = path.get(pathIndex + 1).clone().add(0, 6, 0);

        Vector direction = target.toVector().subtract(current.toVector());
        double distToNext = direction.length();

        double moveAmount = speedBlocksPerSecond / 20.0; // per tick

        if (distToNext <= moveAmount) {
            // Arrive at waypoint and advance index
            pathIndex = Math.min(pathIndex + 1, path.size() - 1);
            distanceTravelled += distToNext;
            float yaw = (float) (Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
            target.setYaw(yaw);
            dragon.teleport(target);
        } else {
            // Teleport dragon toward next waypoint
            Vector step = direction.normalize().multiply(moveAmount);
            Location newLoc = current.clone().add(step);
            float yaw = (float) (Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
            newLoc.setYaw(yaw);
            dragon.teleport(newLoc);

            distanceTravelled += moveAmount;
        }
    }

    // -------------------------------------------------------------------------
    // Terrain destruction
    // -------------------------------------------------------------------------

    private void startDestroyTask() {
        destroyTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead()) {
                    cancel();
                    return;
                }
                destroyTerrain();
            }
        };
        destroyTask.runTaskTimer(plugin, 0L, destroyIntervalTicks);
    }

    private void destroyTerrain() {
        Location dragonLoc = dragon.getLocation();
        World world = dragonLoc.getWorld();

        int cx = dragonLoc.getBlockX();
        int cy = dragonLoc.getBlockY();
        int cz = dragonLoc.getBlockZ();
        int r = destroyRadius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material mat = block.getType();
                    if (INDESTRUCTIBLE.contains(mat) || SKIP_DESTROY.contains(mat)) continue;

                    // Visual effect before breaking
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            block.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0, 0f);
                    block.setType(Material.AIR, false);
                }
            }
        }

        // Sound effect
        world.playSound(dragonLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.8f);
    }

    // -------------------------------------------------------------------------
    // Path building
    // -------------------------------------------------------------------------

    private void buildPath() {
        path.clear();

        Location spawn = mapManager.getSpawnLocation();
        Location finish = mapManager.getFinishLocation();
        List<Location> checkpoints = mapManager.getCheckpoints();

        if (spawn == null) return;

        path.add(spawn.clone());
        path.addAll(checkpoints);
        if (finish != null) {
            path.add(finish.clone());
        }
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    private void loadConfig() {
        speedBlocksPerSecond = plugin.getConfig().getDouble("dragon-blocks-per-second", 4.0)
                * plugin.getConfig().getDouble("dragon-speed", 1.0);
        destroyRadius = plugin.getConfig().getInt("dragon-destroy-radius", 4);
        destroyIntervalTicks = plugin.getConfig().getInt("dragon-destroy-interval", 5);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isActive() {
        return dragon != null && !dragon.isDead();
    }

    public Location getDragonLocation() {
        return dragon != null ? dragon.getLocation() : null;
    }

    /**
     * Returns the straight-line distance from the dragon to the given location.
     */
    public double distanceTo(Location loc) {
        if (dragon == null) return Double.MAX_VALUE;
        if (!dragon.getLocation().getWorld().equals(loc.getWorld())) return Double.MAX_VALUE;
        return dragon.getLocation().distance(loc);
    }

    /**
     * Returns how far along the path (0–1) the dragon has advanced.
     */
    public double getPathProgress() {
        if (path.size() < 2) return 0;
        return (double) pathIndex / (path.size() - 1);
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public EnderDragon getDragon() {
        return dragon;
    }
}
