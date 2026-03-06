package local.simpleenvoy;

import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Periodic task that fires fireworks from each active {@link EnvoyChest}.
 * Runs every 200 ticks (10 seconds) while an envoy is active.
 */
public class FireworkTask extends BukkitRunnable {

    private static final Random RANDOM = new Random();
    private static final int    DETONATION_DELAY_TICKS = 2; // detonate firework early so it doesn't fly away

    private final JavaPlugin plugin;
    private final List<EnvoyChest> chests;

    public FireworkTask(JavaPlugin plugin, List<EnvoyChest> chests) {
        this.plugin = plugin;
        this.chests = new ArrayList<>(chests);
    }

    @Override
    public void run() {
        // Remove broken chests from tracking
        chests.removeIf(EnvoyChest::isBroken);

        if (chests.isEmpty()) {
            cancel();
            return;
        }

        // Fire a firework from a random subset (~25%) to avoid server lag
        for (EnvoyChest chest : chests) {
            if (chest.isBroken()) continue;
            if (RANDOM.nextInt(4) != 0 && chests.size() > 4) continue; // throttle for big events

            shootFirework(chest);
        }
    }

    private void shootFirework(EnvoyChest chest) {
        Location loc = chest.blockLocation();
        if (loc.getWorld() == null) return;

        // Offset to center of block
        Location fireworkLoc = loc.clone().add(0.5, 1.0, 0.5);

        Firework fw = loc.getWorld().spawn(fireworkLoc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(chest.tier().buildFireworkEffect());
        meta.setPower(0);
        fw.setFireworkMeta(meta);

        // Detonate after a short delay so it explodes near the chest
        plugin.getServer().getScheduler().runTaskLater(plugin, fw::detonate, DETONATION_DELAY_TICKS);
    }
}
