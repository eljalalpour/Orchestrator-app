package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.io.IOUtils;
import org.apache.commons.cli.*;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Agent {
    public static final int AGENT_PORT = 2222;
    private static Socket clientSocket = null;
    //TODO: Implement initializing ipAddr
    private static Ip4Address ipAddr = null;

    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int CHAIN_LENGTH_OFFSET = 2;
    public static final int FIRST_IP_OFFSET = 3;
    public static final int IP_LEN = 4;
    public static final int MB_LEN = 1;
    public static final int REPLICA_LEN = IP_LEN + MB_LEN;

    public static void handleInit(boolean fetchState, byte[] bytes) {
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

                if (ipAddrs[i] == ipAddr) {
                    chainPos = i;
                }//if
            }//for

            // Ask for the primary states


            // Ask for the secondary states

        }//if

        // TODO: initialize the click-instance
    }

    public static void handleGetState() {

    }

    public static void rollback() {

    }

    public static void Main (String args[]) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "i", "ip", true, "The ip address of the host that " +
                "this agent is running on" );

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            // validate that block-size has been set
            // print the value of block-size
            Agent.ipAddr = Ip4Address.valueOf(line.getOptionValue("ip"));
        }//try
        catch( ParseException exp ) {
            System.out.println( "Unexpected exception:" + exp.getMessage() );
        }//catch


        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(AGENT_PORT);
        } catch (IOException e) {
            System.out.println(e);
            return;
        }//catch
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                InputStream is = clientSocket.getInputStream();
                byte[] bytes = IOUtils.toByteArray(is);

                if (bytes.length > 0) {
                    switch (bytes[CMD_OFFSET]) {
                        case Commands.MB_INIT:
                            handleInit(false, bytes);
                            break;

                        case Commands.MB_INIT_AND_FETCH_STATE:
                            handleInit(true, bytes);
                            break;

                        case Commands.GET_STATE:
                            handleGetState();
                            break;

                        default:
                            //TODO: Appropriate action when the command is not known
                            break;
                    }//switch
                }//if
            }//try
            catch(IOException ioExc) {
                ioExc.printStackTrace();
                return;
            }//catch
        }//while
    }
}
