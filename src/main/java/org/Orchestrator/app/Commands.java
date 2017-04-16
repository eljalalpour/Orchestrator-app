package org.Orchestrator.app;
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
}
