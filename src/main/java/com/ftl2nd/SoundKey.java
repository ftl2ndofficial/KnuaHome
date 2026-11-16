package com.ftl2nd;

/**
 * Enumerates all configurable sound triggers in messages.yml with their default settings.
 */
public enum SoundKey {

    FAIL("fail", true, "ENTITY_VILLAGER_NO", 1.0f, 1.0f),
    TELEPORT("teleport", false, "ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f),
    HOME_SET("home-set", false, "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f),
    HOME_DELETED("home-deleted", false, "UI_BUTTON_CLICK", 1.0f, 1.0f);

    private final String configKey;
    private final boolean defaultEnabled;
    private final String defaultSound;
    private final float defaultVolume;
    private final float defaultPitch;

    SoundKey(
        String configKey,
        boolean defaultEnabled,
        String defaultSound,
        float defaultVolume,
        float defaultPitch
    ) {
        this.configKey = configKey;
        this.defaultEnabled = defaultEnabled;
        this.defaultSound = defaultSound;
        this.defaultVolume = defaultVolume;
        this.defaultPitch = defaultPitch;
    }

    public String configKey() {
        return configKey;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public String defaultSound() {
        return defaultSound;
    }

    public float defaultVolume() {
        return defaultVolume;
    }

    public float defaultPitch() {
        return defaultPitch;
    }
}
