package com.ftl2nd;

import com.ftl2nd.nms.DialogClickAction;
import com.ftl2nd.nms.DialogSlotData;
import com.ftl2nd.nms.DialogViewData;
import com.ftl2nd.nms.NmsManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Manages the Homes Dialog UI (1.21.6 - 26.3) and automatically falls back to HomeGuiManager
 * when the server or client does not support Dialog UI (1.21 - 1.21.5 or older ViaVersion clients).
 */
public final class HomeDialogManager implements Listener {

    private static final int STAGE_TWO_SLOTS = 27;
    private static final long CLICK_COOLDOWN_NANOS = 75_000_000L; // 75ms anti-packet-flood guard

    private final @Nullable JavaPlugin plugin;
    private final @Nullable NmsManager nmsManager;
    private final HomeDatabase database;
    private final RankLimitResolver rankResolver;
    private final MessageUtil messageUtil;
    private final @Nullable HomeGuiManager guiManager;
    private final ConcurrentHashMap<UUID, AtomicLong> lastClickNanos = new ConcurrentHashMap<>();
    private volatile StaticDialogSlots staticDialogSlots;

    public HomeDialogManager(
        @Nullable JavaPlugin plugin,
        @Nullable NmsManager nmsManager,
        HomeDatabase database,
        RankLimitResolver rankResolver,
        MessageUtil messageUtil,
        @Nullable HomeGuiManager guiManager
    ) {
        this.plugin = plugin;
        this.nmsManager = nmsManager;
        this.database = database;
        this.rankResolver = rankResolver;
        this.messageUtil = messageUtil;
        this.guiManager = guiManager;
        reloadStaticSlots();
    }

    /**
     * Rebuilds pre-cached static EMPTY and LOCKED DialogSlotData arrays on startup and /knuahome reload.
     */
    public void reloadStaticSlots() {
        this.staticDialogSlots = StaticDialogSlots.build(messageUtil, rankResolver.getMaxHomesCap());
    }

    public boolean canUseDialog(Player player) {
        return nmsManager != null && nmsManager.canUseDialog(player);
    }

    public void openHomeDialog(Player player) {
        if (!canUseDialog(player)) {
            if (guiManager != null) {
                guiManager.openMainGui(player, 0);
            }
            return;
        }
        if (plugin == null || nmsManager == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            DialogViewData viewData = buildViewData(player);
            nmsManager.getCore().openHomeDialog(player, viewData, this::onCustomClickPacket);
        }, null);
    }

    public DialogViewData buildViewData(Player player) {
        UUID uuid = player.getUniqueId();
        Map<Integer, HomeLocation> homes = database.getHomesView(uuid);
        int rankLimit = rankResolver.resolveMaxHomes(player);
        int maxHomesCap = rankResolver.getMaxHomesCap();
        boolean useBedSprite = nmsManager != null && nmsManager.canUseBedSprite(player);
        return buildViewDataForState(homes, rankLimit, maxHomesCap, useBedSprite);
    }

    DialogViewData buildViewDataForState(
        Map<Integer, HomeLocation> homes,
        int rankLimit,
        int maxHomesCap,
        boolean useBedSprite
    ) {
        int stage1Count = Math.min(maxHomesCap, Math.max(2, Math.min(rankLimit, 5)));
        List<Integer> stageCounts = new ArrayList<>(3);
        stageCounts.add(stage1Count);
        if (stage1Count < STAGE_TWO_SLOTS && maxHomesCap > stage1Count) {
            stageCounts.add(Math.min(STAGE_TWO_SLOTS, maxHomesCap));
        }
        if (stageCounts.get(stageCounts.size() - 1) < maxHomesCap) {
            stageCounts.add(maxHomesCap);
        }

        int maxVisible = stageCounts.get(stageCounts.size() - 1);
        List<DialogSlotData> allSlots = new ArrayList<>(maxVisible);
        StaticDialogSlots cachedSlots = this.staticDialogSlots;
        String limitStr = null;
        String maxStr = null;

        for (int slot = 1; slot <= maxVisible; slot++) {
            HomeLocation existing = homes.get(slot);
            if (existing != null) {
                if (limitStr == null) {
                    limitStr = String.valueOf(rankLimit);
                    maxStr = String.valueOf(maxHomesCap);
                }
                Map<String, String> placeholders = Map.of(
                    "%slot%", String.valueOf(slot),
                    "%name%", existing.displayNameOrDefault(slot),
                    "%limit%", limitStr,
                    "%max%", maxStr
                );
                Component label = messageUtil.getDialogText(DialogTextKey.HOME_BUTTON_LABEL, placeholders);
                Component tooltip = messageUtil.getDialogText(DialogTextKey.HOME_BUTTON_TOOLTIP, placeholders);
                allSlots.add(new DialogSlotData(
                    slot,
                    DialogSlotData.SlotState.SET,
                    label,
                    tooltip,
                    DialogClickAction.TELEPORT.actionPath(slot)
                ));
            } else if (slot <= rankLimit) {
                DialogSlotData prebuilt = cachedSlots.emptySlot(slot);
                if (prebuilt != null) {
                    allSlots.add(prebuilt);
                } else {
                    if (limitStr == null) {
                        limitStr = String.valueOf(rankLimit);
                        maxStr = String.valueOf(maxHomesCap);
                    }
                    Map<String, String> placeholders = Map.of(
                        "%slot%", String.valueOf(slot),
                        "%name%", "Home " + slot,
                        "%limit%", limitStr,
                        "%max%", maxStr
                    );
                    Component label = messageUtil.getDialogText(DialogTextKey.NEW_HOME_BUTTON_LABEL, placeholders);
                    Component tooltip = messageUtil.getDialogText(DialogTextKey.NEW_HOME_BUTTON_TOOLTIP, placeholders);
                    allSlots.add(new DialogSlotData(
                        slot,
                        DialogSlotData.SlotState.EMPTY,
                        label,
                        tooltip,
                        DialogClickAction.SET_HOME.actionPath(slot)
                    ));
                }
            } else {
                DialogSlotData prebuilt = cachedSlots.lockedSlot(slot);
                if (prebuilt != null) {
                    allSlots.add(prebuilt);
                } else {
                    if (limitStr == null) {
                        limitStr = String.valueOf(rankLimit);
                        maxStr = String.valueOf(maxHomesCap);
                    }
                    Map<String, String> placeholders = Map.of(
                        "%slot%", String.valueOf(slot),
                        "%name%", "Home " + slot,
                        "%limit%", limitStr,
                        "%max%", maxStr
                    );
                    Component label = messageUtil.getDialogText(DialogTextKey.LOCKED_BUTTON_LABEL, placeholders);
                    Component tooltip = messageUtil.getDialogText(DialogTextKey.LOCKED_BUTTON_TOOLTIP, placeholders);
                    allSlots.add(new DialogSlotData(
                        slot,
                        DialogSlotData.SlotState.LOCKED,
                        label,
                        tooltip,
                        DialogClickAction.LOCKED.actionPath(slot)
                    ));
                }
            }
        }

        List<DialogViewData.StageData> stages = new ArrayList<>(stageCounts.size());
        for (int visibleSlots : stageCounts) {
            stages.add(new DialogViewData.StageData(allSlots.subList(0, visibleSlots)));
        }

        Component title = messageUtil.getDialogText(DialogTextKey.TITLE);
        Component showMoreLabel = messageUtil.getDialogText(DialogTextKey.SHOW_MORE_BUTTON_LABEL);

        return new DialogViewData(title, stages, showMoreLabel, useBedSprite);
    }

    boolean tryAcquireClickCooldown(UUID uuid) {
        long now = System.nanoTime();
        AtomicLong stamp = lastClickNanos.computeIfAbsent(uuid, k -> new AtomicLong(now - CLICK_COOLDOWN_NANOS - 1L));
        while (true) {
            long prev = stamp.get();
            if (now - prev < CLICK_COOLDOWN_NANOS) {
                return false;
            }
            if (stamp.compareAndSet(prev, now)) {
                return true;
            }
        }
    }

    private void onCustomClickPacket(Player player, String actionPath) {
        if (player == null || actionPath == null || plugin == null) {
            return;
        }
        if (!tryAcquireClickCooldown(player.getUniqueId())) {
            return;
        }
        player.getScheduler().run(plugin, task -> handleActionOnPlayerThread(player, actionPath), null);
    }

    private void handleActionOnPlayerThread(Player player, String rawPath) {
        if (!player.isOnline()) {
            return;
        }
        DialogClickAction.ParsedClick parsed = DialogClickAction.parse(rawPath, rankResolver.getMaxHomesCap());
        if (parsed == null) {
            return;
        }
        int slot = parsed.slot();

        switch (parsed.action()) {
            case LOCKED -> {
                int limit = rankResolver.resolveMaxHomes(player);
                messageUtil.send(player, MessageKey.SLOT_LOCKED, Map.of(
                    "%slot%", String.valueOf(slot),
                    "%limit%", String.valueOf(limit)
                ));
            }
            case TELEPORT -> {
                if (nmsManager != null) {
                    nmsManager.getCore().closeDialog(player);
                }
                teleportToHome(player, slot);
            }
            case SET_HOME -> {
                UUID uuid = player.getUniqueId();
                int maxHomes = rankResolver.resolveMaxHomes(player);
                Map<Integer, HomeLocation> homes = database.getHomesView(uuid);

                if (slot > maxHomes || (!homes.containsKey(slot) && homes.size() >= maxHomes)) {
                    messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of(
                        "%slot%", String.valueOf(slot),
                        "%limit%", String.valueOf(maxHomes)
                    ));
                    return;
                }

                if (nmsManager != null) {
                    nmsManager.getCore().closeDialog(player);
                }
                HomeLocation homeLoc = HomeLocation.fromBukkit(player.getLocation());
                database.setHome(uuid, slot, homeLoc);
                messageUtil.send(player, MessageKey.HOME_SET, Map.of(
                    "%slot%", String.valueOf(slot),
                    "%limit%", String.valueOf(maxHomes)
                ));
            }
        }
    }

    public void teleportToHome(Player player, int slot) {
        Map<String, String> placeholders = Map.of("%slot%", String.valueOf(slot));
        HomeLocation home = database.getHome(player.getUniqueId(), slot);
        if (home == null) {
            messageUtil.send(player, MessageKey.HOME_NOT_SET, placeholders);
            return;
        }

        Location target = home.toBukkitLocation();
        if (target == null) {
            messageUtil.send(player, MessageKey.WORLD_NOT_LOADED, placeholders);
            return;
        }

        if (plugin == null) {
            return;
        }
        player.teleportAsync(target).thenAccept(success -> {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline()) {
                    return;
                }
                if (Boolean.TRUE.equals(success)) {
                    messageUtil.send(player, MessageKey.TELEPORTED, placeholders);
                } else {
                    messageUtil.send(player, MessageKey.TELEPORT_FAILED, placeholders);
                }
            }, null);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastClickNanos.remove(player.getUniqueId());
        if (nmsManager != null) {
            nmsManager.getCore().uninjectPlayer(player);
        }
    }

    private record StaticDialogSlots(
        DialogSlotData @Nullable [] emptySlots,
        DialogSlotData @Nullable [] lockedSlots
    ) {
        static StaticDialogSlots build(MessageUtil msg, int maxCap) {
            int size = Math.max(1, maxCap) + 1;
            DialogSlotData[] empty = null;
            if (msg.isStaticDialogEntry(DialogTextKey.NEW_HOME_BUTTON_LABEL, DialogTextKey.NEW_HOME_BUTTON_TOOLTIP)) {
                Component label = msg.getDialogText(DialogTextKey.NEW_HOME_BUTTON_LABEL);
                Component tooltip = msg.getDialogText(DialogTextKey.NEW_HOME_BUTTON_TOOLTIP);
                empty = new DialogSlotData[size];
                for (int slot = 1; slot < size; slot++) {
                    empty[slot] = new DialogSlotData(
                        slot,
                        DialogSlotData.SlotState.EMPTY,
                        label,
                        tooltip,
                        DialogClickAction.SET_HOME.actionPath(slot)
                    );
                }
            }

            DialogSlotData[] locked = null;
            if (msg.isStaticDialogEntry(DialogTextKey.LOCKED_BUTTON_LABEL, DialogTextKey.LOCKED_BUTTON_TOOLTIP)) {
                Component label = msg.getDialogText(DialogTextKey.LOCKED_BUTTON_LABEL);
                Component tooltip = msg.getDialogText(DialogTextKey.LOCKED_BUTTON_TOOLTIP);
                locked = new DialogSlotData[size];
                for (int slot = 1; slot < size; slot++) {
                    locked[slot] = new DialogSlotData(
                        slot,
                        DialogSlotData.SlotState.LOCKED,
                        label,
                        tooltip,
                        DialogClickAction.LOCKED.actionPath(slot)
                    );
                }
            }

            return new StaticDialogSlots(empty, locked);
        }

        @Nullable DialogSlotData emptySlot(int slot) {
            return (emptySlots != null && slot >= 1 && slot < emptySlots.length) ? emptySlots[slot] : null;
        }

        @Nullable DialogSlotData lockedSlot(int slot) {
            return (lockedSlots != null && slot >= 1 && slot < lockedSlots.length) ? lockedSlots[slot] : null;
        }
    }
}
