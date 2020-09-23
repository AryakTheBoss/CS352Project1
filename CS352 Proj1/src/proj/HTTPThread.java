package proj;

import java.net.Socket;

public class HTTPThread extends Thread {

    private Socket client;
    private Object[] data;

    public HTTPThread(Socket client, Object[] sharedData){
    this.client = client;
    this.data = sharedData;

    }


    @Override
    public synchronized void start() {
        super.start(); //PLACEHOLDER
    }


    @Override
    public void run() {
        super.run(); //PLACEHOLDER
    }

    @Override
    public void interrupt() {
        super.interrupt(); //PLACEHOLDER
    }
}
