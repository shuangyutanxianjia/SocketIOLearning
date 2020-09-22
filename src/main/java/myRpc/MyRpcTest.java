package myRpc;

/**
 * @Title: RPC拆解
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/20 - 11:39
 */

import com.sun.security.ntlm.Client;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 *
 * 通信，连接数量
 * 动态代理，序列化 反序列化 协议封装
 * 连接池
 */
public class MyRpcTest {

    /**
     * server
     */

    @Test
    public void StartServer(){
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = boss;
        ServerBootstrap server = new ServerBootstrap();
        ChannelFuture bind = server.group(boss, worker).channel(NioServerSocketChannel.class).
                childHandler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                System.out.println("server accept success ..." + nioSocketChannel.remoteAddress().getPort());
                ChannelPipeline p = nioSocketChannel.pipeline();
                p.addLast(new ServerRequsetHandler());

            }
        }).bind(new InetSocketAddress("localhost", 9090));

        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }






    /**
     * consumer
     */
    @Test
    public void get() throws InterruptedException {
//        new Thread(()->{
//            StartServer();
//        });
//        System.out.println("服务端启动 ======= ");
//        Thread.sleep(1000);
       Thread[] threads = new Thread[20];
        for (int i = 0; i <20 ; i++) {
            Car car = proxGet(Car.class);
            // 基于动态代理实
            car.ooxx("hello");
        }


//        Fly fly = proxGet(Fly.class);
//        fly.xxoo("world");

    }

    public static <T>T proxGet(Class<T> interfaceinfo){

        // 实现各个版本的动态代理
        ClassLoader classLoader = interfaceinfo.getClassLoader();
        Class<?>[] methods = {interfaceinfo};
        return (T)Proxy.newProxyInstance(classLoader, methods, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                // consumer 对于provider 的调用过程
                // 1 服务， 方法 ， 参数 ==> msg
                String servername = interfaceinfo.getName();
                String methodname = method.getName();
                Class<?>[] paramsTypes = method.getParameterTypes();
                MyContent myContent = new MyContent();
                myContent.setServername(servername);
                myContent.setMethodname(methodname);
                myContent.setParamterTypes(paramsTypes);
                myContent.setArgs(args);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(myContent);
                byte[] msgBody = out.toByteArray();

                // 2 requestid + msg, 本地要存储 requestid
                // 协议 header<> |body msgBody
                MyHeader myHeader = createHeader(msgBody);
                out.reset();
                oout = new ObjectOutputStream(out);
                oout.writeObject(myHeader);
                byte[] msgheader = out.toByteArray();

                // 3 连接池 取得连接
                ClientFactory factory = ClientFactory.getFactory();
                NioSocketChannel clientChannel = factory.getClient(new InetSocketAddress("localhost", 9090));


                // 4 发送
                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(msgheader.length + msgBody.length);
                long id = myHeader.getRequestid();
                CountDownLatch countDownLatch = new CountDownLatch(1);
                Response.addCallback(id, new Runnable() {
                    @Override
                    public void run() {
                        countDownLatch.countDown();
                    }
                });

                byteBuf.writeBytes(msgheader);
                byteBuf.writeBytes(msgBody);
                ChannelFuture channelFuture = clientChannel.writeAndFlush(byteBuf);
                channelFuture.sync(); // 发送成功前阻塞
                System.out.println("send success");
                countDownLatch.await();
                return null;
            }
        });
    }

    public static MyHeader createHeader(byte[] msg){
        MyHeader myHeader = new MyHeader();
        int size = msg.length;
        int f = 0x14141414;
        long requestId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        myHeader.setFlag(f);
        myHeader.setData_length(msg.length);
        myHeader.setRequestid(requestId);
        return myHeader;
    }

}

class MyContent implements Serializable{
    String servername;
    String methodname;
    Class<?>[] paramterTypes;
    Object[] args;

    public String getServername() {
        return servername;
    }

    public void setServername(String servername) {
        this.servername = servername;
    }

    public String getMethodname() {
        return methodname;
    }

    public void setMethodname(String methodname) {
        this.methodname = methodname;
    }

    public Class<?>[] getParamterTypes() {
        return paramterTypes;
    }

    public void setParamterTypes(Class<?>[] paramterTypes) {
        this.paramterTypes = paramterTypes;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}

interface Car{
    public void ooxx(String msg);
}

interface Fly{
    public void xxoo(String msg);
}

class Response{
    static ConcurrentHashMap<Long,Runnable> mapping = new ConcurrentHashMap<>();

    public static void addCallback(Long requstId,Runnable callback){
        mapping.putIfAbsent(requstId,callback);
    }

    public static void runCallback(Long requestId){
        Runnable callback = mapping.get(requestId);
        callback.run();
        removeCallback(requestId);
    }

    public static void removeCallback(Long requestId){
        mapping.remove(requestId);
    }
}

class MyHeader implements Serializable{
    /**
     * 通信协议
     * 1 ooxx值
     * 2 uuid
     * 3 data_length
     */
    int flag;
    long requestid;
    long data_length;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public long getRequestid() {
        return requestid;
    }

    public void setRequestid(long requestid) {
        this.requestid = requestid;
    }

    public long getData_length() {
        return data_length;
    }

    public void setData_length(long data_length) {
        this.data_length = data_length;
    }
}

class ClientFactory{
    int poolSize = 1;
    Random random = new Random();
    private ClientFactory(){}

    private static final ClientFactory factory;

    NioEventLoopGroup clientworker;

    static {
        factory = new ClientFactory();
    }

    public static ClientFactory getFactory(){
        return factory;
    }

    // 一个consumer 可以连接多个 provider 每个provider都有自己的pool
    ConcurrentHashMap<InetSocketAddress,ClientPool> outboxs = new ConcurrentHashMap<>();

    public synchronized NioSocketChannel getClient(InetSocketAddress address){
        ClientPool clientPool = outboxs.get(address);
        if (clientPool == null ){
            outboxs.putIfAbsent(address,new ClientPool(poolSize));
            clientPool = outboxs.get(address);
        }

        int i = random.nextInt(poolSize);
        if( clientPool.clients[i] != null && clientPool.clients[i].isActive()){
            return clientPool.clients[i];
        }

        synchronized (clientPool.lock[i]){
            return clientPool.clients[i] = create(address);
        }

    }



    private NioSocketChannel create(InetSocketAddress address){
        // 基于
        clientworker = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(clientworker).channel(NioSocketChannel.class).handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new ClientResponses());
            }
        }).connect(address);
        try {
            NioSocketChannel client = (NioSocketChannel) connect.sync().channel();
            return client;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}



class ClientPool{
    NioSocketChannel[] clients;
    Object[] lock;

    ClientPool(int size){
        clients = new NioSocketChannel[size];
        lock = new Object[size];
        for (int i = 0; i < size ; i++) {
            lock[i] = new Object();
        }
    }

}

class ClientResponses extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;

        if (byteBuf.readableBytes() >= 160) {
            byte[] bytes = new byte[160];
            byteBuf.readBytes(bytes);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            System.out.println("server responsed  " + header.getRequestid());

            // TODO
             Response.runCallback(header.requestid);

//            if(byteBuf.readableBytes() >= header.getData_length()){
//                byte[] data = new byte[(int)header.getData_length()];
//                byteBuf.readBytes(data);
//                ByteArrayInputStream din = new ByteArrayInputStream(data);
//                ObjectInputStream odin = new ObjectInputStream(din);
//                MyContent myContent = (MyContent)odin.readObject();
//                System.out.println(myContent.getServername());
//            }
        }

        super.channelRead(ctx, msg);
    }

}
    /**
     * server
     */
    class ServerRequsetHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf byteBuf = (ByteBuf) msg;
            ByteBuf sendBuf = byteBuf.copy();
            if (byteBuf.readableBytes() >= 160) {
                byte[] bytes = new byte[160];
                byteBuf.readBytes(bytes);
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                ObjectInputStream oin = new ObjectInputStream(in);
                MyHeader header = (MyHeader) oin.readObject();
                System.out.println(header.getData_length());
                System.out.println(header.getRequestid());

                if (byteBuf.readableBytes() >= header.getData_length()) {
                    byte[] data = new byte[(int) header.getData_length()];
                    byteBuf.readBytes(data);
                    ByteArrayInputStream din = new ByteArrayInputStream(data);
                    ObjectInputStream odin = new ObjectInputStream(din);
                    MyContent myContent = (MyContent) odin.readObject();
                    System.out.println(myContent.getServername());
                }
            }
            ChannelFuture channelFuture = ctx.writeAndFlush(sendBuf);
            channelFuture.sync();
            System.out.println("send success");
        }
    }

