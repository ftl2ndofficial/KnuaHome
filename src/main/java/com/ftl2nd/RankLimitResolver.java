package com.ftl2nd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the maximum number of homes allowed for a player based on config.yml.
 * Uses DefaultRank and PermissionRule enums, pre-computed rank permission strings,
 * and zero-allocation bounded integer parsing.
 */
public final class RankLimitResolver {

    public static final int DEFAULT_MAX_HOMES_CAP = 100;
    public static final int DEFAULT_BASE_HOMES = 2;

    /**
     * Built-in default ranks when config.yml has no custom rank entries.
     */
    public enum DefaultRank {
        DEFAULT("default", 2),
        VIP("vip", 5),
        MVP("mvp", 10);

        private final String rankKey;
        private final int defaultLimit;

        DefaultRank(String rankKey, int defaultLimit) {
            this.rankKey = rankKey;
            this.defaultLimit = defaultLimit;
        }

        public String rankKey() {
            return rankKey;
        }

        public int defaultLimit() {
            return defaultLimit;
        }
    }

    /**
     * Supported permission node prefixes evaluated natively on Bukkit/Paper/Folia.
     */
    public enum PermissionRule {
        HOMES_LIMIT("knuahome.homes.", RuleKind.NUMERIC),
        ALT_LIMIT("knuahome.limit.", RuleKind.NUMERIC),
        KNUAHOME_RANK("knuahome.rank.", RuleKind.RANK),
        GROUP_RANK("group.", RuleKind.RANK);

        public enum RuleKind {
            NUMERIC,
            RANK
        }

        private static final PermissionRule[] VALUES = values();

        private final String prefix;
        private final RuleKind kind;

        PermissionRule(String prefix, RuleKind kind) {
            this.prefix = prefix;
            this.kind = kind;
        }

        public String prefix() {
            return prefix;
        }

        public RuleKind kind() {
            return kind;
        }

        static int evaluatePermissionNode(String rawPerm, Map<String, Integer> ranks, int cap) {
            int len = rawPerm.length();
            if (len < 7) { // Shortest valid node is "group.x" (7 chars)
                return -1;
            }
            char c0 = rawPerm.charAt(0);
            if (c0 != 'k' && c0 != 'K' && c0 != 'g' && c0 != 'G') {
                return -1;
            }
            for (PermissionRule rule : VALUES) {
                int prefixLen = rule.prefix.length();
                if (len > prefixLen && rawPerm.regionMatches(true, 0, rule.prefix, 0, prefixLen)) {
                    if (rule.kind == RuleKind.NUMERIC) {
                        return parseBoundedNonNegativeInt(rawPerm, prefixLen, cap);
                    } else {
                        String rankKey = rawPerm.substring(prefixLen).toLowerCase(Locale.ROOT);
                        Integer rankHomes = ranks.get(rankKey);
                        return rankHomes != null ? rankHomes : -1;
                    }
                }
            }
            return -1;
        }
    }

    private volatile ConfigSnapshot snapshot;

    public RankLimitResolver(@Nullable FileConfiguration config) {
        reload(config);
    }

    public void reload(@Nullable FileConfiguration config) {
        if (config == null) {
            this.snapshot = createDefaultSnapshot();
            return;
        }
        int maxHomes = Math.max(1, config.getInt("max-homes", DEFAULT_MAX_HOMES_CAP));
        int defaultHomes = Math.clamp(config.getInt("default-homes", DEFAULT_BASE_HOMES), 0, maxHomes);

        Map<String, Integer> loadedRanks = new LinkedHashMap<>();
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String key : ranksSection.getKeys(false)) {
                String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
                if (normalizedKey.isEmpty()) {
                    continue;
                }
                int homes;
                if (ranksSection.isConfigurationSection(key)) {
                    ConfigurationSection sub = ranksSection.getConfigurationSection(key);
                    homes = sub != null ? sub.getInt("home", defaultHomes) : defaultHomes;
                } else {
                    homes = ranksSection.getInt(key, defaultHomes);
                }
                loadedRanks.put(normalizedKey, Math.clamp(homes, 0, maxHomes));
            }
        }
        if (loadedRanks.isEmpty()) {
            loadedRanks.putAll(createDefaultRanks(maxHomes));
        }

        int resolvedDefault = loadedRanks.getOrDefault(DefaultRank.DEFAULT.rankKey(), defaultHomes);
        this.snapshot = buildSnapshot(maxHomes, resolvedDefault, loadedRanks);
    }

    public int getMaxHomesCap() {
        return snapshot.maxHomesCap();
    }

    public int getDefaultHomes() {
        return snapshot.defaultHomes();
    }

    public Map<String, Integer> getRanks() {
        return snapshot.ranks();
    }

    public int resolveMaxHomes(@Nullable Player player) {
        ConfigSnapshot current = this.snapshot;
        if (player == null) {
            return current.defaultHomes();
        }

        int cap = current.maxHomesCap();
        int bestHomes = current.defaultHomes();

        // 1. Evaluate native Bukkit effective permissions
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String rawPerm = info.getPermission();
            if (rawPerm == null || rawPerm.isEmpty()) {
                continue;
            }
            int evaluated = PermissionRule.evaluatePermissionNode(
                rawPerm,
                current.ranks(),
                cap
            );
            if (evaluated > bestHomes) {
                bestHomes = evaluated;
                if (bestHomes >= cap) {
                    return cap;
                }
            }
        }

        // 2. Evaluate pre-computed rank permission nodes directly (handles wildcard/operator/dynamic attachments)
        for (PrecomputedRank rank : current.precomputedRanks()) {
            if (rank.limit() > bestHomes
                && (player.hasPermission(rank.knuaHomeRankPerm()) || player.hasPermission(rank.groupPerm()))) {
                bestHomes = rank.limit();
                if (bestHomes >= cap) {
                    return cap;
                }
            }
        }

        // 3. Optionally check LuckPerms inherited groups if LuckPerms softdepend is present
        if (isLuckPermsEnabled()) {
            int lpHomes = LuckPermsBridge.resolveFromLuckPerms(player.getUniqueId(), current);
            if (lpHomes > bestHomes) {
                bestHomes = lpHomes;
            }
        }

        return Math.min(cap, bestHomes);
    }

    /**
     * Parses a non-negative integer starting at startIdx without substring allocation,
     * clamping values larger than cap to cap instead of overflowing.
     */
    static int parseBoundedNonNegativeInt(String str, int startIdx, int cap) {
        int len = str.length();
        while (startIdx < len && str.charAt(startIdx) == ' ') {
            startIdx++;
        }
        while (len > startIdx && str.charAt(len - 1) == ' ') {
            len--;
        }
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
            if (acc >= cap) {
                // Verify remaining characters are still digits before clamping
                for (int j = i + 1; j < len; j++) {
                    char rem = str.charAt(j);
                    if (rem < '0' || rem > '9') {
                        return -1;
                    }
                }
                return cap;
            }
        }
        return (int) acc;
    }

    private static boolean isLuckPermsEnabled() {
        try {
            Plugin lp = Bukkit.getPluginManager().getPlugin("LuckPerms");
            return lp != null && lp.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ConfigSnapshot createDefaultSnapshot() {
        Map<String, Integer> ranks = createDefaultRanks(DEFAULT_MAX_HOMES_CAP);
        int def = ranks.getOrDefault(DefaultRank.DEFAULT.rankKey(), DEFAULT_BASE_HOMES);
        return buildSnapshot(DEFAULT_MAX_HOMES_CAP, def, ranks);
    }

    private static ConfigSnapshot buildSnapshot(int maxHomes, int defaultHomes, Map<String, Integer> loadedRanks) {
        List<PrecomputedRank> precomputed = new ArrayList<>(loadedRanks.size());
        for (Map.Entry<String, Integer> entry : loadedRanks.entrySet()) {
            String rankKey = entry.getKey();
            precomputed.add(new PrecomputedRank(
                PermissionRule.KNUAHOME_RANK.prefix() + rankKey,
                PermissionRule.GROUP_RANK.prefix() + rankKey,
                entry.getValue()
            ));
        }
        return new ConfigSnapshot(
            maxHomes,
            defaultHomes,
            Collections.unmodifiableMap(loadedRanks),
            Collections.unmodifiableList(precomputed)
        );
    }

    private static Map<String, Integer> createDefaultRanks(int maxCap) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (DefaultRank rank : DefaultRank.values()) {
            map.put(rank.rankKey(), Math.min(rank.defaultLimit(), maxCap));
        }
        return map;
    }

    private static final class LuckPermsBridge {
        static int resolveFromLuckPerms(UUID uuid, ConfigSnapshot current) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(uuid);
                if (user == null) {
                    return -1;
                }
                QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(user)
                    .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());

                int best = -1;
                for (InheritanceNode node : user.resolveInheritedNodes(NodeType.INHERITANCE, queryOptions)) {
                    String groupName = node.getGroupName();
                    if (groupName != null && !groupName.isEmpty()) {
                        Integer candidate = current.ranks().get(groupName.toLowerCase(Locale.ROOT));
                        if (candidate != null && candidate > best) {
                            best = candidate;
                        }
                    }
                }

                String primaryGroup = user.getPrimaryGroup();
                if (primaryGroup != null && !primaryGroup.isBlank()) {
                    Integer candidate = current.ranks().get(primaryGroup.toLowerCase(Locale.ROOT));
                    if (candidate != null && candidate > best) {
                        best = candidate;
                    }
                }
                return best;
            } catch (Throwable ignored) {
                return -1;
            }
        }
    }

    private record PrecomputedRank(String knuaHomeRankPerm, String groupPerm, int limit) {
    }

    private record ConfigSnapshot(
        int maxHomesCap,
        int defaultHomes,
        Map<String, Integer> ranks,
        List<PrecomputedRank> precomputedRanks
    ) {
    }
}
