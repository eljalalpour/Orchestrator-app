/*
 * Copyright 2017-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.Orchestrator.app;
import org.apache.felix.scr.annotations.*;

import org.onosproject.net.Host;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.*;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

@Component(immediate = true)
public class OrchestratorApp {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    private Iterable<Host> availableHosts;
    private String[] chainElems;
    private DeviceListener deviceListener = new InnerDeviceListener();
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ArrayList<Host> replicaMapping;
    private static final String DELIM = ",";
    private String chain = "src_ip" + DELIM + "chain" + DELIM + "dst_ip";

    private static final int AGENT_PORT = 2222;

    private String[] parseChain(String chain) {
        return chain.split(DELIM);
    }

    public void setChain(String chain) {
        this.chain = chain;
        this.chainElems = parseChain(this.chain);
    }

    /**
     * The orchestrator sends a MB_INIT message to the replica.
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param ipAddr the IP of the host in which the click-instance must be initialized
     * @throws IOException
     */
    private void init(byte command, byte middleBox, InetAddress ipAddr) throws IOException {
        Socket replicaSocket = new Socket(ipAddr, AGENT_PORT);
        OutputStream out = replicaSocket.getOutputStream();
        out.write(Commands.getInitCommand(command, middleBox, replicaMapping, chainElems));
        out.close();
    }

    /**
     * Place click-instances of a chain
     * @param chainElems of the chain
     * @return
     */
    private void placeChain(String[] chainElems) throws IOException {
        boolean result = true;
        //TODO: check if we have enough hosts that we can install replicas on them

        for (int i = 1; i < chainElems.length - 1; ++i) {
            byte MB = Byte.parseByte(chainElems[i]);
            Host host = availableHosts.iterator().next();
            init(Commands.MB_INIT, MB, host.ipAddresses().iterator().next().toInetAddress());
            replicaMapping.add(host);
            availableHosts.iterator().remove();
        }//for
    }

    private void route(String[] chainElems){
        //TODO
    }

    private void reroute(String[] chainElems) {

    }

    private void deployChain(String chain) {
        chainElems = parseChain(chain);
        try {
            placeChain(chainElems);
            route(chainElems);
        }
        catch (IOException exception) {

        }//catch
    }

    private void recover(DeviceEvent deviceEvent) {
        // TODO: find the failed host if any, and remove it from replica mapping, replace with new host
    }

    @Activate
    protected void activate()
    {
        availableHosts = hostService.getHosts();

        // Listen for failures
        deviceService.addListener(deviceListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate()
    {
        log.info("Stopped");
    }

    /**
     * Inner Device Event Listener class.
     */
    private class InnerDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            switch (event.type()) {
                case PORT_ADDED:
                    log.info("PORT {} is added at time {} ", event.port(), event.time());
                    break;

                case PORT_REMOVED:
                    log.info("PORT {} is removed at time {}",event.port(), event.time());
                    recover(event);
                    break;

                default:
                    break;
            }//switch
        }
    }

    public static void main(String[] args) {
        OrchestratorApp orch = new OrchestratorApp();
        orch.setChain("127.0.0.1,0,1,127.0.0.1");
        try {
            orch.init(Commands.MB_INIT_AND_FETCH_STATE, (byte) 0, InetAddress.getByName("localhost"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}