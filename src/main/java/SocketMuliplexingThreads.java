import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @Title: 非阻塞多路复用( 接收还是一个 但是读写 创建新的进程)
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/19 - 16:35
 */
public class SocketMuliplexingThreads {
    private ServerSocketChannel serverSocketChannel = null;
    private Selector selector = null;
    int port = 9090;

    public void initServer() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            selector = Selector.open(); // epoll_create
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //注册 Accept 事件
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        initServer();
        System.out.println("服务器启动");
        try {
            while (true) {

                while (selector.select() > 0) {

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    while (iter.hasNext()) {
                        System.out.println(selector.selectedKeys().size());
                        SelectionKey key = iter.next();
                        iter.remove();
                        System.out.println(key.isAcceptable());
                        if (key.isAcceptable()) {
                            HandleAccept(key);
                        } else if (key.isReadable()) {
                            key.cancel();
                            HandleRead(key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void HandleRead(SelectionKey selectionKey) {
        new Thread(() -> {
            System.out.println("handleRead");
            SocketChannel client = (SocketChannel) selectionKey.channel();
            ByteBuffer byteBuffer = (ByteBuffer) selectionKey.attachment();
            byteBuffer.clear();
            try {
                int read = 0;
                while (true) {
                    read = client.read(byteBuffer);
                    if (read > 0) {
                        byteBuffer.flip();
                        while (byteBuffer.hasRemaining()) {
                            client.write(byteBuffer);
                        }
                        byteBuffer.clear();
                    } else if (read == 0) {
                        break;
                    } else {
                        client.close();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void HandleAccept(SelectionKey selectionKey) throws IOException {
            try {
                System.out.println("handleAccept");
                ServerSocketChannel ss = (ServerSocketChannel) selectionKey.channel();
                SocketChannel client = ss.accept();
                client.configureBlocking(false);
                ByteBuffer buffer;
                System.out.println("client connect" + client.socket().getPort());
                buffer = ByteBuffer.allocateDirect(1024);
                client.register(selector, SelectionKey.OP_READ , buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public static void main(String[] args) {
        SocketMuliplexingThreads t = new SocketMuliplexingThreads();
        t.start();
    }
}
