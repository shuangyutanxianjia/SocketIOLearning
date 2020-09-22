package mynettydemo;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @Title: mynetty
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/20 - 9:23
 */
public class MyNetty {
    @Test
    public void myBytebuf(){
//        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(8, 20);// 初始大小 最大大小
//        ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);

        printMyBytebuf(buffer);
        System.out.println("=================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");
        buffer.writeBytes(new byte[]{1,2,3,4});
        printMyBytebuf(buffer);
        System.out.println("========================");

    }

    public static void printMyBytebuf(ByteBuf buf){
        System.out.println("buf.isReadable(): " +buf.isReadable());
        System.out.println("buf.readerIndex(): " +buf.readerIndex());
        System.out.println("buf.readableBytes(): " +buf.readableBytes());
        System.out.println("buf.isWritable(): " +buf.isWritable());
        System.out.println("buf.writerIndex(): " +buf.writerIndex());
        System.out.println("buf.writableBytes(): " +buf.writableBytes());
        System.out.println("buf.capacity(): " +buf.capacity()); // 动态分配的最大
        System.out.println("buf.maxCapacity(): " +buf.maxCapacity());
        System.out.println("buf.isDirect(): " +buf.isDirect()); // 堆内 堆外
    }

    /**
     * 客户端建立连接
     * 1、主动发送数据
     * 2、别人什么时候发送数据 ？ event selector
     */

    @Test
    public void LoopExecutor() throws IOException {
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(()->{

            try {
                while(true) {
                    System.out.println("Hello world001!");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        selector.execute(()->{

            try {
                while(true) {
                    System.out.println("Hello world002!");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        System.in.read();
    }

    @Test
    public void clientMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
        // 客户端模式
        NioSocketChannel client = new NioSocketChannel();

        thread.register(client); // epoll_ctl

        // 响应式
        ChannelPipeline pipeline = client.pipeline();
        pipeline.addLast(new MyInHandler());

        // reactor 异步的特征
        ChannelFuture connect = client.connect(new InetSocketAddress("192.168.133.131", 9090));
        ChannelFuture sync = connect.sync();

        ByteBuf buffer = Unpooled.copiedBuffer("hello world".getBytes());
        ChannelFuture send = client.writeAndFlush(buffer);
        send.sync();



        // 等待关闭
        sync.channel().closeFuture().sync();

        System.out.println("client over");

    }

    @Test
    public void ServiceDemo() throws InterruptedException {
        NioEventLoopGroup threads = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();
        threads.register(server);
        // 指不定什么时候来 所以要注册
        ChannelPipeline pipeline = server.pipeline();
        pipeline.addLast(new MyAcceptor(threads,new ChannelInit())); // 接收客户端

        ChannelFuture bind = server.bind(new InetSocketAddress(9090));
        bind.sync().channel().closeFuture().sync();
        System.out.println("server close");
    }

    class MyAcceptor extends ChannelInboundHandlerAdapter{
        private final NioEventLoopGroup selector;
        private final ChannelHandler handler;

        public MyAcceptor(NioEventLoopGroup threads, ChannelHandler myInHandler) {
            this.selector = threads;
            this.handler = myInHandler;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("server registed ... ");
            super.channelRegistered(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("server listend sockedchannel ...");
            SocketChannel client = (SocketChannel)msg;
            // 响应客户端
            ChannelPipeline p = client.pipeline();
            p.addLast(handler);
            // 注册客户端
            selector.register(client);
        }
    }

@ChannelHandler.Sharable
    class ChannelInit extends  ChannelInboundHandlerAdapter{
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            Channel client = ctx.channel();
            ChannelPipeline p = client.pipeline();
            p.addLast(new MyInHandler());
            ctx.pipeline().remove(this); // 过河拆桥
        }
    }


    /**
     * 每个人都有自己独有的私有属性
     * @ChannelHandler.Sharable 不应该强压给客户端
     */

    class MyInHandler extends ChannelInboundHandlerAdapter{

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("client registed ...");
            super.channelRegistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("client actived ... ");
            super.channelActive(ctx);
        }
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            CharSequence msgs = buf.getCharSequence(0,buf.readableBytes(), CharsetUtil.UTF_8);
            System.out.println(msgs);
            ctx.writeAndFlush(msg);
        }

    }


    @Test
    public void nettyClient() throws InterruptedException {
        NioEventLoopGroup threads = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        Bootstrap handler = bs.group(threads).channel(NioSocketChannel.class)
//                .handler(new ChannelInit());
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new MyInHandler());
            }
        });
        ChannelFuture connect = handler.connect(new InetSocketAddress("192.168.133.131", 9090));
        Channel client = connect.sync().channel();

        ByteBuf buffer = Unpooled.copiedBuffer("hello world".getBytes());
        ChannelFuture send = client.writeAndFlush(buffer);
        send.sync();

        client.closeFuture().sync();

    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup server = new NioEventLoopGroup(1);
        ServerBootstrap boss = new ServerBootstrap();
        ChannelFuture bind = boss.group(server, server).channel(NioServerSocketChannel.class)
                //.childHandler(new ChannelInit())
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new MyInHandler());
                    }
                })
                .bind(9090);
        bind.sync().channel().closeFuture().sync();
    }

}
