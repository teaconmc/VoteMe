package org.teacon.voteme.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.teacon.voteme.VoteMe;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteMeHttpServer {
    private static final int MAX_CONTENT_LENGTH = 2097152;
    private static final CorsConfig CORS_CONFIG = CorsConfigBuilder.forAnyOrigin().allowNullOrigin().allowCredentials().build();

    @Nullable
    private static ChannelFuture future = null;

    public static MinecraftServer getMinecraftServer() {
        return Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer());
    }

    @SubscribeEvent
    public static void start(ServerStartingEvent event) {
        try {
            int port = VoteMe.CONFIG.HTTP_SERVER_PORT.get();
            if (port > 0) {
                VoteMe.LOGGER.info("Starting the vote server on port {} ...", port);
                ServerBootstrap bootstrap = new ServerBootstrap().group(Connection.NETWORK_WORKER_GROUP.get());
                future = bootstrap.channel(NioServerSocketChannel.class).childHandler(new Handler()).bind(port).sync();
                VoteMe.LOGGER.info("Successfully started the vote server on port {}.", port);
            } else {
                VoteMe.LOGGER.info("Skipping the initialization of vote server ...");
                future = null;
            }
        } catch (Exception e) {
            VoteMe.LOGGER.error("Failed to start the vote server.", e);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        try {
            if (future != null) {
                VoteMe.LOGGER.info("Stopping the vote server ...");
                future.channel().close().sync();
                VoteMe.LOGGER.info("Successfully stopped the vote server.");
            }
        } catch (Exception e) {
            VoteMe.LOGGER.error("Failed to stop the vote server.", e);
        }
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private static final class Handler extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpResponseEncoder());
            pipeline.addLast(new HttpRequestDecoder());
            pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
            pipeline.addLast(new CorsHandler(CORS_CONFIG));
            pipeline.addLast(new VoteMeHttpServerHandlerImpl());
        }
    }
}
