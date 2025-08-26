package me.qiooip.lazarus.glass;

import me.qiooip.lazarus.Lazarus;
import me.qiooip.lazarus.config.Config;
import me.qiooip.lazarus.factions.FactionsManager;
import me.qiooip.lazarus.factions.claim.Claim;
import me.qiooip.lazarus.factions.claim.ClaimManager;
import me.qiooip.lazarus.factions.type.PlayerFaction;
import me.qiooip.lazarus.factions.type.RoadFaction;
import me.qiooip.lazarus.timer.TimerManager;
import me.qiooip.lazarus.utils.ManagerEnabler;
import me.qiooip.lazarus.utils.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class GlassManager implements Listener, ManagerEnabler {

    private static final int WALL_BORDER_HEIGHT = 4;
    private static final int WALL_BORDER_WIDTH = 5;

    private ScheduledThreadPoolExecutor executor;
    private ScheduledFuture<?> updater;

    private final Map<UUID, Map<Location, GlassInfo>> glassCache;
    private final Set<Material> overriddenBlocks;

    public GlassManager() {
        this.glassCache = new ConcurrentHashMap<>();

        this.overriddenBlocks = EnumSet.of(Material.AIR, Material.LONG_GRASS, Material.DOUBLE_PLANT,
            Material.YELLOW_FLOWER, Material.RED_ROSE, Material.VINE);

        Tasks.syncLater(this::setupTasks, 10L);
        Bukkit.getPluginManager().registerEvents(this, Lazarus.getInstance());
    }

    private void setupTasks() {
        this.executor = new ScheduledThreadPoolExecutor(2, Tasks.newThreadFactory("Glass Thread - %d"));
        this.executor.setRemoveOnCancelPolicy(true);

        this.updater = this.executor.scheduleAtFixedRate(new GlassUpdater(), 0L, 100L, TimeUnit.MILLISECONDS);
    }

    public void disable() {
        this.glassCache.clear();

        if(this.updater != null) this.updater.cancel(true);
        if(this.executor != null) this.executor.shutdownNow();
    }

    private void addGlassInfo(Player player, GlassInfo info) {
        this.glassCache.computeIfAbsent(player.getUniqueId(),
            t -> new ConcurrentHashMap<>()).put(info.getLocation(), info);
    }

    private void clearGlassInfoForPlayer(Player player) {
        this.glassCache.remove(player.getUniqueId());
    }

    private Map<Location, GlassInfo> getGlassInfoForPlayer(Player player) {
        return this.glassCache.get(player.getUniqueId());
    }

    public GlassInfo getGlassAt(Player player, Location location) {
        Map<Location, GlassInfo> glassInfo = this.glassCache.get(player.getUniqueId());
        return glassInfo != null ? glassInfo.get(location) : null;
    }

    public void generateGlassVisual(Player player, GlassInfo info) {
        if(this.getGlassAt(player, info.getLocation()) != null) return;

        Location location = info.getLocation();
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;

        if(!location.getWorld().isChunkLoaded(x, z)) return;

        location.getWorld().getChunkAtAsync(x, z, (chunk) -> {
            Material material = location.getBlock().getType();
            if(!this.overriddenBlocks.contains(material)) return;

            player.sendBlockChange(location, info.getMaterial(), info.getData());
            this.addGlassInfo(player, info);
        });
    }

    public void clearGlassVisuals(Player player, GlassType type) {
        this.clearGlassVisuals(player, glassInfo -> glassInfo.getType() == type);
    }

    public void clearGlassVisuals(Player player, GlassType type, Predicate<GlassInfo> predicate) {
        this.clearGlassVisuals(player, glassInfo -> glassInfo.getType() == type && predicate.test(glassInfo));
    }

    private void clearGlassVisuals(Player player, Predicate<GlassInfo> predicate) {
        Map<Location, GlassInfo> glassInfo = this.getGlassInfoForPlayer(player);
        if(glassInfo == null) return;

        Iterator<Entry<Location, GlassInfo>> iterator = glassInfo.entrySet().iterator();

        while(iterator.hasNext()) {
            Entry<Location, GlassInfo> entry = iterator.next();
            if(!predicate.test(entry.getValue())) continue;

            Location location = entry.getKey();

            if(!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                iterator.remove();
                continue;
            }

            player.sendBlockChange(entry.getKey(), location.getBlock().getType(), location.getBlock().getData());
            iterator.remove();
        }
    }

    private GlassType getGlassType(Player player, GlassType forced) {
        if(forced != null) {
            return forced;
        } else if(TimerManager.getInstance().getCombatTagTimer().isActive(player)) {
            return GlassType.SPAWN_WALL;
        } else if(TimerManager.getInstance().getPvpProtTimer().isActive(player)) {
            return GlassType.CLAIM_WALL;
        } else {
            return null;
        }
    }

    private void handlePlayerMove(Player player, Location from, Location to) {
        this.handlePlayerMove(player, from, to, null);
    }

    private void handlePlayerMove(Player player, Location from, Location to, GlassType forced) {
        GlassType type = this.getGlassType(player, forced);
        if(type == null) return;

        this.clearGlassVisuals(player, type, glassInfo -> {
            Location loc = glassInfo.getLocation();

            return loc.getWorld().getName().equals(to.getWorld().getName())
                && (Math.abs(loc.getBlockX() - to.getBlockX()) > WALL_BORDER_WIDTH
                || Math.abs(loc.getBlockY() - to.getBlockY()) > WALL_BORDER_HEIGHT
                || Math.abs(loc.getBlockZ() - to.getBlockZ()) > WALL_BORDER_WIDTH);
        });

        Set<Claim> claims = ClaimManager.getInstance().getClaimsInSelection(to.getWorld(),
            to.getBlockX() - WALL_BORDER_WIDTH, to.getBlockX() + WALL_BORDER_WIDTH,
            to.getBlockZ() - WALL_BORDER_WIDTH, to.getBlockZ() + WALL_BORDER_WIDTH);

        if(claims.isEmpty()) return;

        if(type == GlassType.SPAWN_WALL) {
            claims.removeIf(claim -> !claim.getOwner().isSafezone());
        } else {
            PlayerFaction playerFaction = FactionsManager.getInstance().getPlayerFaction(player);

            claims.removeIf(claim -> claim.getOwner() instanceof RoadFaction || claim.getOwner().isSafezone()
                || (Config.PVP_PROTECTION_CAN_ENTER_OWN_CLAIM && claim.getOwner() == playerFaction));
        }

        claims.forEach(claim -> claim.getClosestSides(to).forEach(side -> {
            for(int y = -WALL_BORDER_HEIGHT + 1; y <= WALL_BORDER_HEIGHT; y++) {
                Location location = side.clone();
                location.setY(to.getBlockY() + y);

                this.generateGlassVisual(player, new GlassInfo(type, location, Material.STAINED_GLASS, (byte) 14));
            }
        }));
    }
    
    private boolean isPlayerInsideGlassWall(Player player, Claim claim) {
        Location playerLoc = player.getLocation();
        
        double px = playerLoc.getX();
        double pz = playerLoc.getZ();
        
        // Check if player is inside the claim
        boolean insideClaim = px >= claim.getMinX() && px <= claim.getMaxX() && 
                             pz >= claim.getMinZ() && pz <= claim.getMaxZ();
        
        if(!insideClaim) {
            return false; // Player is outside, no need to teleport
        }
        
        // Calculate distance from boundaries (when inside the claim)
        double distanceFromMinX = px - claim.getMinX();  // Distance from west boundary
        double distanceFromMaxX = claim.getMaxX() - px;  // Distance from east boundary
        double distanceFromMinZ = pz - claim.getMinZ();  // Distance from north boundary
        double distanceFromMaxZ = claim.getMaxZ() - pz;  // Distance from south boundary
        
        // Check if player is within 0.8 blocks of any boundary (inside the claim)
        boolean nearMinX = distanceFromMinX < 0.8 && pz >= claim.getMinZ() && pz <= claim.getMaxZ();
        boolean nearMaxX = distanceFromMaxX < 0.8 && pz >= claim.getMinZ() && pz <= claim.getMaxZ();
        boolean nearMinZ = distanceFromMinZ < 0.8 && px >= claim.getMinX() && px <= claim.getMaxX();
        boolean nearMaxZ = distanceFromMaxZ < 0.8 && px >= claim.getMinX() && px <= claim.getMaxX();
        
        return nearMinX || nearMaxX || nearMinZ || nearMaxZ;
    }
    
    private void teleportPlayerOutsideGlass(Player player, Claim claim) {
        Location playerLoc = player.getLocation();
        double px = playerLoc.getX();
        double pz = playerLoc.getZ();
        
        // Calculate the safe teleport location outside the glass
        double newX = playerLoc.getX();
        double newZ = playerLoc.getZ();
        
        // Calculate distances from boundaries to determine which one player is closest to
        double distanceFromMinX = px - claim.getMinX();
        double distanceFromMaxX = claim.getMaxX() - px;
        double distanceFromMinZ = pz - claim.getMinZ();
        double distanceFromMaxZ = claim.getMaxZ() - pz;
        
        // Find the closest boundary and teleport outside it
        double minDistance = Math.min(Math.min(distanceFromMinX, distanceFromMaxX), 
                                     Math.min(distanceFromMinZ, distanceFromMaxZ));
        
        // Teleport based on which boundary the player is closest to
        if(distanceFromMinX == minDistance) {
            newX = claim.getMinX() - 3.5; // Teleport 3.5 blocks outside west boundary
        } else if(distanceFromMaxX == minDistance) {
            newX = claim.getMaxX() + 3.5; // Teleport 3.5 blocks outside east boundary
        }
        
        if(distanceFromMinZ == minDistance) {
            newZ = claim.getMinZ() - 3.5; // Teleport 3.5 blocks outside north boundary
        } else if(distanceFromMaxZ == minDistance) {
            newZ = claim.getMaxZ() + 3.5; // Teleport 3.5 blocks outside south boundary
        }

        Location teleportLoc = new Location(playerLoc.getWorld(), newX, playerLoc.getY(), newZ, 
            playerLoc.getYaw(), playerLoc.getPitch());
        player.getWorld().playEffect(playerLoc, Effect.ENDER_SIGNAL, 15);
        player.getWorld().playEffect(teleportLoc, Effect.ENDER_SIGNAL, 15);
        
        // Play sound for nearby players within 10 blocks
        for(Player nearbyPlayer : Bukkit.getOnlinePlayers()) {
            if(nearbyPlayer.getWorld() != playerLoc.getWorld()) continue;
            if(nearbyPlayer.getLocation().distance(playerLoc) <= 10) {
                nearbyPlayer.playSound(nearbyPlayer.getLocation(), Sound.ENDERMAN_TELEPORT, 0.8f, 1.0f);
            }
        }

        // Debug message
        if(Config.GLASS_DEBUG) {
            player.sendMessage("§c[Glass Debug] You were " + String.format("%.2f", minDistance) + " blocks from the nearest boundary!");
            player.sendMessage("§c[Glass Debug] From: " + String.format("%.2f, %.2f, %.2f", 
                playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));
            player.sendMessage("§c[Glass Debug] To: " + String.format("%.2f, %.2f, %.2f", 
                teleportLoc.getX(), teleportLoc.getY(), teleportLoc.getZ()));
        }
        
        player.teleport(teleportLoc);
    }
    
    private void checkAndTeleportFromGlass(Player player) {
        GlassType type = this.getGlassType(player, null);
        if(type == null) return;
        
        Location playerLoc = player.getLocation();
        
        // Get nearby claims
        Set<Claim> claims = ClaimManager.getInstance().getClaimsInSelection(playerLoc.getWorld(),
            playerLoc.getBlockX() - 1, playerLoc.getBlockX() + 1,
            playerLoc.getBlockZ() - 1, playerLoc.getBlockZ() + 1);
        
        if(claims.isEmpty()) return;
        
        // Filter claims based on glass type
        if(type == GlassType.SPAWN_WALL) {
            claims.removeIf(claim -> !claim.getOwner().isSafezone());
        } else {
            PlayerFaction playerFaction = FactionsManager.getInstance().getPlayerFaction(player);
            
            claims.removeIf(claim -> claim.getOwner() instanceof RoadFaction || claim.getOwner().isSafezone()
                || (Config.PVP_PROTECTION_CAN_ENTER_OWN_CLAIM && claim.getOwner() == playerFaction));
        }
        
        // Debug: log checking
        if(Config.GLASS_DEBUG && !claims.isEmpty()) {
            player.sendMessage("§7[Glass Debug] Checking " + claims.size() + " claims for collision...");
        }
        
        // Check each claim and teleport if needed
        for(Claim claim : claims) {
            if(this.isPlayerInsideGlassWall(player, claim)) {
                if(Config.GLASS_DEBUG) {
                    player.sendMessage("§e[Glass Debug] Collision detected with claim: " + 
                        claim.getOwner().getDisplayName(player));
                }
                this.teleportPlayerOutsideGlass(player, claim);
                break; // Only teleport once per tick
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.clearGlassInfoForPlayer(event.getPlayer());
    }

    public enum GlassType {
        SPAWN_WALL, CLAIM_WALL, CLAIM_MAP, CLAIM_SELECTION
    }

    private class GlassUpdater implements Runnable, Listener {

        private final Map<UUID, Location> lastPlayerLocations;

        public GlassUpdater() {
            this.lastPlayerLocations = new HashMap<>();
            Bukkit.getPluginManager().registerEvents(this, Lazarus.getInstance());
        }

        public boolean isEqual(Location loc1, Location loc2) {
            return loc1.getWorld() == loc2.getWorld()
                && loc1.getBlockX() == loc2.getBlockX()
                && loc1.getBlockY() == loc2.getBlockY()
                && loc1.getBlockZ() == loc2.getBlockZ();
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            this.lastPlayerLocations.remove(event.getPlayer().getUniqueId());
        }

        @Override
        public void run() {
            try {
                for(Player player : Bukkit.getOnlinePlayers()) {
                    Location lastLocation = this.lastPlayerLocations.get(player.getUniqueId());

                    if(lastLocation == null || !this.isEqual(lastLocation, player.getLocation())) {
                        handlePlayerMove(player, lastLocation, player.getLocation());
                        this.lastPlayerLocations.put(player.getUniqueId(), player.getLocation());
                    }
                    
                    // Check for glass wall collision and teleport if needed
                    checkAndTeleportFromGlass(player);
                }
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
