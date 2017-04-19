package org.Orchestrator.app;

import org.onlab.packet.Ip4Address;
import java.nio.ByteBuffer;

/**
 * Created by ejalalpo on 4/13/17.
 */
public class Commands {
    public static final int CMD_OFFSET = 0;
    public static final int MB_OFFSET = 1;
    public static final int F_OFFSET = 2;
    public static final int CHAIN_LENGTH_OFFSET = 3;
    public static final int FIRST_IP_OFFSET = 4;
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
     * Command Middlebox F ChainLength IP1-MB1  IP2-MB2  ...
     * 0       1         2 3           4        9        4 + ChainLength * 5
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param chain the chain
     * @return the byte string of the command
     */
    public static byte[] getInitCommand(byte command, byte middleBox, FaultTolerantChain chain) {
        byte ipsSize = (byte) ((Integer.SIZE / Byte.SIZE + 1) * chain.length());
        ByteBuffer buffer = (command == Commands.MB_INIT_AND_FETCH_STATE) ?
                ByteBuffer.allocate(ipsSize + 4) :
                ByteBuffer.allocate(3);

        buffer.put(command);
        buffer.put(middleBox);
        buffer.put(chain.getF());

        // If the command include fetch state, then the orchestrator has to provide the IP addresses of the other agents
        if (command == Commands.MB_INIT_AND_FETCH_STATE) {
            buffer.put((byte)chain.length());

            for (int i = 0; i < chain.replicaMapping.size(); ++i){
                buffer.put(chain.replicaMapping.get(i).toOctets());
                buffer.put(chain.getMB(i));
            }//for

            buffer.put(Ip4Address.valueOf("127.0.0.1").toOctets());
            buffer.put(chain.getMB(0));
            buffer.put(Ip4Address.valueOf("10.20.159.142").toOctets());
            buffer.put(chain.getMB(1));
        }//if
        return buffer.array();
    }

    public static FaultTolerantChain parseInitResponse(byte[] bytes) {
        // Parse the rest of the command
        byte ipsLen = bytes[CHAIN_LENGTH_OFFSET];
        // Find the position of this replica in the chain

        FaultTolerantChain chain = new FaultTolerantChain();
        chain.setF(bytes[F_OFFSET]);

        for (byte i = 0; i < ipsLen; ++i) {
            //TODO: Make sure that Ip4Address.valueOf function works as expected
//            ipAddrs.add(Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET));
//            types.add(bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN]);
            chain.appendToChain(bytes[i * REPLICA_LEN + FIRST_IP_OFFSET + IP_LEN]);
            chain.replicaMapping.add(Ip4Address.valueOf(bytes, i * REPLICA_LEN + FIRST_IP_OFFSET));
        }//for

        return chain;
    }
}
