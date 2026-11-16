package com.ftl2nd.nms;

import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Version-specific NMS Core interface for Minecraft 1.21 through 26.3.
 */
public interface NmsCore {

    String ACTION_NAMESPACE = "knuahome";
    String NETTY_HANDLER_NAME = "knuahome_dialog_click";
    String NETTY_SIGN_HANDLER_NAME = "knuahome_sign_search";

    NmsVersion getVersion();

    boolean supportsDialog();

    boolean supportsBedSprite();

    int getServerProtocolVersion();

    void sendChat(Player player, Component message);

    void sendActionBar(Player player, Component message);

    void openHomeDialog(Player player, DialogViewData viewData, BiConsumer<Player, String> clickHandler);

    void closeDialog(Player player);

    void openSignEditor(Player player, Location signLocation, List<Component> lines, BiConsumer<Player, String[]> signCallback);

    void uninjectPlayer(Player player);
}
