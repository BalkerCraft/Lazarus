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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

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
        // Glass visuals are disabled - only knockback functionality remains
        // This method is now primarily used for clearing any existing glass visuals
        GlassType type = this.getGlassType(player, forced);
        if(type == null) return;

        // Clear any existing glass visuals that might be present
        this.clearGlassVisuals(player, type, glassInfo -> {
            Location loc = glassInfo.getLocation();

            return loc.getWorld().getName().equals(to.getWorld().getName())
                && (Math.abs(loc.getBlockX() - to.getBlockX()) > WALL_BORDER_WIDTH
                || Math.abs(loc.getBlockY() - to.getBlockY()) > WALL_BORDER_HEIGHT
                || Math.abs(loc.getBlockZ() - to.getBlockZ()) > WALL_BORDER_WIDTH);
        });

        // Glass generation has been removed - only knockback remains
    }
    
    private boolean isPlayerInsideGlassWall(Player player, Claim claim) {
        Location playerLoc = player.getLocation();
        
        double px = playerLoc.getX();
        double pz = playerLoc.getZ();
        
        // Check if player is inside the claim boundaries
        boolean insideClaim = px >= claim.getMinX() && px <= claim.getMaxX() && 
                             pz >= claim.getMinZ() && pz <= claim.getMaxZ();
        
        if(!insideClaim) {
            // Player is outside - check if they're trying to enter (within buffer zone)
            double buffer = 1.5; // Increased buffer zone for better detection
            boolean inBufferZone = px >= (claim.getMinX() - buffer) && px <= (claim.getMaxX() + buffer) &&
                                  pz >= (claim.getMinZ() - buffer) && pz <= (claim.getMaxZ() + buffer);
            
            if(Config.GLASS_DEBUG && inBufferZone) {
                player.sendMessage("§a[Glass Debug] In buffer zone - Outside claim, will knockback");
                player.sendMessage("§a[Glass Debug] Player pos: X=" + String.format("%.2f", px) + ", Z=" + String.format("%.2f", pz));
                player.sendMessage("§a[Glass Debug] Claim bounds: X=[" + claim.getMinX() + "," + claim.getMaxX() + "], Z=[" + claim.getMinZ() + "," + claim.getMaxZ() + "]");
            }
            
            return inBufferZone; // Knockback if in buffer zone but outside claim
        }
        
        // Player is inside claim - check if they're near the edge (for knockback out)
        double edgeDistance = 0.5; // Distance from edge to trigger knockback
        
        double distanceFromMinX = px - claim.getMinX();
        double distanceFromMaxX = claim.getMaxX() - px;
        double distanceFromMinZ = pz - claim.getMinZ();
        double distanceFromMaxZ = claim.getMaxZ() - pz;
        
        // Find minimum distance to any boundary
        double minDistanceToBoundary = Math.min(Math.min(distanceFromMinX, distanceFromMaxX),
                                               Math.min(distanceFromMinZ, distanceFromMaxZ));
        
        return minDistanceToBoundary <= edgeDistance;
    }
    
    private void knockbackPlayerFromGlass(Player player, Claim claim) {
        Location playerLoc = player.getLocation();
        double px = playerLoc.getX();
        double pz = playerLoc.getZ();
        
        // Check if player is inside or outside the claim
        boolean insideClaim = px >= claim.getMinX() && px <= claim.getMaxX() && 
                             pz >= claim.getMinZ() && pz <= claim.getMaxZ();
        
        double knockbackX = 0;
        double knockbackZ = 0;
        double knockbackStrength = 1.5;
        
        if(insideClaim) {
            // Player is inside - push them to the nearest boundary (outward)
            double distanceFromMinX = px - claim.getMinX();
            double distanceFromMaxX = claim.getMaxX() - px;
            double distanceFromMinZ = pz - claim.getMinZ();
            double distanceFromMaxZ = claim.getMaxZ() - pz;
            
            // Find closest boundary and push away from it
            double minDistance = Math.min(Math.min(distanceFromMinX, distanceFromMaxX), 
                                         Math.min(distanceFromMinZ, distanceFromMaxZ));
            
            if(distanceFromMinX == minDistance) {
                knockbackX = knockbackStrength; // Push east (away from west boundary)
            } else if(distanceFromMaxX == minDistance) {
                knockbackX = -knockbackStrength; // Push west (away from east boundary)
            } else if(distanceFromMinZ == minDistance) {
                knockbackZ = knockbackStrength; // Push south (away from north boundary)
            } else {
                knockbackZ = -knockbackStrength; // Push north (away from south boundary)
            }
        } else {
            // Player is outside - determine which boundary they're closest to and push away
            double distToMinX = Math.abs(px - claim.getMinX());
            double distToMaxX = Math.abs(px - claim.getMaxX());
            double distToMinZ = Math.abs(pz - claim.getMinZ());
            double distToMaxZ = Math.abs(pz - claim.getMaxZ());
            
            // Determine primary knockback direction based on closest boundary
            if(px < claim.getMinX()) {
                // Player is west of claim - push west
                knockbackX = -knockbackStrength;
            } else if(px > claim.getMaxX()) {
                // Player is east of claim - push east
                knockbackX = knockbackStrength;
            }
            
            if(pz < claim.getMinZ()) {
                // Player is north of claim - push north
                knockbackZ = -knockbackStrength;
            } else if(pz > claim.getMaxZ()) {
                // Player is south of claim - push south
                knockbackZ = knockbackStrength;
            }
            
            // If player is at a corner, normalize the knockback vector
            if(knockbackX != 0 && knockbackZ != 0) {
                double length = Math.sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ);
                knockbackX = (knockbackX / length) * knockbackStrength;
                knockbackZ = (knockbackZ / length) * knockbackStrength;
            }
        }
        
        // Create and apply knockback velocity
        Vector knockback = new Vector(knockbackX, 0.3, knockbackZ);
        player.setVelocity(knockback);
        
        // Play effects at player location
        player.getWorld().playEffect(playerLoc, Effect.ENDER_SIGNAL, 15);
        
        // Play sound for nearby players
        for(Player nearbyPlayer : Bukkit.getOnlinePlayers()) {
            if(nearbyPlayer.getWorld() != playerLoc.getWorld()) continue;
            if(nearbyPlayer.getLocation().distance(playerLoc) <= 10) {
                nearbyPlayer.playSound(nearbyPlayer.getLocation(), Sound.ENDERMAN_TELEPORT, 0.8f, 1.0f);
            }
        }

        if(Config.GLASS_DEBUG) {
            player.sendMessage("§c[Glass Debug] Knockback applied from claim: " + claim.getOwner().getDisplayName(player));
            player.sendMessage("§c[Glass Debug] Knockback vector: X=" + String.format("%.2f", knockbackX) + 
                ", Z=" + String.format("%.2f", knockbackZ));
        }
    }
    
    private void checkAndKnockbackFromGlass(Player player) {
        GlassType type = this.getGlassType(player, null);
        if(type == null) return;
        
        Location playerLoc = player.getLocation();
        
        // Get nearby claims - asymmetric expansion for proper boundary detection
        // Need more expansion on min sides (west/north) for approaching detection
        Set<Claim> claims = ClaimManager.getInstance().getClaimsInSelection(playerLoc.getWorld(),
            playerLoc.getBlockX() - 2, playerLoc.getBlockX() + 1,
            playerLoc.getBlockZ() - 2, playerLoc.getBlockZ() + 1);
        
        if(claims.isEmpty()) return;
        
        // Filter claims based on glass type
        if(type == GlassType.SPAWN_WALL) {
            claims.removeIf(claim -> !claim.getOwner().isSafezone());
        } else {
            PlayerFaction playerFaction = FactionsManager.getInstance().getPlayerFaction(player);
            
            claims.removeIf(claim -> claim.getOwner() instanceof RoadFaction || claim.getOwner().isSafezone()
                || (Config.PVP_PROTECTION_CAN_ENTER_OWN_CLAIM && claim.getOwner() == playerFaction));
        }
        
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
                this.knockbackPlayerFromGlass(player, claim);
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
                    
                    // Check for glass wall collision and knockback if needed
                    checkAndKnockbackFromGlass(player);
                }
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
