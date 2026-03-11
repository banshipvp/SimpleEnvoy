package local.simpleenvoy.command;

import local.simpleenvoy.SimpleEnvoyPlugin;
import local.simpleenvoy.manager.EnvoyManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EnvoyCommand implements CommandExecutor, TabCompleter {

    private final SimpleEnvoyPlugin plugin;
    private final EnvoyManager envoyManager;

    public EnvoyCommand(SimpleEnvoyPlugin plugin, EnvoyManager envoyManager) {
        this.plugin = plugin;
        this.envoyManager = envoyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        // /envoy status is public — any player can check the countdown.
        if (sub.equals("status") || sub.isEmpty()) {
            boolean adminView = sender.hasPermission("simpleenvoy.admin") || sender.isOp();

            long secs = envoyManager.getSecondsUntilNextSpawn();
            String countdown = secs < 0 ? "§7Unknown (scheduler not active)" : "§a" + formatCountdown(secs);

            sender.sendMessage("§6§l⚔ SimpleEnvoy ⚔");
            sender.sendMessage("§7Active crates: §f" + envoyManager.getActiveCrateCount());
            sender.sendMessage("§7Next envoy: " + countdown);

            if (adminView && !sub.isEmpty()) {
                Location center = envoyManager.getSpawnCenter();
                sender.sendMessage("§7Center: §f" + (center == null ? "not set" : format(center)));
                sender.sendMessage("§7Schedule: §f" + envoyManager.getEveryRaw());
                sender.sendMessage("§7Amount (sample): §f" + envoyManager.getConfiguredAmountSample());
            }
            return true;
        }

        if (!sender.hasPermission("simpleenvoy.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }

        switch (sub) {
            case "start" -> {
                int spawned = envoyManager.spawnEvent();
                if (spawned <= 0) {
                    sender.sendMessage("§cNo crates spawned. Check min-players, center, and spawn constraints.");
                } else {
                    sender.sendMessage("§aSpawned §f" + spawned + " §aenvoy crates.");
                }
                return true;
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can use /envoy setspawn.");
                    return true;
                }

                if (envoyManager.setSpawnCenter(player.getLocation())) {
                    sender.sendMessage("§aSet random-spawn.center to §f" + format(player.getLocation()));
                } else {
                    sender.sendMessage("§cFailed to set spawn center.");
                }
                return true;
            }
            case "clear" -> {
                int removed = envoyManager.clearActiveCrates();
                sender.sendMessage("§aCleared §f" + removed + " §aactive envoy crates.");
                return true;
            }
            case "reload" -> {
                envoyManager.reload();
                sender.sendMessage("§aReloaded §fenvoys/default.yml§a.");
                return true;
            }
            default -> {
                sender.sendMessage("§e/envoy §7- show next-envoy countdown");
                sender.sendMessage("§e/envoy start §7- spawn crates now");
                sender.sendMessage("§e/envoy setspawn §7- set random center to your location");
                sender.sendMessage("§e/envoy clear §7- remove active crates");
                sender.sendMessage("§e/envoy reload §7- reload envoys/default.yml");
                sender.sendMessage("§e/envoy status §7- show detailed settings");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = List.of("start", "setspawn", "clear", "reload", "status");
            List<String> out = new ArrayList<>();
            for (String opt : options) {
                if (opt.startsWith(prefix)) {
                    out.add(opt);
                }
            }
            return out;
        }
        return List.of();
    }

    private String formatCountdown(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, mins, secs);
        }
        if (mins > 0) {
            return String.format("%dm %02ds", mins, secs);
        }
        return String.format("%ds", secs);
    }

    private String format(Location location) {
        return location.getWorld().getName() + ";"
                + location.getBlockX() + ";"
                + location.getBlockY() + ";"
                + location.getBlockZ();
    }
}
