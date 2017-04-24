package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.Orchestrator.app.FaultTolerantChain;
import org.onlab.packet.Ip4Address;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class Agent {
    public static final int DEFAULT_AGENT_PORT = 2222;
    public static final int CLICK_INS_PORT = 33333;
    private static Socket clientSocket = null;
    private Ip4Address ipAddr = null;

    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;

    private static final String RUN_CLICK_INSTANCE = "click-%s.click";

    private static boolean allSet(boolean[] arr) {
        boolean result = true;
        for (int i = 0; i < arr.length && result; ++i)
            result = arr[i];

        return result;
    }

    private static int whoToAsk(byte f, byte n, byte i, byte chainPos) {
        return (chainPos - f + i + n) % n;
    }

    public void handleInit(boolean fetchState, byte[] bytes) {
        // Fetch the state
        if (fetchState) {
            // Find the position of this replica in the chain
            FaultTolerantChain chain = Commands.parseChainFromInitCommand(bytes);
            byte chainPos = -1;
            ArrayList<Ip4Address> ipAddrs = chain.getReplicaMapping();
            for (byte i = 0; i < ipAddrs.size(); ++i) {
                if (ipAddrs.get(i).equals(ipAddr)) {
                    chainPos = i;
                    break;
                }//if
            }//for

            byte f = chain.getF();
            byte n = (byte)chain.length();
            int[] whoToAsk = new int[f + 1];
            for (byte i = 0; i < whoToAsk.length; ++i) whoToAsk[i] = whoToAsk(f, n, i, chainPos);
            whoToAsk[chainPos] = (chainPos + 1) % n;

            byte[][] states = new byte[f + 1][];
            boolean[] successes = new boolean[f + 1];
            FetchStateThread[] threads = new FetchStateThread[f + 1];

            do {
                // Run a thread for the required states
                for (byte i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    threads[i] = new FetchStateThread(ipAddrs.get(whoToAsk[i]),
                            DEFAULT_AGENT_PORT, chain.getMB(whoToAsk(f, n, i, chainPos)));
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

        // Run the click instance
        try {
            byte chainPos = Commands.parseChainPosFromInitCommand(bytes);
            byte middlebox = Commands.parseMiddleboxFromInitCommand(bytes);

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
//        Agent agent = new Agent();
//        CommandLineParser parser = new DefaultParser();
//        Options options = new Options();
//        options.addOption( "i", "ip", true, "The ip address of the host that " +
//                "this agent is running on" );
//        try {
//            // parse the command line arguments
//            CommandLine line = parser.parse(options, args);
//            agent.ipAddr = Ip4Address.valueOf(line.getOptionValue("ip"));
//        }//try
//        catch( ParseException exp ) {
//            System.out.println( "Unexpected exception:" + exp.getMessage() );
//        }//catch
//
//
//        ServerSocket serverSocket;
//        try {
//            serverSocket = new ServerSocket(DEFAULT_AGENT_PORT, 0, agent.ipAddr.toInetAddress());
//        } catch (IOException e) {
//            System.out.println(e);
//            return;
//        }//catch
//        while (true) {
//            try {
//                clientSocket = serverSocket.accept();
//                InputStream is = clientSocket.getInputStream();
//                byte[] bytes = IOUtils.toByteArray(is);
//
//                if (bytes.length > 0) {
//                    switch (bytes[CMD_OFFSET]) {
//                        case Commands.MB_INIT:
//                            agent.handleInit(false, bytes);
//                            break;
//
//                        case Commands.MB_INIT_AND_FETCH_STATE:
//                            agent.handleInit(true, bytes);
//                            break;
//
//                        case Commands.GET_STATE:
//                            byte[] states = agent.handleGetState(bytes);
//                            OutputStream out = clientSocket.getOutputStream();
//                            out.write(states);
//                            break;
//
//                        default:
//                            //TODO: Appropriate action when the command is not known
//                            break;
//                    }//switch
//                }//if
//            }//try
//            catch(IOException ioExc) {
//                ioExc.printStackTrace();
//                return;
//            }//catch
//        }//while
    }
}
