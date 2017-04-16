package org.Agent.app;

import org.Orchestrator.app.Commands;
import org.apache.commons.io.IOUtils;
import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

class AgentThread extends Thread {
    private Socket socket = null;
    private InputStream is = null;
    private byte[] bytes = null;
    private Ip4Address ipAddr = null;

    public AgentThread(Socket socket, Ip4Address ipAddr) {
        this.socket = socket;
        this.ipAddr = ipAddr;
    }

    @Override
    public void run() {

    }
}