package org.Orchestrator.app;
/**
 * Created by ejalalpo on 4/13/17.
 */
public class Commands {
    // Used to initialize the click-instance.
    // This command is used during the initial deployment.
    public static final byte MB_INIT = 0x00;

    // Used to initialize the instructs the agent to fetch the states from the other agents.
    // This command is used during the failure recovery.
    public static final byte MB_INIT_AND_FETCH_STATE = 0x01;

    // Used to get the state from the agent
    // This command is used during the failure recovery.
    public static final byte GET_STATE = 0x02;
}
