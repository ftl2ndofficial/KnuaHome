package com.ftl2nd.nms.v1_21;

import com.ftl2nd.nms.DialogViewData;
import com.ftl2nd.nms.NettyPipelineUtil;
import com.ftl2nd.nms.NmsCore;
import com.ftl2nd.nms.NmsVersion;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * NMS implementation for Minecraft 1.21 (and base for 1.21.1 - 1.21.5).
 */
public class NmsV1_21 implements NmsCore {

    private final NmsVersion version;

    public NmsV1_21() {
        this(NmsVersion.V1_21);
    }

    protected NmsV1_21(NmsVersion version) {
        this.version = version;
    }

    @Override
    public NmsVersion getVersion() {
        return version;
    }

    @Override
    public boolean supportsDialog() {
        return false;
    }

    @Override
    public boolean supportsBedSprite() {
        return false;
    }

    @Override
    public int getServerProtocolVersion() {
        try {
            return SharedConstants.getProtocolVersion();
        } catch (Throwable ignored) {
            return version.getProtocolVersion();
        }
    }

    @Override
    public void sendChat(Player player, Component message) {
        if (player == null || message == null) {
            return;
        }
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(new ClientboundSystemChatPacket(PaperAdventure.asVanilla(message), false));
    }

    @Override
    public void sendActionBar(Player player, Component message) {
        if (player == null || message == null) {
            return;
        }
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(new ClientboundSystemChatPacket(PaperAdventure.asVanilla(message), true));
    }

    @Override
    public void openHomeDialog(Player player, DialogViewData viewData, BiConsumer<Player, String> clickHandler) {
        throw new UnsupportedOperationException("Dialog UI is not supported on NMS " + version.getVersionString());
    }

    @Override
    public void closeDialog(Player player) {
        // No-op on 1.21 - 1.21.5
    }

    @Override
    public void openSignEditor(
        Player player,
        Location signLocation,
        List<Component> lines,
        BiConsumer<Player, String[]> signCallback
    ) {
        if (player == null || signLocation == null) {
            return;
        }
        player.sendBlockChange(signLocation, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(signLocation, lines);

        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        BlockPos targetPos = new BlockPos(
            signLocation.getBlockX(),
            signLocation.getBlockY(),
            signLocation.getBlockZ()
        );
        NettyPipelineUtil.installOneShotSignInterceptor(
            handle.connection.connection.channel,
            player,
            msg -> msg instanceof ServerboundSignUpdatePacket p && p.getPos().equals(targetPos),
            msg -> ((ServerboundSignUpdatePacket) msg).getLines(),
            signCallback
        );
        handle.connection.send(new ClientboundOpenSignEditorPacket(targetPos, true));
    }

    @Override
    public void uninjectPlayer(Player player) {
        if (player == null) {
            return;
        }
        try {
            ServerPlayer handle = ((CraftPlayer) player).getHandle();
            NettyPipelineUtil.uninjectAll(handle.connection.connection.channel);
        } catch (Throwable ignored) {
        }
    }
}
