package com.ftl2nd.nms;

import com.viaversion.viaversion.api.Via;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages version detection and instantiates the matching NMS Core handler (1.21 - 26.3).
 */
public final class NmsManager {

    private final NmsCore nmsCore;
    private final boolean viaVersionAvailable;

    public NmsManager(Logger logger) {
        String mcVersion;
        try {
            mcVersion = Bukkit.getMinecraftVersion();
        } catch (Throwable ignored) {
            mcVersion = null;
        }
        NmsVersion detected = NmsVersion.fromMinecraftVersion(mcVersion);
        this.nmsCore = instantiateCore(detected, logger);
        this.viaVersionAvailable = detectViaVersion();
    }

    public NmsManager(NmsVersion targetVersion, Logger logger) {
        this.nmsCore = instantiateCore(targetVersion, logger);
        this.viaVersionAvailable = false;
    }

    private static NmsCore instantiateCore(NmsVersion version, Logger logger) {
        try {
            Class<?> clazz = Class.forName(version.getHandlerClassName());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return (NmsCore) instance;
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Failed to load NMS handler " + version.getHandlerClassName() + ": " + t.getMessage());
            }
            throw new IllegalStateException("Could not initialize NMS Core for " + version.getVersionString(), t);
        }
    }

    private static boolean detectViaVersion() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ViaVersion");
            if (plugin == null || !plugin.isEnabled()) {
                return false;
            }
            Class.forName("com.viaversion.viaversion.api.Via");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public NmsCore getCore() {
        return nmsCore;
    }

    public int getClientProtocolVersion(Player player) {
        if (viaVersionAvailable && player != null) {
            int viaProto = ViaVersionBridge.getProtocol(player.getUniqueId());
            if (viaProto > 0) {
                return viaProto;
            }
        }
        return nmsCore.getServerProtocolVersion();
    }

    public boolean canUseDialog(Player player) {
        return nmsCore.supportsDialog()
            && nmsCore.getVersion().canClientUseDialog(getClientProtocolVersion(player));
    }

    public boolean canUseBedSprite(Player player) {
        return nmsCore.supportsBedSprite()
            && nmsCore.getVersion().canClientUseBedSprite(getClientProtocolVersion(player));
    }

    private static final class ViaVersionBridge {
        static int getProtocol(UUID uuid) {
            try {
                return Via.getAPI().getPlayerVersion(uuid);
            } catch (Throwable ignored) {
                return -1;
            }
        }
    }
}
