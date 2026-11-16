package com.ftl2nd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles /home, /sethome, /delhome, and /knuahome reload using CommandType enum dispatch (Folia-safe).
 */
public final class HomeCommand implements CommandExecutor, TabCompleter {

    public enum CommandType {
        HOME("home", true),
        SETHOME("sethome", true),
        DELHOME("delhome", true),
        ADMIN("knuahome", false);

        private static final Map<String, CommandType> BY_NAME;

        static {
            Map<String, CommandType> map = new HashMap<>(values().length * 2);
            for (CommandType type : values()) {
                map.put(type.commandName, type);
            }
            BY_NAME = Collections.unmodifiableMap(map);
        }

        private final String commandName;
        private final boolean playerOnly;

        CommandType(String commandName, boolean playerOnly) {
            this.commandName = commandName;
            this.playerOnly = playerOnly;
        }

        public String commandName() {
            return commandName;
        }

        public boolean isPlayerOnly() {
            return playerOnly;
        }

        public static CommandType fromName(String name) {
            if (name == null) {
                return HOME;
            }
            return BY_NAME.getOrDefault(name.toLowerCase(Locale.ROOT), HOME);
        }
    }

    private final KnuaHomePlugin plugin;
    private final HomeDatabase database;
    private final RankLimitResolver rankResolver;
    private final HomeDialogManager dialogManager;
    private final MessageUtil messageUtil;

    public HomeCommand(
        KnuaHomePlugin plugin,
        HomeDatabase database,
        RankLimitResolver rankResolver,
        HomeDialogManager dialogManager,
        MessageUtil messageUtil
    ) {
        this.plugin = plugin;
        this.database = database;
        this.rankResolver = rankResolver;
        this.dialogManager = dialogManager;
        this.messageUtil = messageUtil;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandType type = CommandType.fromName(command.getName());
        if (type == CommandType.ADMIN) {
            return handleAdminCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            messageUtil.send(sender, MessageKey.PLAYERS_ONLY);
            return true;
        }

        switch (type) {
            case HOME -> handleHome(player, args);
            case SETHOME -> handleSetHome(player, args);
            case DELHOME -> handleDelHome(player, args);
            default -> handleHome(player, args);
        }
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("knuahome.admin")) {
            messageUtil.send(sender, MessageKey.NO_PERMISSION);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadKnuaHomeConfig();
            messageUtil.send(sender, MessageKey.RELOADED, Map.of(
                "%max%", String.valueOf(rankResolver.getMaxHomesCap()),
                "%ranks%", String.valueOf(rankResolver.getRanks().size())
            ));
            return true;
        }
        messageUtil.send(sender, MessageKey.ADMIN_USAGE);
        return true;
    }

    private void handleHome(Player player, String[] args) {
        if (args.length == 0) {
            dialogManager.openHomeDialog(player);
            return;
        }
        int maxCap = rankResolver.getMaxHomesCap();
        int slot = parseSlot(args[0], maxCap);
        if (slot <= 0) {
            messageUtil.send(player, MessageKey.INVALID_SLOT, Map.of("%max%", String.valueOf(maxCap)));
            return;
        }
        dialogManager.teleportToHome(player, slot);
    }

    private void handleSetHome(Player player, String[] args) {
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            UUID uuid = player.getUniqueId();
            int maxHomes = rankResolver.resolveMaxHomes(player);
            int maxCap = rankResolver.getMaxHomesCap();
            Map<Integer, HomeLocation> homes = database.getHomesView(uuid);

            int targetSlot;
            if (args.length == 0) {
                if (homes.size() >= maxHomes) {
                    messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of("%limit%", String.valueOf(maxHomes)));
                    return;
                }
                targetSlot = -1;
                for (int i = 1; i <= maxHomes; i++) {
                    if (!homes.containsKey(i)) {
                        targetSlot = i;
                        break;
                    }
                }
                if (targetSlot <= 0) {
                    messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of("%limit%", String.valueOf(maxHomes)));
                    return;
                }
            } else {
                targetSlot = parseSlot(args[0], maxCap);
                if (targetSlot <= 0) {
                    messageUtil.send(player, MessageKey.INVALID_SLOT, Map.of("%max%", String.valueOf(maxCap)));
                    return;
                }
                if (targetSlot > maxHomes) {
                    messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of(
                        "%slot%", String.valueOf(targetSlot),
                        "%limit%", String.valueOf(maxHomes)
                    ));
                    return;
                }
                if (homes.containsKey(targetSlot)) {
                    messageUtil.send(player, MessageKey.ALREADY_SET, Map.of("%slot%", String.valueOf(targetSlot)));
                    return;
                }
                if (homes.size() >= maxHomes) {
                    messageUtil.send(player, MessageKey.LIMIT_REACHED, Map.of("%limit%", String.valueOf(maxHomes)));
                    return;
                }
            }

            HomeLocation homeLoc = HomeLocation.fromBukkit(player.getLocation());
            database.setHome(uuid, targetSlot, homeLoc);
            messageUtil.send(player, MessageKey.HOME_SET, Map.of(
                "%slot%", String.valueOf(targetSlot),
                "%limit%", String.valueOf(maxHomes)
            ));
        }, null);
    }

    private void handleDelHome(Player player, String[] args) {
        int maxCap = rankResolver.getMaxHomesCap();
        int targetSlot = 1;
        if (args.length > 0) {
            targetSlot = parseSlot(args[0], maxCap);
            if (targetSlot <= 0) {
                messageUtil.send(player, MessageKey.INVALID_SLOT, Map.of("%max%", String.valueOf(maxCap)));
                return;
            }
        }

        boolean removed = database.deleteHome(player.getUniqueId(), targetSlot);
        messageUtil.send(
            player,
            removed ? MessageKey.HOME_DELETED : MessageKey.HOME_NOT_SET,
            Map.of("%slot%", String.valueOf(targetSlot))
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CommandType type = CommandType.fromName(command.getName());
        if (type == CommandType.ADMIN) {
            if (args.length == 1 && sender.hasPermission("knuahome.admin")) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                if ("reload".startsWith(prefix)) {
                    return List.of("reload");
                }
            }
            return Collections.emptyList();
        }

        if (!(sender instanceof Player player) || args.length != 1) {
            return Collections.emptyList();
        }

        String prefix = args[0].trim();
        UUID uuid = player.getUniqueId();

        return switch (type) {
            case HOME, DELHOME -> {
                List<Integer> setSlots = database.getSortedSlots(uuid);
                if (setSlots.isEmpty()) {
                    yield Collections.emptyList();
                }
                List<String> suggestions = new ArrayList<>(setSlots.size());
                for (Integer slot : setSlots) {
                    String s = String.valueOf(slot);
                    if (s.startsWith(prefix)) {
                        suggestions.add(s);
                    }
                }
                yield suggestions;
            }
            case SETHOME -> {
                Map<Integer, HomeLocation> homes = database.getHomesView(uuid);
                int maxHomes = rankResolver.resolveMaxHomes(player);
                List<String> suggestions = new ArrayList<>();
                for (int i = 1; i <= maxHomes; i++) {
                    if (!homes.containsKey(i)) {
                        String s = String.valueOf(i);
                        if (s.startsWith(prefix)) {
                            suggestions.add(s);
                        }
                    }
                }
                yield suggestions;
            }
            default -> Collections.emptyList();
        };
    }

    static int parseSlot(String raw, int maxCap) {
        if (raw == null || maxCap < 1) {
            return -1;
        }
        int val = RankLimitResolver.parseBoundedNonNegativeInt(raw, 0, maxCap + 1);
        return (val >= 1 && val <= maxCap) ? val : -1;
    }
}
