package local.simpleenvoy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

/**
 * Manages loot tables for all six envoy tiers.
 * Each tier stores a list of {@link LootEntry} objects with weighted chances.
 */
public class LootManager {

    private static final Random RANDOM = new Random();

    private final JavaPlugin plugin;
    private final File lootDir;

    /** tier key → entries */
    private final Map<String, List<LootEntry>> tables = new LinkedHashMap<>();
    /** tier key → reward-amount (how many slots spawn in each chest) */
    private final Map<String, Integer> rewardAmounts = new LinkedHashMap<>();

    public LootManager(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.lootDir = new File(plugin.getDataFolder(), "loot");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void load() {
        lootDir.mkdirs();
        for (EnvoyTier tier : EnvoyTier.values()) {
            loadTier(tier.key());
        }
    }

    public void reload() {
        tables.clear();
        rewardAmounts.clear();
        load();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<LootEntry> getEntries(EnvoyTier tier) {
        return Collections.unmodifiableList(tables.getOrDefault(tier.key(), List.of()));
    }

    public int getRewardAmount(EnvoyTier tier) {
        return rewardAmounts.getOrDefault(tier.key(), 3);
    }

    /**
     * Roll {@code count} loot entries from the given tier's table using weighted random selection.
     * The same entry can be selected multiple times.
     */
    public List<LootEntry> rollRewards(EnvoyTier tier, int count) {
        List<LootEntry> pool = tables.getOrDefault(tier.key(), List.of());
        if (pool.isEmpty()) return List.of();

        double total = pool.stream().mapToDouble(LootEntry::chance).sum();
        if (total <= 0) return List.of();

        List<LootEntry> rolled = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double roll = RANDOM.nextDouble() * total;
            double cumulative = 0;
            for (LootEntry entry : pool) {
                cumulative += entry.chance();
                if (roll <= cumulative) {
                    rolled.add(entry);
                    break;
                }
            }
        }
        return rolled;
    }

    // ── Mutations (Editor + /envoyadd) ────────────────────────────────────────

    /**
     * Add a new loot entry backed by an in-hand item.
     */
    public void addEntry(EnvoyTier tier, ItemStack item, double chance) {
        if (item == null || item.getType() == Material.AIR || chance <= 0) return;
        List<LootEntry> list = tables.computeIfAbsent(tier.key(), k -> new ArrayList<>());
        LootEntry entry = new LootEntry(chance, List.of(item.clone()), List.of(), List.of(), null);
        list.add(entry);
        saveTier(tier.key());
    }

    /**
     * Remove the entry at {@code index} in the given tier's table.
     * @return true if removed
     */
    public boolean removeEntry(EnvoyTier tier, int index) {
        List<LootEntry> list = tables.get(tier.key());
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        saveTier(tier.key());
        return true;
    }

    // ── Private I/O ───────────────────────────────────────────────────────────

    private void loadTier(String tierKey) {
        File file = new File(lootDir, tierKey + ".yml");

        // Extract from jar if not present
        if (!file.exists()) {
            String resourcePath = "loot/" + tierKey + ".yml";
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    plugin.saveResource(resourcePath, false);
                } else {
                    // Create empty file
                    file.getParentFile().mkdirs();
                    try { file.createNewFile(); } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }

        if (!file.exists()) {
            tables.put(tierKey, new ArrayList<>());
            rewardAmounts.put(tierKey, 3);
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Try embedded resource defaults for missing keys
        String resourcePath = "loot/" + tierKey + ".yml";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
            }
        } catch (IOException ignored) {}

        rewardAmounts.put(tierKey, yaml.getInt("reward-amount", 3));

        List<LootEntry> entries = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection es = section.getConfigurationSection(key);
                if (es == null) continue;
                LootEntry entry = parseEntry(es);
                if (entry != null) entries.add(entry);
            }
        }

        tables.put(tierKey, entries);
        plugin.getLogger().info("[LootManager] Loaded " + entries.size() + " entries for tier " + tierKey);
    }

    private LootEntry parseEntry(ConfigurationSection es) {
        double chance = es.getDouble("chance", 0);
        if (chance <= 0) return null;

        // Commands
        List<String> commands = es.getStringList("commands");
        commands = new ArrayList<>(commands); // mutable

        // Messages
        List<String> rawMessages = es.getStringList("messages");
        List<String> messages = rawMessages.stream()
                .map(m -> m.replace("&", "§"))
                .toList();

        // Real items
        List<ItemStack> items = new ArrayList<>();
        ConfigurationSection itemsSection = es.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String ik : itemsSection.getKeys(false)) {
                ConfigurationSection im = itemsSection.getConfigurationSection(ik);
                if (im == null) continue;
                ItemStack built = buildItemFromSection(im);
                if (built != null) items.add(built);
            }
        }
        // Also handle flat item list
        if (items.isEmpty() && es.contains("item")) {
            ConfigurationSection im = es.getConfigurationSection("item");
            if (im != null) {
                ItemStack built = buildItemFromSection(im);
                if (built != null) items.add(built);
            }
        }

        // Display item for command-only rewards
        ItemStack displayItem = null;
        ConfigurationSection displaySection = es.getConfigurationSection("display");
        if (displaySection != null) {
            displayItem = buildItemFromSection(displaySection);
        } else if (items.isEmpty() && !commands.isEmpty()) {
            // Auto-generate a display item from the first message
            displayItem = autoDisplay(commands, messages);
        }

        return new LootEntry(chance, items, commands, messages, displayItem);
    }

    private ItemStack buildItemFromSection(ConfigurationSection s) {
        if (s == null) return null;
        String matName = s.getString("material", s.getString("type", "PAPER"));
        Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.PAPER;

        int amount = s.getInt("amount", 1);
        ItemStack item = new ItemStack(mat, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = s.getString("name");
        if (name != null) meta.setDisplayName(name.replace("&", "§"));

        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) meta.setLore(lore.stream().map(l -> l.replace("&", "§")).toList());

        // Enchants section
        ConfigurationSection enchSection = s.getConfigurationSection("enchants");
        if (enchSection != null) {
            for (String enchName : enchSection.getKeys(false)) {
                try {
                    @SuppressWarnings("deprecation")
                    org.bukkit.enchantments.Enchantment ench =
                            org.bukkit.enchantments.Enchantment.getByName(enchName.toUpperCase(Locale.ROOT));
                    if (ench != null) meta.addEnchant(ench, enchSection.getInt(enchName, 1), true);
                } catch (Exception ignored) {}
            }
        }

        // Glow flag
        if (s.getBoolean("glow", false)) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Auto-generate a display item for command-only rewards based on the command text. */
    private ItemStack autoDisplay(List<String> commands, List<String> messages) {
        String cmd = commands.isEmpty() ? "" : commands.get(0).toLowerCase(Locale.ROOT);
        Material mat = Material.PAPER;
        String name  = "§eReward";

        if (cmd.contains("banknote")) {
            mat = Material.PAPER;
            // Try to extract amount
            String[] parts = cmd.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("give") && i + 2 < parts.length) {
                    String amtStr = parts[i + 2].replace(",", "").replace("_", "");
                    try {
                        long amt = Long.parseLong(amtStr);
                        name = "§a$" + formatMoney(amt) + " Bank Note";
                    } catch (NumberFormatException ignored) { name = "§aBank Note"; }
                    break;
                }
            }
        } else if (cmd.contains("simplecrates:crate give") || cmd.contains("crate give")) {
            mat = Material.CHEST;
            name = "§eCrate Key";
        } else if (cmd.contains("rankvoucher")) {
            mat = Material.BOOK;
            name = "§6Rank Voucher";
        } else if (cmd.contains("gkitgem")) {
            mat = Material.NETHER_STAR;
            name = "§6GKit Gem";
        } else if (cmd.contains("spawner")) {
            mat = Material.SPAWNER;
            name = "§6Mystery Spawner";
        } else if (cmd.contains("sellwand") || cmd.contains("sell_wand")) {
            mat = Material.STICK;
            name = "§6Sell Wand";
        } else if (cmd.contains("tntwand") || cmd.contains("tnt_wand")) {
            mat = Material.TNT;
            name = "§cTNT Wand";
        } else if (cmd.contains("fat_water_bucket") || cmd.contains("fat_lava_bucket")) {
            mat = Material.BUCKET;
            name = cmd.contains("lava") ? "§6Fat Lava Bucket" : "§bFat Water Bucket";
        } else if (!messages.isEmpty()) {
            // Use stripped message as fallback
            name = messages.get(0).replaceAll("§[0-9a-fk-or]", "").trim();
            if (name.length() > 40) name = name.substring(0, 40);
        }

        return LootEntry.buildDisplayItem(mat, name, "§7Click to claim this reward!");
    }

    private String formatMoney(long amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)    return String.format("%,d", amount);
        return String.valueOf(amount);
    }

    private void saveTier(String tierKey) {
        File file = new File(lootDir, tierKey + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("reward-amount", rewardAmounts.getOrDefault(tierKey, 3));

        List<LootEntry> entries = tables.getOrDefault(tierKey, List.of());
        for (int i = 0; i < entries.size(); i++) {
            String base = "entries." + i;
            LootEntry e = entries.get(i);
            yaml.set(base + ".chance", e.chance());
            if (!e.commands().isEmpty()) yaml.set(base + ".commands", e.commands());
            if (!e.messages().isEmpty()) yaml.set(base + ".messages", e.messages());
            if (!e.items().isEmpty()) {
                for (int j = 0; j < e.items().size(); j++) {
                    yaml.set(base + ".items." + j, e.items().get(j).serialize());
                }
            }
        }

        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Failed to save loot/" + tierKey + ".yml", ex); }
    }
}
