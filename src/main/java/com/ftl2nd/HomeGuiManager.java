package com.ftl2nd;

import com.ftl2nd.nms.NmsManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * High-performance Fallback Inventory GUI for servers or clients without Dialog UI (< 1.21.6):
 * - Enum-driven button layouts (GuiType, MainBarButton, ManageButton, IconBarButton).
 * - Pre-indexed IconCatalog (O(1) title-case lookup & zero-allocation substring filtering).
 * - Pre-built StaticGuiItems cache eliminating redundant ItemMeta conversions on 54-slot GUI opens.
 * - Uses Inventory#getHolder(false) to avoid TileEntity snapshot overhead on vanilla container clicks.
 */
public final class HomeGuiManager implements Listener {

    public static final int ITEMS_PER_PAGE = 45;
    private static final int CHEST_SIZE = 54;

    public enum GuiType {
        MAIN_LIST,
        MANAGE_HOPPER,
        ICON_PICKER
    }

    /**
     * Bottom-bar buttons for the 54-slot Main Homes Chest GUI.
     */
    public enum MainBarButton {
        PREV_PAGE(45, Material.ARROW, GuiTextKey.PREV_PAGE_NAME, GuiTextKey.PREV_PAGE_LORE),
        CENTER_INFO(49, Material.GRAY_BED, GuiTextKey.CENTER_BED_NAME, GuiTextKey.CENTER_BED_LORE),
        NEXT_PAGE(53, Material.ARROW, GuiTextKey.NEXT_PAGE_NAME, GuiTextKey.NEXT_PAGE_LORE);

        private final int slot;
        private final Material material;
        private final GuiTextKey nameKey;
        private final GuiTextKey loreKey;

        MainBarButton(int slot, Material material, GuiTextKey nameKey, GuiTextKey loreKey) {
            this.slot = slot;
            this.material = material;
            this.nameKey = nameKey;
            this.loreKey = loreKey;
        }

        public int slot() {
            return slot;
        }

        public Material material() {
            return material;
        }

        public GuiTextKey nameKey() {
            return nameKey;
        }

        public GuiTextKey loreKey() {
            return loreKey;
        }
    }

    /**
     * Buttons inside the 5-slot Manage Home Hopper GUI with O(1) slot lookup.
     */
    public enum ManageButton {
        TELEPORT(0, Material.LODESTONE, GuiTextKey.TELEPORT_NAME, GuiTextKey.TELEPORT_LORE),
        RENAME(1, Material.WRITABLE_BOOK, GuiTextKey.RENAME_NAME, GuiTextKey.RENAME_LORE),
        ICON(2, null, GuiTextKey.ICON_NAME, GuiTextKey.ICON_LORE),
        DELETE(4, Material.LAVA_BUCKET, GuiTextKey.DELETE_NAME, GuiTextKey.DELETE_LORE);

        private static final ManageButton[] BY_SLOT = new ManageButton[5];

        static {
            for (ManageButton b : values()) {
                BY_SLOT[b.slot] = b;
            }
        }

        private final int slot;
        private final @Nullable Material fixedMaterial;
        private final GuiTextKey nameKey;
        private final GuiTextKey loreKey;

        ManageButton(int slot, @Nullable Material fixedMaterial, GuiTextKey nameKey, GuiTextKey loreKey) {
            this.slot = slot;
            this.fixedMaterial = fixedMaterial;
            this.nameKey = nameKey;
            this.loreKey = loreKey;
        }

        public int slot() {
            return slot;
        }

        public @Nullable Material fixedMaterial() {
            return fixedMaterial;
        }

        public GuiTextKey nameKey() {
            return nameKey;
        }

        public GuiTextKey loreKey() {
            return loreKey;
        }

        public static @Nullable ManageButton fromSlot(int slot) {
            return (slot >= 0 && slot < BY_SLOT.length) ? BY_SLOT[slot] : null;
        }
    }

    /**
     * Bottom-bar buttons inside the 54-slot Home Icons Picker GUI with O(1) slot lookup.
     */
    public enum IconBarButton {
        PREV_PAGE(45, Material.ARROW, GuiTextKey.PREV_PAGE_NAME, GuiTextKey.PREV_PAGE_LORE),
        BACK(46, Material.RED_STAINED_GLASS_PANE, GuiTextKey.BACK_NAME, GuiTextKey.BACK_LORE),
        CURRENT_ICON(49, null, GuiTextKey.CURRENT_ICON_NAME, GuiTextKey.CURRENT_ICON_LORE),
        DEFAULT_ICON(50, Material.WHITE_BED, GuiTextKey.DEFAULT_ICON_NAME, GuiTextKey.DEFAULT_ICON_LORE),
        SEARCH(52, Material.OAK_SIGN, GuiTextKey.SEARCH_NAME, GuiTextKey.SEARCH_LORE),
        NEXT_PAGE(53, Material.ARROW, GuiTextKey.NEXT_PAGE_NAME, GuiTextKey.NEXT_PAGE_LORE);

        private static final IconBarButton[] BY_SLOT = new IconBarButton[CHEST_SIZE];

        static {
            for (IconBarButton b : values()) {
                BY_SLOT[b.slot] = b;
            }
        }

        private final int slot;
        private final @Nullable Material fixedMaterial;
        private final GuiTextKey nameKey;
        private final GuiTextKey loreKey;

        IconBarButton(int slot, @Nullable Material fixedMaterial, GuiTextKey nameKey, GuiTextKey loreKey) {
            this.slot = slot;
            this.fixedMaterial = fixedMaterial;
            this.nameKey = nameKey;
            this.loreKey = loreKey;
        }

        public int slot() {
            return slot;
        }

        public @Nullable Material fixedMaterial() {
            return fixedMaterial;
        }

        public GuiTextKey nameKey() {
            return nameKey;
        }

        public GuiTextKey loreKey() {
            return loreKey;
        }

        public static @Nullable IconBarButton fromSlot(int slot) {
            return (slot >= 0 && slot < BY_SLOT.length) ? BY_SLOT[slot] : null;
        }
    }

    private static final Set<String> EXCLUDED_MATERIAL_NAMES = Set.of(
        "AIR",
        "CAVE_AIR",
        "VOID_AIR",
        "BARRIER",
        "LIGHT",
        "STRUCTURE_BLOCK",
        "STRUCTURE_VOID",
        "JIGSAW",
        "DEBUG_STICK",
        "KNOWLEDGE_BOOK",
        "COMMAND_BLOCK",
        "CHAIN_COMMAND_BLOCK",
        "REPEATING_COMMAND_BLOCK",
        "COMMAND_BLOCK_MINECART",
        "TEST_BLOCK",
        "TEST_INSTANCE_BLOCK",
        "SPAWNER",
        "TRIAL_SPAWNER",
        "VAULT",
        "BEDROCK",
        "REINFORCED_DEEPSLATE",
        "END_PORTAL_FRAME",
        "PETRIFIED_OAK_SLAB",
        "PLAYER_HEAD",
        "FROGSPAWN",
        "BUDDING_AMETHYST",
        "FARMLAND",
        "DIRT_PATH",
        "CHORUS_PLANT"
    );

    private static final EnumSet<Material> EXCLUDED_MATERIALS = buildExcludedMaterialEnumSet();
    private static final EnumMap<Material, String> DISPLAY_NAMES = buildDisplayNameMap();
    private static volatile @Nullable IconCatalog cachedServerCatalog;
    private static volatile @Nullable IconCatalog cachedHeadlessCatalog;

    private final JavaPlugin plugin;
    private final @Nullable NmsManager nmsManager;
    private final HomeDatabase database;
    private final RankLimitResolver rankResolver;
    private final MessageUtil messageUtil;
    private final ConcurrentHashMap<UUID, RenameSession> pendingRenames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Location> pendingSignLocations = new ConcurrentHashMap<>();
    private volatile @Nullable StaticGuiItems staticGuiItems;
    private @Nullable HomeDialogManager dialogManager;

    public HomeGuiManager(
        JavaPlugin plugin,
        @Nullable NmsManager nmsManager,
        HomeDatabase database,
        RankLimitResolver rankResolver,
        MessageUtil messageUtil
    ) {
        this.plugin = plugin;
        this.nmsManager = nmsManager;
        this.database = database;
        this.rankResolver = rankResolver;
        this.messageUtil = messageUtil;
        reloadStaticItems();
    }

    public void setDialogManager(HomeDialogManager dialogManager) {
        this.dialogManager = dialogManager;
    }

    /**
     * Rebuilds pre-cached static GUI ItemStacks when messages.yml is loaded/reloaded.
     */
    public void reloadStaticItems() {
        if (Bukkit.getServer() == null) {
            this.staticGuiItems = null;
            return;
        }
        try {
            this.staticGuiItems = StaticGuiItems.build(messageUtil);
        } catch (Throwable ignored) {
            this.staticGuiItems = null;
        }
    }

    public static List<Material> getAllIconMaterials() {
        return getCatalog().materials();
    }

    public static List<Material> filterIcons(@Nullable String searchQuery) {
        IconCatalog catalog = getCatalog();
        if (searchQuery == null || searchQuery.isBlank()) {
            return catalog.materials();
        }
        String normalized = searchQuery.trim().toLowerCase(Locale.ROOT);
        String underscored = normalized.replace(' ', '_');
        List<IconEntry> entries = catalog.entries();
        List<Material> matched = new ArrayList<>();
        for (int i = 0, size = entries.size(); i < size; i++) {
            IconEntry entry = entries.get(i);
            if (entry.lowerKey().contains(underscored) || entry.lowerDisplay().contains(normalized)) {
                matched.add(entry.material());
            }
        }
        return matched;
    }

    public static String formatMaterialName(@Nullable Material material) {
        if (material == null) {
            return "White Bed";
        }
        String precomputed = DISPLAY_NAMES.get(material);
        return precomputed != null ? precomputed : computeTitleCase(material.name());
    }

    public void openMainGui(Player player, int page) {
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            UUID uuid = player.getUniqueId();
            Map<Integer, HomeLocation> homes = database.getHomesView(uuid);
            int rankLimit = rankResolver.resolveMaxHomes(player);
            int maxHomesCap = rankResolver.getMaxHomesCap();
            int maxPages = Math.max(1, (maxHomesCap + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            int safePage = Math.clamp(page, 0, maxPages - 1);

            Component title = messageUtil.getGuiText(GuiTextKey.TITLE, Map.of(
                "%page%", String.valueOf(safePage + 1),
                "%max_page%", String.valueOf(maxPages)
            ));
            HomeGuiHolder holder = new HomeGuiHolder(GuiType.MAIN_LIST, 0, safePage, safePage, "");
            Inventory inv = Bukkit.createInventory(holder, CHEST_SIZE, title);
            holder.setInventory(inv);

            StaticGuiItems cached = this.staticGuiItems;
            String limitStr = String.valueOf(rankLimit);
            String maxStr = String.valueOf(maxHomesCap);
            int startSlot = safePage * ITEMS_PER_PAGE + 1;

            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int homeSlot = startSlot + i;
                if (homeSlot > maxHomesCap) {
                    break;
                }
                HomeLocation existing = homes.get(homeSlot);
                if (existing != null) {
                    Map<String, String> placeholders = Map.of(
                        "%slot%", String.valueOf(homeSlot),
                        "%name%", existing.displayNameOrDefault(homeSlot),
                        "%limit%", limitStr,
                        "%max%", maxStr
                    );
                    inv.setItem(i, createGuiItem(
                        existing.resolveIconMaterial(),
                        messageUtil.getGuiText(GuiTextKey.HOME_NAME, placeholders),
                        messageUtil.getGuiLore(GuiTextKey.HOME_LORE, placeholders)
                    ));
                } else if (homeSlot <= rankLimit) {
                    if (cached != null && cached.newHomePane() != null) {
                        inv.setItem(i, cached.newHomePane());
                    } else {
                        Map<String, String> placeholders = Map.of(
                            "%slot%", String.valueOf(homeSlot),
                            "%limit%", limitStr,
                            "%max%", maxStr
                        );
                        inv.setItem(i, createGuiItem(
                            Material.GRAY_STAINED_GLASS_PANE,
                            messageUtil.getGuiText(GuiTextKey.NEW_HOME_NAME, placeholders),
                            messageUtil.getGuiLore(GuiTextKey.NEW_HOME_LORE, placeholders)
                        ));
                    }
                } else {
                    if (cached != null && cached.lockedPane() != null) {
                        inv.setItem(i, cached.lockedPane());
                    } else {
                        Map<String, String> placeholders = Map.of(
                            "%slot%", String.valueOf(homeSlot),
                            "%limit%", limitStr,
                            "%max%", maxStr
                        );
                        inv.setItem(i, createGuiItem(
                            Material.RED_STAINED_GLASS_PANE,
                            messageUtil.getGuiText(GuiTextKey.LOCKED_NAME, placeholders),
                            messageUtil.getGuiLore(GuiTextKey.LOCKED_LORE, placeholders)
                        ));
                    }
                }
            }

            if (safePage > 0) {
                Map<String, String> prevPh = Map.of("%page%", String.valueOf(safePage));
                inv.setItem(MainBarButton.PREV_PAGE.slot(), createGuiItem(
                    MainBarButton.PREV_PAGE.material(),
                    messageUtil.getGuiText(MainBarButton.PREV_PAGE.nameKey(), prevPh),
                    messageUtil.getGuiLore(MainBarButton.PREV_PAGE.loreKey(), prevPh)
                ));
            }

            if (cached != null && cached.centerBed() != null) {
                inv.setItem(MainBarButton.CENTER_INFO.slot(), cached.centerBed());
            } else {
                inv.setItem(MainBarButton.CENTER_INFO.slot(), createGuiItem(
                    MainBarButton.CENTER_INFO.material(),
                    messageUtil.getGuiText(MainBarButton.CENTER_INFO.nameKey()),
                    messageUtil.getGuiLore(MainBarButton.CENTER_INFO.loreKey())
                ));
            }

            if (safePage + 1 < maxPages) {
                Map<String, String> nextPh = Map.of("%page%", String.valueOf(safePage + 2));
                inv.setItem(MainBarButton.NEXT_PAGE.slot(), createGuiItem(
                    MainBarButton.NEXT_PAGE.material(),
                    messageUtil.getGuiText(MainBarButton.NEXT_PAGE.nameKey(), nextPh),
                    messageUtil.getGuiLore(MainBarButton.NEXT_PAGE.loreKey(), nextPh)
                ));
            }

            player.openInventory(inv);
        }, null);
    }

    public void openManageGui(Player player, int homeSlot, int returnPage) {
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            HomeLocation home = database.getHome(player.getUniqueId(), homeSlot);
            if (home == null) {
                openMainGui(player, returnPage);
                return;
            }

            String rawName = home.displayNameOrDefault(homeSlot);
            Map<String, String> placeholders = Map.of(
                "%slot%", String.valueOf(homeSlot),
                "%name%", rawName
            );

            Component title = messageUtil.getGuiText(GuiTextKey.MANAGE_TITLE, placeholders);
            HomeGuiHolder holder = new HomeGuiHolder(GuiType.MANAGE_HOPPER, homeSlot, 0, returnPage, "");
            Inventory inv = Bukkit.createInventory(holder, InventoryType.HOPPER, title);
            holder.setInventory(inv);

            StaticGuiItems cached = this.staticGuiItems;
            for (ManageButton btn : ManageButton.values()) {
                ItemStack prebuilt = cached != null ? cached.manageButton(btn) : null;
                if (prebuilt != null && btn.fixedMaterial() != null) {
                    inv.setItem(btn.slot(), prebuilt);
                } else {
                    Material mat = btn.fixedMaterial() != null ? btn.fixedMaterial() : home.resolveIconMaterial();
                    inv.setItem(btn.slot(), createGuiItem(
                        mat,
                        messageUtil.getGuiText(btn.nameKey(), placeholders),
                        messageUtil.getGuiLore(btn.loreKey(), placeholders)
                    ));
                }
            }

            player.openInventory(inv);
        }, null);
    }

    public void openIconPickerGui(Player player, int homeSlot, int iconPage, int returnPage) {
        openIconPickerGui(player, homeSlot, iconPage, returnPage, "");
    }

    public void openIconPickerGui(
        Player player,
        int homeSlot,
        int iconPage,
        int returnPage,
        @Nullable String searchQuery
    ) {
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            HomeLocation home = database.getHome(player.getUniqueId(), homeSlot);
            if (home == null) {
                openMainGui(player, returnPage);
                return;
            }

            String safeQuery = searchQuery == null ? "" : searchQuery.trim();
            List<Material> icons = filterIcons(safeQuery);
            int totalIcons = icons.size();
            int maxPages = Math.max(1, (totalIcons + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            int safePage = Math.clamp(iconPage, 0, maxPages - 1);

            String homeDisplayName = home.displayNameOrDefault(homeSlot);
            Material currentIconMat = home.resolveIconMaterial();
            String currentIconDisplay = formatMaterialName(currentIconMat);

            Map<String, String> placeholders = Map.of(
                "%slot%", String.valueOf(homeSlot),
                "%name%", homeDisplayName,
                "%home_name%", homeDisplayName,
                "%icon_name%", currentIconDisplay,
                "%page%", String.valueOf(safePage + 1),
                "%max_page%", String.valueOf(maxPages)
            );
            Component title = messageUtil.getGuiText(GuiTextKey.ICON_PICKER_TITLE, placeholders);
            HomeGuiHolder holder = new HomeGuiHolder(GuiType.ICON_PICKER, homeSlot, safePage, returnPage, safeQuery);
            Inventory inv = Bukkit.createInventory(holder, CHEST_SIZE, title);
            holder.setInventory(inv);

            List<Component> entryLore = messageUtil.getGuiLore(GuiTextKey.ICON_ENTRY_LORE, placeholders);
            boolean hasEntryLore = !entryLore.isEmpty();
            int startIdx = safePage * ITEMS_PER_PAGE;
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int idx = startIdx + i;
                if (idx >= totalIcons) {
                    break;
                }
                ItemStack stack = new ItemStack(icons.get(idx));
                if (hasEntryLore) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.lore(entryLore);
                        stack.setItemMeta(meta);
                    }
                }
                inv.setItem(i, stack);
            }

            StaticGuiItems cached = this.staticGuiItems;

            int prevTargetPage = safePage > 0 ? safePage : maxPages;
            Map<String, String> prevPh = Map.of("%page%", String.valueOf(prevTargetPage));
            inv.setItem(IconBarButton.PREV_PAGE.slot(), createGuiItem(
                Material.ARROW,
                messageUtil.getGuiText(IconBarButton.PREV_PAGE.nameKey(), prevPh),
                messageUtil.getGuiLore(IconBarButton.PREV_PAGE.loreKey(), prevPh)
            ));

            inv.setItem(
                IconBarButton.BACK.slot(),
                (cached != null && cached.backPane() != null)
                    ? cached.backPane()
                    : createGuiItem(
                        Material.RED_STAINED_GLASS_PANE,
                        messageUtil.getGuiText(IconBarButton.BACK.nameKey(), placeholders),
                        messageUtil.getGuiLore(IconBarButton.BACK.loreKey(), placeholders)
                    )
            );

            inv.setItem(IconBarButton.CURRENT_ICON.slot(), createGuiItem(
                currentIconMat,
                messageUtil.getGuiText(IconBarButton.CURRENT_ICON.nameKey(), placeholders),
                messageUtil.getGuiLore(IconBarButton.CURRENT_ICON.loreKey(), placeholders)
            ));

            inv.setItem(
                IconBarButton.DEFAULT_ICON.slot(),
                (cached != null && cached.defaultBed() != null)
                    ? cached.defaultBed()
                    : createGuiItem(
                        Material.WHITE_BED,
                        messageUtil.getGuiText(IconBarButton.DEFAULT_ICON.nameKey(), placeholders),
                        messageUtil.getGuiLore(IconBarButton.DEFAULT_ICON.loreKey(), placeholders)
                    )
            );

            inv.setItem(
                IconBarButton.SEARCH.slot(),
                (cached != null && cached.searchSign() != null)
                    ? cached.searchSign()
                    : createGuiItem(
                        Material.OAK_SIGN,
                        messageUtil.getGuiText(IconBarButton.SEARCH.nameKey(), placeholders),
                        messageUtil.getGuiLore(IconBarButton.SEARCH.loreKey(), placeholders)
                    )
            );

            int nextTargetPage = (safePage + 1 < maxPages) ? (safePage + 2) : 1;
            Map<String, String> nextPh = Map.of("%page%", String.valueOf(nextTargetPage));
            inv.setItem(IconBarButton.NEXT_PAGE.slot(), createGuiItem(
                Material.ARROW,
                messageUtil.getGuiText(IconBarButton.NEXT_PAGE.nameKey(), nextPh),
                messageUtil.getGuiLore(IconBarButton.NEXT_PAGE.loreKey(), nextPh)
            ));

            player.openInventory(inv);
        }, null);
    }

    private void openSearchSignGui(Player player, int homeSlot, int returnPage) {
        Location pLoc = player.getLocation();
        World world = pLoc.getWorld();
        if (world == null || nmsManager == null) {
            return;
        }
        player.closeInventory();

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int signY = pLoc.getBlockY() - 4;
        if (signY < minY) {
            signY = Math.min(maxY, pLoc.getBlockY() + 4);
        }
        Location signLoc = new Location(world, pLoc.getBlockX(), signY, pLoc.getBlockZ());
        pendingSignLocations.put(player.getUniqueId(), signLoc);

        List<Component> lines = messageUtil.getSignSearchLines();
        nmsManager.getCore().openSignEditor(player, signLoc, lines, (p, rawLines) -> {
            p.getScheduler().run(plugin, task -> {
                pendingSignLocations.remove(p.getUniqueId());
                if (!p.isOnline()) {
                    return;
                }
                restoreVirtualSignBlock(p, signLoc);
                String query = (rawLines != null && rawLines.length > 0 && rawLines[0] != null)
                    ? rawLines[0].trim()
                    : "";
                openIconPickerGui(p, homeSlot, 0, returnPage, query);
            }, null);
        });
    }

    private static void restoreVirtualSignBlock(Player player, Location signLoc) {
        World signWorld = signLoc.getWorld();
        if (signWorld == null || !signWorld.equals(player.getWorld())) {
            return;
        }
        int chunkX = signLoc.getBlockX() >> 4;
        int chunkZ = signLoc.getBlockZ() >> 4;
        if (!signWorld.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Block realBlock = signLoc.getBlock();
        player.sendBlockChange(signLoc, realBlock.getBlockData());
        BlockState state = realBlock.getState();
        if (state instanceof TileState tileState) {
            player.sendBlockUpdate(signLoc, tileState);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder(false) instanceof HomeGuiHolder holder)) {
            return;
        }
        // First-line cancel blocks shift-click, hotbar 1-9, offhand F, and double-click collect-to-cursor
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || clickedInv != holder.getInventory()) {
            return;
        }

        int slot = event.getSlot();
        switch (holder.type()) {
            case MAIN_LIST -> handleMainListClick(player, holder, slot);
            case MANAGE_HOPPER -> handleManageHopperClick(player, holder, slot);
            case ICON_PICKER -> handleIconPickerClick(player, holder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof HomeGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        RenameSession session = pendingRenames.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String rawInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            int homeSlot = session.homeSlot();
            if (rawInput.equalsIgnoreCase("cancel")) {
                messageUtil.send(player, MessageKey.RENAME_CANCELLED, Map.of("%slot%", String.valueOf(homeSlot)));
                openManageGui(player, homeSlot, session.returnPage());
                return;
            }
            String sanitized = HomeLocation.sanitizeCustomName(rawInput);
            if (sanitized == null) {
                messageUtil.send(player, MessageKey.RENAME_CANCELLED, Map.of("%slot%", String.valueOf(homeSlot)));
                openManageGui(player, homeSlot, session.returnPage());
                return;
            }
            boolean updated = database.updateHomeName(player.getUniqueId(), homeSlot, sanitized);
            if (updated) {
                messageUtil.send(player, MessageKey.HOME_RENAMED, Map.of(
                    "%slot%", String.valueOf(homeSlot),
                    "%name%", sanitized
                ));
            }
            openManageGui(player, homeSlot, session.returnPage());
        }, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingRenames.remove(uuid);
        pendingSignLocations.remove(uuid);
    }

    private void handleMainListClick(Player player, HomeGuiHolder holder, int clickedSlot) {
        int page = holder.page();
        int maxHomesCap = rankResolver.getMaxHomesCap();
        int maxPages = Math.max(1, (maxHomesCap + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);

        if (clickedSlot == MainBarButton.PREV_PAGE.slot() && page > 0) {
            openMainGui(player, page - 1);
            return;
        }
        if (clickedSlot == MainBarButton.NEXT_PAGE.slot() && page + 1 < maxPages) {
            openMainGui(player, page + 1);
            return;
        }
        if (clickedSlot < 0 || clickedSlot >= ITEMS_PER_PAGE) {
            return;
        }

        int homeSlot = page * ITEMS_PER_PAGE + clickedSlot + 1;
        if (homeSlot > maxHomesCap) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Map<Integer, HomeLocation> homes = database.getHomesView(uuid);
        int rankLimit = rankResolver.resolveMaxHomes(player);

        if (homes.containsKey(homeSlot)) {
            openManageGui(player, homeSlot, page);
            return;
        }

        if (homeSlot > rankLimit) {
            messageUtil.send(player, MessageKey.SLOT_LOCKED, Map.of(
                "%slot%", String.valueOf(homeSlot),
                "%limit%", String.valueOf(rankLimit)
            ));
            return;
        }

        if (homes.size() >= rankLimit) {
            messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of(
                "%slot%", String.valueOf(homeSlot),
                "%limit%", String.valueOf(rankLimit)
            ));
            return;
        }

        HomeLocation newLoc = HomeLocation.fromBukkit(player.getLocation());
        database.setHome(uuid, homeSlot, newLoc);
        player.closeInventory();
        messageUtil.send(player, MessageKey.HOME_SET, Map.of(
            "%slot%", String.valueOf(homeSlot),
            "%limit%", String.valueOf(rankLimit)
        ));
    }

    private void handleManageHopperClick(Player player, HomeGuiHolder holder, int clickedSlot) {
        ManageButton button = ManageButton.fromSlot(clickedSlot);
        if (button == null) {
            return;
        }
        int homeSlot = holder.homeSlot();
        int returnPage = holder.returnPage();

        switch (button) {
            case TELEPORT -> {
                player.closeInventory();
                if (dialogManager != null) {
                    dialogManager.teleportToHome(player, homeSlot);
                }
            }
            case RENAME -> {
                player.closeInventory();
                pendingRenames.put(player.getUniqueId(), new RenameSession(homeSlot, returnPage));
                messageUtil.send(player, MessageKey.RENAME_PROMPT, Map.of("%slot%", String.valueOf(homeSlot)));
            }
            case ICON -> openIconPickerGui(player, homeSlot, 0, returnPage, "");
            case DELETE -> {
                boolean deleted = database.deleteHome(player.getUniqueId(), homeSlot);
                player.closeInventory();
                messageUtil.send(
                    player,
                    deleted ? MessageKey.HOME_DELETED : MessageKey.HOME_NOT_SET,
                    Map.of("%slot%", String.valueOf(homeSlot))
                );
            }
        }
    }

    private void handleIconPickerClick(Player player, HomeGuiHolder holder, int clickedSlot) {
        int homeSlot = holder.homeSlot();
        int iconPage = holder.page();
        int returnPage = holder.returnPage();
        String searchQuery = holder.searchQuery();

        List<Material> icons = filterIcons(searchQuery);
        int totalIcons = icons.size();
        int maxPages = Math.max(1, (totalIcons + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);

        IconBarButton barButton = IconBarButton.fromSlot(clickedSlot);
        if (barButton != null) {
            switch (barButton) {
                case PREV_PAGE -> {
                    if (maxPages > 1) {
                        int targetPage = iconPage > 0 ? iconPage - 1 : maxPages - 1;
                        openIconPickerGui(player, homeSlot, targetPage, returnPage, searchQuery);
                    }
                }
                case BACK -> openManageGui(player, homeSlot, returnPage);
                case CURRENT_ICON -> {
                    // Preview slot only
                }
                case DEFAULT_ICON -> {
                    boolean updated = database.updateHomeIcon(player.getUniqueId(), homeSlot, Material.WHITE_BED.name());
                    if (updated) {
                        messageUtil.send(player, MessageKey.ICON_UPDATED, Map.of(
                            "%slot%", String.valueOf(homeSlot),
                            "%icon%", formatMaterialName(Material.WHITE_BED)
                        ));
                    }
                    openManageGui(player, homeSlot, returnPage);
                }
                case SEARCH -> openSearchSignGui(player, homeSlot, returnPage);
                case NEXT_PAGE -> {
                    if (maxPages > 1) {
                        int targetPage = (iconPage + 1 < maxPages) ? iconPage + 1 : 0;
                        openIconPickerGui(player, homeSlot, targetPage, returnPage, searchQuery);
                    }
                }
            }
            return;
        }

        if (clickedSlot < 0 || clickedSlot >= ITEMS_PER_PAGE) {
            return;
        }
        int idx = iconPage * ITEMS_PER_PAGE + clickedSlot;
        if (idx >= totalIcons) {
            return;
        }
        Material chosen = icons.get(idx);
        boolean updated = database.updateHomeIcon(player.getUniqueId(), homeSlot, chosen.name());
        if (updated) {
            messageUtil.send(player, MessageKey.ICON_UPDATED, Map.of(
                "%slot%", String.valueOf(homeSlot),
                "%icon%", formatMaterialName(chosen)
            ));
        }
        openManageGui(player, homeSlot, returnPage);
    }

    private static ItemStack createGuiItem(Material material, Component displayName, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static IconCatalog getCatalog() {
        if (Bukkit.getServer() != null) {
            IconCatalog cached = cachedServerCatalog;
            if (cached != null) {
                return cached;
            }
            IconCatalog built = buildCatalog(true);
            cachedServerCatalog = built;
            return built;
        }
        IconCatalog headless = cachedHeadlessCatalog;
        if (headless != null) {
            return headless;
        }
        IconCatalog built = buildCatalog(false);
        cachedHeadlessCatalog = built;
        return built;
    }

    @SuppressWarnings("deprecation")
    private static IconCatalog buildCatalog(boolean serverBootstrapped) {
        List<Material> rawMaterials = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isLegacy() || EXCLUDED_MATERIALS.contains(mat) || !isItemMaterialSafe(mat, serverBootstrapped)) {
                continue;
            }
            String name = mat.name();
            if (name.endsWith("_SPAWN_EGG") || name.startsWith("INFESTED_")) {
                continue;
            }
            rawMaterials.add(mat);
        }
        rawMaterials.sort(Comparator.comparing(Material::name));

        List<IconEntry> entries = new ArrayList<>(rawMaterials.size());
        for (Material mat : rawMaterials) {
            String rawName = mat.name();
            String lowerKey = rawName.toLowerCase(Locale.ROOT);
            String titleCase = DISPLAY_NAMES.getOrDefault(mat, computeTitleCase(rawName));
            String lowerDisplay = titleCase.toLowerCase(Locale.ROOT);
            entries.add(new IconEntry(mat, titleCase, lowerKey, lowerDisplay));
        }

        return new IconCatalog(
            Collections.unmodifiableList(rawMaterials),
            Collections.unmodifiableList(entries)
        );
    }

    @SuppressWarnings("deprecation")
    private static EnumMap<Material, String> buildDisplayNameMap() {
        EnumMap<Material, String> map = new EnumMap<>(Material.class);
        for (Material mat : Material.values()) {
            if (!mat.isLegacy()) {
                map.put(mat, computeTitleCase(mat.name()));
            }
        }
        return map;
    }

    private static EnumSet<Material> buildExcludedMaterialEnumSet() {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : EXCLUDED_MATERIAL_NAMES) {
            Material mat = Material.getMaterial(name);
            if (mat != null) {
                set.add(mat);
            }
        }
        return set;
    }

    private static String computeTitleCase(String enumName) {
        int len = enumName.length();
        StringBuilder sb = new StringBuilder(len);
        boolean capitalizeNext = true;
        for (int i = 0; i < len; i++) {
            char c = enumName.charAt(i);
            if (c == '_') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static boolean isItemMaterialSafe(Material mat, boolean serverBootstrapped) {
        if (serverBootstrapped) {
            try {
                return mat.isItem() && !mat.isAir();
            } catch (Throwable ignored) {
                // Fall through to deterministic string filter
            }
        }
        String name = mat.name();
        return !name.equals("AIR")
            && !name.endsWith("_AIR")
            && !name.startsWith("POTTED_")
            && !name.contains("_WALL_");
    }

    private record IconEntry(
        Material material,
        String displayName,
        String lowerKey,
        String lowerDisplay
    ) {
    }

    private record IconCatalog(
        List<Material> materials,
        List<IconEntry> entries
    ) {
    }

    private record StaticGuiItems(
        @Nullable ItemStack newHomePane,
        @Nullable ItemStack lockedPane,
        @Nullable ItemStack centerBed,
        @Nullable ItemStack backPane,
        @Nullable ItemStack defaultBed,
        @Nullable ItemStack searchSign,
        EnumMap<ManageButton, ItemStack> manageButtons
    ) {
        static StaticGuiItems build(MessageUtil msg) {
            ItemStack newHome = buildIfStatic(msg, Material.GRAY_STAINED_GLASS_PANE, GuiTextKey.NEW_HOME_NAME, GuiTextKey.NEW_HOME_LORE);
            ItemStack locked = buildIfStatic(msg, Material.RED_STAINED_GLASS_PANE, GuiTextKey.LOCKED_NAME, GuiTextKey.LOCKED_LORE);
            ItemStack center = buildIfStatic(msg, Material.GRAY_BED, GuiTextKey.CENTER_BED_NAME, GuiTextKey.CENTER_BED_LORE);
            ItemStack back = buildIfStatic(msg, Material.RED_STAINED_GLASS_PANE, GuiTextKey.BACK_NAME, GuiTextKey.BACK_LORE);
            ItemStack defBed = buildIfStatic(msg, Material.WHITE_BED, GuiTextKey.DEFAULT_ICON_NAME, GuiTextKey.DEFAULT_ICON_LORE);
            ItemStack search = buildIfStatic(msg, Material.OAK_SIGN, GuiTextKey.SEARCH_NAME, GuiTextKey.SEARCH_LORE);

            EnumMap<ManageButton, ItemStack> manage = new EnumMap<>(ManageButton.class);
            for (ManageButton btn : ManageButton.values()) {
                if (btn.fixedMaterial() != null) {
                    ItemStack item = buildIfStatic(msg, btn.fixedMaterial(), btn.nameKey(), btn.loreKey());
                    if (item != null) {
                        manage.put(btn, item);
                    }
                }
            }
            return new StaticGuiItems(newHome, locked, center, back, defBed, search, manage);
        }

        private static @Nullable ItemStack buildIfStatic(
            MessageUtil msg,
            Material material,
            GuiTextKey nameKey,
            GuiTextKey loreKey
        ) {
            if (!msg.isStaticGuiEntry(nameKey, loreKey)) {
                return null;
            }
            return createGuiItem(material, msg.getGuiText(nameKey), msg.getGuiLore(loreKey));
        }

        @Nullable ItemStack manageButton(ManageButton btn) {
            return manageButtons.get(btn);
        }
    }

    private record RenameSession(int homeSlot, int returnPage) {
    }

    public static final class HomeGuiHolder implements InventoryHolder {
        private final GuiType type;
        private final int homeSlot;
        private final int page;
        private final int returnPage;
        private final String searchQuery;
        private Inventory inventory;

        public HomeGuiHolder(GuiType type, int homeSlot, int page, int returnPage, String searchQuery) {
            this.type = type;
            this.homeSlot = homeSlot;
            this.page = page;
            this.returnPage = returnPage;
            this.searchQuery = searchQuery == null ? "" : searchQuery;
        }

        public GuiType type() {
            return type;
        }

        public int homeSlot() {
            return homeSlot;
        }

        public int page() {
            return page;
        }

        public int returnPage() {
            return returnPage;
        }

        public String searchQuery() {
            return searchQuery;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
