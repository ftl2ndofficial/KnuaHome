package com.ftl2nd;

import com.ftl2nd.nms.NmsManager;
import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for KnuaHome (Paper & Folia 1.21 - 26.3).
 */
public final class KnuaHomePlugin extends JavaPlugin {

    private NmsManager nmsManager;
    private MessageUtil messageUtil;
    private HomeDatabase database;
    private RankLimitResolver rankResolver;
    private HomeGuiManager guiManager;
    private HomeDialogManager dialogManager;
    private Object placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("messages.yml");

        this.nmsManager = new NmsManager(getLogger());
        this.messageUtil = new MessageUtil(this, nmsManager.getCore());
        this.rankResolver = new RankLimitResolver(getConfig());

        File dbFile = new File(getDataFolder(), "homes.db");
        this.database = new HomeDatabase(dbFile, getLogger());
        try {
            this.database.initialize();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize SQLite database homes.db! Disabling KnuaHome.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.guiManager = new HomeGuiManager(this, nmsManager, database, rankResolver, messageUtil);
        this.dialogManager = new HomeDialogManager(this, nmsManager, database, rankResolver, messageUtil, guiManager);
        this.guiManager.setDialogManager(dialogManager);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(dialogManager, this);

        HomeCommand homeCommand = new HomeCommand(this, database, rankResolver, dialogManager, messageUtil);
        for (HomeCommand.CommandType cmdType : HomeCommand.CommandType.values()) {
            registerCommand(cmdType.commandName(), homeCommand);
        }

        if (isPluginEnabled("PlaceholderAPI")) {
            this.placeholderExpansion = PlaceholderBridge.register(this, database, rankResolver);
        }

        getLogger().info(
            "KnuaHome enabled! NMS=" + nmsManager.getCore().getVersion().getVersionString()
                + ", Dialog=" + nmsManager.getCore().supportsDialog()
                + ", BedSprite=" + nmsManager.getCore().supportsBedSprite()
                + ", MaxHomes=" + rankResolver.getMaxHomesCap()
                + ", Ranks=" + rankResolver.getRanks().keySet()
        );
    }

    public void reloadKnuaHomeConfig() {
        reloadConfig();
        if (this.rankResolver != null) {
            this.rankResolver.reload(getConfig());
        }
        if (this.messageUtil != null) {
            this.messageUtil.reload();
        }
        if (this.guiManager != null) {
            this.guiManager.reloadStaticItems();
        }
        if (this.dialogManager != null) {
            this.dialogManager.reloadStaticSlots();
        }
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            PlaceholderBridge.unregister(placeholderExpansion);
            placeholderExpansion = null;
        }
        if (database != null) {
            database.close();
        }
    }

    private void saveResourceIfAbsent(String resourceName) {
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) {
            saveResource(resourceName, false);
        }
    }

    private void registerCommand(String name, HomeCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private boolean isPluginEnabled(String name) {
        Plugin p = getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    public NmsManager getNmsManager() {
        return nmsManager;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }

    public HomeDatabase getDatabase() {
        return database;
    }

    public RankLimitResolver getRankResolver() {
        return rankResolver;
    }

    public HomeGuiManager getGuiManager() {
        return guiManager;
    }

    public HomeDialogManager getDialogManager() {
        return dialogManager;
    }

    private static final class PlaceholderBridge {
        static Object register(KnuaHomePlugin plugin, HomeDatabase db, RankLimitResolver resolver) {
            try {
                KnuaHomePlaceholderExpansion exp = new KnuaHomePlaceholderExpansion(plugin, db, resolver);
                exp.register();
                return exp;
            } catch (Throwable ignored) {
                return null;
            }
        }

        static void unregister(Object expansion) {
            try {
                if (expansion instanceof KnuaHomePlaceholderExpansion exp) {
                    exp.unregister();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
