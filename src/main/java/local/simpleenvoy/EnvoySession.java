package local.simpleenvoy;

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single running envoy event for one warp location
 * (spawn, desert, plains, or nether).
 */
public class EnvoySession {

    public enum LocationKey {
        SPAWN("spawn"),
        DESERT("desert"),
        PLAINS("plains"),
        NETHER("nether");

        private final String key;

        LocationKey(String key) { this.key = key; }
        public String key() { return key; }

        public static LocationKey fromString(String s) {
            if (s == null) return null;
            for (LocationKey lk : values()) {
                if (lk.key.equalsIgnoreCase(s)) return lk;
            }
            return null;
        }

        /** Whether this location runs on the heroic schedule (90 min). */
        public boolean isHeroic() { return this == NETHER; }
    }

    private final LocationKey locationKey;
    private final List<EnvoyChest> chests;
    private BukkitTask fireworkTask;
    private final long startTimeMs;

    public EnvoySession(LocationKey locationKey) {
        this.locationKey = locationKey;
        this.chests      = new ArrayList<>();
        this.startTimeMs = System.currentTimeMillis();
    }

    public LocationKey locationKey()      { return locationKey; }
    public long startTimeMs()             { return startTimeMs; }
    public List<EnvoyChest> chests()      { return Collections.unmodifiableList(chests); }
    public void addChest(EnvoyChest chest){ chests.add(chest); }

    public void setFireworkTask(BukkitTask task) {
        this.fireworkTask = task;
    }

    public int remainingChests() {
        return (int) chests.stream().filter(c -> !c.isBroken()).count();
    }

    public boolean isFinished() {
        return chests.stream().allMatch(EnvoyChest::isBroken);
    }

    /** Stop all firework tasks and break remaining chests. */
    public void stopAll() {
        if (fireworkTask != null) {
            fireworkTask.cancel();
            fireworkTask = null;
        }
        for (EnvoyChest chest : chests) {
            if (!chest.isBroken()) chest.breakChest();
        }
    }
}
