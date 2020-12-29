package org.teacon.voteme;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

final class VoteMeHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    public static final byte[] DATA = "{\"value\":\"Hello World!\"}".getBytes(StandardCharsets.UTF_8);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            FullHttpResponse response = new DefaultFullHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.OK, Unpooled.wrappedBuffer(DATA));

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
