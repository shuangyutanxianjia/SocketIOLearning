
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @Title: 非阻塞多路复用（单进程版本）
 * @Description: 默认调用Linux 下 epoll模式
 * 进化多线程版本
 * @Author: xugw
 * @Date: 2020/9/19 - 16:01
 */
public class SocketMultiplexingSingleThreadv1 {
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
                while (selector.select(500) > 0) {
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isAcceptable()) {
                            HandleAccept(key);
                        } else if (key.isReadable()) {
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
        SocketChannel client = (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer = (ByteBuffer) selectionKey.attachment();
        byteBuffer.clear();
        int read = 0;
        try {
            while (true) {
                read = client.read(byteBuffer);
                if(read > 0){
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()){
                        client.write(byteBuffer);
                    }
                    byteBuffer.clear();
                }else if (read == 0){
                    break;
                }else {
                    client.close();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void HandleAccept(SelectionKey selectionKey) throws IOException {
        ServerSocketChannel ss = (ServerSocketChannel) selectionKey.channel();
        SocketChannel client = ss.accept();
        client.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        client.register(selector, SelectionKey.OP_READ, buffer);
        System.out.println("client connect" + client.socket().getPort());
    }

    public static void main(String[] args) {
        SocketMultiplexingSingleThreadv1 t = new SocketMultiplexingSingleThreadv1();
        t.start();
    }
}
