package com.ftl2nd;

/**
 * Enumerates all customizable Fallback Inventory GUI title, item name, and lore keys in messages.yml.
 */
public enum GuiTextKey {

    TITLE("title", "Homes"),
    MANAGE_TITLE("manage-title", "%name%"),
    ICON_PICKER_TITLE("icon-picker-title", "Home Icons %page%/%max_page%"),

    HOME_NAME("home-name", "<white>%name%"),
    HOME_LORE("home-lore", "<gray><i>Click to view"),

    NEW_HOME_NAME("new-home-name", "<dark_gray>New Home"),
    NEW_HOME_LORE("new-home-lore", "<gray><i>Click to set home"),

    LOCKED_NAME("locked-name", "<red>Locked"),
    LOCKED_LORE("locked-lore", "<white>Buy <aqua>Rank<white> for more home slots"),

    CENTER_BED_NAME("center-bed-name", "<white>Homes"),
    CENTER_BED_LORE("center-bed-lore", "<gray><i>Save teleport locations"),

    NEXT_PAGE_NAME("next-page-name", "<white>Next Page"),
    NEXT_PAGE_LORE("next-page-lore", "<gray><i>Click to go to page %page%"),

    PREV_PAGE_NAME("prev-page-name", "<white>Previous Page"),
    PREV_PAGE_LORE("prev-page-lore", "<gray><i>Click to go to page %page%"),

    BACK_NAME("back-name", "<red>Back"),
    BACK_LORE("back-lore", "<gray><i>Click to go back"),

    TELEPORT_NAME("teleport-name", "<white>Teleport"),
    TELEPORT_LORE("teleport-lore", "<gray><i>Click to teleport"),

    RENAME_NAME("rename-name", "<white>Rename"),
    RENAME_LORE("rename-lore", "<gray><i>Click to rename"),

    ICON_NAME("icon-name", "<white>Icon"),
    ICON_LORE("icon-lore", "<gray><i>Click to select home icon"),

    DELETE_NAME("delete-name", "<white>Delete"),
    DELETE_LORE("delete-lore", "<gray><i>Click to delete"),

    CURRENT_ICON_NAME("current-icon-name", "<white>Current Icon"),
    CURRENT_ICON_LORE("current-icon-lore", "<gray><i>%icon_name%\n<gray><i>%home_name%"),

    DEFAULT_ICON_NAME("default-icon-name", "<white>Default"),
    DEFAULT_ICON_LORE("default-icon-lore", "<gray><i>Use the default bed icon."),

    SEARCH_NAME("search-name", "<white>Search"),
    SEARCH_LORE("search-lore", "<gray><i>Filter items"),

    ICON_ENTRY_LORE("icon-entry-lore", "");

    private final String configKey;
    private final String defaultTemplate;

    GuiTextKey(String configKey, String defaultTemplate) {
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
