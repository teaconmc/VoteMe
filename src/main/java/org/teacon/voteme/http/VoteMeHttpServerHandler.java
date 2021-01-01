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
import net.minecraft.util.ResourceLocation;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

final class VoteMeHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            ByteBuf buf = Unpooled.buffer();
            HttpRequest req = (HttpRequest) msg;
            HttpResponseStatus status = this.handle(new QueryStringDecoder(req.uri()), buf);

            FullHttpResponse response = new DefaultFullHttpResponse(req.protocolVersion(), status, buf);
            response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            if (HttpUtil.isKeepAlive(req)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                ctx.write(response);
            } else {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            }
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
                Optional<VoteList.Entry> entryOptional = handler.getEntry(id);
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
                Optional<VoteList.Entry> entryOptional = handler.getEntry(id);
                if (entryOptional.isPresent()) {
                    VoteList.Entry entry = entryOptional.get();
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
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            String msg = "{\"error\":\"Internal Server Error\"}";
            ByteBuf buf = Unpooled.wrappedBuffer(msg.getBytes(StandardCharsets.UTF_8));

            HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);

            response.headers()
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        } finally {
            VoteMe.LOGGER.error("Internal server error was thrown when handling the request.", cause);
        }
    }
}
