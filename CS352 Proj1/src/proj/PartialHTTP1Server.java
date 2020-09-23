package proj;
import java.net.Socket;

public class PartialHTTP1Server {

    public static void main(String args[]) throws Exception{ //not sure if it should be a specific type of exception
        if(args.length != 1){
            System.out.println("Enter port number");
            System.exit(-1);
        }
        //idk if i need try catch
        try{
            int portNumber = Integer.parseInt(args[0]);
        }catch(Exception e){
            System.out.println("Port taken");
            System.exit(-1);
        }

        ServerSocket newSocket = new ServerSocket(portNumber);

        while(true){
            ServerSocket connectedSocket = newSocket.accept();
            //I am assumeing either the store thread method is keeping track of the thread count, or threadpool executer


            HTTPThread newThread = new HTTPThread(connectedSocket);
            storeThread(newThread);
        }
    }

}
