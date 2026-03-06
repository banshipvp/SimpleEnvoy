package local.simpleenvoy;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * SimpleEnvoy — A custom envoy chest plugin for TechnoClash Factions.
 *
 * <p>Features:
 * <ul>
 *   <li>6 chest tiers: SIMPLE, UNIQUE, ELITE, ULTIMATE, LEGENDARY, GODLY</li>
 *   <li>4 locations: spawn (30 min), desert (30 min), plains (30 min), nether/heroic (90 min)</li>
 *   <li>Player-scaled chest count: 7–15 for 1 player, +3 per extra player (capped at 10)</li>
 *   <li>Virtual chest inventory with per-item claim and command execution</li>
 *   <li>Colour-coded hologram name tags and periodic fireworks per chest</li>
 *   <li>Commands: /envoy, /envoys, /envoyedit, /envoyadd</li>
 *   <li>Soft-depends on SimpleFactions for warzone zone-based player checks</li>
 * </ul>
 */
public class SimpleEnvoyPlugin extends JavaPlugin {

    private static SimpleEnvoyPlugin instance;

    private LootManager  lootManager;
    private EnvoyManager envoyManager;
    private EnvoyCommand envoyCommand;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config (currently unused beyond logging, loot files are in loot/)
        saveDefaultConfig();

        // Wire up WarzoneHelper soft-dependency to SimpleFactions
        WarzoneHelper.init(getLogger());

        // Initialise managers
        lootManager  = new LootManager(this);
        lootManager.load();

        envoyManager = new EnvoyManager(this, lootManager);
        envoyManager.load();
        envoyManager.startSchedulers();

        // Register commands
        envoyCommand = new EnvoyCommand(this, envoyManager, lootManager);
        registerCommand("envoy",     envoyCommand);
        registerCommand("envoys",    envoyCommand);
        registerCommand("envoyedit", envoyCommand);
        registerCommand("envoyadd",  envoyCommand);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChestListener(this, envoyManager), this);
        getServer().getPluginManager().registerEvents(envoyCommand, this); // editor GUI listener

        getLogger().info("SimpleEnvoy enabled. 6 tiers, 4 locations, ready.");
    }

    @Override
    public void onDisable() {
        if (envoyManager != null) envoyManager.disable();
        getLogger().info("SimpleEnvoy disabled.");
    }

    // ── Static accessor ───────────────────────────────────────────────────────

    public static SimpleEnvoyPlugin getInstance() { return instance; }
    public LootManager  getLootManager()           { return lootManager;  }
    public EnvoyManager getEnvoyManager()          { return envoyManager; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerCommand(String name, EnvoyCommand handler) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml!");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }
}
