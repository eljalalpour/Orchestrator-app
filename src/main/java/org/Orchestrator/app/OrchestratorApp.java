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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Dictionary;

@Component(immediate = true)
public class OrchestratorApp {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    private Iterable<Host> hosts;

    private DeviceListener deviceListener = new InnerDeviceListener();
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ArrayList<Host> replicaMapping;
    private static final String DELIM = ",";
    private String chain = "src_ip" + DELIM + "chain" + DELIM + "dst_ip";

    private static final int REPLICA_PORT = 2222;

    private String[] parseChain(String chain) {
        return chain.split(DELIM);
    }

    private void collectHostInformation() {
         hosts = hostService.getHosts();
    }

    /**
     * The orchestrator sends a MB_INIT message to the replica. The format of message is as follows
     * Command Middlebox IPsLength IP1  IP2  ...
     * 1       2         3         IPsLength * 4
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param host the host in which the click-instance must be initialized
     * @throws IOException
     */
    private void init(byte command, byte middleBox, Host host) throws IOException {
        byte ipsSize = (byte) (Integer.SIZE / Byte.SIZE * replicaMapping.size());
        ByteBuffer buffer = ByteBuffer.allocate(ipsSize + 3);
        buffer.put(command);
        buffer.put(middleBox);
        buffer.put(ipsSize);
        for(Host h : replicaMapping) {
            buffer.put(h.ipAddresses().iterator().next().getIp4Address().toOctets());
        }//for

        Socket replicaSocket = new Socket(host.ipAddresses().iterator().next().toInetAddress(), REPLICA_PORT);
        OutputStream out = replicaSocket.getOutputStream();
        out.write(buffer.array());
        out.close();
    }

    private boolean placeReplicas() {
        boolean result = true;

        return result;
    }


    @Activate
    protected void activate()
    {
        collectHostInformation();

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
                    collectHostInformation();
                    break;

                case PORT_REMOVED:
                    log.info("PORT {} is removed at time {}",event.port(), event.time());
                    break;

                default:
                    break;
            }//switch
        }
    }

}
