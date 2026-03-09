package local.simpleenvoy;

import local.simpleenvoy.command.EnvoyCommand;
import local.simpleenvoy.command.XPBottleCommand;
import local.simpleenvoy.listener.XPBottleListener;
import local.simpleenvoy.manager.CrateRewardManager;
import local.simpleenvoy.manager.EnvoyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SimpleEnvoyPlugin extends JavaPlugin {

    private CrateRewardManager rewardManager;
    private EnvoyManager envoyManager;

    @Override
    public void onEnable() {
        createDefaultFiles();

        this.rewardManager = new CrateRewardManager(this);
        this.envoyManager = new EnvoyManager(this, rewardManager);
        this.envoyManager.reload();
        this.envoyManager.startScheduler();

        EnvoyCommand envoyCommand = new EnvoyCommand(this, envoyManager);
        getCommand("envoy").setExecutor(envoyCommand);
        getCommand("envoy").setTabCompleter(envoyCommand);
        getCommand("xpbottle").setExecutor(new XPBottleCommand(this));

        Bukkit.getPluginManager().registerEvents(envoyManager, this);
        Bukkit.getPluginManager().registerEvents(new XPBottleListener(this), this);

        getLogger().info("SimpleEnvoy enabled.");
    }

    @Override
    public void onDisable() {
        if (envoyManager != null) {
            envoyManager.stopScheduler();
            envoyManager.clearActiveCrates();
        }
        getLogger().info("SimpleEnvoy disabled.");
    }

    public EnvoyManager getEnvoyManager() {
        return envoyManager;
    }

    private void createDefaultFiles() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }

        File envoysFolder = new File(getDataFolder(), "envoys");
        if (!envoysFolder.exists() && !envoysFolder.mkdirs()) {
            getLogger().warning("Could not create envoys folder.");
        }

        File defaultEnvoy = new File(envoysFolder, "default.yml");
        if (!defaultEnvoy.exists()) {
            try (InputStream in = getResource("envoys/default.yml")) {
                if (in == null) {
                    getLogger().warning("Missing bundled envoys/default.yml resource.");
                    return;
                }
                Files.copy(in, defaultEnvoy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                getLogger().severe("Failed to copy envoys/default.yml: " + ex.getMessage());
            }
        }
    }
}
