import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * @Title: 非阻塞模型
 * @Description:
 * 每个客户端都要去遍历继续进化
 * @Author: xugw
 * @Date: 2020/9/19 - 15:35
 */
public class SocketNIO {
    public static void main(String[] args) throws Exception {
        LinkedList<SocketChannel> clients = new LinkedList<SocketChannel>();
        ServerSocketChannel socketChannel = ServerSocketChannel.open();
        socketChannel.bind(new InetSocketAddress(9090));
        socketChannel.configureBlocking(false); // 非阻塞
        System.out.println("setp1 服务启动");
        while(true){
            SocketChannel client = socketChannel.accept(); // 不会阻塞发生空转

            if(client != null) {
                client.configureBlocking(false); // 客户端读取非阻塞
                System.out.println("客户端接入 " + client.socket().getPort());
                clients.add(client);
            }

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4096); // 开辟堆外空间
            //
            for (SocketChannel client1:
                 clients) {
                int num = client1.read(byteBuffer);
                if(num >0){
                    byteBuffer.flip();
                    byte[] bb = new byte[byteBuffer.limit()];
                    byteBuffer.get(bb);
                    String b =new String (bb);
                    System.out.println( client1.socket().getLocalAddress() + ":" + client1.socket().getPort() + "said" + b);
                    byteBuffer.clear();
                }
            }
        }
    }

}
