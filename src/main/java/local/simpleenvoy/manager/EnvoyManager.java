package local.simpleenvoy.manager;

import local.simpleenvoy.SimpleEnvoyPlugin;
import local.simpleenvoy.manager.CrateRewardManager.CrateTier;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EnvoyManager implements Listener {

    public record ActiveCrate(Location location, CrateTier tier, long spawnedAtMs) {}

    private final SimpleEnvoyPlugin plugin;
    private final CrateRewardManager rewardManager;
    private final File envoyFile;
    private final Random random = new Random();

    private YamlConfiguration envoyYaml;
    private final Map<String, ActiveCrate> activeCrates = new LinkedHashMap<>();
    private final Map<String, Long> cooldownUntilMs = new HashMap<>();

    private BukkitTask autoTask;
    private BukkitTask timeoutTask;
    private long nextSpawnMs = -1L;
    private long spawnPeriodMs = -1L;

    public EnvoyManager(SimpleEnvoyPlugin plugin, CrateRewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
        this.envoyFile = new File(plugin.getDataFolder(), "envoys/default.yml");
    }

    public void reload() {
        this.envoyYaml = YamlConfiguration.loadConfiguration(envoyFile);
        restartScheduler();
    }

    public void startScheduler() {
        restartScheduler();
    }

    public void stopScheduler() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public int spawnEvent() {
        if (envoyYaml == null) {
            reload();
        }

        int minPlayers = Math.max(0, envoyYaml.getInt("min-players", 0));
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            return 0;
        }

        int amount = resolveAmount(envoyYaml.get("amount"));
        if (amount <= 0) {
            return 0;
        }

        List<Location> spawnLocations = resolveSpawnLocations(amount);
        if (spawnLocations.isEmpty()) {
            return 0;
        }

        clearActiveCrates();

        for (Location location : spawnLocations) {
            Block block = location.getBlock();
            block.setType(Material.CHEST);
            CrateTier tier = rollTier();
            activeCrates.put(key(block.getLocation()), new ActiveCrate(block.getLocation(), tier, System.currentTimeMillis()));
        }

        int timeoutSeconds = Math.max(5, envoyYaml.getInt("timeout-time", 300));
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::clearActiveCrates, timeoutSeconds * 20L);

        if (envoyYaml.getBoolean("send-spawn-message", false)) {
            Bukkit.broadcastMessage("§6[SimpleEnvoy] §e" + activeCrates.size() + " crates spawned.");
        }

        return activeCrates.size();
    }

    public int clearActiveCrates() {
        int removed = 0;
        for (ActiveCrate crate : new ArrayList<>(activeCrates.values())) {
            Location location = crate.location();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
            removed++;
        }
        activeCrates.clear();
        return removed;
    }

    public int getActiveCrateCount() {
        return activeCrates.size();
    }

    public boolean setSpawnCenter(Location location) {
        if (location == null || location.getWorld() == null || envoyYaml == null) {
            return false;
        }

        String serialized = serializeLocation(location, false);
        envoyYaml.set("random-spawn.center", serialized);
        saveYaml();
        return true;
    }

    public Location getSpawnCenter() {
        if (envoyYaml == null) {
            return null;
        }
        return parseLocation(envoyYaml.getString("random-spawn.center"));
    }

    public String getEveryRaw() {
        return envoyYaml == null ? "" : envoyYaml.getString("every", "");
    }

    public int getConfiguredAmountSample() {
        return resolveAmount(envoyYaml == null ? 0 : envoyYaml.get("amount"));
    }

    @EventHandler
    public void onCrateInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CHEST) {
            return;
        }

        ActiveCrate crate = activeCrates.get(key(clicked.getLocation()));
        if (crate == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        String cooldownMessage = checkAndApplyCooldown(player, clicked.getLocation());
        if (cooldownMessage != null) {
            player.sendMessage(cooldownMessage);
            return;
        }

        activeCrates.remove(key(clicked.getLocation()));
        clicked.setType(Material.AIR);

        int rewardCount = rewardCount(crate.tier());
        List<ItemStack> rewards = rewardManager.generateRewards(crate.tier(), rewardCount);
        for (ItemStack reward : rewards) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
        }

        if (envoyYaml.getBoolean("broadcast-collect", true)) {
            Bukkit.broadcastMessage("§6[SimpleEnvoy] §f" + player.getName() + " §ecollected a §f" + crate.tier().name().toLowerCase(Locale.ROOT) + " §ecrate.");
        } else {
            player.sendMessage("§aCollected §f" + crate.tier().name().toLowerCase(Locale.ROOT) + " §acrate and received " + rewardCount + " rewards.");
        }
    }

    private String checkAndApplyCooldown(Player player, Location crateLocation) {
        int cooldownSeconds = Math.max(0, envoyYaml.getInt("collect-cooldown", 0));
        if (cooldownSeconds <= 0) {
            return null;
        }

        boolean global = envoyYaml.getBoolean("collect-global-cooldown", false);
        String cooldownKey = global
                ? player.getUniqueId().toString()
                : key(crateLocation) + "|" + player.getUniqueId();

        long now = System.currentTimeMillis();
        long until = cooldownUntilMs.getOrDefault(cooldownKey, 0L);
        if (now < until) {
            long remainingSec = Math.max(1L, (until - now + 999L) / 1000L);
            return "§cYou must wait §f" + remainingSec + "s §cbefore collecting another crate.";
        }

        cooldownUntilMs.put(cooldownKey, now + cooldownSeconds * 1000L);
        return null;
    }

    private int rewardCount(CrateTier tier) {
        return switch (tier) {
            case SIMPLE -> 3;
            case UNIQUE -> 4;
            case GODLY -> 5;
            case LEGENDARY -> 6;
        };
    }

    private List<Location> resolveSpawnLocations(int amount) {
        List<Location> out = new ArrayList<>();

        ConfigurationSection predefined = envoyYaml.getConfigurationSection("pre-defined-spawns");
        boolean predefinedEnabled = predefined != null && predefined.getBoolean("enabled", false);
        boolean limitPredefined = envoyYaml.getBoolean("limit-predefined", true);

        if (predefinedEnabled && predefined != null) {
            List<String> locations = predefined.getStringList("locations");
            for (String value : locations) {
                if (out.size() >= amount) {
                    break;
                }
                Location location = parseLocation(value);
                if (location != null && isValidSpawn(location, out)) {
                    out.add(location);
                }
            }
            if (limitPredefined || out.size() >= amount) {
                return out;
            }
        }

        ConfigurationSection randomSection = envoyYaml.getConfigurationSection("random-spawn");
        if (randomSection == null || !randomSection.getBoolean("enabled", true)) {
            return out;
        }

        int maxAttempts = Math.max(200, amount * 60);
        int attempts = 0;
        while (out.size() < amount && attempts++ < maxAttempts) {
            Location randomLocation = findRandomLocation(randomSection, out);
            if (randomLocation != null) {
                out.add(randomLocation);
            }
        }

        return out;
    }

    private Location findRandomLocation(ConfigurationSection randomSection, List<Location> alreadyChosen) {
        Location center = parseLocation(randomSection.getString("center"));
        if (center == null || center.getWorld() == null) {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (!players.isEmpty()) {
                center = players.iterator().next().getLocation();
            } else {
                return null;
            }
        }

        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        int maxDistanceX = Math.max(1, randomSection.getInt("max-distance.x", 100));
        int maxDistanceZ = Math.max(1, randomSection.getInt("max-distance.z", 100));
        int minDistance = Math.max(0, randomSection.getInt("min-distance", 0));
        int minHeight = Math.max(0, randomSection.getInt("min-height", world.getMinHeight()));
        // Hard cap at 130 – prevents chests spawning on top of tall structures like spawn builds.
        int maxHeight = Math.min(130, Math.min(world.getMaxHeight() - 1, randomSection.getInt("max-height", 130)));

        int x = centerX + random.nextInt((maxDistanceX * 2) + 1) - maxDistanceX;
        int z = centerZ + random.nextInt((maxDistanceZ * 2) + 1) - maxDistanceZ;

        double distanceFromCenter2D = Math.hypot(x - centerX, z - centerZ);
        if (distanceFromCenter2D < minDistance) {
            return null;
        }

        int y = resolveY(world, x, z, minHeight, maxHeight, randomSection);
        if (y < minHeight || y > maxHeight) {
            return null;
        }

        Location location = new Location(world, x, y, z);
        return isValidSpawn(location, alreadyChosen) ? location : null;
    }

    private int resolveY(World world, int x, int z, int minHeight, int maxHeight, ConfigurationSection randomSection) {
        String finder = envoyYaml.getString("top-block-finder", "heightmap").toLowerCase(Locale.ROOT);

        if ("iterative".equals(finder)) {
            for (int y = maxHeight; y >= minHeight; y--) {
                if (isAirAndSafe(world, x, y, z)) {
                    return y;
                }
            }
            return -1;
        }

        HeightMap heightMap = HeightMap.MOTION_BLOCKING;
        try {
            heightMap = HeightMap.valueOf(randomSection.getString("heightmap", "MOTION_BLOCKING").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }

        int y = world.getHighestBlockYAt(x, z, heightMap) + 1;
        if (y < minHeight || y > maxHeight) {
            return -1;
        }

        if (isAirAndSafe(world, x, y, z)) {
            return y;
        }

        for (int nudge = 1; nudge <= 4; nudge++) {
            int up = y + nudge;
            if (up <= maxHeight && isAirAndSafe(world, x, up, z)) {
                return up;
            }
        }

        return -1;
    }

    private boolean isAirAndSafe(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (!block.getType().isAir()) {
            return false;
        }

        Block below = world.getBlockAt(x, y - 1, z);
        if (below.getType().isAir()) {
            return false;
        }

        return !isBlacklistedBelow(below.getType());
    }

    private boolean isBlacklistedBelow(Material type) {
        ConfigurationSection randomSection = envoyYaml.getConfigurationSection("random-spawn");
        if (randomSection == null) {
            return false;
        }

        String material = type.name().toLowerCase(Locale.ROOT);
        for (String pattern : randomSection.getStringList("not-on-blocks")) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(material).find()) {
                    return true;
                }
            } catch (PatternSyntaxException ignored) {
            }
        }

        return false;
    }

    private boolean isValidSpawn(Location location, List<Location> alreadyChosen) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        Block block = location.getBlock();
        if (!block.getType().isAir()) {
            return false;
        }

        if (activeCrates.containsKey(key(location))) {
            return false;
        }

        ConfigurationSection randomSection = envoyYaml.getConfigurationSection("random-spawn");
        int minDistanceBetween = randomSection == null ? 0 : Math.max(0, randomSection.getInt("min-distance-between-crates", 0));
        if (minDistanceBetween <= 0) {
            return true;
        }

        double minDistanceSq = minDistanceBetween * minDistanceBetween;
        for (Location chosen : alreadyChosen) {
            if (!Objects.equals(chosen.getWorld(), location.getWorld())) {
                continue;
            }
            if (chosen.distanceSquared(location) < minDistanceSq) {
                return false;
            }
        }

        return true;
    }

    private CrateTier rollTier() {
        ConfigurationSection rates = envoyYaml.getConfigurationSection("rates");
        if (rates == null) {
            rates = envoyYaml.getConfigurationSection("crates");
        }

        int common = rates == null ? 50 : Math.max(0, rates.getInt("common", 50));
        int rare = rates == null ? 40 : Math.max(0, rates.getInt("rare", 40));
        int legendary = rates == null ? 30 : Math.max(0, rates.getInt("legendary", 30));

        int total = common + rare + legendary;
        if (total <= 0) {
            return CrateTier.SIMPLE;
        }

        int roll = random.nextInt(total) + 1;
        if (roll <= common) {
            return CrateTier.SIMPLE;
        }
        if (roll <= common + rare) {
            return CrateTier.UNIQUE;
        }
        return CrateTier.LEGENDARY;
    }

    private int resolveAmount(Object rawAmount) {
        if (rawAmount == null) {
            return 0;
        }

        if (rawAmount instanceof Number number) {
            return Math.max(0, number.intValue());
        }

        String text = rawAmount.toString().trim();
        if (text.isEmpty()) {
            return 0;
        }

        if (text.contains("-")) {
            String[] parts = text.split("-", 2);
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
                return min + random.nextInt((max - min) + 1);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Location parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.split(";");
        if (parts.length < 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String serializeLocation(Location location, boolean includeYawPitch) {
        if (location == null || location.getWorld() == null) {
            return "";
        }

        if (!includeYawPitch) {
            return location.getWorld().getName() + ";"
                    + location.getBlockX() + ";"
                    + location.getBlockY() + ";"
                    + location.getBlockZ();
        }

        return location.getWorld().getName() + ";"
                + location.getX() + ";"
                + location.getY() + ";"
                + location.getZ() + ";"
                + location.getYaw() + ";"
                + location.getPitch();
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void saveYaml() {
        try {
            envoyYaml.save(envoyFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save envoy config: " + ex.getMessage());
        }
    }

    private void restartScheduler() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        nextSpawnMs = -1L;
        spawnPeriodMs = -1L;

        String every = envoyYaml == null ? "" : envoyYaml.getString("every", "").trim();
        long periodTicks = parseDurationToTicks(every);
        if (periodTicks <= 0L) {
            return;
        }

        spawnPeriodMs = periodTicks * 50L;
        nextSpawnMs = System.currentTimeMillis() + spawnPeriodMs;

        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int spawned = spawnEvent();
            if (spawned > 0) {
                plugin.getLogger().info("Auto-spawned " + spawned + " envoy crates.");
            }
            nextSpawnMs = System.currentTimeMillis() + spawnPeriodMs;
        }, periodTicks, periodTicks);
    }

    /** Returns seconds until the next automatic envoy spawn, or -1 if the scheduler is not running. */
    public long getSecondsUntilNextSpawn() {
        if (nextSpawnMs < 0L || autoTask == null) return -1L;
        long remaining = nextSpawnMs - System.currentTimeMillis();
        return Math.max(0L, remaining / 1000L);
    }

    private long parseDurationToTicks(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        String value = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("ms")) {
                long ms = Long.parseLong(value.substring(0, value.length() - 2));
                return Math.max(1L, ms / 50L);
            }
            if (value.endsWith("s")) {
                long sec = Long.parseLong(value.substring(0, value.length() - 1));
                return sec * 20L;
            }
            if (value.endsWith("m")) {
                long min = Long.parseLong(value.substring(0, value.length() - 1));
                return min * 60L * 20L;
            }
            if (value.endsWith("h")) {
                long hour = Long.parseLong(value.substring(0, value.length() - 1));
                return hour * 60L * 60L * 20L;
            }

            long seconds = Long.parseLong(value);
            return seconds * 20L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
