package local.simpleenvoy;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder that links a Bukkit inventory to the EnvoyChest it belongs to.
 */
public class ChestInventoryHolder implements InventoryHolder {

    private final EnvoyChest envoyChest;
    private Inventory inventory;

    public ChestInventoryHolder(EnvoyChest envoyChest) {
        this.envoyChest = envoyChest;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public EnvoyChest getEnvoyChest() {
        return envoyChest;
    }
}
