package local.simpleenvoy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles all SimpleEnvoy commands:
 * <ul>
 *   <li>/envoy start [default|heroic]   — start envoys</li>
 *   <li>/envoy stopall                  — stop all active envoys</li>
 *   <li>/envoy setspawn|setdesert|setplains|setnether — set center locations</li>
 *   <li>/envoys                         — show timer status</li>
 *   <li>/envoyedit &lt;tier&gt;         — open loot editor GUI</li>
 *   <li>/envoyadd &lt;tier&gt; &lt;%&gt; — add held item to tier loot</li>
 * </ul>
 */
public class EnvoyCommand implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin   plugin;
    private final EnvoyManager envoyManager;
    private final LootManager  lootManager;

    /** Map of player UUID → tier being edited (for the editor GUI) */
    private final Map<UUID, EnvoyTier> openEditors = new HashMap<>();

    public EnvoyCommand(JavaPlugin plugin, EnvoyManager envoyManager, LootManager lootManager) {
        this.plugin       = plugin;
        this.envoyManager = envoyManager;
        this.lootManager  = lootManager;
    }

    // ── CommandExecutor ───────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        return switch (cmd) {
            case "envoy"     -> handleEnvoy(sender, args);
            case "envoys"    -> handleEnvoys(sender);
            case "envoyedit" -> handleEnvoyEdit(sender, args);
            case "envoyadd"  -> handleEnvoyAdd(sender, args);
            default          -> false;
        };
    }

    // ── /envoy ────────────────────────────────────────────────────────────────

    private boolean handleEnvoy(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cYou don't have permission to manage envoys.");
            return true;
        }

        if (args.length == 0) {
            sendEnvoyHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "start" -> {
                String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "default";
                if (type.equals("heroic") || type.equals("nether")) {
                    EnvoySession session = envoyManager.startHeroic();
                    if (session == null) {
                        sender.sendMessage("§cHeroic envoy not started. Ensure /envoy setnether is set and at least 1 player is in the nether warzone.");
                        return true;
                    }
                    envoyManager.broadcastStartHeroic();
                    sender.sendMessage("§aHeroic nether envoy started with §f" + session.chests().size() + " §achests.");
                } else {
                    List<EnvoySession> sessions = envoyManager.startStandard();
                    if (sessions.isEmpty()) {
                        sender.sendMessage("§cNo envoys started. Ensure spawn/desert/plains centers are set and at least 1 player is in each warzone.");
                        return true;
                    }
                    List<String> names = sessions.stream().map(s -> s.locationKey().key()).toList();
                    envoyManager.broadcastStart(false, names);
                    int total = sessions.stream().mapToInt(s -> s.chests().size()).sum();
                    sender.sendMessage("§aStarted " + sessions.size() + " locations with §f" + total + " §achests total.");
                }
            }

            case "stopall" -> {
                envoyManager.stopAllSessions();
                sender.sendMessage("§cAll active envoy sessions have been stopped.");
                Bukkit.broadcastMessage("§c[SimpleEnvoy] All envoys have been forcibly stopped by an admin.");
            }

            case "setspawn"  -> handleSetCenter(sender, "spawn");
            case "setdesert" -> handleSetCenter(sender, "desert");
            case "setplains" -> handleSetCenter(sender, "plains");
            case "setnether" -> handleSetCenter(sender, "nether");

            case "cleanup" -> {
                int removed = 0;
                for (World w : Bukkit.getWorlds()) {
                    for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                        String name = as.getCustomName();
                        if (name == null) continue;
                        if (name.contains("\u25B6 Right-click to open")
                                || name.contains("\u25B6 Left-click for instant loot")
                                || name.contains("ENVOY")) {
                            as.remove();
                            removed++;
                        }
                    }
                }
                sender.sendMessage("§aRemoved §f" + removed + "§a orphaned envoy hologram entities.");
            }

            case "reload" -> {
                lootManager.reload();
                sender.sendMessage("§aSimpleEnvoy loot tables reloaded.");
            }

            case "info" -> {
                sender.sendMessage("§6§lSimpleEnvoy Active Sessions:");
                Map<String, EnvoySession> sessions = envoyManager.getActiveSessions();
                if (sessions.isEmpty()) {
                    sender.sendMessage("§7  No active sessions.");
                } else {
                    for (Map.Entry<String, EnvoySession> e : sessions.entrySet()) {
                        sender.sendMessage("§e  " + e.getKey() + "§7: §f" + e.getValue().remainingChests() + " §7chests remaining.");
                    }
                }
            }

            default -> sendEnvoyHelp(sender);
        }

        return true;
    }

    private void handleSetCenter(CommandSender sender, String location) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can set centers.");
            return;
        }
        if (envoyManager.setCenter(location, player.getLocation())) {
            player.sendMessage("§aEnvoy center §f" + location + " §aset to your current location.");
        } else {
            player.sendMessage("§cFailed to set center for §f" + location + "§c.");
        }
    }

    private void sendEnvoyHelp(CommandSender sender) {
        sender.sendMessage("§6§lSimpleEnvoy Commands:");
        sender.sendMessage("§e/envoy start [default|heroic] §7- Start envoy event");
        sender.sendMessage("§e/envoy stopall §7- Stop all active envoys");
        sender.sendMessage("§e/envoy setspawn|setdesert|setplains|setnether §7- Set center location");
        sender.sendMessage("§e/envoy info §7- Show active session info");
        sender.sendMessage("§e/envoy reload §7- Reload loot tables");
        sender.sendMessage("§e/envoy cleanup §7- Remove orphaned envoy hologram entities");
        sender.sendMessage("§e/envoys §7- Show timer status");
        sender.sendMessage("§e/envoyedit <tier> §7- Edit loot for a tier");
        sender.sendMessage("§e/envoyadd <tier> <chance> §7- Add held item to tier loot");
    }

    // ── /envoys ───────────────────────────────────────────────────────────────

    private boolean handleEnvoys(CommandSender sender) {
        sender.sendMessage("§6§lSimpleEnvoy Timer Status:");

        // Active sessions
        Map<String, EnvoySession> sessions = envoyManager.getActiveSessions();
        for (EnvoySession.LocationKey lk : EnvoySession.LocationKey.values()) {
            EnvoySession session = sessions.get(lk.key());
            if (session != null && !session.isFinished()) {
                sender.sendMessage("§e  " + lk.key().toUpperCase() + ": §a§lACTIVE §7— §f"
                        + session.remainingChests() + " §7chests remaining");
            } else {
                boolean isHeroic = lk.isHeroic();
                long nextMs = isHeroic ? envoyManager.getNextHeroicMs() : envoyManager.getNextStandardMs();
                if (nextMs <= 0) {
                    sender.sendMessage("§e  " + lk.key().toUpperCase() + ": §7Scheduler stopped or no data");
                } else {
                    long remaining = nextMs - System.currentTimeMillis();
                    sender.sendMessage("§e  " + lk.key().toUpperCase() + ": §7Next in §f"
                            + formatDuration(Math.max(0, remaining)));
                }
            }
        }

        return true;
    }

    // ── /envoyedit <tier> ─────────────────────────────────────────────────────

    private boolean handleEnvoyEdit(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§cUsage: /envoyedit <tier>");
            player.sendMessage("§7Tiers: " + tierNames());
            return true;
        }

        EnvoyTier tier = EnvoyTier.fromString(args[0]);
        if (tier == null) {
            player.sendMessage("§cUnknown tier: §f" + args[0]);
            player.sendMessage("§7Valid tiers: " + tierNames());
            return true;
        }

        openEditor(player, tier);
        return true;
    }

    private void openEditor(Player player, EnvoyTier tier) {
        List<LootEntry> entries = lootManager.getEntries(tier);
        int size = Math.max(9, ((entries.size() + 8) / 9) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, "§6EnvoyEdit: " + tier.key());

        for (int i = 0; i < Math.min(entries.size(), 54); i++) {
            LootEntry entry = entries.get(i);
            List<ItemStack> items = entry.inventoryItems();
            if (items.isEmpty()) continue;
            ItemStack display = items.get(0).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7");
                lore.add("§eChance: §f" + entry.chance() + "%");
                lore.add("§cRight-click to remove");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        openEditors.put(player.getUniqueId(), tier);
        player.openInventory(inv);
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith("§6EnvoyEdit: ")) return;

        event.setCancelled(true);

        EnvoyTier tier = openEditors.get(player.getUniqueId());
        if (tier == null) return;

        if (event.getClick().isRightClick()) {
            int slot = event.getSlot();
            if (slot < 0) return;
            boolean removed = lootManager.removeEntry(tier, slot);
            if (removed) {
                player.sendMessage("§aRemoved loot entry #" + slot + " from §f" + tier.key() + "§a.");
                // Refresh the GUI
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, tier));
            }
        }
    }

    // ── /envoyadd <tier> <chance> ─────────────────────────────────────────────

    private boolean handleEnvoyAdd(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /envoyadd <tier> <chancePercent>");
            player.sendMessage("§7Hold the item you want to add in your main hand.");
            return true;
        }

        EnvoyTier tier = EnvoyTier.fromString(args[0]);
        if (tier == null) {
            player.sendMessage("§cUnknown tier: §f" + args[0]);
            player.sendMessage("§7Valid tiers: " + tierNames());
            return true;
        }

        double chance;
        try { chance = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) {
            player.sendMessage("§cChance must be a number (e.g. 5.0).");
            return true;
        }

        if (chance <= 0) {
            player.sendMessage("§cChance must be greater than 0.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage("§cHold the item you want to add in your main hand.");
            return true;
        }

        lootManager.addEntry(tier, hand.clone(), chance);
        player.sendMessage("§aAdded §f" + hand.getType().name().toLowerCase() + " §ato the §f"
                + tier.key() + " §aloot table with §f" + chance + "% §achance.");
        return true;
    }

    // ── TabCompleter ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasPermission(sender)) return List.of();

        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("envoy")) {
            if (args.length == 1) return filter(List.of("start", "stopall", "setspawn", "setdesert",
                    "setplains", "setnether", "info", "reload", "cleanup"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("start"))
                return filter(List.of("default", "heroic"), args[1]);
            return List.of();
        }

        if (cmd.equals("envoyedit") || cmd.equals("envoyadd")) {
            if (args.length == 1) return filter(tierKeyList(), args[0]);
            if (cmd.equals("envoyadd") && args.length == 2)
                return filter(List.of("5", "10", "15", "20", "30", "50"), args[1]);
            return List.of();
        }

        return List.of();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("simpleenvoy.manage")
                || sender.hasPermission("group.owner")
                || sender.hasPermission("group.admin")
                || sender.hasPermission("group.dev")
                || sender.isOp();
    }

    private List<String> tierKeyList() {
        return Arrays.stream(EnvoyTier.values()).map(EnvoyTier::key).toList();
    }

    private String tierNames() {
        return String.join(", ", tierKeyList());
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private String formatDuration(long millis) {
        long h = TimeUnit.MILLISECONDS.toHours(millis);
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (h > 0) return String.format("%dh %dm %ds", h, m, s);
        if (m > 0) return String.format("%dm %ds", m, s);
        return String.format("%ds", s);
    }
}
