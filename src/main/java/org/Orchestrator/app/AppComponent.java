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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.felix.scr.annotations.*;
import org.onosproject.core.CoreService;
import org.onosproject.net.host.*;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.ovsdb.controller.EventSubject;
import org.onosproject.ovsdb.controller.OvsdbEvent;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.ovsdb.controller.OvsdbEventSubject;
import org.slf4j.Logger;
import static org.onlab.util.Tools.toHex;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URI;
import java.net.URISyntaxException;

import org.onlab.packet.VlanId;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.PortNumber;
import org.onosproject.net.SparseAnnotations;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbEventListener;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent extends AbstractProvider implements HostProvider {
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected HostService hostService;
//
//    private HostListener hostListener = new InnerHostListener();
//
//    private final Logger log = LoggerFactory.getLogger(getClass());
//    @Activate
//    protected void activate()
//    {
//        log.info("Started");
//        hostService.addListener(hostListener);
//    }
//
//    @Deactivate
//    protected void deactivate()
//    {
//        log.info("Stopped");
//    }
//
//    /**
//     * Inner Host Event Listener class.
//     */
//    private class InnerHostListener implements HostListener {
//        @Override
//        public void event(HostEvent event) {
//            EventSubject subject = null;
//            if (event.subject() instanceof EventSubject) {
//                subject = (EventSubject) event.subject();
//            }//if
//            checkNotNull(subject, "EventSubject is not null");
//
//            switch (event.type()) {
//                case HOST_REMOVED:
//                    log.debug("Host is removed at time {}", event.time());
//
//            }//switch
//        }
//    }

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OvsdbController controller;

    private HostProviderService providerService;
    private OvsdbEventListener innerEventListener = new InnerOvsdbEventListener();

    @Activate
    public void activate() {
        providerService = providerRegistry.register(this);
        controller.addOvsdbEventListener(innerEventListener);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        providerRegistry.unregister(this);
        providerService = null;
        log.info("Stopped");
    }

    public AppComponent() {
        super(new ProviderId("ovsdb", "org.onosproject.ovsdb.provider.host"));
    }

    @Override
    public void triggerProbe(Host host) {
        log.info("Triggering probe on host {}", host);
    }

    private class InnerOvsdbEventListener implements OvsdbEventListener {

        @Override
        public void handle(OvsdbEvent<EventSubject> event) {
            OvsdbEventSubject subject = null;
            if (event.subject() instanceof OvsdbEventSubject) {
                subject = (OvsdbEventSubject) event.subject();
            }
            checkNotNull(subject, "EventSubject is not null");
            // If ifaceid is null,it indicates this is not a vm port.
            if (subject.ifaceid() == null) {
                return;
            }
            switch (event.type()) {
                case PORT_REMOVED:
                    HostId hostId = HostId.hostId(subject.hwAddress(), VlanId.vlanId());
                    DeviceId deviceId = DeviceId.deviceId(uri(subject.dpid().value()));
                    PortNumber portNumber = PortNumber.portNumber(subject
                            .portNumber().value(), subject.portName().value());
                    log.debug("Port {}, in Host {}, in device {}", portNumber.toString(), hostId, deviceId);
                    break;
                default:
                    break;
            }//switch
        }

    }

    public URI uri(String value) {
        try {
            return new URI("of", toHex(Long.valueOf(value)), null);
        } catch (URISyntaxException e) {
            return null;
        }
    }

}
