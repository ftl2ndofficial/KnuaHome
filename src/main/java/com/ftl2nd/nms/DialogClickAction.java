package com.ftl2nd.nms;

import org.jspecify.annotations.Nullable;

/**
 * Type-safe custom click action protocol for Dialog buttons ("tp_<slot>", "set_<slot>", "locked_<slot>").
 * Pre-caches paths for slots 1..128 for zero-allocation Dialog construction and parses without substring allocation.
 */
public enum DialogClickAction {

    TELEPORT("tp_"),
    SET_HOME("set_"),
    LOCKED("locked_");

    private static final int PRECACHE_LIMIT = 128;
    private static final DialogClickAction[] VALUES = values();

    private final String prefix;
    private final String[] cachedPaths;

    DialogClickAction(String prefix) {
        this.prefix = prefix;
        this.cachedPaths = new String[PRECACHE_LIMIT + 1];
        for (int i = 1; i <= PRECACHE_LIMIT; i++) {
            this.cachedPaths[i] = prefix + i;
        }
    }

    public String prefix() {
        return prefix;
    }

    public String actionPath(int slot) {
        if (slot >= 1 && slot <= PRECACHE_LIMIT) {
            return cachedPaths[slot];
        }
        return prefix + slot;
    }

    public static @Nullable ParsedClick parse(@Nullable String rawPath, int maxCap) {
        if (rawPath == null || rawPath.isEmpty() || maxCap < 1) {
            return null;
        }
        for (DialogClickAction action : VALUES) {
            String p = action.prefix;
            if (rawPath.startsWith(p)) {
                int slot = parsePositiveIntBounded(rawPath, p.length(), maxCap);
                return slot > 0 ? new ParsedClick(action, slot) : null;
            }
        }
        return null;
    }

    private static int parsePositiveIntBounded(String str, int startIdx, int maxCap) {
        int len = str.length();
        if (startIdx >= len) {
            return -1;
        }
        long acc = 0L;
        for (int i = startIdx; i < len; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            acc = acc * 10L + (c - '0');
            if (acc > maxCap) {
                return -1;
            }
        }
        return acc >= 1L ? (int) acc : -1;
    }

    public record ParsedClick(DialogClickAction action, int slot) {
    }
}
