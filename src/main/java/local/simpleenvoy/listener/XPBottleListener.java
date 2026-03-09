package local.simpleenvoy.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class XPBottleListener implements Listener {

    private final Plugin plugin;

    public XPBottleListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.EXPERIENCE_BOTTLE) {
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) {
            return;
        }

        Integer customXp = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "xp_amount"),
                org.bukkit.persistence.PersistentDataType.INTEGER
        );

        if (customXp == null || customXp <= 0) {
            return;
        }

        player.giveExp(customXp);

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage("§a✓ §fGained " + formatXP(customXp) + " XP!");
        event.setCancelled(true);
    }

    private String formatXP(int xp) {
        if (xp >= 1_000_000) {
            return String.format("%.1f", xp / 1_000_000.0).replace(".0", "") + "M";
        }
        if (xp >= 1000) {
            return String.format("%.1f", xp / 1000.0).replace(".0", "") + "k";
        }
        return String.valueOf(xp);
    }
}
