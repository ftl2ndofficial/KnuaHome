package com.ftl2nd.nms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enumerates every supported Minecraft server version from 1.21 through 26.3
 * and maps each version to its dedicated NMS Core implementation class and protocol capabilities.
 */
public enum NmsVersion {

    V1_21("1.21", 767, "com.ftl2nd.nms.v1_21.NmsV1_21", false, false),
    V1_21_1("1.21.1", 767, "com.ftl2nd.nms.v1_21.NmsV1_21_1", false, false),
    V1_21_2("1.21.2", 768, "com.ftl2nd.nms.v1_21.NmsV1_21_2", false, false),
    V1_21_3("1.21.3", 768, "com.ftl2nd.nms.v1_21.NmsV1_21_3", false, false),
    V1_21_4("1.21.4", 769, "com.ftl2nd.nms.v1_21.NmsV1_21_4", false, false),
    V1_21_5("1.21.5", 770, "com.ftl2nd.nms.v1_21.NmsV1_21_5", false, false),

    V1_21_6("1.21.6", 771, "com.ftl2nd.nms.v1_21_6.NmsV1_21_6", true, false),
    V1_21_7("1.21.7", 772, "com.ftl2nd.nms.v1_21_6.NmsV1_21_7", true, false),
    V1_21_8("1.21.8", 772, "com.ftl2nd.nms.v1_21_6.NmsV1_21_8", true, false),

    V1_21_9("1.21.9", 773, "com.ftl2nd.nms.v1_21_9.NmsV1_21_9", true, true),
    V1_21_10("1.21.10", 773, "com.ftl2nd.nms.v1_21_9.NmsV1_21_10", true, true),

    V1_21_11("1.21.11", 774, "com.ftl2nd.nms.v1_21_11.NmsV1_21_11", true, true),

    V26_1("26.1", 775, "com.ftl2nd.nms.v26_2.NmsV26_1", true, true),
    V26_1_1("26.1.1", 775, "com.ftl2nd.nms.v26_2.NmsV26_1_1", true, true),
    V26_1_2("26.1.2", 775, "com.ftl2nd.nms.v26_2.NmsV26_1_2", true, true),
    V26_2("26.2", 775, "com.ftl2nd.nms.v26_2.NmsV26_2", true, true),
    V26_3("26.3", 775, "com.ftl2nd.nms.v26_3.NmsV26_3", true, true);

    public static final int MIN_DIALOG_PROTOCOL = 771;     // 1.21.6+
    public static final int MIN_BED_SPRITE_PROTOCOL = 773; // 1.21.9+

    private static final Map<String, NmsVersion> BY_VERSION_STRING;

    static {
        Map<String, NmsVersion> map = new HashMap<>(values().length * 2);
        for (NmsVersion v : values()) {
            map.put(v.versionString, v);
        }
        BY_VERSION_STRING = Collections.unmodifiableMap(map);
    }

    private final String versionString;
    private final int protocolVersion;
    private final String handlerClassName;
    private final boolean dialogSupported;
    private final boolean bedSpriteSupported;

    NmsVersion(
        String versionString,
        int protocolVersion,
        String handlerClassName,
        boolean dialogSupported,
        boolean bedSpriteSupported
    ) {
        this.versionString = versionString;
        this.protocolVersion = protocolVersion;
        this.handlerClassName = handlerClassName;
        this.dialogSupported = dialogSupported;
        this.bedSpriteSupported = bedSpriteSupported;
    }

    public String getVersionString() {
        return versionString;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getHandlerClassName() {
        return handlerClassName;
    }

    public boolean isDialogSupported() {
        return dialogSupported;
    }

    public boolean isBedSpriteSupported() {
        return bedSpriteSupported;
    }

    public boolean canClientUseDialog(int clientProtocol) {
        return dialogSupported && clientProtocol >= MIN_DIALOG_PROTOCOL;
    }

    public boolean canClientUseBedSprite(int clientProtocol) {
        return bedSpriteSupported && clientProtocol >= MIN_BED_SPRITE_PROTOCOL;
    }

    public static NmsVersion fromMinecraftVersion(String mcVersion) {
        if (mcVersion != null && !mcVersion.isBlank()) {
            String normalized = mcVersion.trim().toLowerCase(Locale.ROOT);
            int dashIdx = normalized.indexOf('-');
            if (dashIdx > 0) {
                normalized = normalized.substring(0, dashIdx);
            }
            NmsVersion matched = BY_VERSION_STRING.get(normalized);
            if (matched != null) {
                return matched;
            }
        }
        return detectByNmsClasses();
    }

    /**
     * Fallback NMS feature probe when the server reports an unknown version string.
     */
    public static NmsVersion detectByNmsClasses() {
        if (classExists("net.minecraft.world.level.block.entity.SignTextSlot")) {
            return V26_3;
        }
        if (classExists("net.minecraft.world.item.ItemStackTemplate")) {
            return V26_2;
        }
        if (classExists("net.minecraft.resources.Identifier")) {
            return V1_21_11;
        }
        if (classExists("net.minecraft.network.chat.contents.objects.PlayerSprite")) {
            return V1_21_9;
        }
        if (classExists("net.minecraft.server.dialog.Dialog")) {
            return V1_21_6;
        }
        return V1_21;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, NmsVersion.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
