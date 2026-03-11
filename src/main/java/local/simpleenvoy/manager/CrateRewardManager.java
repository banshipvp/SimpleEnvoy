package local.simpleenvoy.manager;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class CrateRewardManager {

    private final Plugin plugin;
    private static final Random RANDOM = new Random();

    public enum CrateTier {
        SIMPLE,
        UNIQUE,
        GODLY,
        LEGENDARY
    }

    public CrateRewardManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public List<ItemStack> generateRewards(CrateTier tier, int count) {
        List<ItemStack> rewards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rewards.add(generateSingleReward(tier));
        }
        return rewards;
    }

    public ItemStack generateSingleReward(CrateTier tier) {
        // First try to roll a FactionEnchants special item (orbs, scrolls, white scrolls, etc.)
        ItemStack feItem = rollFactionEnchantItem(tier);
        if (feItem != null) return feItem;

        int roll = RANDOM.nextInt(100);

        if (tier == CrateTier.SIMPLE) {
            if (roll < 12) return getTreasureItem(tier);
            if (roll < 25) return getEnchanterBook(tier);
            if (roll < 38) return getMysteryDust(tier);
            if (roll < 50) return getXPBottle(tier);
            if (roll < 65) return getRandomGearFromKit(tier);
            if (roll < 80) return getMysterySpawner(tier);
            return getPotionItem(tier);
        } else if (tier == CrateTier.UNIQUE) {
            if (roll < 10) return getTreasureItem(tier);
            if (roll < 23) return getEnchanterBook(tier);
            if (roll < 35) return getMysteryDust(tier);
            if (roll < 48) return getXPBottle(tier);
            if (roll < 63) return getRandomGearFromKit(tier);
            if (roll < 78) return getMysterySpawner(tier);
            return getPotionItem(tier);
        } else if (tier == CrateTier.GODLY) {
            if (roll < 8) return getTreasureItem(tier);
            if (roll < 20) return getEnchanterBook(tier);
            if (roll < 32) return getMysteryDust(tier);
            if (roll < 46) return getXPBottle(tier);
            if (roll < 62) return getRandomGearFromKit(tier);
            if (roll < 78) return getMysterySpawner(tier);
            return getPotionItem(tier);
        } else {
            if (roll < 6) return getTreasureItem(tier);
            if (roll < 18) return getEnchanterBook(tier);
            if (roll < 30) return getMysteryDust(tier);
            if (roll < 45) return getXPBottle(tier);
            if (roll < 62) return getRandomGearFromKit(tier);
            if (roll < 78) return getMysterySpawner(tier);
            return getPotionItem(tier);
        }
    }

    private ItemStack getTreasureItem(CrateTier tier) {
        List<ItemStack> items = getTreasurePool(tier);
        return items.get(RANDOM.nextInt(items.size())).clone();
    }

    private List<ItemStack> getTreasurePool(CrateTier tier) {
        List<ItemStack> items = new ArrayList<>();

        items.add(createItem(Material.EXPERIENCE_BOTTLE, "XP Bottle", randomInt(8, 16)));
        items.add(createItem(Material.GOLDEN_APPLE, "Golden Apple", randomInt(1, 3)));
        items.add(createItem(Material.OBSIDIAN, "Obsidian Block", randomInt(16, 64)));
        items.add(createItem(Material.TNT, "TNT", randomInt(1, 8)));
        items.add(createItem(Material.SPAWNER, "Creeper Egg", 1));
        items.add(createItem(Material.CHEST, "Collection Chest", 16));
        items.add(createCustomTntReward(randomExplosiveVariant(), 64));
        items.add(createCustomCreeperEggReward(randomExplosiveVariant(), 8));

        if (tier == CrateTier.SIMPLE) {
            items.add(createItem(Material.DIAMOND, "Resources", randomInt(16, 64)));
            items.add(createItem(Material.EMERALD, "Resources", randomInt(8, 32)));
            items.add(createItem(Material.OAK_LOG, "Wood", randomInt(32, 128)));
            items.add(createItem(Material.PAPER, "Money Note", randomInt(1, 3)));
            items.add(createItem(Material.EMERALD_BLOCK, "Gkit Gem (Simple)", randomInt(1, 2)));
            items.add(createItem(Material.BOOK, "Black Scroll", randomInt(1, 2)));
            items.add(createItem(Material.STICK, "Wand (TNT)", 1));
            items.add(createItem(Material.STICK, "Wand (Sell)", 1));
            items.add(createItem(Material.BUCKET, "Fat Bucket", 1));
        } else if (tier == CrateTier.UNIQUE) {
            items.add(createItem(Material.PAPER, "Money Note", randomInt(2, 4)));
            items.add(createItem(Material.EMERALD_BLOCK, "Gkit Gem", randomInt(1, 2)));
            items.add(createItem(Material.BOOK, "Black Scroll", randomInt(1, 2)));
            items.add(createItem(Material.STICK, "Wand (TNT)", 1));
            items.add(createItem(Material.STICK, "Wand (Sell)", 1));
            items.add(createItem(Material.BUCKET, "Fat Bucket", 1));
        } else {
            items.add(createItem(Material.PAPER, "Money Note (Large)", randomInt(3, 6)));
            items.add(createItem(Material.EMERALD_BLOCK, "Gkit Gem (Legendary)", randomInt(2, 4)));
            items.add(createItem(Material.BOOK, "Black Scroll (Legendary)", randomInt(2, 4)));
            items.add(createItem(Material.STICK, "Wand (TNT)", 1));
            items.add(createItem(Material.STICK, "Wand (Sell)", 1));
            items.add(createItem(Material.BUCKET, "Fat Bucket", 1));
            items.add(createItem(Material.NETHER_STAR, "Fallen Hero", randomInt(1, 2)));
        }

        return items;
    }

    private ItemStack getMysteryDust(CrateTier tier) {
        ItemStack dust = new ItemStack(Material.FIRE_CHARGE);
        dust.setAmount(randomInt(1, 4));

        ItemMeta meta = dust.getItemMeta();
        if (meta != null) {
            if (tier == CrateTier.SIMPLE) {
                meta.setDisplayName("§eSimple Mystery Dust");
            } else if (tier == CrateTier.UNIQUE) {
                meta.setDisplayName("§aUnique Mystery Dust");
            } else if (tier == CrateTier.GODLY) {
                meta.setDisplayName("§dGodly Mystery Dust");
            } else {
                meta.setDisplayName("§cLegendary Mystery Dust");
            }
            dust.setItemMeta(meta);
        }

        return dust;
    }

    private ItemStack getXPBottle(CrateTier tier) {
        int xpAmount;
        if (tier == CrateTier.SIMPLE) {
            xpAmount = randomInt(1000, 5000);
        } else if (tier == CrateTier.UNIQUE) {
            xpAmount = randomInt(5000, 15000);
        } else if (tier == CrateTier.GODLY) {
            xpAmount = randomInt(15000, 50000);
        } else {
            xpAmount = randomInt(50000, 150000);
        }

        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        bottle.setAmount(1);

        ItemMeta meta = bottle.getItemMeta();
        if (meta != null) {
            String tierColor = tier == CrateTier.SIMPLE ? "§e" : tier == CrateTier.UNIQUE ? "§a" : tier == CrateTier.GODLY ? "§d" : "§c";
            meta.setDisplayName(tierColor + "XP Bottle (" + formatXP(xpAmount) + ")");
            meta.setLore(Arrays.asList(
                    "§7Grants " + formatXP(xpAmount) + " experience",
                    "§7Throw to gain XP"
            ));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "xp_amount"),
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    xpAmount
            );
            bottle.setItemMeta(meta);
        }

        return bottle;
    }

    private ItemStack getEnchanterBook(CrateTier tier) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            if (tier == CrateTier.SIMPLE) {
                meta.setDisplayName("§eSimple Enchanter Book");
                meta.setLore(Arrays.asList("§7Grants simple enchantments", "§7for gear"));
            } else if (tier == CrateTier.UNIQUE) {
                meta.setDisplayName("§aUnique Enchanter Book");
                meta.setLore(Arrays.asList("§7Grants unique enchantments", "§7for gear"));
            } else if (tier == CrateTier.GODLY) {
                meta.setDisplayName("§dGodly Enchanter Book");
                meta.setLore(Arrays.asList("§7Grants godly & soul", "§7enchantments for gear"));
            } else {
                meta.setDisplayName("§cLegendary Enchanter Book");
                meta.setLore(Arrays.asList("§7Grants legendary & soul", "§7enchantments for gear"));
            }
            book.setItemMeta(meta);
        }
        return book;
    }

    private ItemStack getMysterySpawner(CrateTier tier) {
        String mobType;
        if (tier == CrateTier.SIMPLE || tier == CrateTier.UNIQUE) {
            String[] basicMobs = {"ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "ENDERMAN"};
            mobType = basicMobs[RANDOM.nextInt(basicMobs.length)];
        } else {
            String[] advancedMobs = {"GHAST", "IRON_GOLEM", "WARDEN", "BLAZE", "CAVE_SPIDER"};
            mobType = advancedMobs[RANDOM.nextInt(advancedMobs.length)];
        }
        return createMysterySpawnerGem(mobType, tier);
    }

    private ItemStack createMysterySpawnerGem(String mobType, CrateTier tier) {
        ItemStack gem = new ItemStack(Material.AMETHYST_SHARD);
        gem.setAmount(1);

        ItemMeta meta = gem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5Mystery Spawner Gem (" + mobType + ")");
            String tierColor = tier == CrateTier.SIMPLE ? "§e" : tier == CrateTier.UNIQUE ? "§a" : tier == CrateTier.GODLY ? "§d" : "§c";
            meta.setLore(Arrays.asList(
                    tierColor + tier.name().toLowerCase(),
                    "§7Place to spawn a mystery mob",
                    "§7Type: §f" + mobType
            ));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "spawner_mob"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    mobType
            );
            gem.setItemMeta(meta);
        }

        return gem;
    }

    private ItemStack getRandomGearFromKit(CrateTier tier) {
        String[] gkitNames = {"paladin", "berserker", "raider", "archer", "miner", "assassin", "tank", "warlock", "duelist"};
        String selectedKit = gkitNames[RANDOM.nextInt(gkitNames.length)];

        try {
            org.bukkit.plugin.Plugin simpleKits = plugin.getServer().getPluginManager().getPlugin("SimpleKits");
            if (simpleKits == null) {
                return getPlaceholderGear(tier);
            }

            Object kitManager = simpleKits.getClass().getMethod("getKitManager").invoke(simpleKits);
            Object gkit = kitManager.getClass().getMethod("getKit", String.class).invoke(kitManager, selectedKit);
            if (gkit == null) {
                return getPlaceholderGear(tier);
            }

            List<?> kitItems = (List<?>) gkit.getClass().getMethod("getItems").invoke(gkit);
            if (kitItems == null || kitItems.isEmpty()) {
                return getPlaceholderGear(tier);
            }

            ItemStack singleItem = (ItemStack) kitItems.get(RANDOM.nextInt(kitItems.size()));
            return singleItem.clone();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get gear from SimpleKits: " + e.getMessage());
            return getPlaceholderGear(tier);
        }
    }

    private ItemStack getPlaceholderGear(CrateTier tier) {
        Material[] materials = {
                Material.DIAMOND_SWORD, Material.DIAMOND_HELMET,
                Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.NETHERITE_SWORD, Material.NETHERITE_HELMET
        };

        ItemStack gear = new ItemStack(materials[RANDOM.nextInt(materials.length)]);
        ItemMeta meta = gear.getItemMeta();
        if (meta != null) {
            String tierColor = tier == CrateTier.SIMPLE ? "§e" : tier == CrateTier.UNIQUE ? "§a" : tier == CrateTier.GODLY ? "§d" : "§c";
            meta.setDisplayName(tierColor + tier.name() + " Gear");
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            addEnchant(meta, "sharpness", tier == CrateTier.SIMPLE || tier == CrateTier.UNIQUE ? 2 : tier == CrateTier.GODLY ? 4 : 5);
            addEnchant(meta, "protection", tier == CrateTier.SIMPLE || tier == CrateTier.UNIQUE ? 1 : tier == CrateTier.GODLY ? 3 : 4);

            gear.setItemMeta(meta);
        }
        return gear;
    }

    private void addEnchant(ItemMeta meta, String key, int level) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private ItemStack createItem(Material material, String displayName, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String randomExplosiveVariant() {
        String[] variants = {"LETHAL", "GIGANTIC", "LUCKY"};
        return variants[RANDOM.nextInt(variants.length)];
    }

    private ItemStack createCustomTntReward(String variant, int amount) {
        String color = switch (variant) {
            case "LETHAL" -> "§c";
            case "GIGANTIC" -> "§6";
            case "LUCKY" -> "§e";
            default -> "§f";
        };
        String symbol = switch (variant) {
            case "LETHAL" -> "⚡";
            case "GIGANTIC" -> "✦";
            case "LUCKY" -> "♻";
            default -> "•";
        };

        ItemStack item = new ItemStack(Material.TNT, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + symbol + " " + variant + " TNT");
            meta.setLore(Arrays.asList("§7Custom raiding explosive", "§7Recognized by SimpleFactionsRaiding"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCustomCreeperEggReward(String variant, int amount) {
        String color = switch (variant) {
            case "LETHAL" -> "§c";
            case "GIGANTIC" -> "§6";
            case "LUCKY" -> "§e";
            default -> "§f";
        };
        String symbol = switch (variant) {
            case "LETHAL" -> "⚡";
            case "GIGANTIC" -> "✦";
            case "LUCKY" -> "♻";
            default -> "•";
        };

        ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + symbol + " " + variant + " CREEPER EGG");
            meta.setLore(Arrays.asList("§7Spawns a custom raiding creeper", "§7Recognized by SimpleFactionsRaiding"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getPotionItem(CrateTier tier) {
        String[] potionEffects = {"Speed", "Strength", "Resistance", "Jump Boost"};
        String effect = potionEffects[RANDOM.nextInt(potionEffects.length)];

        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        if (meta != null) {
            String tierColor = tier == CrateTier.SIMPLE ? "§e" : tier == CrateTier.UNIQUE ? "§a" : tier == CrateTier.GODLY ? "§d" : "§c";
            meta.setDisplayName(tierColor + effect + " Potion");
            meta.setLore(List.of("§7" + effect + " Buff Potion"));
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private int randomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    private String formatXP(int xp) {
        if (xp >= 1000) {
            if (xp % 1000 == 0) {
                return (xp / 1000) + "k";
            }
            return String.format("%.1f", xp / 1000.0) + "k";
        }
        return String.valueOf(xp);
    }

    // ── FactionEnchants integration ────────────────────────────────────────────

    /**
     * Rolls for a FactionEnchants special item for the given envoy tier.
     * Returns null if FactionEnchants is not loaded or the roll misses.
     *
     * Tier probabilities (independent of regular loot roll):
     *   SIMPLE   – 5 % randomization scroll (Ultimate)
     *   UNIQUE   – 4 % Legendary scroll, 3 % white scroll, 3 % name tag, 3 % soul gem generator
     *   GODLY    – 5 % Godly scroll, 6 % white scroll, 3 % name tag, 3 % soul gem generator,
     *              6 % weapon/armor orb [10]
     *   LEGENDARY– 6 % Godly scroll, 8 % white scroll, 5 % name tag, 4 % soul gem generator,
     *              10 % weapon/armor orb [10–12]
     */
    private ItemStack rollFactionEnchantItem(CrateTier tier) {
        com.factionenchants.FactionEnchantsPlugin fe = getFactionEnchants();
        if (fe == null) return null;

        int roll = RANDOM.nextInt(100);

        return switch (tier) {
            case SIMPLE -> {
                if (roll < 5)  yield fe_randScroll(tier, fe);  // 5%
                yield null;
            }
            case UNIQUE -> {
                if (roll < 4)  yield fe_randScroll(tier, fe);  // 4%
                if (roll < 7)  yield com.factionenchants.items.WhiteScrollItem.create(fe);   // 3%
                if (roll < 10) yield com.factionenchants.items.NameTagItem.create(fe);       // 3%
                if (roll < 13) yield com.factionenchants.items.SoulGemItem.createGenerator(fe); // 3%
                yield null;
            }
            case GODLY -> {
                if (roll < 5)  yield fe_randScroll(tier, fe);  // 5%
                if (roll < 11) yield com.factionenchants.items.WhiteScrollItem.create(fe);   // 6%
                if (roll < 14) yield com.factionenchants.items.NameTagItem.create(fe);       // 3%
                if (roll < 17) yield com.factionenchants.items.SoulGemItem.createGenerator(fe); // 3%
                if (roll < 23) yield RANDOM.nextBoolean()                                    // 6%
                        ? com.factionenchants.items.EnchantmentOrbItem.createWeaponOrb(fe, 10)
                        : com.factionenchants.items.EnchantmentOrbItem.createArmorOrb(fe, 10);
                yield null;
            }
            case LEGENDARY -> {
                if (roll < 6)  yield fe_randScroll(tier, fe);  // 6%
                if (roll < 14) yield com.factionenchants.items.WhiteScrollItem.create(fe);   // 8%
                if (roll < 19) yield com.factionenchants.items.NameTagItem.create(fe);       // 5%
                if (roll < 23) yield com.factionenchants.items.SoulGemItem.createGenerator(fe); // 4%
                if (roll < 33) {                                                             // 10%
                    int slots = 10 + RANDOM.nextInt(3); // 10, 11, or 12
                    yield RANDOM.nextBoolean()
                            ? com.factionenchants.items.EnchantmentOrbItem.createWeaponOrb(fe, slots)
                            : com.factionenchants.items.EnchantmentOrbItem.createArmorOrb(fe, slots);
                }
                yield null;
            }
        };
    }

    private ItemStack fe_randScroll(CrateTier tier, com.factionenchants.FactionEnchantsPlugin fe) {
        return switch (tier) {
            case SIMPLE  -> com.factionenchants.items.RandomizationScrollItem.createUltimate(fe);
            case UNIQUE  -> com.factionenchants.items.RandomizationScrollItem.createLegendary(fe);
            case GODLY, LEGENDARY -> com.factionenchants.items.RandomizationScrollItem.createGodly(fe);
        };
    }

    private com.factionenchants.FactionEnchantsPlugin getFactionEnchants() {
        org.bukkit.plugin.Plugin fe = plugin.getServer().getPluginManager().getPlugin("FactionEnchants");
        return fe instanceof com.factionenchants.FactionEnchantsPlugin fep ? fep : null;
    }
}
