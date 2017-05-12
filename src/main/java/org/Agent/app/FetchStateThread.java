package org.Agent.app;

import org.Orchestrator.app.Commands;

import java.net.InetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class FetchStateThread extends Thread {
    //TODO: Find an appropriate timeout value
    public static final int SO_TIMEOUT = 200;
    public static final String GET_STATE_COMMAND = "read se.g %s";

    private InetAddress ipAddr;
    private int port;
    private byte mbId;
    private byte[] MBState;
    private boolean success;

    /**
     * Initialize FetchStateThread
     * @param ipAddr The ip address of the agent
     * @param port The port on which the agent listens
     * @param mbId The type of the middlebox whose state will be fetched
     */
    public FetchStateThread(InetAddress ipAddr, int port, byte mbId) {
        this.ipAddr = ipAddr;
        this.port   = port;
        this.mbId = mbId;
        this.success = false;
    }

    public void fetchState() {
        try {
            Socket socket = new Socket(ipAddr, port);
            socket.setSoTimeout(SO_TIMEOUT);
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            out.write(Commands.getStateCommand(mbId));
            out.flush();

            InputStream in = socket.getInputStream();
            int size = in.read();
            MBState = new byte[size];
            in.read(MBState);

            out.close();
            in.close();

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
