package org.Orchestrator.app;

import org.onlab.packet.Ip4Address;
import java.nio.ByteBuffer;

public class Commands {
    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int CHAIN_POS_OFFSET = 2;
    public static final int F_OFFSET = 3;
    public static final int FIRST_VLAN_TAG_OFFSET = 4;
    public static final int CHAIN_LENGTH_OFFSET = 5;
    public static final int FIRST_IP_OFFSET = 6;
    public static final int IP_LEN = 4;
    public static final int MB_LEN = 1;
    public static final int REPLICA_LEN = IP_LEN + MB_LEN;

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
     * Command Middlebox ChainPos F FirstVLANTag ChainLength IP1-MB1  IP2-MB2  ...
     * 0       1         2        3 4            5           6        11       5 + ChainLength * 5
     * @param command Either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox The middlebox that the agent should initialize inside the click-instance
     * @param chainPos The position of the middlebox in the chain
     * @param firstVlanTag The Vlan tag is used for routing
     * @param chain The chain
     * @return The byte string of the command
     */
    public static byte[] getInitCommand(byte command, byte middleBox, byte chainPos, byte firstVlanTag, FaultTolerantChain chain) {
        byte ipsSize = (byte) ((Integer.SIZE / Byte.SIZE + 1) * chain.length());
        ByteBuffer buffer = (command == Commands.MB_INIT_AND_FETCH_STATE) ?
                ByteBuffer.allocate(ipsSize + 6) :
                ByteBuffer.allocate(5);

        buffer.put(command);
        buffer.put(middleBox);
        buffer.put(chainPos);
        buffer.put(chain.getF());
        buffer.put(firstVlanTag);

        // If the command include fetch state, then the orchestrator has to provide the IP addresses of the other agents
        if (command == Commands.MB_INIT_AND_FETCH_STATE) {
            buffer.put((byte)chain.length());

            for (int i = 0; i < chain.replicaMapping.size(); ++i){
                buffer.put(chain.replicaMapping.get(i).toOctets());
                buffer.put(chain.getMB(i));
            }//for
//            buffer.put(Ip4Address.valueOf("127.0.0.1").toOctets());
//            buffer.put(chain.getMB(0));
//            buffer.put(Ip4Address.valueOf("10.20.159.142").toOctets());
//            buffer.put(chain.getMB(1));
        }//if
        return buffer.array();
    }

    /**
     * Parses an init response
     * @param bytes the byte stream of the init response
     * @return A pair of byte (i.e., a middlebox that must be initialized) and a FaultTolerantChain.
     */
    public static FaultTolerantChain parseChainFromInitCommand(byte[] bytes) {
        byte firstVlanTag = bytes[FIRST_VLAN_TAG_OFFSET];
        byte f = bytes[F_OFFSET];
        byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];

        FaultTolerantChain chain = new FaultTolerantChain();
        chain.setF(f);
        chain.setFirstTag(firstVlanTag);

        for (byte i = 0; i < ipsLen; ++i) {
            chain.appendToChain(bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN]);
            chain.replicaMapping.add(Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET));
        }//for

        return chain;
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
}
