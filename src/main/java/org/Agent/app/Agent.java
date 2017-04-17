package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.io.IOUtils;
import org.apache.commons.cli.*;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Agent {
    public static final int AGENT_PORT = 2222;
    public static final int CLICK_INS_PORT = 33333;
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

    private static boolean allSet(boolean[] arr) {
        boolean result = true;
        for (int i = 0; i < arr.length && result; ++i) result = arr[i];

        return result;
    }

    public static void handleInit(boolean fetchState, byte[] bytes) {
        byte middlebox = bytes[MB_OFFSET];

        // Fetch the state
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

            //TODO: There is a bug below, we need only f+1 threads, not n (chain length) thread
            int[] whoToAsk = new int[types.length];
            for (int i = 0; i < whoToAsk.length; ++i) whoToAsk[i] = i;
            whoToAsk[chainPos] = (chainPos + 1) % types.length;

            byte[][] states = new byte[types.length][];
            boolean[] successes = new boolean[types.length];

            FetchStateThread[] threads = new FetchStateThread[types.length];

            do {
                // Run a thread for the required states
                for (int i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    threads[i] = new FetchStateThread(ipAddrs[whoToAsk[i]], AGENT_PORT, types[i]);
                    threads[i].start();
                }//for

                // Wait for the threads to finish their job
                for (int i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;
                    try {
                        threads[i].join();
                    }//try
                    catch(InterruptedException iExc) {
                        iExc.printStackTrace();
                    }//catch
                }//for

                // Check for the result of the threads
                for (int i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    successes[i] = threads[i].getSuccess();
                    if (successes[i])
                        states[i] = threads[i].getMBState();
                }//for
            }//do
            while(allSet(successes));//while
        }//if

        // TODO: initialize the click-instance
    }

    public static byte[] handleGetState(byte[] bytes) {
        //TODO: Handle the case if some exception happens
        FetchStateThread thread = new FetchStateThread(ipAddr, CLICK_INS_PORT, bytes[MB_OFFSET]);
        thread.fetchState();

        if (thread.getSuccess())
            return thread.getMBState();
        return null;
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
                            byte[] states = handleGetState(bytes);
                            OutputStream out = clientSocket.getOutputStream();
                            out.write(states);
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
