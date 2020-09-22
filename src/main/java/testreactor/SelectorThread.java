package testreactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @Title: 多路复用器的线程
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/19 - 17:45
 */
public class SelectorThread  extends ThreadLocal<LinkedBlockingDeque<Channel>> implements Runnable {

    // 每个线程对应一个selector
    Selector selector = null;
    SelectorThreadGroup stg ;

    // 队列
    LinkedBlockingDeque<Channel> linkedBlockingDeque = get();

    @Override
    protected LinkedBlockingDeque<Channel> initialValue() {
        return new LinkedBlockingDeque<>(); // threadLocal
    }

    public SelectorThread(SelectorThreadGroup stg) {
        try {
            this.selector = Selector.open();
            this.stg = stg;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // Loop
        while(true){
            try {
                System.out.println(Thread.currentThread().getName() + " : before ============" + selector.keys().size()) ;
                // select
                int nums = selector.select(); // 会有阻塞 selector.wakeup();
                // sleep 会注册上
                System.out.println(Thread.currentThread().getName() + " : after ============" + selector.keys().size());
                // 处理selectKeys
                if( nums > 0 ){
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){ // 线性处理
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if(key.isAcceptable()){
                            acceptHandler(key);
                        }else if(key.isReadable()){
                            readHandler(key);
                        }else if(key.isWritable()){

                        }
                    }

                }
                // 处理task
                if(!linkedBlockingDeque.isEmpty()){
                    Channel c = linkedBlockingDeque.take();
                    if( c instanceof ServerSocketChannel){
                        ServerSocketChannel server = (ServerSocketChannel) c;
                        server.register(selector,SelectionKey.OP_ACCEPT);
                    }if(c instanceof SocketChannel){
                        SocketChannel client = (SocketChannel) c;
                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4096);
                        client.register(selector,SelectionKey.OP_READ,byteBuffer);
                    }
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        }

    }

    private void readHandler(SelectionKey key) {
        try{
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            SocketChannel client = (SocketChannel) key.channel();
            buffer.clear();
            while (true){
                int num = client.read(buffer);
                if(num > 0){
                    buffer.flip();
                    while (buffer.hasRemaining()){
                        client.write(buffer);
                    }
                    buffer.clear();
                }else if(num == 0){
                    break;
                }else if(num < 0){ // 可能客户端断开连接
                    System.out.println("client:" + client.getRemoteAddress() + " 客户端连接断开。。。。");
                    client.close();
                    key.cancel(); // 从多路复用器中取消
                    break;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void acceptHandler(SelectionKey key) {
        System.out.println("accept Handler ....");
        try {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            // TODO 需要把client注册到对应的selector
           // stg.nextSelector(client);
            stg.nextSelectorV3(client);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setWorker(SelectorThreadGroup stgworker) {
        this.stg = stgworker;
    }
}
