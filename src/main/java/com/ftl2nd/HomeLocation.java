package com.ftl2nd;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, validated coordinate and metadata record for a saved player home.
 */
public record HomeLocation(
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    @Nullable String customName,
    String iconMaterial
) {

    public static final String DEFAULT_ICON = "WHITE_BED";
    public static final int MAX_CUSTOM_NAME_LENGTH = 32;

    public HomeLocation {
        Objects.requireNonNull(worldName, "worldName cannot be null");
        worldName = worldName.trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        if (!Double.isFinite(x)) {
            x = 0.0;
        }
        if (!Double.isFinite(y)) {
            y = 64.0;
        }
        if (!Double.isFinite(z)) {
            z = 0.0;
        }
        if (!Float.isFinite(yaw)) {
            yaw = 0.0f;
        }
        if (!Float.isFinite(pitch)) {
            pitch = 0.0f;
        }
        customName = sanitizeCustomName(customName);
        iconMaterial = normalizeIconName(iconMaterial);
    }

    public HomeLocation(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        this(worldName, x, y, z, yaw, pitch, null, DEFAULT_ICON);
    }

    public static HomeLocation fromBukkit(Location loc) {
        Objects.requireNonNull(loc, "location cannot be null");
        World world = loc.getWorld();
        String resolvedWorld = (world != null && !world.getName().isBlank()) ? world.getName() : "world";
        return new HomeLocation(
            resolvedWorld,
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            loc.getYaw(),
            loc.getPitch(),
            null,
            DEFAULT_ICON
        );
    }

    public HomeLocation withCustomName(@Nullable String newName) {
        return new HomeLocation(worldName, x, y, z, yaw, pitch, newName, iconMaterial);
    }

    public HomeLocation withIconMaterial(@Nullable String newIcon) {
        return new HomeLocation(worldName, x, y, z, yaw, pitch, customName, newIcon);
    }

    public String displayNameOrDefault(int slot) {
        return customName != null ? customName : ("Home " + slot);
    }

    public Material resolveIconMaterial() {
        Material mat = Material.matchMaterial(iconMaterial);
        if (mat != null && isUsableItem(mat)) {
            return mat;
        }
        return Material.WHITE_BED;
    }

    public @Nullable Location toBukkitLocation() {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public static @Nullable String sanitizeCustomName(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        // Strip control characters and trim whitespace
        StringBuilder cleaned = null;
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c)) {
                if (cleaned == null) {
                    cleaned = new StringBuilder(len);
                    cleaned.append(raw, 0, i);
                }
            } else if (cleaned != null) {
                cleaned.append(c);
            }
        }
        String trimmed = (cleaned != null ? cleaned.toString() : raw).trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_CUSTOM_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CUSTOM_NAME_LENGTH).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeIconName(@Nullable String rawIcon) {
        if (rawIcon == null || rawIcon.isBlank()) {
            return DEFAULT_ICON;
        }
        String normalized = rawIcon.trim().toUpperCase(Locale.ROOT);
        int colonIdx = normalized.indexOf(':');
        if (colonIdx >= 0 && colonIdx + 1 < normalized.length()) {
            normalized = normalized.substring(colonIdx + 1);
        }
        return normalized.isEmpty() ? DEFAULT_ICON : normalized;
    }

    private static boolean isUsableItem(Material mat) {
        try {
            return mat.isItem() && !mat.isAir();
        } catch (Throwable ignored) {
            String name = mat.name();
            return !name.equals("AIR") && !name.endsWith("_AIR");
        }
    }
}
