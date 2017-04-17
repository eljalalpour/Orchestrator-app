package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class FetchStateThread extends Thread {
    //TODO: Find an appropriate timeout value
    public static final int SO_TIMEOUT = 200;

    private Ip4Address ipAddr;
    private int port;
    private byte MBType;
    private byte[] MBState;
    private boolean success;

    /**
     * Initialize FetchStateThread
     * @param ipAddr The ip address of the agent
     * @param port The port on which the agent listens
     * @param MBType The type of the middlebox whose state will be fetched
     */
    public FetchStateThread(Ip4Address ipAddr, int port, byte MBType) {
        this.ipAddr = ipAddr;
        this.port   = port;
        this.MBType = MBType;
        this.success = false;
    }

    public void fetchState() {
        try {
            Socket socket = new Socket(ipAddr.toInetAddress(), port);
            socket.setSoTimeout(SO_TIMEOUT);
            OutputStream out = socket.getOutputStream();
            out.write(Commands.getStateCommand(MBType));

            InputStream in = socket.getInputStream();
            int size = in.read();
            MBState = new byte[size];
            in.read(MBState);

            in.close();
            out.close();

            success = true;
        }//try
        catch(SocketTimeoutException stExc) {
            success = false;
            MBState = null;
        }//catch
        catch(IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
    }

    /**
     * Send the request to fetch the state of a middlebox. The format of the response is as follows:
     * StateLength  MiddleboxState
     * 0         3  4  StateLength
     */
    @Override
    public void run() {
        fetchState();
    }

    public byte[] getMBState() {
        return MBState;
    }

    public boolean getSuccess() {
        return success;
    }
}
