package com.fileServer.nettyServer;

import com.fileServer.bigFileUpload.FileWebSocketFrameHandler;
import com.fileServer.bigFileUpload.HttpRequestHandler;
import com.fileServer.redis.RedisService;
import com.github.tobato.fastdfs.service.AppendFileStorageClient;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
public class FileServerBoot {

    Logger logger = LoggerFactory.getLogger(FileServerBoot.class);

    @Value("${netty.port}")
    private int port;

    private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    /**
     * 创建bootstrap
     */
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    /**
     * BOSS
     */
    EventLoopGroup boss = new NioEventLoopGroup();
    /**
     * Worker
     */
    EventLoopGroup work = new NioEventLoopGroup();

    @Autowired
    private AppendFileStorageClient appendFileStorageClient;

    @Autowired
    private RedisService redisService;

    @Autowired
    private FileWebSocketFrameHandler fileWebSocketFrameHandler;

    /**
     * 关闭服务器方法
     */
    @PreDestroy
    public void close() {
        logger.info("关闭服务器....");
        //优雅退出
        boss.shutdownGracefully();
        work.shutdownGracefully();
    }

    /**
     * 开启及服务线程
     */
    public void start() {
        // 从配置文件中(application.yml)获取服务端监听端口号
        serverBootstrap.group(boss, work)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO));
        try {
            //设置事件处理
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new HttpServerCodec());
                    // 写入一个文件的内容
                    pipeline.addLast(new ChunkedWriteHandler());
                    // 将一个HttpMessage和跟随它的多个HttpContent聚合为单个FullHttpRequest或FullHttpResponse。安装这个之后，ChannelPipeLine中的下一个ChannelHandle只会收到完整的Http请求或响应
                    pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                    // 处理HTTP请求，WEBSOCKET握手之前
                    pipeline.addLast(new HttpRequestHandler("/ws"));
                    // 按照WEBSOCKET规范要求，处理WEBSOCKET升级握手、PingWebSocketFrame、PongWebSocketFrame、CloseWebSocketFrame
                    pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null,true));
                    // 处理TextWebSocketFrame和握手完成事件
                    //pipeline.addLast(new TextWebSocketFrameHandler(group));
                    // 处理TextWebSocketFrame和握手完成事件
                    pipeline.addLast(fileWebSocketFrameHandler);

                }
            });
            logger.info("netty服务器在[{}]端口启动监听", port);
            ChannelFuture f = serverBootstrap.bind(port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.info("[出现异常] 释放资源");
            boss.shutdownGracefully();
            work.shutdownGracefully();
        }
    }
}
