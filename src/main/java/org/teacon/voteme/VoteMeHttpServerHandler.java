package org.teacon.voteme;

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
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;

import java.util.Map;

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
        ByteBufUtil.writeUtf8(buf, "{\"error\":\"Not Found\"}");
        return HttpResponseStatus.NOT_FOUND;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        VoteMe.LOGGER.error("Exception caught when handling requests.", cause);
        ctx.close();
    }
}
