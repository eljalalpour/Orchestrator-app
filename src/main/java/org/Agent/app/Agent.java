package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.onlab.packet.Ip4Address;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Agent {
    public static final int DEFAULT_AGENT_PORT = 2222;
    public static final int CLICK_INS_PORT = 10001;
    private static Socket clientSocket = null;
    private Ip4Address ipAddr = null;

    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;

    private static final String RUN_CLICK_INSTANCE = "click %s.click";
    private static final String DEF_CLICK_INSTANCE_CONF = "click -e 'require(package \"FTSFC\");ControlSocket(TCP, %d, " +
            "VERBOSE true);se::FTStateElement(ID %d, FAILURE_COUNT %d);'";
    private static final String LAST_CLICK_INSTANCE_CONF = "";
    private static final String GET_STATE_FROM_CLICK_INSTANCE = "read se.g %d";
    private static final String PUT_STATE_TO_CLICK_INSTANCE = "write se.p";

    private static boolean allSet(boolean[] arr) {
        boolean result = true;
        for (int i = 0; i < arr.length && result; ++i)
            result = arr[i];

        return result;
    }

    private static byte whoToAsk(byte f, byte n, byte i, byte chainPos) {
        return (byte)((chainPos - f + i + n) % n);
    }

    public void handleInit(boolean fetchState, byte[] bytes) {
        // Run the click instance
        try {
            byte chainPos = Commands.parseChainPosFromInitCommand(bytes);
            byte middlebox = Commands.parseMiddleboxFromInitCommand(bytes);
            byte f = Commands.parseF(bytes);
            String clickRun = String.format(DEF_CLICK_INSTANCE_CONF, CLICK_INS_PORT, chainPos, f);
            System.out.println(clickRun);
            Process p = Runtime.getRuntime().exec(clickRun);
            //TODO: check if the process is loaded completely
        }//try
        catch (IOException ioExc) {
            ioExc.printStackTrace();
        }//catch

        // Fetch the state
        if (fetchState) {
            // Find the position of this replica in the chain
//            FaultTolerantChain chain = Commands.parseChainFromInitCommand(bytes);
            ArrayList<Ip4Address> ip4Addresses = Commands.parseIpAddresses(bytes);
            byte chainPos = Commands.parseChainPosFromInitCommand(bytes);
//            for (byte i = 0; i < ipAddrs.size(); ++i) {
//                if (ipAddrs.get(i).equals(ipAddr)) {
//                    chainPos = i;
//                    break;
//                }//if
//            }//for

            byte f = Commands.parseF(bytes);
            byte n = (byte)Commands.parseLength(bytes);
            int[] whoToAsk = new int[f + 1];
            for (byte i = 0; i < whoToAsk.length; ++i) whoToAsk[i] = whoToAsk(f, n, i, chainPos);
            whoToAsk[chainPos] = (chainPos + 1) % n;

//            byte[][] states = new byte[f + 1][];
            boolean[] successes = new boolean[f + 1];
            FetchStateThread[] threads = new FetchStateThread[f + 1];

            do {
                // Run a thread for the required states
                for (byte i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    threads[i] = new FetchStateThread(ip4Addresses.get(whoToAsk[i]),
                            DEFAULT_AGENT_PORT, whoToAsk(f, n, i, chainPos));
                    threads[i].start();
                }//for

                // Wait for the threads to finish their job
                for (int i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;
                    try {
                        threads[i].join();
                        successes[i] = threads[i].getSuccess();
                        if (successes[i]) {
                            putState(threads[i].getMBState());
                        }//if
                    }//try
                    catch(InterruptedException iExc) {
                        iExc.printStackTrace();
                    }//catch
                }//for

                // Check for the result of the threads
//                for (int i = 0; i < whoToAsk.length; ++i) {
//                    if (successes[i]) continue;
//
//                    successes[i] = threads[i].getSuccess();
//                    if (successes[i])
//                        states[i] = threads[i].getMBState();
//                }//for
            }//do
            while(allSet(successes));//while
        }//if
    }

    public byte[] handleGetState(byte[] bytes) {
        byte mb = Commands.parseMiddleboxFromGetStateCommand(bytes);
        byte[] state = null;
        try {
            Socket socket = new Socket("localhost", CLICK_INS_PORT);
            OutputStream out = socket.getOutputStream();
            PrintWriter printWriter = new PrintWriter(out);
            printWriter.write(String.format(GET_STATE_FROM_CLICK_INSTANCE, mb));
            printWriter.close();
            out.close();

            InputStream in = socket.getInputStream();
            state = stripClickJunk(in);
            in.close();
        }//try
        catch(IOException ioExc) { }//catch

        return state;
    }

    public void putState(byte[] bytes) {
        try {
            Socket socket = new Socket("localhost", CLICK_INS_PORT);
            OutputStream out = socket.getOutputStream();
            PrintWriter printWriter = new PrintWriter(out);
            printWriter.write(PUT_STATE_TO_CLICK_INSTANCE);
            out.write(bytes);
            out.close();
        }//try
        catch(IOException ioExc) { }//catch
    }

    private byte[] stripClickJunk(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        byte[] result = null;
        int count = 0;
        try {
            // Read the response from ControlSocket
            String fristLine = reader.readLine();
            String secondLine = reader.readLine();

            int size = Integer.valueOf(secondLine.split(" ")[1]);
            result = new byte[size];
            in.read(result, 0, size);
        }//try
        catch(IOException ioExc) {

        }//catch

        return result;
    }

//    public byte[] readFromClickInstance(){
//        byte[] states = null;
//
//        try {
//            Socket socket = new Socket(ipAddr.toInetAddress(), CLICK_INS_PORT);
//            InputStream is = socket.getInputStream();
//            states = stripClickJunk(is);
//        }//try
//        catch(IOException ioExc) {
//            ioExc.printStackTrace();
//        }//catch
//
//        return states;
//    }

    public static void main (String args[]) {
        Agent agent = new Agent();
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption( "i", "ip", true, "The ip address of the host that " +
                "this agent is running on" );
        options.addOption( "p", "clickport", true, "The port number on which the click instance is listening");
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
