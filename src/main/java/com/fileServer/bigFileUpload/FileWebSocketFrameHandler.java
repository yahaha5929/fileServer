package com.fileServer.bigFileUpload;

import com.fileServer.redis.RedisService;
import com.github.tobato.fastdfs.domain.fdfs.StorePath;
import com.github.tobato.fastdfs.service.AppendFileStorageClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@ChannelHandler.Sharable
public class FileWebSocketFrameHandler extends SimpleChannelInboundHandler<Object> {

    private Logger logger = LoggerFactory.getLogger(FileWebSocketFrameHandler.class);

    private Map<String, Object> channelMap = new ConcurrentHashMap<>();

    @Autowired
    private AppendFileStorageClient appendFileStorageClient;

    @Autowired
    private RedisService redisService;

    public final static String DEFAULT_GROUP = "group1";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            logger.error("不能识别的请求类型!");
            ctx.writeAndFlush("不能识别的请求类型!");
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("断开连接!");
        //断开连接,移除MAP保存的文件名
        channelMap.remove(ctx.channel().id() + "_fileName");
    }


    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {

        if (frame instanceof TextWebSocketFrame) {
            handleActTextWebSocketFrame(ctx, frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryWebSocketFrame(ctx, frame);
        }

    }

    private void handleBinaryWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        BinaryWebSocketFrame msg = (BinaryWebSocketFrame) frame;
        logger.info("接受到channelId:"+ctx.channel().id()+" 文件数据:" + msg.content().capacity());
        ByteBuffer byteBuffer = ByteBuffer.allocate(msg.content().capacity());
        byteBuffer.clear();
        byte[] body = new byte[msg.content().readableBytes()];
        msg.content().getBytes(0, body);
        byteBuffer.put(body);
        byteBuffer.flip();
        InputStream stream = new ByteArrayInputStream(byteBuffer.array());
        Long fileCompSize = 0L;

        String fileName = channelMap.get(ctx.channel().id().toString() + "_fileName").toString();

        String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
        //无上传记录,从头开始上传
        //这里记录文件上传进度是以文件名为key,最好是用文件MD5做key,毕竟不同文件会重名
        if (redisService.get(fileName + "_fileSize") == null) {
            StorePath path = appendFileStorageClient.uploadAppenderFile(DEFAULT_GROUP, stream, byteBuffer.array().length, fileType);
            redisService.put(fileName + "_path", path.getPath(), 24, TimeUnit.HOURS);
            fileCompSize = (long) body.length;
        } else {//有上传记录,从记录的位置开始上传
            fileCompSize = Long.parseLong(redisService.get(fileName + "_fileSize").toString());
            String filePath = redisService.get(fileName + "_path").toString();
            appendFileStorageClient.modifyFile(DEFAULT_GROUP, filePath, stream, byteBuffer.array().length, fileCompSize);
            fileCompSize += (long) body.length;
        }
        redisService.put(fileName + "_fileSize", fileCompSize, 24, TimeUnit.HOURS);
        ctx.channel().writeAndFlush(new TextWebSocketFrame("ok"));
    }

    private void handleActTextWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        TextWebSocketFrame message = (TextWebSocketFrame) frame;
        logger.info("接受到channelId:"+ctx.channel().id()+" 文本指令:" + message.text());
        //上传文件数据前,先传递文件名做为KEY值保存进度状态
        if (message.text().startsWith("fileName:")) {
            String fileName = message.text().split(":")[1];
            //以channelId为key值,存储文件名
            channelMap.put(ctx.channel().id().toString() + "_fileName", fileName);

            if (redisService.get(fileName + "_fileSize") == null) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("0"));
            } else {
                Long fileCompSize = Long.parseLong(redisService.get(fileName + "_fileSize").toString());
                ctx.channel().writeAndFlush(new TextWebSocketFrame(String.valueOf(fileCompSize.longValue())));
            }
        } else if (message.text().startsWith("act:")) {
            String actStr = message.text().split(":")[1];
            String fileName = channelMap.get(ctx.channel().id().toString() + "_fileName").toString();
            switch (actStr) {
                //文件上传完成
                case "complete": {
                    String filePath = redisService.get(fileName + "_path").toString();
                    redisService.remove(fileName + "_path");
                    redisService.remove(fileName + "_fileSize");
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(filePath));
                    break;
                }
                //取消上传文件
                case "cancel": {
                    redisService.remove(fileName + "_path");
                    redisService.remove(fileName + "_fileSize");
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("canceled"));
                    break;
                }
                default: {
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("ok"));
                    break;
                }
            }
        }


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getLocalizedMessage());
        ctx.close();
    }
}
