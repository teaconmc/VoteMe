package org.teacon.voteme.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import mcp.MethodsReturnNonnullByDefault;
import org.teacon.voteme.VoteMe;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
abstract class VoteMeHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
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

    protected abstract HttpResponseStatus handle(QueryStringDecoder decoder, ByteBuf buf);

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
        this.sendMethodNotAllowed(ctx, request);
    }

    private void sendMethodNotAllowed(ChannelHandlerContext ctx, HttpRequest request) {
        String msg = "{\"error\":\"Method Not Allowed\"}";
        HttpResponseStatus status = HttpResponseStatus.METHOD_NOT_ALLOWED;
        ByteBuf buf = Unpooled.wrappedBuffer(msg.getBytes(StandardCharsets.UTF_8));

        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, buf);
        response.headers()
                .set(HttpHeaderNames.ALLOW, "GET, HEAD")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
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
