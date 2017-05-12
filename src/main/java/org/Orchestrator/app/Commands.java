package org.Orchestrator.app;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class Commands {
    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int CHAIN_POS_OFFSET = 2;
    public static final int F_OFFSET = 3;
    public static final int VLAN_TAG_OFFSET = 4;
    public static final int CHAIN_LENGTH_OFFSET = 5;
    public static final int FIRST_IP_OFFSET = 6;
    public static final int IP_LEN = 4;
    public static final int GET_STATE_CMD_LEN = 2;

    // Used to initialize the click-instance.
    // This command is used during the initial deployment.
    public static final byte MB_INIT = 0x00;

    // Used to initialize the instructs the agent to fetch the states from the other agents.
    // This command is used during the failure recovery.
    public static final byte MB_INIT_AND_FETCH_STATE = 0x01;

    // Used to get the state from the agent
    // This command is used during the failure recovery.
    public static final byte GET_STATE = 0x02;

    // Used to put the state to the click instance
    public static final byte PUT_STATE = 0x03;

    /**
     * Creating the byte stream of a get-state-command
     * @param middlebox The type of the middlebox
     * @return The byte stream of the get-command
     */
    public static byte[] getStateCommand(byte middlebox) {
        byte[] command = new byte[GET_STATE_CMD_LEN];
        command[CMD_OFFSET] = GET_STATE;
        command[MB_OFFSET] = middlebox;
        return command;
    }

    /**
     * The format of message is as follows
     * Command Middlebox ChainPos F FirstVLANTag ChainLength IP1  IP2  ...
     * 0       1         2        3 4            5           6        10       4 + ChainLength * 4
     * @param command Either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox The middlebox that the agent should initialize inside the click-instance
     * @param chainPos The position of the middlebox in the chain
     * @param vlanTag The Vlan tag is used for routing
     * @param chain The chain
     * @return The byte string of the command
     */
    public static byte[] getInitCommand(byte command, byte middleBox,
                                        byte chainPos, byte vlanTag, FaultTolerantChain chain) {
        byte ipsSize = (byte) ((Integer.SIZE / Byte.SIZE + 1) * chain.length());
        ByteBuffer buffer = (command == Commands.MB_INIT_AND_FETCH_STATE) ?
                ByteBuffer.allocate(ipsSize + 6) :
                ByteBuffer.allocate(6);

        buffer.put(command);
        buffer.put(middleBox);
        buffer.put(chainPos);
        buffer.put(chain.getF());
        buffer.put(vlanTag);
        buffer.put((byte)chain.length());

        // If the command include fetch state, then the orchestrator has to provide the IP addresses of the other agents
        if (command == Commands.MB_INIT_AND_FETCH_STATE) {
            for (int i = 0; i < chain.replicaMapping.size(); ++i){
                buffer.put(chain.replicaMapping.get(i).ipAddresses().iterator().next().toOctets());
            }//for
        }//if
        return buffer.array();
    }

    /**
     * The format of the message is as follows
     * Command ChainPos StateSize State
     * 0       1        2         6
     * @param chainPos The id of the middlebox whose state will be sent
     * @param state The state of the middlebox
     * @return The byte string of the command
     */
    public static byte[] getPutCommand(byte chainPos, byte[] state, int offset, int length) {
        ByteBuffer result = ByteBuffer.allocate(7 + state.length);

        result.put(PUT_STATE);
        result.put(chainPos);
        result.putInt(length);
        result.put(state, offset, length);

        return result.array();
    }

    public static byte[] getPutCommand(byte chainPos, byte[] state) {
        return getPutCommand(chainPos, state, 0, state.length);
    }

    /**
     * Parses an init response
     * @param bytes the byte stream of the init response
     * @return A pair of byte (i.e., a middlebox that must be initialized) and a FaultTolerantChain.
     */
//    public static FaultTolerantChain parseChainFromInitCommand(byte[] bytes) {
//        byte firstVlanTag = bytes[FIRST_VLAN_TAG_OFFSET];
//        byte f = bytes[F_OFFSET];
//        byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];
//
//        FaultTolerantChain chain = new FaultTolerantChain();
//        chain.setF(f);
//        chain.setFirstTag(firstVlanTag);
//
//        for (byte i = 0; i < ipsLen; ++i) {
//            chain.appendToChain(bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN]);
//            chain.replicaMapping.add(Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET));
//        }//for
//
//        return chain;
//    }

    public static byte parseVlanTag(byte[] bytes) {
        return bytes[VLAN_TAG_OFFSET];
    }

    public static byte parseChainLength(byte[] bytes) {
        return bytes[CHAIN_LENGTH_OFFSET];
    }

    public static byte parseF(byte[] bytes) {
        return bytes[F_OFFSET];
    }

    public static ArrayList<InetAddress> parseIpAddresses(byte[] bytes) {
        ArrayList<InetAddress> IpAddresses = new ArrayList<InetAddress>();
        byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];
        try {
            for (byte i = 0; i < ipsLen; ++i) {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.put(bytes, i * IP_LEN + FIRST_IP_OFFSET, 4);
                IpAddresses.add(InetAddress.getByAddress(buffer.array()));
            }//for
        }//try
        catch(UnknownHostException uhExc) {
            uhExc.printStackTrace();
        }//catch
        return IpAddresses;
    }

    /**
     * Parsing the middlebox from the init command
     * @param bytes the byte-stream of the init command
     * @return a byte representing the middlebox
     */
    public static byte parseMiddleboxFromInitCommand(byte[] bytes) {
        return bytes[MB_OFFSET];
    }

    /**
     * Parsing the chain-position from the init command
     * @param bytes the byte-stream of the init command
     * @return A byte representing the position of the chain
     */
    public static byte parseChainPosFromInitCommand(byte[] bytes) {
        return bytes[CHAIN_POS_OFFSET];
    }

    public static byte parseMiddleboxFromGetStateCommand(byte[] bytes) { return bytes[MB_OFFSET]; }
}
