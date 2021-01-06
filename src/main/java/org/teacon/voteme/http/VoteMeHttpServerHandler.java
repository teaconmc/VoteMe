package org.teacon.voteme.http;

import com.google.common.primitives.Ints;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.ResourceLocation;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class VoteMeHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            VoteMeHttpServer.getMinecraftServer().runAsync(() -> {
                ByteBuf buf = Unpooled.buffer();
                HttpResponseStatus status = this.handle(decoder, buf);
                this.sendResponse(ctx, request, buf, status);
            }).exceptionally(cause -> {
                this.sendInternalServerError(ctx, request, cause);
                return null;
            });
        }
    }

    private HttpResponseStatus handle(QueryStringDecoder decoder, ByteBuf buf) {
        String path = decoder.path();
        if ("/v1/categories".equals(path)) {
            JsonArray result = new JsonArray();
            for (Map.Entry<ResourceLocation, VoteCategory> entry : VoteCategoryHandler.getCategoryMap().entrySet()) {
                JsonObject child = new JsonObject();
                child.addProperty("id", entry.getKey().toString());
                entry.getValue().toJson(child);
                result.add(child);
            }
            ByteBufUtil.writeUtf8(buf, result.toString());
            return HttpResponseStatus.OK;
        }
        if (path.startsWith("/v1/category/")) {
            String id = path.substring("/v1/category/".length());
            VoteCategory category = VoteCategoryHandler.getCategoryMap().get(new ResourceLocation(id));
            if (category != null) {
                JsonObject result = new JsonObject();
                result.addProperty("id", id);
                category.toJson(result);
                ByteBufUtil.writeUtf8(buf, result.toString());
                return HttpResponseStatus.OK;
            }
        }
        if ("/v1/vote_lists".equals(path)) {
            JsonArray result = new JsonArray();
            VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
            handler.getIds().forEach(id -> {
                Optional<VoteListEntry> entryOptional = handler.getEntry(id);
                entryOptional.ifPresent(entry -> {
                    JsonObject child = new JsonObject();
                    child.addProperty("id", id);
                    entry.toJson(child);
                    result.add(child);
                });
            });
            ByteBufUtil.writeUtf8(buf, result.toString());
            return HttpResponseStatus.OK;
        }
        if (path.startsWith("/v1/vote_list/")) {
            // noinspection UnstableApiUsage
            Integer id = Ints.tryParse(path.substring("/v1/vote_list/".length()));
            if (id != null) {
                VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
                Optional<VoteListEntry> entryOptional = handler.getEntry(id);
                if (entryOptional.isPresent()) {
                    VoteListEntry entry = entryOptional.get();
                    JsonObject result = new JsonObject();
                    result.addProperty("id", id);
                    entry.toJson(result);
                    ByteBufUtil.writeUtf8(buf, result.toString());
                    return HttpResponseStatus.OK;
                }
            }
        }
        ByteBufUtil.writeUtf8(buf, "{\"error\":\"Not Found\"}");
        return HttpResponseStatus.NOT_FOUND;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        this.sendInternalServerError(ctx, request, cause);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpRequest request, ByteBuf buf, HttpResponseStatus status) {
        if (HttpMethod.HEAD.equals(request.method())) {
            FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status);
            this.sendFinalResponse(ctx, request, response);
            return;
        }
        if (HttpMethod.GET.equals(request.method())) {
            FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, buf);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            this.sendFinalResponse(ctx, request, response);
            return;
        }
        this.sendBadRequestError(ctx, request);
    }

    private void sendBadRequestError(ChannelHandlerContext ctx, HttpRequest request) {
        String msg = "{\"error\":\"Bad Request\"}";
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        ByteBuf buf = Unpooled.wrappedBuffer(msg.getBytes(StandardCharsets.UTF_8));

        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, buf);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        this.sendFinalResponse(ctx, request, response);
    }

    private void sendInternalServerError(ChannelHandlerContext ctx, HttpRequest request, Throwable cause) {
        try {
            String msg = "{\"error\":\"Internal Server Error\"}";
            HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            ByteBuf buf = Unpooled.wrappedBuffer(msg.getBytes(StandardCharsets.UTF_8));

            FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, buf);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            this.sendFinalResponse(ctx, request, response);
        } finally {
            VoteMe.LOGGER.error("Internal server error was thrown when handling the request.", cause);
        }
    }

    private void sendFinalResponse(ChannelHandlerContext ctx, HttpRequest request, FullHttpResponse response) {
        if (HttpUtil.isKeepAlive(request)) {
            response.headers()
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
            ctx.writeAndFlush(response, ctx.voidPromise());
        } else {
            response.headers()
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
