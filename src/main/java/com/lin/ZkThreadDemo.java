package com.lin;

import org.apache.zookeeper.ZooKeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.Collections;
import java.util.List;

/**
 * Created by Administrator on 2018/8/14.
 */
//用zk代替synchronized锁
public class ZkThreadDemo {
    // 以一个静态变量来模拟公共资源
    private static int counter = 0;
    private static final String LOCK_ROOT_PATH = "/Locks";
    private static final String LOCK_NODE_NAME = "Lock_";

    public static void plus() {

        // 计数器加一
        counter++;

        // 线程随机休眠数毫秒，模拟现实中的费时操作
        int sleepMillis = (int) (Math.random() * 100);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 线程实现类
    static class CountPlus extends Thread {


        // 每个线程持有一个zk客户端，负责获取锁与释放锁
        ZooKeeper zkClient;

        @Override
        public void run() {

            for (int i = 0; i < 2; i++) {

                // 访问计数器之前需要先获取锁
                String path = getLock();

                // 执行任务
                plus();

                // 执行完任务后释放锁
                releaseLock(path);
            }

            closeZkClient();
            System.out.println(Thread.currentThread().getName() + "执行完毕：" + counter);
        }

        /**
         * 获取锁，即创建子节点，当该节点成为序号最小的节点时则获取锁
         */
        private String getLock() {
            try {
                // 创建EPHEMERAL_SEQUENTIAL类型节点
                String lockPath = zkClient.create(LOCK_ROOT_PATH + "/" + LOCK_NODE_NAME,
                        Thread.currentThread().getName().getBytes(), Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL_SEQUENTIAL);
                System.out.println(Thread.currentThread().getName() + " create path : " + lockPath);

                // 尝试获取锁
                tryLock(lockPath);

                return lockPath;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * 该函数是一个递归函数 如果获得锁，直接返回；否则，阻塞线程，等待上一个节点释放锁的消息，然后重新tryLock
         */
        private boolean tryLock(String lockPath) throws KeeperException, InterruptedException {

            // 获取LOCK_ROOT_PATH下所有的子节点，并按照节点序号排序
            List<String> lockPaths = zkClient.getChildren(LOCK_ROOT_PATH, false);
            Collections.sort(lockPaths);

            int index = lockPaths.indexOf(lockPath.substring(LOCK_ROOT_PATH.length() + 1));
            if (index == 0) { // lockPath是序号最小的节点，则获取锁
                System.out.println(Thread.currentThread().getName() + " get lock, lockPath: " + lockPath);
                return true;
            } else { // lockPath不是序号最小的节点

                // 创建Watcher，监控lockPath的前一个节点
                Watcher watcher = new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        System.out.println(event.getPath() + " has been deleted");
                        synchronized (this) {
                            notifyAll();
                        }
                    }
                };
                String preLockPath = lockPaths.get(index - 1);
                Stat stat = zkClient.exists(LOCK_ROOT_PATH + "/" + preLockPath, watcher);

                if (stat == null) { // 由于某种原因，前一个节点不存在了（比如连接断开），重新tryLock
                    return tryLock(lockPath);
                } else { // 阻塞当前进程，直到preLockPath释放锁，重新tryLock
                    System.out.println(Thread.currentThread().getName() + " wait for " + preLockPath);
                    synchronized (watcher) {
                        watcher.wait();
                    }
                    return tryLock(lockPath);
                }
            }

        }

        /**
         * 释放锁，即删除lockPath节点
         */
        private void releaseLock(String lockPath) {
            try {
                zkClient.delete(lockPath, -1);
                System.out.println(lockPath + " deleted");
            } catch (InterruptedException | KeeperException e) {
                e.printStackTrace();
            }
        }

        public void setZkClient(ZooKeeper zkClient) {
            this.zkClient = zkClient;
        }

        public void closeZkClient() {
            try {
                zkClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public CountPlus(String threadName) {
            super(threadName);
        }
    }

    //借助Zookeeper 可以实现这种分布式锁：需要获得锁的 Server 创建一个 EPHEMERAL_SEQUENTIAL 目录节点，
    // 然后调用 getChildren()方法获取列表中最小的目录节点，如果最小节点就是自己创建的目录节点，那么它就获得了这个锁，
    // 如果不是那么它就调用 exists() 方法并监控前一节点的变化，一直到自己创建的节点成为列表中最小编号的目录节点，从而获得锁。
    // 释放锁很简单，只要删除它自己所创建的目录节点就行了
    public static void main(String[] args) throws Exception {

        // 开启五个线程
        CountPlus threadA = new CountPlus("threadA");
        setZkClient(threadA);
        threadA.start();

        CountPlus threadB = new CountPlus("threadB");
        setZkClient(threadB);
        threadB.start();
//
//        CountPlus threadC = new CountPlus("threadC");
//        setZkClient(threadC);
//        threadC.start();
//
//        CountPlus threadD = new CountPlus("threadD");
//        setZkClient(threadD);
//        threadD.start();
//
//        CountPlus threadE = new CountPlus("threadE");
//        setZkClient(threadE);
//        threadE.start();
    }

//        1.建立一个节点，假如名为：lock 。节点类型为持久节点（PERSISTENT） 
//        2.每当进程需要访问共享资源时，会调用分布式锁的lock()或tryLock()方法获得锁，这个时候会在第一步创建的lock节点下建立相应的顺序子节点，节点类型为临时顺序节点（EPHEMERAL_SEQUENTIAL），通过组成特定的名字name+lock+顺序号。 
//        3.在建立子节点后，对lock下面的所有以name开头的子节点进行排序，判断刚刚建立的子节点顺序号是否是最小的节点，假如是最小节点，则获得该锁对资源进行访问。 
//        4.假如不是该节点，就获得该节点的上一顺序节点，并给该节点是否存在注册监听事件。同时在这里阻塞。等待监听事件的发生，获得锁控制权。 
//        5.当调用完共享资源后，调用unlock（）方法，关闭zk，进而可以引发监听事件，释放该锁。 
    public static void setZkClient(CountPlus thread) throws Exception {
        ZooKeeper zkClient = new ZooKeeper("127.0.0.1:2181", 30000, null);
        thread.setZkClient(zkClient);
        if (null == zkClient.exists(LOCK_ROOT_PATH, false)) {
            String Path = zkClient.create(LOCK_ROOT_PATH,
                    Thread.currentThread().getName().getBytes(), Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);
        } else {
            System.out.println("---------------------create---------------");
        }
    }

}
