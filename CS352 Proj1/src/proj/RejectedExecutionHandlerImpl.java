/**
 * Contains class that helps make a thread pool
 */
package proj;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Class that helps make a thread pool
 * @author https://www.journaldev.com/1069/threadpoolexecutor-java-thread-pool-example-executorservice
 */
public class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {

	@Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        System.out.println(r.toString() + " is rejected");
    }
}
