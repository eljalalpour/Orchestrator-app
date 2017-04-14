package org.Agent.app;

import org.onlab.packet.Ip4Address;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Agent {
    public static final int AGENT_PORT = 2222;
    private static Socket clientSocket = null;
    //TODO: Implement initializing ipAddr
    private static Ip4Address ipAddr = null;

    public static void Main (String args[]) {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(AGENT_PORT);
        } catch (IOException e) {
            System.out.println(e);
            return;
        }//catch
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                new AgentThread(clientSocket, ipAddr).start();
            }//try
            catch(IOException ioExc) {

            }//catch
        }//while
    }
}
