/**
 * Driver class to receive http requests and send http responses.
 */
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
	
	/**
	 * Threadpool that will store threads, and control minimum and maximum size.
	 */
	private static ThreadPoolExecutor threadPool;
	
	/**
	 * Static method to make the threadpool.
	 * @return
	 */
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
	
	/**
	 * 
	 * @param	thread	Thread that holds the socket that is connected to the client
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	private void storeThread(HTTPThread thread) throws UnknownHostException, IOException{
        threadPool.execute(thread); //starts up a new thread from the threadpool
	}

	public static void main(String args[]) throws InterruptedException {
		threadPool = makeThreadPool(); // initializes the thread pool
        
        threadPool.shutdown(); //shuts down the threadPool
	}
}
