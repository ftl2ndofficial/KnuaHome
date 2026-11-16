package com.ftl2nd;

import com.ftl2nd.nms.NmsCore;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * High-performance, Enum-indexed message, GUI/Dialog text, and sound manager.
 *
 * Performance characteristics:
 * - Backed by EnumMap for O(1) array-indexed lookups with zero hashing.
 * - Pre-compiles static templates (no '%' placeholders) into immutable Adventure Components at load time.
 * - Uses single-pass placeholder replacement with MiniMessage tag escaping to prevent tag/click-event injection
 *   and recursive placeholder expansion.
 * - Pre-builds Adventure Sound instances at load time for zero-allocation playback.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();
    private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<String, Key> BUKKIT_SOUND_KEYS = buildBukkitSoundKeyMap();
    private static final List<String> DEFAULT_SIGN_SEARCH_LINES = List.of(
        "",
        "↑↑↑↑↑↑↑↑↑↑↑",
        "Search",
        ""
    );

    private final @Nullable JavaPlugin plugin;
    private final @Nullable NmsCore nmsCore;
    private volatile Snapshot snapshot;

    public MessageUtil(JavaPlugin plugin, @Nullable NmsCore nmsCore) {
        this.plugin = plugin;
        this.nmsCore = nmsCore;
        reload();
    }

    public MessageUtil(@Nullable FileConfiguration messagesConfig, @Nullable NmsCore nmsCore) {
        this.plugin = null;
        this.nmsCore = nmsCore;
        this.snapshot = parseConfig(messagesConfig);
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.snapshot = parseConfig(config);
    }

    public Component getDialogText(DialogTextKey key) {
        return getDialogText(key, Collections.emptyMap());
    }

    public Component getDialogText(DialogTextKey key, Map<String, String> placeholders) {
        CompiledTemplate compiled = snapshot.dialogTexts.get(key);
        return compiled != null ? compiled.render(null, placeholders) : Component.empty();
    }

    public Component getGuiText(GuiTextKey key) {
        return getGuiText(key, Collections.emptyMap());
    }

    public Component getGuiText(GuiTextKey key, Map<String, String> placeholders) {
        CompiledTemplate compiled = snapshot.guiTexts.get(key);
        return compiled != null ? compiled.render(null, placeholders) : Component.empty();
    }

    public List<Component> getGuiLore(GuiTextKey key) {
        return getGuiLore(key, Collections.emptyMap());
    }

    public List<Component> getGuiLore(GuiTextKey key, Map<String, String> placeholders) {
        CompiledTemplate compiled = snapshot.guiTexts.get(key);
        return compiled != null ? compiled.renderLore(placeholders) : Collections.emptyList();
    }

    public boolean isStaticDialogEntry(DialogTextKey labelKey, DialogTextKey tooltipKey) {
        Snapshot current = this.snapshot;
        CompiledTemplate labelTpl = current.dialogTexts.get(labelKey);
        CompiledTemplate tooltipTpl = current.dialogTexts.get(tooltipKey);
        return (labelTpl == null || !labelTpl.isDynamic()) && (tooltipTpl == null || !tooltipTpl.isDynamic());
    }

    public boolean isStaticGuiEntry(GuiTextKey nameKey, GuiTextKey loreKey) {
        Snapshot current = this.snapshot;
        CompiledTemplate nameTpl = current.guiTexts.get(nameKey);
        CompiledTemplate loreTpl = current.guiTexts.get(loreKey);
        return (nameTpl == null || !nameTpl.isDynamic()) && (loreTpl == null || !loreTpl.isDynamic());
    }

    /**
     * Always returns an immutable 4-element list of Components as required by CraftPlayer#sendSignChange.
     */
    public List<Component> getSignSearchLines() {
        return snapshot.signSearchLines;
    }

    public Component getMessageComponent(@Nullable Player player, MessageKey key, Map<String, String> placeholders) {
        CompiledTemplate compiled = snapshot.messages.get(key);
        return compiled != null ? compiled.render(player, placeholders) : Component.empty();
    }

    public void send(CommandSender sender, MessageKey key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            send(player, key, placeholders, key.defaultSound());
        } else {
            Component comp = getMessageComponent(null, key, placeholders);
            if (!Component.empty().equals(comp)) {
                sender.sendMessage(comp);
            }
        }
    }

    public void send(Player player, MessageKey key) {
        send(player, key, Collections.emptyMap(), key.defaultSound());
    }

    public void send(Player player, MessageKey key, Map<String, String> placeholders) {
        send(player, key, placeholders, key.defaultSound());
    }

    public void send(
        @Nullable Player player,
        MessageKey key,
        Map<String, String> placeholders,
        @Nullable SoundKey soundOverride
    ) {
        if (player == null) {
            return;
        }
        Snapshot current = this.snapshot;
        CompiledTemplate compiled = current.messages.get(key);
        if (compiled != null && !compiled.isEmpty()) {
            Component component = compiled.render(player, placeholders);
            if (current.sendChat) {
                if (nmsCore != null) {
                    nmsCore.sendChat(player, component);
                } else {
                    player.sendMessage(component);
                }
            }
            if (current.sendActionBar) {
                if (nmsCore != null) {
                    nmsCore.sendActionBar(player, component);
                } else {
                    player.sendActionBar(component);
                }
            }
        }
        if (soundOverride != null) {
            playSound(player, soundOverride);
        }
    }

    public void playSound(@Nullable Player player, @Nullable SoundKey soundKey) {
        if (player == null || soundKey == null) {
            return;
        }
        Sound adventureSound = this.snapshot.activeSounds.get(soundKey);
        if (adventureSound != null) {
            player.playSound(adventureSound);
        }
    }

    public static Component parseComponent(
        @Nullable Player player,
        @Nullable String input,
        @Nullable Map<String, String> placeholders
    ) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String normalized = convertLegacyToMiniMessage(input);
        String substituted = applyPlaceholdersSinglePass(normalized, placeholders);
        if (player != null && substituted.indexOf('%') >= 0 && isPlaceholderApiEnabled()) {
            substituted = PlaceholderApiBridge.apply(player, substituted);
        }
        return deserializeNonItalic(substituted);
    }

    /**
     * Single-pass placeholder substitution:
     * - Prevents recursive expansion when a replacement value itself contains "%...%".
     * - Escapes MiniMessage tags in replacement values to prevent player input tag injection.
     */
    public static String applyPlaceholdersSinglePass(
        String template,
        @Nullable Map<String, String> placeholders
    ) {
        if (placeholders == null || placeholders.isEmpty() || template.indexOf('%') < 0) {
            return template;
        }
        int len = template.length();
        StringBuilder sb = null;
        int cursor = 0;

        while (cursor < len) {
            int openPct = template.indexOf('%', cursor);
            if (openPct < 0 || openPct + 1 >= len) {
                break;
            }
            int closePct = template.indexOf('%', openPct + 1);
            if (closePct < 0) {
                break;
            }
            String token = template.substring(openPct, closePct + 1);
            String replacement = placeholders.get(token);
            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(len + 16);
                }
                sb.append(template, cursor, openPct);
                sb.append(escapeMiniMessageIfNeeded(replacement));
                cursor = closePct + 1;
            } else {
                if (sb == null) {
                    sb = new StringBuilder(len + 16);
                }
                sb.append(template, cursor, closePct);
                cursor = closePct;
            }
        }

        if (sb == null) {
            return template;
        }
        if (cursor < len) {
            sb.append(template, cursor, len);
        }
        return sb.toString();
    }

    private static String escapeMiniMessageIfNeeded(String value) {
        if (value.indexOf('<') < 0 && value.indexOf('>') < 0) {
            return value;
        }
        return MINI_MESSAGE.escapeTags(value);
    }

    private static Component deserializeNonItalic(String miniMessageText) {
        if (miniMessageText.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(miniMessageText)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String convertLegacyToMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }
        String normalized = input.replace('§', '&');
        if (normalized.contains("&#")) {
            Matcher matcher = HEX_LEGACY_PATTERN.matcher(normalized);
            StringBuilder sb = new StringBuilder(normalized.length() + 8);
            while (matcher.find()) {
                matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
            }
            matcher.appendTail(sb);
            normalized = sb.toString();
        }
        if (normalized.indexOf('&') >= 0) {
            Component legacyComp = LEGACY_AMPERSAND.deserialize(normalized);
            return MINI_MESSAGE.serialize(legacyComp);
        }
        return normalized;
    }

    private static Snapshot parseConfig(@Nullable FileConfiguration config) {
        boolean sendChat = config == null || config.getBoolean("settings.send-chat", true);
        boolean sendActionBar = config == null || config.getBoolean("settings.send-actionbar", true);

        ConfigurationSection dialogSec = config != null ? config.getConfigurationSection("dialog") : null;
        EnumMap<DialogTextKey, CompiledTemplate> dialogTexts = new EnumMap<>(DialogTextKey.class);
        for (DialogTextKey key : DialogTextKey.values()) {
            String raw = readScalar(dialogSec, key.configKey(), key.defaultTemplate());
            dialogTexts.put(key, CompiledTemplate.compile(raw));
        }

        ConfigurationSection guiSec = config != null ? config.getConfigurationSection("gui") : null;
        EnumMap<GuiTextKey, CompiledTemplate> guiTexts = new EnumMap<>(GuiTextKey.class);
        for (GuiTextKey key : GuiTextKey.values()) {
            String raw = readScalar(guiSec, key.configKey(), key.defaultTemplate());
            guiTexts.put(key, CompiledTemplate.compile(raw));
        }

        List<String> rawSignLines = DEFAULT_SIGN_SEARCH_LINES;
        if (guiSec != null && guiSec.isList("sign-search-lines")) {
            rawSignLines = guiSec.getStringList("sign-search-lines");
        }
        List<Component> signSearchComponents = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            String line = (i < rawSignLines.size() && rawSignLines.get(i) != null) ? rawSignLines.get(i) : "";
            signSearchComponents.add(deserializeNonItalic(convertLegacyToMiniMessage(line)));
        }

        ConfigurationSection msgSec = config != null ? config.getConfigurationSection("messages") : null;
        EnumMap<MessageKey, CompiledTemplate> messages = new EnumMap<>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            String raw = readScalar(msgSec, key.configKey(), key.defaultTemplate());
            messages.put(key, CompiledTemplate.compile(raw));
        }

        ConfigurationSection soundSec = config != null ? config.getConfigurationSection("sounds") : null;
        EnumMap<SoundKey, Sound> activeSounds = new EnumMap<>(SoundKey.class);
        for (SoundKey key : SoundKey.values()) {
            boolean enabled = key.defaultEnabled();
            String soundName = key.defaultSound();
            float volume = key.defaultVolume();
            float pitch = key.defaultPitch();

            if (soundSec != null) {
                ConfigurationSection sub = soundSec.getConfigurationSection(key.configKey());
                if (sub != null) {
                    enabled = sub.getBoolean("enabled", enabled);
                    soundName = sub.getString("sound", soundName);
                    volume = Math.max(0.0f, (float) sub.getDouble("volume", volume));
                    pitch = Math.clamp((float) sub.getDouble("pitch", pitch), 0.0f, 2.0f);
                }
            }
            if (enabled) {
                Key resolvedKey = resolveSoundKey(soundName);
                if (resolvedKey != null) {
                    activeSounds.put(key, Sound.sound(resolvedKey, Sound.Source.MASTER, volume, pitch));
                }
            }
        }

        return new Snapshot(
            sendChat,
            sendActionBar,
            dialogTexts,
            guiTexts,
            Collections.unmodifiableList(signSearchComponents),
            messages,
            activeSounds
        );
    }

    private static String readScalar(
        @Nullable ConfigurationSection section,
        String key,
        String defaultValue
    ) {
        if (section == null || section.isList(key) || section.isConfigurationSection(key)) {
            return defaultValue;
        }
        String val = section.getString(key);
        return val != null ? val : defaultValue;
    }

    public static @Nullable Key resolveSoundKey(@Nullable String rawSound) {
        if (rawSound == null || rawSound.isBlank()) {
            return null;
        }
        String trimmed = rawSound.trim();
        Key mapped = BUKKIT_SOUND_KEYS.get(trimmed.toUpperCase(Locale.ROOT));
        if (mapped != null) {
            return mapped;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int colonIdx = lower.indexOf(':');
        if (colonIdx > 0 && colonIdx + 1 < lower.length()) {
            String ns = lower.substring(0, colonIdx);
            String path = lower.substring(colonIdx + 1).replace('_', '.');
            if (Key.parseableNamespace(ns) && Key.parseableValue(path)) {
                return Key.key(ns, path);
            }
        }
        String path = lower.replace('_', '.');
        if (Key.parseableValue(path)) {
            return Key.key("minecraft", path);
        }
        return Key.key("minecraft", "entity.villager.no");
    }

    private static Map<String, Key> buildBukkitSoundKeyMap() {
        Map<String, Key> map = new HashMap<>();
        try {
            for (Field field : org.bukkit.Sound.class.getFields()) {
                if (org.bukkit.Sound.class.isAssignableFrom(field.getType())) {
                    Object val = field.get(null);
                    if (val instanceof org.bukkit.Keyed keyed) {
                        map.put(field.getName().toUpperCase(Locale.ROOT), keyed.key());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Collections.unmodifiableMap(map);
    }

    private static boolean isPlaceholderApiEnabled() {
        try {
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            return papi != null && papi.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class PlaceholderApiBridge {
        static String apply(Player player, String text) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {
                return text;
            }
        }
    }

    /**
     * Pre-compiled MiniMessage template with O(1) zero-allocation fast-path for static strings/lore.
     */
    private static final class CompiledTemplate {
        private final String normalizedTemplate;
        private final String[] normalizedLines;
        private final boolean dynamic;
        private final Component staticComponent;
        private final List<Component> staticLore;

        private CompiledTemplate(
            String normalizedTemplate,
            String[] normalizedLines,
            boolean dynamic,
            Component staticComponent,
            List<Component> staticLore
        ) {
            this.normalizedTemplate = normalizedTemplate;
            this.normalizedLines = normalizedLines;
            this.dynamic = dynamic;
            this.staticComponent = staticComponent;
            this.staticLore = staticLore;
        }

        static CompiledTemplate compile(@Nullable String raw) {
            if (raw == null || raw.isEmpty()) {
                return new CompiledTemplate("", new String[0], false, Component.empty(), Collections.emptyList());
            }
            String[] rawSplit = raw.split("\\r?\\n", -1);
            String[] normLines = new String[rawSplit.length];
            for (int i = 0; i < rawSplit.length; i++) {
                normLines[i] = convertLegacyToMiniMessage(rawSplit[i]);
            }
            String normFull = rawSplit.length == 1
                ? normLines[0]
                : String.join("\n", normLines);
            boolean hasPlaceholders = normFull.indexOf('%') >= 0;

            if (!hasPlaceholders) {
                Component comp = deserializeNonItalic(normFull);
                List<Component> loreList = new ArrayList<>(normLines.length);
                for (String line : normLines) {
                    loreList.add(deserializeNonItalic(line));
                }
                return new CompiledTemplate(normFull, normLines, false, comp, Collections.unmodifiableList(loreList));
            }
            return new CompiledTemplate(normFull, normLines, true, Component.empty(), Collections.emptyList());
        }

        boolean isEmpty() {
            return normalizedTemplate.isEmpty();
        }

        boolean isDynamic() {
            return dynamic;
        }

        Component render(@Nullable Player player, @Nullable Map<String, String> placeholders) {
            if (!dynamic) {
                return staticComponent;
            }
            String text = applyPlaceholdersSinglePass(normalizedTemplate, placeholders);
            if (player != null && text.indexOf('%') >= 0 && isPlaceholderApiEnabled()) {
                text = PlaceholderApiBridge.apply(player, text);
            }
            return deserializeNonItalic(text);
        }

        List<Component> renderLore(@Nullable Map<String, String> placeholders) {
            if (normalizedLines.length == 0) {
                return Collections.emptyList();
            }
            if (!dynamic) {
                return staticLore;
            }
            List<Component> lore = new ArrayList<>(normalizedLines.length);
            for (String line : normalizedLines) {
                String substituted = applyPlaceholdersSinglePass(line, placeholders);
                lore.add(deserializeNonItalic(substituted));
            }
            return lore;
        }
    }

    private record Snapshot(
        boolean sendChat,
        boolean sendActionBar,
        EnumMap<DialogTextKey, CompiledTemplate> dialogTexts,
        EnumMap<GuiTextKey, CompiledTemplate> guiTexts,
        List<Component> signSearchLines,
        EnumMap<MessageKey, CompiledTemplate> messages,
        EnumMap<SoundKey, Sound> activeSounds
    ) {
    }
}
