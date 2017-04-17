package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.io.IOUtils;
import org.apache.commons.cli.*;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.Host;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Agent {
    public static final int DEFAULT_AGENT_PORT = 2222;
    public static final int CLICK_INS_PORT = 33333;
    private static Socket clientSocket = null;
    private Ip4Address ipAddr = null;

    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;

    private static final String RUN_CLICK_INSTANCE = "click %s.click";

    private static boolean allSet(boolean[] arr) {
        boolean result = true;
        for (int i = 0; i < arr.length && result; ++i) result = arr[i];

        return result;
    }

    public void handleInit(boolean fetchState, byte[] bytes) {
        // Fetch the state
        if (fetchState) {
            // Find the position of this replica in the chain
            ArrayList<Ip4Address> ipAddrs = new ArrayList<>();
            ArrayList<Byte> types = new ArrayList<>();
            Commands.parseInitResponse(bytes, ipAddrs, types);
            byte chainPos = -1;
            for (byte i = 0; i < ipAddrs.size(); ++i) {
                if (ipAddrs.get(i).equals(ipAddr)) {
                    chainPos = i;
                    break;
                }//if
            }//for

            //TODO: There is a bug below, we need only f+1 threads, not n (chain length) thread
            int[] whoToAsk = new int[types.size()];
            for (int i = 0; i < whoToAsk.length; ++i) whoToAsk[i] = i;
            whoToAsk[chainPos] = (chainPos + 1) % types.size();

            byte[][] states = new byte[types.size()][];
            boolean[] successes = new boolean[types.size()];

            FetchStateThread[] threads = new FetchStateThread[types.size()];

            do {
                // Run a thread for the required states
                for (int i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    threads[i] = new FetchStateThread(ipAddrs.get(whoToAsk[i]), DEFAULT_AGENT_PORT, types.get(i));
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

        try {
            String middlebox = Byte.toString(bytes[MB_OFFSET]);
            System.out.println(String.format(RUN_CLICK_INSTANCE, middlebox));
            Process p = Runtime.getRuntime().exec(String.format(RUN_CLICK_INSTANCE, middlebox));
        }//try
        catch (IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
    }

    public byte[] handleGetState(byte[] bytes) {
        //TODO: Handle the case if some exception happens
        FetchStateThread thread = new FetchStateThread(ipAddr, CLICK_INS_PORT, bytes[MB_OFFSET]);
        thread.fetchState();

        if (thread.getSuccess())
            return thread.getMBState();
        return null;
    }

    public static void main (String args[]) {
        Agent agent = new Agent();
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "i", "ip", true, "The ip address of the host that " +
                "this agent is running on" );
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);
            agent.ipAddr = Ip4Address.valueOf(line.getOptionValue("ip"));
        }//try
        catch( ParseException exp ) {
            System.out.println( "Unexpected exception:" + exp.getMessage() );
        }//catch


        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(DEFAULT_AGENT_PORT, 0, agent.ipAddr.toInetAddress());
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
                            agent.handleInit(false, bytes);
                            break;

                        case Commands.MB_INIT_AND_FETCH_STATE:
                            agent.handleInit(true, bytes);
                            break;

                        case Commands.GET_STATE:
                            byte[] states = agent.handleGetState(bytes);
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
