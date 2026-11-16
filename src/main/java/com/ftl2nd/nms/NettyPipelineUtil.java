package com.ftl2nd.nms;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Shared Netty pipeline utility across all NMS versions (1.21 - 26.3).
 * Avoids pipeline remove/add churn by updating the active callback in-place when already injected.
 */
public final class NettyPipelineUtil {

    private static final String PACKET_HANDLER = "packet_handler";

    private NettyPipelineUtil() {
    }

    /**
     * Installs or updates the persistent Dialog custom-click packet interceptor on the player's channel.
     */
    public static void installOrUpdateClickInterceptor(
        @Nullable Channel channel,
        Player player,
        Function<Object, @Nullable String> actionPathExtractor,
        java.util.function.BiConsumer<Player, String> clickCallback
    ) {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        ChannelHandler existing = pipeline.get(NmsCore.NETTY_HANDLER_NAME);
        if (existing instanceof ReusableClickInterceptor reusable) {
            reusable.update(player, clickCallback);
            return;
        }
        if (existing != null) {
            pipeline.remove(NmsCore.NETTY_HANDLER_NAME);
        }
        ReusableClickInterceptor interceptor = new ReusableClickInterceptor(player, actionPathExtractor, clickCallback);
        addBeforePacketHandler(pipeline, NmsCore.NETTY_HANDLER_NAME, interceptor);
    }

    /**
     * Installs a one-shot Sign update packet interceptor on the player's channel.
     */
    public static void installOneShotSignInterceptor(
        @Nullable Channel channel,
        Player player,
        Predicate<Object> packetMatcher,
        Function<Object, String[]> linesExtractor,
        java.util.function.BiConsumer<Player, String[]> signCallback
    ) {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(NmsCore.NETTY_SIGN_HANDLER_NAME) != null) {
            pipeline.remove(NmsCore.NETTY_SIGN_HANDLER_NAME);
        }
        OneShotSignInterceptor interceptor = new OneShotSignInterceptor(
            player,
            packetMatcher,
            linesExtractor,
            signCallback
        );
        addBeforePacketHandler(pipeline, NmsCore.NETTY_SIGN_HANDLER_NAME, interceptor);
    }

    public static void uninjectAll(@Nullable Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        removeQuietly(pipeline, NmsCore.NETTY_HANDLER_NAME);
        removeQuietly(pipeline, NmsCore.NETTY_SIGN_HANDLER_NAME);
    }

    private static void addBeforePacketHandler(ChannelPipeline pipeline, String name, ChannelHandler handler) {
        if (pipeline.get(PACKET_HANDLER) != null) {
            pipeline.addBefore(PACKET_HANDLER, name, handler);
        } else {
            pipeline.addLast(name, handler);
        }
    }

    private static void removeQuietly(ChannelPipeline pipeline, String name) {
        try {
            if (pipeline.get(name) != null) {
                pipeline.remove(name);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class ReusableClickInterceptor extends ChannelDuplexHandler {
        private final Function<Object, @Nullable String> extractor;
        private volatile Player player;
        private volatile java.util.function.BiConsumer<Player, String> callback;

        ReusableClickInterceptor(
            Player player,
            Function<Object, @Nullable String> extractor,
            java.util.function.BiConsumer<Player, String> callback
        ) {
            this.player = player;
            this.extractor = extractor;
            this.callback = callback;
        }

        void update(Player newPlayer, java.util.function.BiConsumer<Player, String> newCallback) {
            this.player = newPlayer;
            this.callback = newCallback;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String path = extractor.apply(msg);
            if (path != null) {
                callback.accept(player, path);
                return;
            }
            super.channelRead(ctx, msg);
        }
    }

    private static final class OneShotSignInterceptor extends ChannelDuplexHandler {
        private final Player player;
        private final Predicate<Object> matcher;
        private final Function<Object, String[]> linesExtractor;
        private final java.util.function.BiConsumer<Player, String[]> callback;

        OneShotSignInterceptor(
            Player player,
            Predicate<Object> matcher,
            Function<Object, String[]> linesExtractor,
            java.util.function.BiConsumer<Player, String[]> callback
        ) {
            this.player = player;
            this.matcher = matcher;
            this.linesExtractor = linesExtractor;
            this.callback = callback;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (matcher.test(msg)) {
                removeQuietly(ctx.pipeline(), NmsCore.NETTY_SIGN_HANDLER_NAME);
                callback.accept(player, linesExtractor.apply(msg));
                return;
            }
            super.channelRead(ctx, msg);
        }
    }
}
