package local.simpleenvoy.command;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public class XPBottleCommand implements CommandExecutor {

    private final Plugin plugin;

    public XPBottleCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§c/xpbottle <amount>[k|m]");
            player.sendMessage("§7Example: §f/xpbottle 10k §7(converts 10,000 XP)");
            return true;
        }

        int xpAmount;
        try {
            xpAmount = parseXPAmount(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount! Use format: 10k, 1m, 5000");
            player.sendMessage("§7k = thousands, m = millions");
            return true;
        }

        int playerExp = player.getTotalExperience();
        if (xpAmount > playerExp) {
            player.sendMessage("§cYou don't have enough XP! You have " + formatXP(playerExp) + ", need " + formatXP(xpAmount));
            return true;
        }

        player.setTotalExperience(playerExp - xpAmount);
        player.sendMessage("§a✓ §fWithdrew " + formatXP(xpAmount) + " XP from your total.");

        int bottlesNeeded = (int) Math.ceil(xpAmount / 10000.0);
        int remainingXP = xpAmount;

        for (int i = 0; i < bottlesNeeded; i++) {
            int bottleXP = Math.min(10000, remainingXP);
            ItemStack bottle = createXPBottle(bottleXP);

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(bottle);
            } else {
                player.getWorld().dropItem(player.getLocation(), bottle);
                player.sendMessage("§eYour inventory is full! Dropped XP bottle on ground.");
            }

            remainingXP -= bottleXP;
        }

        player.sendMessage("§a✓ §fCreated " + bottlesNeeded + " XP bottle(s)!");
        return true;
    }

    private int parseXPAmount(String input) {
        String normalized = input.toLowerCase().trim();
        if (normalized.endsWith("k")) {
            int base = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            return base * 1000;
        }
        if (normalized.endsWith("m")) {
            int base = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            return base * 1_000_000;
        }
        return Integer.parseInt(normalized);
    }

    private ItemStack createXPBottle(int xpAmount) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        bottle.setAmount(1);

        ItemMeta meta = bottle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§3XP Bottle (" + formatXP(xpAmount) + ")");
            meta.setLore(Arrays.asList(
                    "§7Grants " + formatXP(xpAmount) + " experience",
                    "§7Throw to gain XP",
                    "§8Right-click to use"
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
