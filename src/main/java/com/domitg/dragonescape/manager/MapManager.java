package com.domitg.dragonescape.manager;

import com.domitg.dragonescape.DragonEscapePlugin;
import com.domitg.dragonescape.model.DragonEscapePlayer;
import com.domitg.dragonescape.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages arena configuration: spawn, finish, checkpoints, and waiting room.
 */
public class MapManager {

    private final DragonEscapePlugin plugin;

    private Location waitingRoom;
    private Location spawnLocation;
    private Location finishLocation;
    private final List<Location> checkpoints = new ArrayList<>();

    public MapManager(DragonEscapePlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    // -------------------------------------------------------------------------
    // Config persistence
    // -------------------------------------------------------------------------

    public void loadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();

        waitingRoom = loadLocation(cfg, "waiting-room");
        spawnLocation = loadLocation(cfg, "arena.spawn");
        finishLocation = loadLocation(cfg, "arena.finish");

        checkpoints.clear();
        List<?> rawCheckpoints = cfg.getList("arena.checkpoints");
        if (rawCheckpoints != null) {
            for (Object obj : rawCheckpoints) {
                if (obj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;
                    Location loc = locationFromMap(map);
                    if (loc != null) {
                        checkpoints.add(loc);
                    }
                }
            }
        }
    }

    public void saveToConfig() {
        FileConfiguration cfg = plugin.getConfig();

        saveLocation(cfg, "waiting-room", waitingRoom);
        saveLocation(cfg, "arena.spawn", spawnLocation);
        saveLocation(cfg, "arena.finish", finishLocation);

        List<java.util.Map<String, Object>> checkpointMaps = new ArrayList<>();
        for (Location loc : checkpoints) {
            checkpointMaps.add(locationToMap(loc));
        }
        cfg.set("arena.checkpoints", checkpointMaps);

        plugin.saveConfig();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location loadLocation(FileConfiguration cfg, String path) {
        String worldName = cfg.getString(path + ".world", "");
        if (worldName == null || worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = cfg.getDouble(path + ".x", 0);
        double y = cfg.getDouble(path + ".y", 64);
        double z = cfg.getDouble(path + ".z", 0);
        float yaw = (float) cfg.getDouble(path + ".yaw", 0);
        float pitch = (float) cfg.getDouble(path + ".pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveLocation(FileConfiguration cfg, String path, Location loc) {
        if (loc == null) {
            cfg.set(path + ".world", "");
            return;
        }
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location locationFromMap(java.util.Map<String, Object> map) {
        Object worldObj = map.get("world");
        if (worldObj == null) return null;
        World world = Bukkit.getWorld(worldObj.toString());
        if (world == null) return null;
        double x = toDouble(map.get("x"), 0);
        double y = toDouble(map.get("y"), 64);
        double z = toDouble(map.get("z"), 0);
        return new Location(world, x, y, z);
    }

    private java.util.Map<String, Object> locationToMap(Location loc) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        return map;
    }

    private double toDouble(Object obj, double def) {
        if (obj == null) return def;
        try { return Double.parseDouble(obj.toString()); }
        catch (NumberFormatException e) { return def; }
    }

    // -------------------------------------------------------------------------
    // Setters (called from admin commands)
    // -------------------------------------------------------------------------

    public void setWaitingRoom(Location loc) {
        this.waitingRoom = loc;
        saveToConfig();
    }

    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc;
        cfg().set("arena.world", loc.getWorld().getName());
        saveToConfig();
    }

    public void setFinishLocation(Location loc) {
        this.finishLocation = loc;
        saveToConfig();
    }

    public void addCheckpoint(Location loc) {
        checkpoints.add(loc.clone());
        saveToConfig();
    }

    public void clearCheckpoints() {
        checkpoints.clear();
        saveToConfig();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Location getWaitingRoom() { return waitingRoom; }
    public Location getSpawnLocation() { return spawnLocation; }
    public Location getFinishLocation() { return finishLocation; }
    public List<Location> getCheckpoints() { return new ArrayList<>(checkpoints); }

    public boolean isFullyConfigured() {
        return spawnLocation != null && finishLocation != null;
    }

    /**
     * Teleports a player to the waiting room, or to spawn if no waiting room is set.
     */
    public void sendToWaitingRoom(Player player) {
        Location dest = waitingRoom != null ? waitingRoom : spawnLocation;
        if (dest != null) {
            player.teleport(dest);
        }
    }

    /**
     * Returns the nearest checkpoint index ahead of or at the given location.
     * Used to track dragon position relative to players.
     */
    public int getNearestCheckpointIndex(Location loc) {
        if (checkpoints.isEmpty()) return 0;
        int nearest = 0;
        double nearestDist = Double.MAX_VALUE;
        for (int i = 0; i < checkpoints.size(); i++) {
            double dist = checkpoints.get(i).distanceSquared(loc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    /**
     * Checks whether a location is within the finish zone.
     */
    public boolean isAtFinish(Location loc) {
        if (finishLocation == null) return false;
        if (!loc.getWorld().equals(finishLocation.getWorld())) return false;
        return loc.distanceSquared(finishLocation) <= 9.0; // within 3 blocks
    }
}
