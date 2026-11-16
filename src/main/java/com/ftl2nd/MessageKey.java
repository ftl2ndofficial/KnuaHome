package com.ftl2nd;

import org.jspecify.annotations.Nullable;

/**
 * Enumerates all feedback message keys in messages.yml along with their default template and default sound effect.
 */
public enum MessageKey {

    HOME_SET("home-set", "<gray>Home %slot% set", SoundKey.HOME_SET),
    HOME_DELETED("home-deleted", "<gray>Home deleted", SoundKey.HOME_DELETED),
    TELEPORTED("teleported", "<gray>Teleported to Home %slot%", SoundKey.TELEPORT),

    LIMIT_REACHED("limit-reached", "<red>You reached home limits", SoundKey.FAIL),
    SLOT_LOCKED("slot-locked", "<red>You reached home limits", SoundKey.FAIL),
    HOME_NOT_SET("home-not-set", "<red>Home %slot% is not set", SoundKey.FAIL),
    ALREADY_SET("already-set", "<red>Home %slot% is already set", SoundKey.FAIL),
    INVALID_SLOT("invalid-slot", "<red>Invalid home slot (1-%max%)", SoundKey.FAIL),
    WORLD_NOT_LOADED("world-not-loaded", "<red>The world for Home %slot% is not loaded", SoundKey.FAIL),
    TELEPORT_FAILED("teleport-failed", "<red>Failed to teleport to Home %slot%", SoundKey.FAIL),

    RENAME_PROMPT("rename-prompt", "<gray>Type the new home name in chat", null),
    RENAME_CANCELLED("rename-cancelled", "<red>Home rename cancelled", SoundKey.FAIL),
    HOME_RENAMED("home-renamed", "<gray>Home %slot% renamed to <white>%name%", SoundKey.HOME_SET),
    ICON_UPDATED("icon-updated", "<gray>Home %slot% icon updated", SoundKey.HOME_SET),

    PLAYERS_ONLY("players-only", "<red>Only players can execute this command.", SoundKey.FAIL),
    NO_PERMISSION("no-permission", "<red>You do not have permission to execute this command.", SoundKey.FAIL),
    RELOADED("reloaded", "<gray>KnuaHome configuration and messages reloaded (max-homes=%max%, ranks=%ranks%).", null),
    ADMIN_USAGE("admin-usage", "<red>Usage: /knuahome reload", SoundKey.FAIL);

    private final String configKey;
    private final String defaultTemplate;
    private final @Nullable SoundKey defaultSound;

    MessageKey(String configKey, String defaultTemplate, @Nullable SoundKey defaultSound) {
        this.configKey = configKey;
        this.defaultTemplate = defaultTemplate;
        this.defaultSound = defaultSound;
    }

    public String configKey() {
        return configKey;
    }

    public String defaultTemplate() {
        return defaultTemplate;
    }

    public @Nullable SoundKey defaultSound() {
        return defaultSound;
    }
}
