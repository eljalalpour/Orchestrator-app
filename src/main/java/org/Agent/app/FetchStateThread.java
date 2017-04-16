package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class FetchStateThread extends Thread {
    private Ip4Address ipAddr;
    private int port;
    private byte MBType;

    public FetchStateThread(Ip4Address ipAddr, int port, byte MBType) {
        this.ipAddr = ipAddr;
        this.port   = port;
        this.MBType = MBType;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(ipAddr.toInetAddress(), port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] command = Commands.getStateCommand(MBType);
            out.write(command);

            //TODO

            in.close();
            out.close();
        }//try
        catch(IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
    }
}
