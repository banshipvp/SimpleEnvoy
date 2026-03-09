package local.simpleenvoy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

/**
 * Central manager for the SimpleEnvoy plugin.
 * Handles center storage, auto-scheduling, chest spawning, and session tracking.
 */
public class EnvoyManager {

    // ── Constants ─────────────────────────────────────────────────────────────
    /** 30 minutes in ticks */
    private static final long STANDARD_INTERVAL_TICKS = 30L * 60L * 20L;
    /** 90 minutes in ticks */
    private static final long HEROIC_INTERVAL_TICKS   = 90L * 60L * 20L;

    private static final int  MAX_PLAYERS          = 10;
    private static final int  SPAWN_RADIUS         = 100;
    private static final int  MIN_DISTANCE_BETWEEN = 15;  // min blocks between chests
    private static final int  MIN_SPAWN_Y          = 60;  // minimum Y for chest placement
    private static final int  MAX_SPAWN_Y          = 130; // maximum Y for chest placement

    /**
     * Tier weights per location. Defines which tier can spawn at each location
     * and its relative spawn weight.
     */
    private static final Map<EnvoySession.LocationKey, List<TierWeight>> TIER_WEIGHTS;

    static {
        TIER_WEIGHTS = new EnumMap<>(EnvoySession.LocationKey.class);
        TIER_WEIGHTS.put(EnvoySession.LocationKey.SPAWN, List.of(
                new TierWeight(EnvoyTier.SIMPLE,   55),
                new TierWeight(EnvoyTier.UNIQUE,   30),
                new TierWeight(EnvoyTier.ELITE,    12),
                new TierWeight(EnvoyTier.LEGENDARY, 3)));
        TIER_WEIGHTS.put(EnvoySession.LocationKey.DESERT, List.of(
                new TierWeight(EnvoyTier.UNIQUE,    40),
                new TierWeight(EnvoyTier.ELITE,     35),
                new TierWeight(EnvoyTier.ULTIMATE,  20),
                new TierWeight(EnvoyTier.LEGENDARY,  5)));
        TIER_WEIGHTS.put(EnvoySession.LocationKey.PLAINS, List.of(
                new TierWeight(EnvoyTier.ELITE,     40),
                new TierWeight(EnvoyTier.ULTIMATE,  35),
                new TierWeight(EnvoyTier.LEGENDARY, 20),
                new TierWeight(EnvoyTier.GODLY,      5)));
        TIER_WEIGHTS.put(EnvoySession.LocationKey.NETHER, List.of(
                new TierWeight(EnvoyTier.LEGENDARY, 40),
                new TierWeight(EnvoyTier.ULTIMATE,  35),
                new TierWeight(EnvoyTier.GODLY,     25)));
    }

    private record TierWeight(EnvoyTier tier, int weight) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final Random RANDOM = new Random();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LootManager lootManager;
    private final File centersFile;

    /** Location key → center location */
    private final Map<String, Location> centers = new HashMap<>();

    /** Active sessions, keyed by location key */
    private final Map<String, EnvoySession> activeSessions = new HashMap<>();

    /** Block location key → EnvoyChest (for fast lookup on player interaction) */
    private final Map<String, EnvoyChest> chestByBlock = new HashMap<>();

    /** Auto-spawn task handles */
    private BukkitTask standardTask;
    private BukkitTask heroicTask;

    /** Tracked "next fire" time in millis for timer display */
    private long nextStandardMs = -1;
    private long nextHeroicMs   = -1;

    // ── Constructor ───────────────────────────────────────────────────────────

    public EnvoyManager(JavaPlugin plugin, LootManager lootManager) {
        this.plugin      = plugin;
        this.lootManager = lootManager;
        this.centersFile = new File(plugin.getDataFolder(), "centers.yml");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void load() {
        loadCenters();
    }

    public void startSchedulers() {
        stopSchedulers();

        long now = System.currentTimeMillis();

        standardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runStandardCycle,
                STANDARD_INTERVAL_TICKS, STANDARD_INTERVAL_TICKS);
        nextStandardMs = now + STANDARD_INTERVAL_TICKS * 50;

        heroicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runHeroicCycle,
                HEROIC_INTERVAL_TICKS, HEROIC_INTERVAL_TICKS);
        nextHeroicMs = now + HEROIC_INTERVAL_TICKS * 50;
    }

    public void stopSchedulers() {
        if (standardTask != null) { standardTask.cancel(); standardTask = null; }
        if (heroicTask   != null) { heroicTask.cancel();   heroicTask   = null; }
    }

    public void stopAllSessions() {
        for (EnvoySession session : new ArrayList<>(activeSessions.values())) {
            session.stopAll();
        }
        activeSessions.clear();
        chestByBlock.clear();
    }

    public void disable() {
        stopSchedulers();
        stopAllSessions();
    }

    // ── Center management ─────────────────────────────────────────────────────

    public boolean setCenter(String locationName, Location loc) {
        String key = locationName.toLowerCase(Locale.ROOT);
        if (EnvoySession.LocationKey.fromString(key) == null) return false;
        if (loc == null || loc.getWorld() == null) return false;
        centers.put(key, loc.clone());
        saveCenters();
        return true;
    }

    public Location getCenter(String locationName) {
        Location loc = centers.get(locationName.toLowerCase(Locale.ROOT));
        return loc == null ? null : loc.clone();
    }

    public Map<String, Location> getCenters() {
        return Collections.unmodifiableMap(centers);
    }

    // ── Manual start ──────────────────────────────────────────────────────────

    /** Manually start the standard envoys (spawn, desert, plains). */
    public List<EnvoySession> startStandard() {
        List<EnvoySession> started = new ArrayList<>();
        for (EnvoySession.LocationKey lk : List.of(
                EnvoySession.LocationKey.SPAWN,
                EnvoySession.LocationKey.DESERT,
                EnvoySession.LocationKey.PLAINS)) {
            EnvoySession session = startSession(lk);
            if (session != null) started.add(session);
        }
        return started;
    }

    /** Manually start the heroic nether envoy. */
    public EnvoySession startHeroic() {
        return startSession(EnvoySession.LocationKey.NETHER);
    }

    /** Manually start a specific location's envoy. */
    public EnvoySession startByName(String name) {
        EnvoySession.LocationKey lk = EnvoySession.LocationKey.fromString(name);
        if (lk == null) return null;
        return startSession(lk);
    }

    // ── Session lookup ────────────────────────────────────────────────────────

    public EnvoyChest getChestAt(Block block) {
        return chestByBlock.get(blockKey(block));
    }

    public Map<String, EnvoySession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    public long getNextStandardMs() { return nextStandardMs; }
    public long getNextHeroicMs()   { return nextHeroicMs; }

    // ── Chest removed notification ────────────────────────────────────────────

    public void notifyChestRemoved(EnvoyChest chest) {
        chestByBlock.remove(blockKey(chest.blockLocation().getBlock()));

        // Check if the session is finished
        for (Map.Entry<String, EnvoySession> e : new ArrayList<>(activeSessions.entrySet())) {
            if (e.getValue().chests().contains(chest)) {
                if (e.getValue().isFinished()) {
                    activeSessions.remove(e.getKey());
                    broadcast(MM.deserialize(
                            "<dark_gray>***</dark_gray> <gradient:#00F7FF:#9D00FF><bold>TECHNOCLASH ENVOY</bold></gradient> <dark_gray>***</dark_gray>\n" +
                            "<gray>The </gray><gold><bold>" + e.getKey().toUpperCase() + "</bold></gold><gray> envoy has ended. All crates have been claimed.</gray>"));
                }
                break;
            }
        }
    }

    // ── Private spawn logic ───────────────────────────────────────────────────

    private void runStandardCycle() {
        long now = System.currentTimeMillis();
        nextStandardMs = now + STANDARD_INTERVAL_TICKS * 50;
        runStandardCycleInternal();
    }

    private void runStandardCycleInternal() {
        List<EnvoySession> sessions = startStandard();
        if (!sessions.isEmpty()) {
            List<String> names = sessions.stream().map(s -> s.locationKey().key()).toList();
            broadcastStart(false, names);
        }
    }

    private void runHeroicCycle() {
        long now = System.currentTimeMillis();
        nextHeroicMs = now + HEROIC_INTERVAL_TICKS * 50;
        EnvoySession session = startHeroic();
        if (session != null) {
            broadcastStartHeroic();
        }
    }

    private EnvoySession startSession(EnvoySession.LocationKey lk) {
        Location center = centers.get(lk.key());
        if (center == null || center.getWorld() == null) return null;

        int playersInZone = WarzoneHelper.countPlayersInWarzone(center, SPAWN_RADIUS);
        if (playersInZone < 1) return null;

        // If session already running for this location, don't restart
        if (activeSessions.containsKey(lk.key()) && !activeSessions.get(lk.key()).isFinished()) {
            return null;
        }

        EnvoySession session = new EnvoySession(lk);
        int chestCount = computeChestCount(Math.min(playersInZone, MAX_PLAYERS));

        Set<String> occupied = new HashSet<>(chestByBlock.keySet());
        List<EnvoyTier> tierPool = buildTierPool(TIER_WEIGHTS.get(lk));

        int spawned = 0, attempts = 0, maxAttempts = chestCount * 15 + 200;

        while (spawned < chestCount && attempts < maxAttempts) {
            attempts++;
            Location spawnLoc = findSpawnLocation(center, occupied);
            if (spawnLoc == null) continue;

            EnvoyTier tier = pickTier(tierPool);
            int rewardCount = lootManager.getRewardAmount(tier);
            List<LootEntry> rolled = lootManager.rollRewards(tier, rewardCount);
            if (rolled.isEmpty()) continue;

            Block block = spawnLoc.getBlock();
            block.setType(tier.blockMaterial());

            EnvoyChest chest = new EnvoyChest(block.getLocation(), tier, rolled);
            chest.spawnHolograms();

            session.addChest(chest);
            String key = blockKey(block);
            chestByBlock.put(key, chest);
            occupied.add(key);
            spawned++;
        }

        if (spawned == 0) return null;

        // Start firework task for this session
        List<EnvoyChest> allChests = new ArrayList<>(session.chests());
        BukkitTask fw = new FireworkTask(plugin, allChests)
                .runTaskTimer(plugin, 200L, 200L); // every 10 seconds
        session.setFireworkTask(fw);

        activeSessions.put(lk.key(), session);
        plugin.getLogger().info("[SimpleEnvoy] Started " + lk.key() + " with " + spawned + " chests.");
        return session;
    }

    private int computeChestCount(int playerCount) {
        int capped = Math.max(1, Math.min(MAX_PLAYERS, playerCount));
        int min = 7 + (capped - 1) * 3;
        int max = 15 + (capped - 1) * 3;
        return RANDOM.nextInt(max - min + 1) + min;
    }

    private Location findSpawnLocation(Location center, Set<String> occupied) {
        if (center == null || center.getWorld() == null) return null;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = RANDOM.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
            int dz = RANDOM.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
            int x = cx + dx;
            int z = cz + dz;

            // Find the highest solid block at this X, Z
            Block surface;
            try {
                int topY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                surface = world.getBlockAt(x, topY, z);
            } catch (Exception e) {
                surface = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
            }

            // Must be solid and not something we'd destroy
            if (surface.isEmpty() || surface.isLiquid()) continue;

            // Chest goes one above the surface
            Block chestBlock = world.getBlockAt(x, surface.getY() + 1, z);
            if (!chestBlock.isEmpty()) continue; // something already there

            // Enforce Y height bounds
            int chestY = chestBlock.getY();
            if (chestY < MIN_SPAWN_Y || chestY > MAX_SPAWN_Y) continue;

            // Check min distance from other chests
            String blockKey = blockKey(chestBlock);
            if (occupied.contains(blockKey)) continue;

            if (isTooClose(chestBlock.getLocation(), occupied, world)) continue;

            return chestBlock.getLocation();
        }
        return null;
    }

    private boolean isTooClose(Location loc, Set<String> occupied, World world) {
        for (String key : occupied) {
            String[] parts = key.split(":");
            if (parts.length != 4) continue;
            if (!parts[0].equals(world.getName())) continue;
            try {
                int ox = Integer.parseInt(parts[1]);
                int oy = Integer.parseInt(parts[2]);
                int oz = Integer.parseInt(parts[3]);
                double dist = Math.sqrt(Math.pow(loc.getBlockX() - ox, 2)
                        + Math.pow(loc.getBlockZ() - oz, 2));
                if (dist < MIN_DISTANCE_BETWEEN) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private List<EnvoyTier> buildTierPool(List<TierWeight> weights) {
        List<EnvoyTier> pool = new ArrayList<>();
        for (TierWeight tw : weights) {
            for (int i = 0; i < tw.weight(); i++) pool.add(tw.tier());
        }
        Collections.shuffle(pool, RANDOM);
        return pool;
    }

    private EnvoyTier pickTier(List<EnvoyTier> pool) {
        if (pool.isEmpty()) return EnvoyTier.SIMPLE;
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    // ── Broadcast messages ────────────────────────────────────────────────────

    public void broadcastStart(boolean isHeroic, List<String> locations) {
        String locationList = String.join(", ", locations.stream()
                .map(l -> "/" + l).toList());

        String msg = "<dark_gray>***</dark_gray> <gradient:#00F7FF:#9D00FF><bold>TECHNOCLASH ENVOY</bold></gradient> <dark_gray>***</dark_gray>\n" +
                "<gray>A TechnoClash Envoy has emerged beneath the main</gray> <white><bold>/spawn</bold></white> <gray>and across all PvP</gray> <white><bold>/warps</bold></white><gray>!</gray>\n" +
                "<gray>Supply crates are now descending throughout the</gray> <red><bold>WARZONE</bold></red><gray>.</gray>\n" +
                "<dark_gray>((</dark_gray> <gray>Loot-filled crates have spawned at random locations across the Warzone and PvP warps. Move fast, secure your rewards, and escape before others arrive. More crates appear where more players gather.</gray> <dark_gray>))</dark_gray>\n" +
                "<dark_gray>***</dark_gray> <gradient:#00F7FF:#9D00FF><bold>TECHNOCLASH ENVOY</bold></gradient> <dark_gray>***</dark_gray>";
        broadcast(MM.deserialize(msg));
    }

    public void broadcastStartHeroic() {
        String msg = "<dark_gray>***</dark_gray> <gradient:#FF0000:#FF00FF><bold>HEROIC NETHER ENVOY</bold></gradient> <dark_gray>***</dark_gray>\n" +
                "<gray>A</gray> <light_purple><bold>HEROIC</bold></light_purple> <gray>TechnoClash Envoy has emerged from the depths of the</gray> <red><bold>NETHER</bold></red><gray>!</gray>\n" +
                "<gray>Insanely powerful loot awaits those brave enough to enter the</gray> <red><bold>WARZONE</bold></red><gray>.</gray>\n" +
                "<dark_gray>((</dark_gray> <gray>This is a Heroic event with rare and powerful crates. Godly rewards await you — head to</gray> <white><bold>/warp nether</bold></white> <gray>now!</gray> <dark_gray>))</dark_gray>\n" +
                "<dark_gray>***</dark_gray> <gradient:#FF0000:#FF00FF><bold>HEROIC NETHER ENVOY</bold></gradient> <dark_gray>***</dark_gray>";
        broadcast(MM.deserialize(msg));
    }

    private void broadcast(Component msg) {
        Bukkit.broadcast(msg);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadCenters() {
        centers.clear();
        if (!centersFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(centersFile);
        for (EnvoySession.LocationKey lk : EnvoySession.LocationKey.values()) {
            String base = "centers." + lk.key();
            if (!yaml.contains(base)) continue;
            String worldName = yaml.getString(base + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = yaml.getDouble(base + ".x");
            double y = yaml.getDouble(base + ".y");
            double z = yaml.getDouble(base + ".z");
            float yaw = (float) yaml.getDouble(base + ".yaw");
            float pitch = (float) yaml.getDouble(base + ".pitch");
            centers.put(lk.key(), new Location(world, x, y, z, yaw, pitch));
        }
        plugin.getLogger().info("[SimpleEnvoy] Loaded " + centers.size() + " envoy centers.");
    }

    private void saveCenters() {
        centersFile.getParentFile().mkdirs(); // ensure plugins/SimpleEnvoy/ directory exists
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Location> e : centers.entrySet()) {
            Location loc = e.getValue();
            String base = "centers." + e.getKey();
            yaml.set(base + ".world", loc.getWorld().getName());
            yaml.set(base + ".x", loc.getX());
            yaml.set(base + ".y", loc.getY());
            yaml.set(base + ".z", loc.getZ());
            yaml.set(base + ".yaw", loc.getYaw());
            yaml.set(base + ".pitch", loc.getPitch());
        }
        try { yaml.save(centersFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Failed to save centers.yml", ex); }
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String blockKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
