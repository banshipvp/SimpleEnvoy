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
        if (!sender.hasPermission("simpleenvoy.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/envoy start §7- spawn crates now");
            sender.sendMessage("§e/envoy setspawn §7- set random center to your location");
            sender.sendMessage("§e/envoy clear §7- remove active crates");
            sender.sendMessage("§e/envoy reload §7- reload envoys/default.yml");
            sender.sendMessage("§e/envoy status §7- show current settings summary");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
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
            case "status" -> {
                Location center = envoyManager.getSpawnCenter();
                sender.sendMessage("§6SimpleEnvoy Status");
                sender.sendMessage("§7Active crates: §f" + envoyManager.getActiveCrateCount());
                sender.sendMessage("§7Configured amount (sample): §f" + envoyManager.getConfiguredAmountSample());
                sender.sendMessage("§7Schedule (every): §f" + envoyManager.getEveryRaw());
                sender.sendMessage("§7Center: §f" + (center == null ? "not set" : format(center)));
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown subcommand.");
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

    private String format(Location location) {
        return location.getWorld().getName() + ";"
                + location.getBlockX() + ";"
                + location.getBlockY() + ";"
                + location.getBlockZ();
    }
}
