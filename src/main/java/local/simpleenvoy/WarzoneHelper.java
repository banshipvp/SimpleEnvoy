package local.simpleenvoy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Soft-dependency bridge to SimpleFactions' WarzoneManager.
 *
 * <p>If SimpleFactions is present and its WarzoneManager is accessible via reflection,
 * we delegate player-count queries to it so that the envoy respects actual warzone claims.
 *
 * <p>Fallback: count online players within the configured detection radius (100 blocks)
 * of the center location when SimpleFactions is unavailable.
 */
public class WarzoneHelper {

    private static Method countMethod  = null;
    private static Object warzoneManager = null;
    private static boolean initialized  = false;

    private WarzoneHelper() {}

    /** Call once on plugin enable to wire up SimpleFactions if available. */
    public static void init(Logger logger) {
        if (initialized) return;
        initialized = true;

        Plugin sf = Bukkit.getPluginManager().getPlugin("SimpleFactions");
        if (sf == null) {
            logger.info("[SimpleEnvoy] SimpleFactions not found — using proximity check for warzone detection.");
            return;
        }

        try {
            Method getWZ = sf.getClass().getMethod("getWarzoneManager");
            warzoneManager = getWZ.invoke(sf);
            countMethod = warzoneManager.getClass()
                    .getMethod("countOnlinePlayersInSameWarzoneClaim", Location.class);
            logger.info("[SimpleEnvoy] Linked to SimpleFactions WarzoneManager successfully.");
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "[SimpleEnvoy] Could not link to SimpleFactions WarzoneManager — using proximity check.", e);
        }
    }

    /**
     * Count online players present in the warzone claim that contains {@code center}.
     * Falls back to checking within {@code fallbackRadius} blocks if SimpleFactions is unavailable.
     *
     * @param center          The anchor location (envoy center).
     * @param fallbackRadius  Radius used when SimpleFactions is not wired.
     * @return Player count ≥ 0.
     */
    public static int countPlayersInWarzone(Location center, int fallbackRadius) {
        if (center == null || center.getWorld() == null) return 0;

        if (countMethod != null && warzoneManager != null) {
            try {
                Object result = countMethod.invoke(warzoneManager, center);
                if (result instanceof Number n) return n.intValue();
            } catch (Exception e) {
                // fall through to proximity
            }
        }

        // Proximity fallback
        int count = 0;
        double radiusSq = (double) fallbackRadius * fallbackRadius;
        String worldName = center.getWorld().getName();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getWorld() == null) continue;
            if (!player.getLocation().getWorld().getName().equals(worldName)) continue;
            if (player.getLocation().distanceSquared(center) <= radiusSq) count++;
        }
        return count;
    }
}
