/**
 * Driver class to receive http requests and send http responses.
 */
package proj;
import java.net.Socket;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.ServerSocket;


/**
 * 
 * @author Jake, Pankaj, Aryak
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
        
        //values being input into the ThreadPoolExecutor
        int corePoolSize = 5; //number of threads that the pool will attempt to maintain
        int maxPoolSize = 50; //maximum number of threads in the pool
        int keepAliveTime = 10; //time that an idle thread will be killed (milliseconds)
        //int queueSize = 1; //max number of tasks stored in the queue UNUSED
        
        //Creates the ThreadPoolExecutor
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(corePoolSize, 
        		maxPoolSize, 
        		keepAliveTime, 
        		TimeUnit.MILLISECONDS, 
        		new SynchronousQueue<Runnable>(), 
        		threadFactory, 
        		rejectionHandler);
        
        return executorPool;//return the thread pool
	}
	
	/**
	 * 
	 * @param	thread	Thread that holds the socket that is connected to the client
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	private static void storeThread(HTTPThread thread) throws UnknownHostException, IOException{
        threadPool.execute(thread); //starts up a new thread from the threadpool
	}
	
	/**
	 * Driver method for the HTTP server
	 * @param args
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void main(String args[]) throws InterruptedException, IOException {
		
		threadPool = makeThreadPool(); // initializes the thread pool
		
		if(args.length != 1){
            System.out.println("Enter port number");
            System.exit(-1);
        }
        //idk if i need try catch
		
		int portNumber = 0;
        try{
            portNumber = Integer.parseInt(args[0]);
        }catch(Exception e){
            System.out.println("Port taken");
            System.exit(-1);
        }

        ServerSocket newSocket = new ServerSocket(portNumber);

        while(true){
            Socket connectedSocket = newSocket.accept();
            
            connectedSocket.setSoTimeout(5000); //time out after 5000 miliseconds
            
            //I am assumeing either the store thread method is keeping track of the thread count, or threadpool executer
            HTTPThread newThread = new HTTPThread(connectedSocket);
            
            //variables for threshold checking
            int totalThreads = threadPool.getActiveCount() + threadPool.getPoolSize();
            DataOutputStream outToClient = null;
            
            //gets a file stream that will send data to the client
          	try {
          		outToClient = new DataOutputStream(connectedSocket.getOutputStream());      
            } catch (IOException e) {
            	e.printStackTrace();
            }
          	
          	//check if the threshold is reached
            if(totalThreads >= 50) {
            	try {
            		System.err.println("HTTP/1.0 503 Service Unavailable\n");
    				outToClient.writeChars("HTTP/1.0 503 Service Unavailable\n");
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
            }
            
            storeThread(newThread);
        }
		
		
        
        //threadPool.shutdown(); //shuts down the threadPool
	}
}
