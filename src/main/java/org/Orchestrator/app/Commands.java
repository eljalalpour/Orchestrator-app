package org.Orchestrator.app;

import org.onlab.packet.Ip4Address;
import org.onosproject.net.Host;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by ejalalpo on 4/13/17.
 */
public class Commands {
    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int CHAIN_LENGTH_OFFSET = 2;
    public static final int FIRST_IP_OFFSET = 3;
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

    public static byte[] getStateCommand(byte MBType) {
        byte[] command = new byte[GET_STATE_CMD_LEN];
        command[CMD_OFFSET] = GET_STATE;
        command[MB_OFFSET] = MBType;
        return command;
    }

    /**
     * The format of message is as follows
     * Command Middlebox ChainLength IP1-MB1  IP2-MB2  ...
     * 1       2         3           ChainLength * 5
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param replicaMapping the mapping of replicas to hosts
     * @param chainElems the elements of the chain
     * @return
     */
    public static byte[] getInitCommand(byte command, byte middleBox, ArrayList<Host> replicaMapping, String[] chainElems) {
        byte ipsSize = (byte) ((Integer.SIZE / Byte.SIZE + 1) * 2);
//        byte ipsSize = (byte) ((Integer.SIZE / Byte.SIZE + 1) * replicaMapping.size());

        ByteBuffer buffer = (command == Commands.MB_INIT_AND_FETCH_STATE) ?
                ByteBuffer.allocate(ipsSize + 3) :
                ByteBuffer.allocate(2);

        buffer.put(command);
        buffer.put(middleBox);

        // If the command include fetch state, then the orchestrator has to provide the IP addresses of the other agents
        if (command == Commands.MB_INIT_AND_FETCH_STATE) {
//            buffer.put(replicaMapping.size());
//            for (int i = 0; i < replicaMapping.size(); ++i){
//                buffer.put(replicaMapping.get(i).ipAddresses().iterator().next().getIp4Address().toOctets());
//                buffer.put(Byte.parseByte(chainElems[i + 1]));
//            }//for

            buffer.put((byte)2);
            buffer.put(Ip4Address.valueOf("127.0.0.1").toOctets());
            buffer.put(Byte.parseByte(chainElems[1]));
            buffer.put(Ip4Address.valueOf("10.20.159.142").toOctets());
            buffer.put(Byte.parseByte(chainElems[2]));
        }//if
        return buffer.array();
    }

    public static void parseInitResponse (byte[] bytes, ArrayList<Ip4Address> ipAddrs, ArrayList<Byte> types) {
        // Parse the rest of the command
        byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];
        // Find the position of this replica in the chain

        for (byte i = 0; i < ipsLen; ++i) {
            //TODO: Make sure that Ip4Address.valueOf function works as expected
            ipAddrs.add(Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET));
            types.add(bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN]);
        }//for
    }
}
