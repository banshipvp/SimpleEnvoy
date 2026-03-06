package local.simpleenvoy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single spawned envoy chest in the world.
 *
 * <p>Each chest holds:
 * <ul>
 *   <li>Its block location.</li>
 *   <li>Its rarity {@link EnvoyTier}.</li>
 *   <li>A virtual {@link Inventory} players open on right-click.</li>
 *   <li>References to hologram {@link ArmorStand} entities.</li>
 *   <li>Loot entries paired per inventory slot, so commands can be executed on claim.</li>
 * </ul>
 */
public class EnvoyChest {

    private final Location blockLocation;
    private final EnvoyTier tier;
    private final Inventory inventory;
    private final List<LootEntry> slotEntries; // index aligns with inventory slot
    private final List<ArmorStand> holograms = new ArrayList<>();
    private boolean broken = false;

    public EnvoyChest(Location blockLocation, EnvoyTier tier,
                      List<LootEntry> slotEntries) {
        this.blockLocation = blockLocation.clone();
        this.tier          = tier;
        this.slotEntries   = new ArrayList<>(slotEntries);

        // Build the inventory
        ChestInventoryHolder holder = new ChestInventoryHolder(this);
        int size = Math.max(9, ((slotEntries.size() + 8) / 9) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(holder, size,
                tier.displayName() + " §8[" + slotEntries.size() + " items]");
        holder.setInventory(inv);
        this.inventory = inv;

        // Fill inventory slots
        int slot = 0;
        for (LootEntry entry : slotEntries) {
            List<ItemStack> invItems = entry.inventoryItems();
            for (ItemStack is : invItems) {
                if (slot < size) {
                    inv.setItem(slot++, is.clone());
                }
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Location blockLocation()   { return blockLocation.clone(); }
    public EnvoyTier tier()           { return tier; }
    public Inventory inventory()      { return inventory; }
    public boolean isBroken()         { return broken; }
    public List<LootEntry> slotEntries() { return slotEntries; }

    /** The underlying Block (may or may not be a CHEST if already removed). */
    public Block block() {
        World world = Bukkit.getWorld(blockLocation.getWorld().getName());
        if (world == null) return null;
        return world.getBlockAt(blockLocation);
    }

    public List<ArmorStand> holograms() { return holograms; }

    // ── Hologram registration ─────────────────────────────────────────────────

    public void addHologram(ArmorStand stand) { holograms.add(stand); }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Returns true when the inventory has no more items left. */
    public boolean isEmpty() {
        for (ItemStack is : inventory.getContents()) {
            if (is != null && is.getType().isItem()) return false;
        }
        return true;
    }

    /**
     * Mark the chest as broken, remove its block, close all open views,
     * and remove hologram entities.
     */
    public void breakChest() {
        if (broken) return;
        broken = true;

        // Close all open views of this inventory
        inventory.close();

        // Remove block
        Block b = block();
        if (b != null && b.getType() == tier.blockMaterial()) {
            b.setType(org.bukkit.Material.AIR);
        }

        // Remove holograms
        for (ArmorStand stand : holograms) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        holograms.clear();
    }

    /**
     * Find the loot entry that corresponds to data in a given inventory slot.
     * The slot-to-entry mapping tracks which "item group" populated each slot.
     */
    public LootEntry getEntryForSlot(int invSlot) {
        if (invSlot < 0 || invSlot >= slotEntries.size()) return null;
        return slotEntries.get(invSlot);
    }

    /**
     * Spawn hologram ArmorStands above the chest block.
     */
    public void spawnHolograms() {
        World world = blockLocation.getWorld();
        if (world == null) return;

        double bx = blockLocation.getBlockX() + 0.5;
        double by = blockLocation.getBlockY();
        double bz = blockLocation.getBlockZ() + 0.5;

        spawnHologramLine(world, bx, by + 2.5, bz, tier.displayName());
        spawnHologramLine(world, bx, by + 1.95, bz, "§e▶ Right-click to open");
        spawnHologramLine(world, bx, by + 1.45, bz, "§c▶ Left-click for instant loot");
    }

    private void spawnHologramLine(World world, double x, double y, double z, String text) {
        Location loc = new Location(world, x, y, z);
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSilent(true);
        holograms.add(stand);
    }
}
