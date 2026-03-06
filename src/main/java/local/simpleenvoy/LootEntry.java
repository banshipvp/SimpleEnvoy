package local.simpleenvoy;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry in a tier's loot table.
 *
 * <p>An entry may carry:
 * <ul>
 *   <li>A list of real {@link ItemStack}s that go into the inventory.</li>
 *   <li>A list of commands executed for the claiming player.</li>
 *   <li>A display item shown in the inventory when the entry is command-only.</li>
 * </ul>
 */
public class LootEntry {

    private final double chance;
    private final List<ItemStack> items;
    private final List<String> commands;
    private final List<String> messages;
    private final ItemStack displayItem; // used when items list is empty (command-only)

    public LootEntry(double chance, List<ItemStack> items, List<String> commands,
                     List<String> messages, ItemStack displayItem) {
        this.chance      = chance;
        this.items       = items != null ? List.copyOf(items) : List.of();
        this.commands    = commands != null ? List.copyOf(commands) : List.of();
        this.messages    = messages != null ? List.copyOf(messages) : List.of();
        this.displayItem = displayItem;
    }

    public double chance()         { return chance; }
    public List<ItemStack> items() { return items; }
    public List<String> commands() { return commands; }
    public List<String> messages() { return messages; }

    /** Returns the item(s) that should appear in the chest inventory. */
    public List<ItemStack> inventoryItems() {
        if (!items.isEmpty()) return items;
        if (displayItem != null) return List.of(displayItem);
        return List.of(buildFallbackDisplay());
    }

    /** True when claiming this slot should also run commands. */
    public boolean hasCommands() { return !commands.isEmpty(); }

    /** True when this entry contains real items that should go into the player's inventory. */
    public boolean hasItems() { return !items.isEmpty(); }

    // ── Serialization helpers for YAML persistence ───────────────────────────

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chance", chance);

        if (!items.isEmpty()) {
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (ItemStack item : items) {
                itemList.add(item.serialize());
            }
            map.put("items", itemList);
        }

        if (!commands.isEmpty()) map.put("commands", new ArrayList<>(commands));
        if (!messages.isEmpty()) map.put("messages", new ArrayList<>(messages));

        if (displayItem != null) {
            map.put("display", displayItem.serialize());
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    public static LootEntry deserialize(Map<?, ?> map) {
        Object chanceRaw = map.get("chance");
        double chance = chanceRaw instanceof Number n ? n.doubleValue() : 0.0;

        List<ItemStack> items = new ArrayList<>();
        Object rawItems = map.get("items");
        if (rawItems instanceof List<?> itemList) {
            for (Object raw : itemList) {
                if (raw instanceof Map<?, ?> itemMap) {
                    try {
                        ItemStack is = ItemStack.deserialize((Map<String, Object>) itemMap);
                        if (is != null) items.add(is);
                    } catch (Exception ignored) {}
                }
            }
        }

        Object rawCommands = map.get("commands");
        List<String> commands = rawCommands instanceof List<?> cl
                ? cl.stream().map(Object::toString).toList()
                : List.of();

        Object rawMessages = map.get("messages");
        List<String> messages = rawMessages instanceof List<?> ml
                ? ml.stream().map(Object::toString).toList()
                : List.of();

        ItemStack displayItem = null;
        Object rawDisplay = map.get("display");
        if (rawDisplay instanceof Map<?, ?> dm) {
            try {
                displayItem = ItemStack.deserialize((Map<String, Object>) dm);
            } catch (Exception ignored) {}
        }

        return new LootEntry(chance, items, commands, messages, displayItem);
    }

    private ItemStack buildFallbackDisplay() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eReward");
            meta.setLore(List.of("§7This reward is redeemed on pickup."));
            paper.setItemMeta(meta);
        }
        return paper;
    }

    // ── Static factory methods for building common command-reward display items ──

    public static ItemStack buildDisplayItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack buildDisplayItem(Material material, String name, String loreLine) {
        return buildDisplayItem(material, name, List.of(loreLine));
    }

    /** Build a real ItemStack from the simple config entries used in loot YAMLs. */
    public static ItemStack buildFromMap(Map<?, ?> itemMap) {
        Object matRaw = itemMap.get("material");
        String materialName = matRaw != null ? matRaw.toString() : "PAPER";
        Object amtRaw = itemMap.get("amount");
        int amount = amtRaw instanceof Number n ? n.intValue() : 1;
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object rawName = itemMap.get("name");
        if (rawName != null) {
            meta.setDisplayName(rawName.toString().replace("&", "§"));
        }

        Object rawLore = itemMap.get("lore");
        if (rawLore instanceof List<?> loreList) {
            meta.setLore(loreList.stream().map(l -> l.toString().replace("&", "§")).toList());
        }

        Object rawEnchants = itemMap.get("enchants");
        if (rawEnchants instanceof Map<?, ?> enchMap) {
            for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                try {
                    @SuppressWarnings("deprecation")
                    Enchantment ench = Enchantment.getByName(e.getKey().toString().toUpperCase());
                    if (ench != null) {
                        int level = ((Number) e.getValue()).intValue();
                        meta.addEnchant(ench, level, true);
                    }
                } catch (Exception ignored) {}
            }
        }

        item.setItemMeta(meta);
        return item;
    }
}
