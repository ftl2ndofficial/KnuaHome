package com.ftl2nd;

/**
 * Enumerates all customizable Dialog UI text and tooltip keys in messages.yml.
 */
public enum DialogTextKey {

    TITLE("title", "<white>Homes"),
    HOME_BUTTON_LABEL("home-button-label", "<white>%name%"),
    HOME_BUTTON_TOOLTIP("home-button-tooltip", "<white>%name%\n<gray>Click to teleport"),
    NEW_HOME_BUTTON_LABEL("new-home-button-label", "<dark_gray>New Home"),
    NEW_HOME_BUTTON_TOOLTIP("new-home-button-tooltip", "<white>New Home\n<gray>Click to set home"),
    LOCKED_BUTTON_LABEL("locked-button-label", "<red>Locked"),
    LOCKED_BUTTON_TOOLTIP("locked-button-tooltip", "<white>Buy <aqua>Rank<white> for more home slots"),
    SHOW_MORE_BUTTON_LABEL("show-more-button-label", "<white>Show More");

    private final String configKey;
    private final String defaultTemplate;

    DialogTextKey(String configKey, String defaultTemplate) {
        this.configKey = configKey;
        this.defaultTemplate = defaultTemplate;
    }

    public String configKey() {
        return configKey;
    }

    public String defaultTemplate() {
        return defaultTemplate;
    }
}
