package ru.gravit.launcher.request.websockets;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import ru.gravit.utils.helper.LogHelper;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URI;

public abstract class ClientJSONPoint {

    private final URI uri;
    protected Channel ch;
    private static final EventLoopGroup group = new NioEventLoopGroup();
    protected WebSocketClientHandler webSocketClientHandler;
    protected Bootstrap bootstrap = new Bootstrap();
    public boolean isClosed;

    public ClientJSONPoint(final String uri) throws SSLException {
        this(URI.create(uri));
    }

    public ClientJSONPoint(URI uri) throws SSLException {
        this.uri = uri;
        String protocol = uri.getScheme();
        if (!"ws".equals(protocol) && !"wss".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        boolean ssl = false;
        if("wss".equals(protocol))
        {
            ssl = true;
        }
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient().build();
        } else sslCtx = null;
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslCtx != null) {
                            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
                        }
                        pipeline.addLast("http-codec", new HttpClientCodec());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                        pipeline.addLast("ws-handler", webSocketClientHandler);
                    }
        });
    }

    public void open() throws Exception {
        //System.out.println("WebSocket Client connecting");
        webSocketClientHandler =
                new WebSocketClientHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                                uri, WebSocketVersion.V13, null, false, EmptyHttpHeaders.INSTANCE, 1280000), this);
        ch = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
        webSocketClientHandler.handshakeFuture().sync();
    }
    public ChannelFuture send(String text)
    {
        LogHelper.dev("Send: %s", text);
        return ch.writeAndFlush(new TextWebSocketFrame(text));
    }
    abstract void onMessage(String message) throws Exception;
    abstract void onDisconnect() throws Exception;
    abstract void onOpen() throws Exception;

    public void close() throws InterruptedException {
        //System.out.println("WebSocket Client sending close");
        isClosed = true;
        if(ch != null && ch.isActive())
        {
            ch.writeAndFlush(new CloseWebSocketFrame());
            ch.closeFuture().sync();
        }

        //group.shutdownGracefully();
    }

    public void eval(final String text) throws IOException {
        ch.writeAndFlush(new TextWebSocketFrame(text));
    }

}