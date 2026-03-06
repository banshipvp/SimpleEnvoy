package local.simpleenvoy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Handles all player interactions with SimpleEnvoy chests.
 *
 * <ul>
 *   <li><b>Right-click block:</b> opens the virtual chest inventory.</li>
 *   <li><b>Left-click block:</b> breaks the chest instantly and gives all unclaimed loot.</li>
 *   <li><b>InventoryClickEvent:</b> intercepts item removal so we can execute commands
 *       and remove claimed items from our tracking.</li>
 * </ul>
 */
public class ChestListener implements Listener {

    private final JavaPlugin plugin;
    private final EnvoyManager envoyManager;

    public ChestListener(JavaPlugin plugin, EnvoyManager envoyManager) {
        this.plugin       = plugin;
        this.envoyManager = envoyManager;
    }

    // ── Block interaction ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        EnvoyChest chest = envoyManager.getChestAt(event.getClickedBlock());
        if (chest == null) return;

        event.setCancelled(true); // prevent default chest/block behaviour

        Player player = event.getPlayer();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(player, chest);
        } else {
            handleLeftClick(player, chest);
        }
    }

    /** Right-click: open the virtual inventory. */
    private void handleRightClick(Player player, EnvoyChest chest) {
        if (chest.isBroken()) {
            player.sendMessage("§cThis crate is already gone!");
            return;
        }
        player.openInventory(chest.inventory());
    }

    /** Left-click: break chest and give all remaining loot. */
    private void handleLeftClick(Player player, EnvoyChest chest) {
        if (chest.isBroken()) {
            player.sendMessage("§cThis crate is already gone!");
            return;
        }

        // Close all viewers first
        chest.inventory().close();

        // Execute all remaining commands and give all items
        for (LootEntry entry : chest.slotEntries()) {
            executeEntry(player, entry);
        }

        sendMessages(player, chest.slotEntries());

        player.sendMessage(chest.tier().colorCode() + "§lInstant Loot! §7You smashed open the "
                + chest.tier().displayName() + " §7and grabbed everything!");

        chest.breakChest();
        envoyManager.notifyChestRemoved(chest);
    }

    // ── Inventory interaction ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChestInventoryHolder holder)) return;

        EnvoyChest chest = holder.getEnvoyChest();
        if (chest == null || chest.isBroken()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        // Prevent placing items into the envoy inventory
        if (event.getClickedInventory() == chest.inventory()) {
            // Taking an item from the chest
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                event.setCancelled(true);
                return;
            }

            // Map click slot back to loot entry
            int slot = event.getSlot();
            LootEntry entry = chest.getEntryForSlot(slot);
            if (entry != null) {
                // Execute commands (if any)
                if (entry.hasCommands()) {
                    Bukkit.getScheduler().runTask(plugin, () -> executeCommands(player, entry.commands()));
                }
                // Send messages
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String msg : entry.messages()) {
                        player.sendMessage(msg);
                    }
                });
            }

            // The item in the slot will be naturally moved to the player's cursor/inventory
            // by Bukkit — we just need to clear it from the chest tracking.
            Bukkit.getScheduler().runTask(plugin, () -> {
                chest.inventory().setItem(slot, null);
                checkChestEmpty(chest, player);
            });

        } else {
            // Player is clicking in their own inventory while chest is open
            // Prevent shift-clicking items INTO the envoy chest
            if (event.isShiftClick()) {
                // do nothing, Bukkit handles shift-click to player naturally
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChestInventoryHolder)) return;
        // Prevent dragging items into the envoy inventory
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void checkChestEmpty(EnvoyChest chest, Player lastPlayer) {
        if (chest.isBroken()) return;
        if (chest.isEmpty()) {
            chest.breakChest();
            envoyManager.notifyChestRemoved(chest);
            lastPlayer.sendMessage("§7The " + chest.tier().displayName() + " §7has been fully looted and disappeared.");
        }
    }

    private void executeEntry(Player player, LootEntry entry) {
        // Give real items
        if (entry.hasItems()) {
            for (ItemStack item : entry.items()) {
                give(player, item.clone());
            }
        }
        // Execute commands
        if (entry.hasCommands()) {
            executeCommands(player, entry.commands());
        }
    }

    private void executeCommands(Player player, List<String> commands) {
        for (String cmd : commands) {
            String resolved = cmd.replace("%player%", player.getName())
                                 .replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void sendMessages(Player player, List<LootEntry> entries) {
        for (LootEntry entry : entries) {
            for (String msg : entry.messages()) {
                player.sendMessage(msg);
            }
        }
    }

    private void give(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }
}
