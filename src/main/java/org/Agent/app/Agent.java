package org.Agent.app;

import org.Orchestrator.app.Commands;

import java.net.InetAddress;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class Agent {
    static final int DEFAULT_AGENT_PORT = 2222;
    static final int CLICK_INS_PORT = 10001;
    static Socket clientSocket = null;
    static final String DEF_CLICK_INSTANCE_CONF =
            "click -e 'require(package \"FTSFC\");" +
                    "FromDevice(p0)" +
                    "->SetVLANAnno" +
                    "->FilterElement(%d)" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::CounterMB" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->ToDevice(p0);'";
    static final String FIRST_CLICK_INSTANCE_CONF =
            "click -e 'require(package \"FTSFC\");" +
                    "FromDevice(p0)" +
                    "->SetVLANAnno" +
                    "->FilterElement(%d,%d)" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::CounterMB" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->ToDevice(p0);'";
    static final String LAST_CLICK_INSTANCE_CONF =
            "click -e 'require(package \"FTSFC\");" +
                    "FromDevice(p0)" +
                    "->SetVLANAnno" +
                    "->FTFilterElement(%d)" +
                    "->CheckIPHeader(14)" +
                    "->se::FTStateElement(ID %d, VLAN_ID %d, F %d)" +
                    "->CheckIPHeader(14)" +
                    "->MB%d::CounterMB" +
                    "->[1]se;" +
                    "se[1]" +
                    "->VLANEncap(VLAN_ID %d)" +
                    "->FTBufferElement" +
                    "->ToDevice(p0);" +
                    "FTBufferElement[1]" +
                    "->ToDevice(p0);'";

    public static final int CMD_OFFSET = 0;

    InetAddress ipAddr = null;
    byte id;
    short vlanId;
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
        vlanId = Commands.parseVlanTag(bytes);
        middlebox = Commands.parseMiddleboxFromInitCommand(bytes);
        failureCount = Commands.parseF(bytes);
        chainLength = Commands.parseChainLength(bytes);
    }

    private String runClickCommand() {
        String command;
        if (chainPos == 0) {
            command = String.format(FIRST_CLICK_INSTANCE_CONF,
                    vlanId,
                    vlanId + chainLength + 1,
                    id,
                    vlanId,
                    failureCount,
                    middlebox,
                    vlanId + 1
            );
        }//if
        else if (chainPos == (chainLength - 1)) {
            command = String.format(LAST_CLICK_INSTANCE_CONF,
                    vlanId,
                    id,
                    vlanId,
                    failureCount,
                    middlebox,
                    vlanId + 1
            );
        }//if
        else {
            command = String.format(DEF_CLICK_INSTANCE_CONF,
                    vlanId,
                    id,
                    vlanId,
                    failureCount,
                    middlebox,
                    vlanId + 1
            );
        }//else

        return command;
    }

    public void handleInit(boolean fetchState, byte[] bytes) {
        // Run the click instance
        try {
            setValues(bytes);
            String clickRun = runClickCommand();
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
            ArrayList<InetAddress> ip4Addresses = Commands.parseIpAddresses(bytes);
            byte chainPos = Commands.parseChainPosFromInitCommand(bytes);
//            for (byte i = 0; i < ipAddrs.size(); ++i) {
//                if (ipAddrs.get(i).equals(ipAddr)) {
//                    chainPos = i;
//                    break;
//                }//if
//            }//for

            byte f = Commands.parseF(bytes);
            byte n = (byte)Commands.parseChainLength(bytes);
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
                for (byte i = 0; i < whoToAsk.length; ++i) {
                    if (successes[i]) continue;
                    try {
                        threads[i].join();
                        successes[i] = threads[i].getSuccess();
                        if (successes[i]) {
                            // TODO: check if the first parameter is correct
                            putState(i, threads[i].getMBState());
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
            System.out.printf("State size is: %d\n", size);

            // Read junk
            byte[] junk = new byte[3];
            inputStream.read(junk);

            // Read state
            state = new byte[size];
            inputStream.read(state, 0, size);

            System.out.println("State: ");
            for (int i = 0; i < size; i++)
                System.out.printf("%d ", state[i]);

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
        InetAddress ipAddr;

        if (args.length < 1) {
            System.out.println("Ip address should be given!");
            return;
        }//if

        try {
            ipAddr = InetAddress.getByName(args[0]);
            System.out.println(ipAddr.getHostAddress());
        }//try
        catch(UnknownHostException exc) {
            exc.printStackTrace();
            return;
        }//catch

//        if (!agentB) {
//            System.out.println("Running Agent a");
            Agent agent = new Agent();
            agent.ipAddr = ipAddr;
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(DEFAULT_AGENT_PORT, 0, agent.ipAddr);
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
                                System.out.println("Received init command");
                                agent.handleInit(false, bytes);
                                break;

                            case Commands.MB_INIT_AND_FETCH_STATE:
                                System.out.println("Received init and fetch state command");
                                agent.handleInit(true, bytes);
                                break;

                            case Commands.GET_STATE:
                                System.out.println("Received get command");
                                byte[] states = agent.handleGetState(bytes);
                                OutputStream out = clientSocket.getOutputStream();
                                out.write(states.length);
                                out.write(states, 0, states.length);
                                out.flush();
                                break;

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
//        }//if
//        else {
//            System.out.println("Running Agent b");
//            Agent agent2 = new Agent();
//            agent2.chainLength = 3;
//            agent2.vlanId = 1;
//            agent2.failureCount = 1;
//            agent2.chainPos = 1;
//            agent2.id = agent2.chainPos;
//            agent2.middlebox = 0;
//
//            byte[] states;
//            try {
//                Socket socket = new Socket("127.0.0.1", DEFAULT_AGENT_PORT);
//                socket.setTcpNoDelay(true);
//                OutputStream out = socket.getOutputStream();
//                out.write(Commands.getStateCommand((byte) 10));
//                out.flush();
//
//                InputStream in = socket.getInputStream();
//                int size = in.read();
//
//                states = new byte[size];
//                size = in.read(states);
//
//                out.close();
//                in.close();
//                System.out.printf("State (%d): ", size);
//                for (int i = 0; i < size; i++)
//                    System.out.printf("%d ", states[i]);
//
//                agent2.putState((byte) 10, states, 0, size, 10002);
//            }//try
//            catch (SocketTimeoutException stExc) {
//                stExc.printStackTrace();
//            }//catch
//            catch (IOException ioExc) {
//                ioExc.printStackTrace();
//            }//catch
//        }//else
    }
}