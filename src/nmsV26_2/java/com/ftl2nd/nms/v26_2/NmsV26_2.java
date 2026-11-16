package com.ftl2nd.nms.v26_2;

import com.ftl2nd.nms.DialogSlotData;
import com.ftl2nd.nms.DialogViewData;
import com.ftl2nd.nms.NettyPipelineUtil;
import com.ftl2nd.nms.NmsCore;
import com.ftl2nd.nms.NmsVersion;
import com.mojang.datafixers.util.Either;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ResolvableProfile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * NMS implementation for Minecraft 26.1 - 26.2.
 */
public class NmsV26_2 implements NmsCore {

    private static final int DIALOG_COLUMNS = 6;
    private static final int BUTTON_WIDTH = 90;

    private final NmsVersion version;
    private volatile DialogBody cachedBedBody;
    private volatile net.minecraft.network.chat.Component cachedBedSprite;

    public NmsV26_2() {
        this(NmsVersion.V26_2);
    }

    protected NmsV26_2(NmsVersion version) {
        this.version = version;
    }

    private net.minecraft.network.chat.Component getOrCreateBedSprite() {
        net.minecraft.network.chat.Component sprite = this.cachedBedSprite;
        if (sprite == null) {
            Identifier bedTexture = Identifier.fromNamespaceAndPath("minecraft", "entity/bed/white");
            PlayerSkin.Patch skinPatch = PlayerSkin.Patch.create(
                Optional.of(new ClientAsset.ResourceTexture(bedTexture)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
            ResolvableProfile profile = new ResolvableProfile.Static(
                Either.right(ResolvableProfile.Partial.EMPTY),
                skinPatch
            );
            sprite = net.minecraft.network.chat.Component.object(new PlayerSprite(profile, false))
                .withStyle(ChatFormatting.WHITE);
            this.cachedBedSprite = sprite;
        }
        return sprite;
    }

    @Override
    public NmsVersion getVersion() {
        return version;
    }

    @Override
    public boolean supportsDialog() {
        return true;
    }

    @Override
    public boolean supportsBedSprite() {
        return true;
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
        if (player == null || viewData == null || viewData.stages().isEmpty()) {
            return;
        }
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        NettyPipelineUtil.installOrUpdateClickInterceptor(
            handle.connection.connection.channel,
            player,
            NmsV26_2::extractActionPath,
            clickHandler
        );

        net.minecraft.network.chat.Component vanillaTitle = PaperAdventure.asVanilla(viewData.title());
        CommonButtonData showMoreBtnData = new CommonButtonData(
            PaperAdventure.asVanilla(viewData.showMoreLabel()),
            Optional.empty(),
            BUTTON_WIDTH
        );
        List<DialogBody> bodyList = List.of(getOrCreateBedBody());

        List<DialogViewData.StageData> stages = viewData.stages();
        List<DialogSlotData> maxStageSlots = stages.get(stages.size() - 1).slots();
        List<ActionButton> sharedButtons = buildActionButtonsOnce(maxStageSlots, viewData.useBedSprite());

        Dialog nextStageDialog = null;
        for (int i = stages.size() - 1; i >= 0; i--) {
            int count = stages.get(i).slots().size();
            List<ActionButton> buttons = new ArrayList<>(count + 1);
            buttons.addAll(sharedButtons.subList(0, count));
            if (nextStageDialog != null) {
                buttons.add(new ActionButton(
                    showMoreBtnData,
                    Optional.of(new StaticAction(new ClickEvent.ShowDialog(Holder.direct(nextStageDialog))))
                ));
            }
            CommonDialogData commonData = new CommonDialogData(
                vanillaTitle,
                Optional.empty(),
                true,
                false,
                DialogAction.NONE,
                bodyList,
                List.of()
            );
            nextStageDialog = new MultiActionDialog(commonData, buttons, Optional.empty(), DIALOG_COLUMNS);
        }

        if (nextStageDialog != null) {
            handle.openDialog(Holder.direct(nextStageDialog));
        }
    }

    private List<ActionButton> buildActionButtonsOnce(List<DialogSlotData> slots, boolean useBedSprite) {
        IdentityHashMap<Component, net.minecraft.network.chat.Component> memo = new IdentityHashMap<>(8);
        List<ActionButton> result = new ArrayList<>(slots.size());
        net.minecraft.network.chat.Component space = useBedSprite ? net.minecraft.network.chat.Component.literal(" ") : null;
        net.minecraft.network.chat.Component bedSprite = useBedSprite ? getOrCreateBedSprite() : null;
        for (DialogSlotData slot : slots) {
            net.minecraft.network.chat.Component baseLabel = memo.computeIfAbsent(slot.label(), PaperAdventure::asVanilla);
            net.minecraft.network.chat.Component label;
            if (useBedSprite && slot.state() == DialogSlotData.SlotState.SET) {
                MutableComponent combined = net.minecraft.network.chat.Component.empty().withStyle(ChatFormatting.WHITE);
                combined.append(bedSprite);
                combined.append(space);
                combined.append(baseLabel);
                label = combined;
            } else {
                label = baseLabel;
            }
            net.minecraft.network.chat.Component tooltip = memo.computeIfAbsent(slot.tooltip(), PaperAdventure::asVanilla);
            Identifier actionId = Identifier.fromNamespaceAndPath(ACTION_NAMESPACE, slot.actionPath());
            result.add(new ActionButton(
                new CommonButtonData(label, Optional.of(tooltip), BUTTON_WIDTH),
                Optional.of(new CustomAll(actionId, Optional.empty()))
            ));
        }
        return result;
    }

    private DialogBody getOrCreateBedBody() {
        DialogBody body = this.cachedBedBody;
        if (body == null) {
            body = new ItemBody(
                new ItemStackTemplate(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "white_bed"))),
                Optional.empty(),
                false,
                false,
                16,
                16
            );
            this.cachedBedBody = body;
        }
        return body;
    }

    private static String extractActionPath(Object msg) {
        if (msg instanceof ServerboundCustomClickActionPacket packet) {
            Identifier id = packet.id();
            if (ACTION_NAMESPACE.equals(id.getNamespace())) {
                return id.getPath();
            }
        }
        return null;
    }

    @Override
    public void closeDialog(Player player) {
        if (player == null) {
            return;
        }
        ((CraftPlayer) player).getHandle().connection.send(ClientboundClearDialogPacket.INSTANCE);
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
