package proj;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.ServerSocket;

/**
 * 
 * @author Jake, Pankaj, 
 * https://www.journaldev.com/1069/threadpoolexecutor-java-thread-pool-example-executorservice
 */
public class PartialHTTP1Server {
	
	private static ThreadPoolExecutor threadPool;
	
	private static ThreadPoolExecutor makeThreadPool() {
		//RejectedExecutionHandler implementation
        RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl(); 
        //Get the ThreadFactory implementation to use
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        
        //creating the ThreadPoolExecutor
        int corePoolSize = 5; //number of threads that the pool will attempt to maintain
        int maxPoolSize = 50; //maximum number of threads in the pool
        int keepAliveTime = 10; //time that an idle thread will be killed (milliseconds)
        int queueSize = 5; //max number of tasks stored in the queue
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 
        		keepAliveTime, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize), 
        		threadFactory, rejectionHandler);
        return executorPool;
	}
	
	private void storeThread(Socket socket) throws UnknownHostException, IOException{
		//this is how you would "spin" a new thread
        Object[] objs = null;//unneeded for now
        threadPool.execute(new HTTPThread(new Socket("192.168.1.1", 5000), objs)); //starts up a new thread from the threadpool
	}

	public static void main(String args[]) throws InterruptedException {
		threadPool = makeThreadPool(); // initializes the thread pool
        
        threadPool.shutdown(); //shuts down the threadPool
	}
}
