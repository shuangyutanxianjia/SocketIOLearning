import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Title: 原始模型
 * @Description:
 * 该模型在获取等待客户端连接时 会阻塞等待
 * 在接受客户端写入时也会阻塞
 * 因此开始进化
 * @Author: xugw
 * @Date: 2020/9/19 - 15:14
 */
public class SocketIO {
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(9090, 20); // 绑定9090 端口 允许最大20个 连接
            System.out.println("step1 new ServerSocket started at 9090");
            while (true) {

                System.out.println("setp1-1 阻塞等待客户端连接");
                final Socket client = serverSocket.accept();// 接受客户端连接
                System.out.println("step2 new Client connected :"+ client.getPort());
                new Thread(new Runnable() {
                    public void run() {
                        InputStream in = null;
                        try {
                            InputStream inputStream = client.getInputStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            while (true) {
                                String dataline = reader.readLine();
                                System.out.println("step3 获取到客户端信息 取消阻塞");
                                if (null != dataline) {
                                    System.out.println(dataline);
                                } else {
                                    client.close();
                                    break;
                                }
                            }
                            System.out.println("客户端断开");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
