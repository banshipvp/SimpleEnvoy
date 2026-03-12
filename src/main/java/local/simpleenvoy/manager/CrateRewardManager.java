package local.simpleenvoy.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CrateRewardManager {

    private final Plugin plugin;
    private static final Random RANDOM = new Random();

    public enum CrateTier {
        SIMPLE("Simple",     org.bukkit.ChatColor.YELLOW),
        UNIQUE("Unique",     org.bukkit.ChatColor.GREEN),
        ELITE("Elite",       org.bukkit.ChatColor.AQUA),
        ULTIMATE("Ultimate", org.bukkit.ChatColor.DARK_PURPLE),
        LEGENDARY("Legendary", org.bukkit.ChatColor.GOLD),
        GODLY("Godly",       org.bukkit.ChatColor.LIGHT_PURPLE);

        private final String display;
        private final org.bukkit.ChatColor color;

        CrateTier(String display, org.bukkit.ChatColor color) {
            this.display = display;
            this.color = color;
        }

        public String getDisplay() { return display; }
        public String getColor()   { return color.toString(); }
        public String getColoredDisplay() { return color + display; }
    }

    public CrateRewardManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Generate count rewards for a player from the appropriate loot YAML. */
    public List<LootReward> generateRewards(CrateTier tier, int count) {
        YamlConfiguration loot = loadLoot(tier);
        List<LootEntry> pool = buildPool(loot);
        if (pool.isEmpty()) return List.of();

        List<LootReward> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LootEntry entry = rollEntry(pool);
            if (entry != null) results.add(entry.toReward());
        }
        return results;
    }

    /** How many rewards to give per tier. */
    public int rewardCount(CrateTier tier) {
        YamlConfiguration loot = loadLoot(tier);
        return Math.max(1, loot.getInt("reward-amount", switch (tier) {
            case SIMPLE    -> 3;
            case UNIQUE    -> 4;
            case ELITE     -> 4;
            case ULTIMATE  -> 5;
            case LEGENDARY -> 5;
            case GODLY     -> 6;
        }));
    }

    /** Build a display ItemStack from a loot entry (for /envoy edit GUI). */
    public List<ItemStack> getPreviewItems(CrateTier tier) {
        YamlConfiguration loot = loadLoot(tier);
        List<LootEntry> pool = buildPool(loot);
        List<ItemStack> out = new ArrayList<>();
        for (LootEntry e : pool) {
            out.add(e.displayItem);
        }
        return out;
    }

    // â”€â”€ Reward execution â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** A reward that has already been rolled â€” can be applied to a player. */
    public record LootReward(ItemStack displayItem, List<String> commands, List<ItemStack> directItems, List<String> messages) {
        public void giveToPlayer(Player player) {
            String name = player.getName();
            for (String cmd : commands) {
                String resolved = cmd.replace("%player%", name);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
            for (ItemStack item : directItems) {
                player.getInventory().addItem(item).values()
                        .forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
            }
            for (String msg : messages) {
                player.sendMessage(colorize(msg));
            }
        }
    }

    // â”€â”€ Internal loot loading â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private YamlConfiguration loadLoot(CrateTier tier) {
        File f = new File(plugin.getDataFolder(), "loot/" + tier.name().toLowerCase() + ".yml");
        if (!f.exists()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(f);
    }

    private List<LootEntry> buildPool(YamlConfiguration yml) {
        ConfigurationSection entries = yml.getConfigurationSection("entries");
        if (entries == null) return List.of();
        List<LootEntry> pool = new ArrayList<>();
        Set<String> keys = entries.getKeys(false);
        for (String key : keys) {
            ConfigurationSection sec = entries.getConfigurationSection(key);
            if (sec == null) continue;
            double chance = sec.getDouble("chance", 1.0);
            if (chance <= 0) continue;

            // Display item
            ItemStack display = buildDisplayItem(sec.getConfigurationSection("display"));

            // Commands
            List<String> commands = sec.getStringList("commands");

            // Direct items
            List<ItemStack> directItems = new ArrayList<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String ik : itemsSec.getKeys(false)) {
                    ConfigurationSection is = itemsSec.getConfigurationSection(ik);
                    if (is == null) continue;
                    ItemStack built = buildDirectItem(is);
                    if (built != null) {
                        directItems.add(built);
                        if (display == null) display = built.clone(); // use as display if no display block
                    }
                }
            }

            // Messages
            List<String> messages = sec.getStringList("messages");

            if (display == null) display = new ItemStack(Material.CHEST);
            pool.add(new LootEntry(chance, display, commands, directItems, messages));
        }
        return pool;
    }

    private LootEntry rollEntry(List<LootEntry> pool) {
        double total = pool.stream().mapToDouble(e -> e.chance).sum();
        double roll = RANDOM.nextDouble() * total;
        double acc = 0;
        for (LootEntry e : pool) {
            acc += e.chance;
            if (roll < acc) return e;
        }
        return pool.isEmpty() ? null : pool.get(pool.size() - 1);
    }

    private ItemStack buildDisplayItem(ConfigurationSection sec) {
        if (sec == null) return null;
        String matName = sec.getString("material", "CHEST");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.CHEST;
        int amount = Math.max(1, sec.getInt("amount", 1));
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = sec.getString("name");
            if (name != null) meta.setDisplayName(colorize(name));
            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(colorize(l));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildDirectItem(ConfigurationSection sec) {
        String matName = sec.getString("material", "CHEST");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) return null;
        int amount = Math.max(1, sec.getInt("amount", 1));
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = sec.getString("name");
            if (name != null) meta.setDisplayName(colorize(name));
            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(colorize(l));
                meta.setLore(colored);
            }
            ConfigurationSection enchSec = sec.getConfigurationSection("enchants");
            if (enchSec != null) {
                for (String ek : enchSec.getKeys(false)) {
                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(ek.toLowerCase()));
                    if (ench != null) meta.addEnchant(ench, enchSec.getInt(ek, 1), true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // â”€â”€ Internal record â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private record LootEntry(double chance, ItemStack displayItem, List<String> commands, List<ItemStack> directItems, List<String> messages) {
        LootReward toReward() {
            return new LootReward(displayItem.clone(), commands, directItems, messages);
        }
    }
}
