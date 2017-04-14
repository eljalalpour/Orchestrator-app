package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.io.IOUtils;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

class AgentThread extends Thread {
    private Socket socket = null;
    private InputStream is = null;
    private byte[] bytes = null;
    private Ip4Address ipAddr = null;

    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int CHAIN_LENGTH_OFFSET = 2;
    public static final int FIRST_IP_OFFSET = 3;
    public static final int IP_LEN = 4;
    public static final int MB_LEN = 1;
    public static final int REPLICA_LEN = IP_LEN + MB_LEN;

    public void handleInit(boolean fetchState) {
        byte middlebox = bytes[MB_OFFSET];
        if (fetchState) {
            // Parse the rest of the command
            byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];
            Ip4Address[] ipAddrs = new Ip4Address[ipsLen];
            byte[] types = new byte[ipsLen];
            byte chainPos = -1;

            // Find the position of this replica in the chain
            for (byte i = 0; i < ipsLen; ++i) {
                //TODO: Make sure that Ip4Address.valueOf function works as expected
                ipAddrs[i] = Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET);
                types[i] = bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN];

                if (ipAddrs[i] == this.ipAddr) {
                    chainPos = i;
                }//if
            }//for

            // Ask for the primary states


            // Ask for the secondary states

        }//if

        // TODO: initialize the click-instance
    }

    public void handleGetState() {

    }

    public void rollback() {

    }

    public AgentThread(Socket socket, Ip4Address ipAddr) {
        this.socket = socket;
        this.ipAddr = ipAddr;
    }

    @Override
    public void run() {
        try {
            is = socket.getInputStream();
            bytes = IOUtils.toByteArray(is);
        }//try
        catch (IOException ioExc) {
            ioExc.printStackTrace();
            return;
        }//catch

        if (bytes.length > 0) {
            switch (bytes[CMD_OFFSET]) {
                case Commands.MB_INIT:
                    handleInit(false);
                    break;

                case Commands.MB_INIT_AND_FETCH_STATE:
                    handleInit(true);
                    break;

                case Commands.GET_STATE:
                    handleGetState();
                    break;

                default:
                    //TODO: Appropriate action when the command is not known
                    break;
            }//switch
        }//if
    }
}