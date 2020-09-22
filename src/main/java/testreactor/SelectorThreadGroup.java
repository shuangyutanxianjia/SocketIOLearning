package testreactor;

import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Title: selectorgroup
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/19 - 18:14
 */
public class SelectorThreadGroup {

    SelectorThread[] selectorThreads;
    ServerSocketChannel serverSocketChannel;
    AtomicInteger indexid = new AtomicInteger(0);

    SelectorThreadGroup stg = this;
    public void setWorker(SelectorThreadGroup stg){
        this.stg = stg;
    }

    // number 线程数
    public SelectorThreadGroup(int num) {
        selectorThreads = new SelectorThread[num];
        for (int i = 0; i < num; i++) {
            selectorThreads[i] = new SelectorThread(this);
            // 启动线程
            new Thread(selectorThreads[i]).start();
        }
    }


    public void bind(int port) {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            // TODO server 应该注册到某一个selector 上
            // nextSelector(serverSocketChannel);
            nextSelectorV3(serverSocketChannel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void nextSelectorV3(Channel channel){

        SelectorThread  st = null;
        if(channel instanceof ServerSocketChannel){
            st = next();
            st.setWorker(stg);
        }else if(channel instanceof SocketChannel){
            st = nextV3();
        }
        st.linkedBlockingDeque.add(channel);
        st.selector.wakeup();

    }
    // 无论serverSocketChannel 还是 socketChannel 都复用该方法
    public void nextSelector(Channel Channel) {
//        try {
//            SelectorThread st = next();
//            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) Channel;
//            st.selector.wakeup(); // 会让selector.select() 直接返回
//            serverSocketChannel.register(st.selector, SelectionKey.OP_ACCEPT); // 会阻塞
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        try{
            SelectorThread st = next();
            // 队列传递 队列是堆上的 线程间共享 栈是线程独立的
            st.linkedBlockingDeque.add(Channel);

            // 打断阻塞让对应的线程自己在打断后完成注册selector
            st.selector.wakeup();

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private SelectorThread next() {
        int index = indexid.incrementAndGet() % selectorThreads.length;
        SelectorThread selectorThread = selectorThreads[index];
        return selectorThread;
    }

    private SelectorThread nextV3() {
        int index = indexid.incrementAndGet() % stg.selectorThreads.length;
        SelectorThread selectorThread = stg.selectorThreads[index];
        return selectorThread;
    }

}
