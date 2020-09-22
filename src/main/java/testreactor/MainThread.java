package testreactor;

/**
 * @Title: 主线程
 * @Description:
 * @Author: xugw
 * @Date: 2020/9/19 - 17:44
 */

public class MainThread {

    public static void main(String[] args) {

        // 创建I/O Thread
//        SelectorThreadGroup selectorThreadGroup = new SelectorThreadGroup(1);
        SelectorThreadGroup selectorBossGroup = new SelectorThreadGroup(3); // 只有一个 selector accept 然后 3个 selector 接收read
        SelectorThreadGroup selectorWorkerGroup = new SelectorThreadGroup(3);
        // 监听的server 注册到 某一个 selector上

        selectorBossGroup.setWorker(selectorWorkerGroup);
        selectorBossGroup.bind(9999);
        selectorBossGroup.bind(8888);


    }
}
