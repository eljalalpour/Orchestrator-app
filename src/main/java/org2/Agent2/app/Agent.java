package org2.Agent2.app;

import org2.Orchestrator2.app.Commands;

import java.net.InetAddress;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
class StreamGobbler extends Thread {
    InputStream is;

    // reads everything from is until empty.
    StreamGobbler(InputStream is) {
        this.is = is;
    }

    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while ( (line = br.readLine()) != null)
                System.out.println(line);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}


public class Agent {

    static final String RECOVERY_LOG_FILE = "/home/ubuntu/experiments/recovery.agent.txt";
    static final int DEFAULT_AGENT_PORT = 2222;
    static final int CLICK_INS_PORT = 10001;
    static Socket clientSocket = null;
    static final String FIRST_CLICK_INSTANCE_CONF =
            "require(package \"FTSFC\");" +
                    "FTControlElement(%d);" +
                    "FromDevice(p0)" +
                    "->FTFilterElement(%d, %d)" +
                    "->CheckIPHeader(18)" +
                    "->FTAppenderElement(%d)" +
                    "->VLANDecap" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::CounterMB(ID %d)" +
                    "->CheckIPHeader(14)" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->Queue" +
                    "->ToDevice(p0)";

    static final String FIXED_FIRST_CLICK_INSTANCE_CONF =
            "require(package \"FTSFC\");" +
                    "FTControlElement(10001);" +
                    "firewall :: Classifier(12/0806 20/0001, 12/0806 20/0002, 12/0800, -);" +
                    "FromDevice(p0)" +
                    "-> FTFilterElement(10, 14)" +
                    "-> ch18 :: CheckIPHeader(18)" +
                    "-> ap::FTAppenderElement(10)" +
                    "-> VLANDecap" +
                    "-> CheckIPHeader(14)" +
                    "-> se::FTStateElement(ID 0, F 1)" +
                    "-> CheckIPHeader(14)" +
                    "-> firewall;firewall[0]" +
                    "-> d :: Discard;firewall[1]" +
                    "-> d;firewall[3]" +
                    "-> d;ip_from_extern :: IPClassifier(dst tcp ssh, dst tcp www or https, src tcp port ftp, tcp or udp, -);" +
                    "firewall[2]" +
                    "-> ip_from_extern;ip_from_extern[0]" +
                    "-> d;" +
                    "ip_from_extern[1] -> d;" +
                    "ip_from_extern[2] -> d;" +
                    "ip_from_extern[3]" +
                    "-> mo::Monitor(ID 0);" +
                    "ip_from_extern[4]" +
                    "-> d;" +
                    "mo -> CheckIPHeader(14) -> [1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID 11)" +
                    "->VLANEncap(VLAN_ID 11)" +
                    "->Queue" +
                    "->ToDevice(p0);";

    static final String LAST_CLICK_INSTANCE_CONF =
            "require(package \"FTSFC\");" +
                    "FTControlElement(%d);" +
                    "FromDevice(p0)" +
                    "->FTFilterElement(%d)" +
                    "->VLANDecap" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::CounterMB(ID %d)" +
                    "->CheckIPHeader(14)" +
                    "->[1]se;" +
                    "se[1]" +
                    "->CheckIPHeader(14)" +
                    "->be::FTBufferElement" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->pe::FTPassElement;" +
                    "be[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->[1]pe;" +
                    "pe" +
                    "->Queue" +
                    "->ToDevice(p0);";

    static final String FIXED_LAST_CLICK_INSTANCE_CONF =
            "require(package FTSFC); " +
                    "FTControlElement(10001); " +
                    "FromDevice(p0) " +
                    "-> FTFilterElement(12) " +
                    "-> VLANDecap " +
                    "-> CheckIPHeader(14) " +
                    "-> se::FTStateElement(ID 2, F 1) " +
                    "-> CheckIPHeader(14) " +
                    "-> lna::nat(ID 2) " +
                    "-> CheckIPHeader(14) " +
                    "-> [1]se;" +
                    "se[1] " +
                    "-> CheckIPHeader(14) " +
                    "-> be::FTBufferElement" +
                    "-> VLANEncap(VLAN_ID 13) " +
                    "-> VLANEncap(VLAN_ID 13)  " +
                    "->pe::FTPassElement;" +
                    "be[1] " +
                    "-> VLANEncap(VLAN_ID 14) " +
                    "-> VLANEncap(VLAN_ID 14) " +
                    "->[1]pe; " +
                    "pe " +
                    "->Queue " +
                    "->ToDevice(p0);";

    static final String DEF_CLICK_INSTANCE_CONF =
            "require(package \"FTSFC\");" +
                    "FTControlElement(%d);" +
                    "FromDevice(p0)" +
                    "->FTFilterElement(%d)" +
                    "->VLANDecap" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::ArrCounterMB(%d, 128)" +
                    "->CheckIPHeader(14)" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->Queue" +
                    "->ToDevice(p0)";

    static final String FIXED_MIDDLE_INSTANCE_CONF =
            "require(package \"FTSFC\");" +
                    "FTControlElement(10001);" +
                    "FromDevice(p0)" +
                    "-> FTFilterElement(11)" +
                    "-> VLANDecap" +
                    "-> CheckIPHeader(14)" +
                    "-> se::FTStateElement(ID 1, F 1)" +
                    "-> cmb::Monitor(ID 1)" +
                    "-> CheckIPHeader(14)" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID 12)" +
                    "->VLANEncap(VLAN_ID 12)" +
                    "-> Queue" +
                    "-> ToDevice(p0);";

    public static final int CMD_OFFSET = 0;

    InetAddress ipAddr = null;
    byte id;
    short firstVlanId;
    byte chainPos;
    byte chainLength;
    byte middlebox;
    byte failureCount;

    private static boolean allSet(boolean[] arr) {
        boolean result = true;
        for (int i = 0; i < arr.length && result; ++i)
            result = arr[i];

        return result;
    }

    private static byte whoToAsk(byte f, byte n, byte i, byte chainPos) {
        return (byte)((chainPos - f + i + n) % n);
    }

    private void setValues(byte[] bytes) {
        chainPos = Commands.parseChainPosFromInitCommand(bytes);
        id = chainPos;
        firstVlanId = Commands.parseFirstVlanTag(bytes);
        middlebox = Commands.parseMiddleboxFromInitCommand(bytes);
        failureCount = Commands.parseF(bytes);
        chainLength = Commands.parseChainLength(bytes);
    }

    private String runClickCommand() {
        String command;
        if (chainPos == 0) {
//            System.out.println("At the beginning of the chain\n");
//            System.out.printf("firstVlanId: %d, chain-length: %d\n", firstVlanId, chainLength);
//            command = String.format(FIRST_CLICK_INSTANCE_CONF,
//                    CLICK_INS_PORT,
//                    firstVlanId + chainPos,
//                    firstVlanId + chainLength + 1,
//                    firstVlanId + chainPos,
//                    id,
//                    firstVlanId + chainPos,
//                    failureCount,
//                    middlebox,
//                    id,
//                    firstVlanId + chainPos + 1,
//                    firstVlanId + chainPos + 1
//            );
            command = FIXED_FIRST_CLICK_INSTANCE_CONF;
        }//if
        else if (chainPos == (chainLength - 1)) {
//            System.out.println("At the end of the chain\n");
//            System.out.printf("firstVlanId: %d, chain-length: %d\n", firstVlanId, chainLength);
//            command = String.format(LAST_CLICK_INSTANCE_CONF,
//                    CLICK_INS_PORT,
//                    firstVlanId + chainPos,
//                    id,
//                    firstVlanId + chainPos,
//                    failureCount,
//                    middlebox,
//                    id,
//                    firstVlanId + chainPos + 1,
//                    firstVlanId + chainPos + 1,
//                    firstVlanId + chainPos + 2,
//                    firstVlanId + chainPos + 2
//            );
            command = FIXED_LAST_CLICK_INSTANCE_CONF;
        }//else if
        else {
//            System.out.println("At the middle of the chain\n");
//            System.out.printf("firstVlanId: %d, chain-length: %d\n", firstVlanId, chainLength);
//            command = String.format(DEF_CLICK_INSTANCE_CONF,
//                    CLICK_INS_PORT,
//                    firstVlanId + chainPos,
//                    id,
//                    firstVlanId + chainPos,
//                    failureCount,
//                    middlebox,
//                    id,
//                    firstVlanId + chainPos + 1,
//                    firstVlanId + chainPos + 1
//            );
            command = FIXED_MIDDLE_INSTANCE_CONF;
        }//else

        return command;
    }

    public void fetchState(byte chainPos, byte n, byte f) {
        byte[] whoToAsk = new byte[n];
        for (byte i = 0; i < n; i++) whoToAsk[i] = i;
        whoToAsk[chainPos] = (byte)((chainPos + 1) % n);
        boolean[] successes = new boolean[f + 1];
        FetchStateThread[] threads = new FetchStateThread[f + 1];
    }

    public void handleInit(boolean fetchState, byte[] bytes) {
        // Run the click instance
        long beforeInit, afterInit, beforeFetch, afterFetch, end;
        beforeInit = afterInit = beforeFetch = afterFetch = end = 0;

        long start = System.nanoTime();
        try {
            beforeInit = System.nanoTime();

            setValues(bytes);
            ArrayList<String> commands = new ArrayList<>();
            commands.add("sudo");
            commands.add("click");
            commands.add("-e");
            String clickRun = runClickCommand();
            commands.add(clickRun);
            ProcessBuilder processBuilder = new ProcessBuilder(commands);

            System.out.println(clickRun);

            Process p = processBuilder.start();
            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream());
            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream());
            errorGobbler.start();
            outputGobbler.start();
            afterInit = System.nanoTime();
        }//try
        catch (IOException ioExc) {
            ioExc.printStackTrace();
        }//catch

        // Fetch the state
        if (fetchState) {
            beforeFetch = System.nanoTime();
            // Find the position of this replica in the chain
//            FaultTolerantChain chain = Commands.parseChainFromInitCommand(bytes);
            ArrayList<InetAddress> inetAddresses = Commands.parseIpAddresses(bytes);

            byte chainPos = Commands.parseChainPosFromInitCommand(bytes);

            byte f = Commands.parseF(bytes);
            byte n = (byte)Commands.parseChainLength(bytes);
            int[] whoToAsk = new int[f + 1];
            for (byte i = 0; i < whoToAsk.length; ++i) whoToAsk[i] = whoToAsk(f, n, i, chainPos);
            whoToAsk[f] = (chainPos + 1) % n;

            boolean[] successes = new boolean[f + 1];
            FetchStateThread[] threads = new FetchStateThread[f + 1];

            do {
                // Run a thread per required state
                for (byte i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;

                    threads[i] = new FetchStateThread(inetAddresses.get(whoToAsk[i]),
                            DEFAULT_AGENT_PORT, (byte)((chainPos -f + i) % n));
                    threads[i].start();
                }//for

                // Wait for the threads to finish their job
                for (byte i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;
                    try {
                        threads[i].join();
                        successes[i] = threads[i].getSuccess();
                        if (successes[i]) {
                            putState((byte)((chainPos - f + i) % n),
                                    threads[i].getMBState());
                        }//if
                        else {
                            whoToAsk[i] = (byte)(whoToAsk[i] + 1) % n;
                        }//else
                    }//try
                    catch(InterruptedException iExc) {
                        iExc.printStackTrace();
                    }//catch
                }//for

            }//do
            while(!allSet(successes));//while

            afterFetch = System.nanoTime();

            try {
                String str = getLogString(RECOVERY_LOG_FILE, start, end, beforeInit, afterInit, beforeFetch, afterFetch);
                System.out.println(str);
                Agent.writeToFile(RECOVERY_LOG_FILE, start, end, beforeInit, afterInit, beforeFetch, afterFetch);
            }//try
            catch(IOException exc) {
                System.out.print(exc.getMessage());
            }//catch
        }//if
    }

    public byte[] handleGetState(byte[] bytes) {
        byte[] state = null;
        try {
            Socket socket = new Socket("localhost", CLICK_INS_PORT);
            socket.setTcpNoDelay(true);

            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();

            InputStream in = socket.getInputStream();
            DataInputStream inputStream = new DataInputStream(in);
            // First read the size of the state, then read the state
            int size = inputStream.read();

            // Read junk
            byte[] junk = new byte[3];
            inputStream.read(junk);

            // Read state
            state = new byte[size];
            inputStream.read(state, 0, size);

            out.close();
            in.close();
            socket.close();
        }//try
        catch(IOException ioExc) {
            ioExc.printStackTrace();
        }//catch

        return state;
    }

    public void putState(byte id, byte[] states, int offset, int length, int port) {
        try {
            Socket socket = new Socket("localhost", port);
            OutputStream out = socket.getOutputStream();
            DataOutputStream outputStream = new DataOutputStream(out);
            outputStream.write(Commands.getPutCommand(id, states, offset, length));
            outputStream.flush();
            out.flush();
            out.close();
        }//try
        catch(IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
    }

    public void putState(byte id, byte[] states, int port) {
        putState(id, states, 0, states.length, port);
    }

    public void putState(byte id, byte[] states) {
        putState(id, states, CLICK_INS_PORT);
    }

    public static void killMiddlebox() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("sudo");
        commands.add("killall");
        commands.add("click");
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        try {
            Process p = processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(String path, long ... args) throws IOException {
        String str = getLogString(path, args);
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path, true)));
        out.println(str);
        out.close();
    }

    public static String getLogString(String path, long ... args) throws IOException {
        String str = "";
        for (int i = 0; i < args.length - 1; ++i) {
            str += Long.toString(args[i]) + ",";
        }//for
        if (args.length > 0)
            str += Long.toString(args[args.length - 1]);

        return str;
    }

    public static void main (String args[]) {
            Agent agent = new Agent();
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(DEFAULT_AGENT_PORT, 0);
            }//try
            catch (IOException e) {
                System.out.println(e);
                return;
            }//catch
            while (true) {
                try {
                    clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    InputStream is = clientSocket.getInputStream();
                    byte[] bytes = new byte[1024];
                    is.read(bytes);

                    if (bytes.length > 0) {
                        switch (bytes[CMD_OFFSET]) {
                            case Commands.MB_INIT:
                                System.out.println("Received init command\n");
                                agent.handleInit(false, bytes);
                                break;

                            case Commands.MB_INIT_AND_FETCH_STATE:
                                System.out.println("Received init and fetch state command\n");
                                agent.handleInit(true, bytes);
                                break;

                            case Commands.GET_STATE:
                                System.out.println("Received get command\n");
                                byte[] states = agent.handleGetState(bytes);
                                OutputStream out = clientSocket.getOutputStream();
                                out.write(states.length);
                                out.write(states, 0, states.length);
                                out.flush();
                                break;

                            case Commands.KILL_MIDDLEBOX:
                                System.out.println("Received kill middlebox command\n");
                                killMiddlebox();

                            default:
                                //TODO: Appropriate action when the command is not known
                                break;
                        }//switch
                    }//if
                }//try
                catch (IOException ioExc) {
                    ioExc.printStackTrace();
                    return;
                }//catch
            }//while
    }
}