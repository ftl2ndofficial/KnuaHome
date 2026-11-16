package com.ftl2nd;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion for KnuaHome:
 *   %knuahome_count% -> current number of homes set
 *   %knuahome_max%   -> maximum allowed homes for the player
 */
public final class KnuaHomePlaceholderExpansion extends PlaceholderExpansion {

    private final KnuaHomePlugin plugin;
    private final HomeDatabase database;
    private final RankLimitResolver rankResolver;

    public KnuaHomePlaceholderExpansion(
        KnuaHomePlugin plugin,
        HomeDatabase database,
        RankLimitResolver rankResolver
    ) {
        this.plugin = plugin;
        this.database = database;
        this.rankResolver = rankResolver;
    }

    @Override
    public String getIdentifier() {
        return "knuahome";
    }

    @Override
    public String getAuthor() {
        return "FTL2nd";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null) {
            return "0";
        }
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "count" -> String.valueOf(database.getHomeCount(offlinePlayer.getUniqueId()));
            case "max" -> {
                Player online = offlinePlayer.getPlayer();
                yield String.valueOf(
                    online != null ? rankResolver.resolveMaxHomes(online) : rankResolver.getDefaultHomes()
                );
            }
            default -> null;
        };
    }
}
