package proj;

import java.net.Socket;

public class HTTPThread extends Thread {

    private Socket client;

    public HTTPThread(Socket client){
    this.client = client;

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
