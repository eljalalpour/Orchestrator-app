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
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class AppComponent {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    private DeviceListener deviceListener = new InnerDeviceListener();

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Activate
    protected void activate()
    {
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
                    break;
            }//switch
        }
    }
}
