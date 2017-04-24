package org.Orchestrator.app;

import com.sun.org.apache.bcel.internal.generic.RETURN;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.VlanId;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Component(immediate = true)
public class OrchestratorApp {
    private static final int AGENT_PORT = 2222;
//    private static final int PUT_TAG_PRIORITY = 20;
    private static final int FORWARD_PRIORITY = 10;
//    private static final int REMOVE_TAG_PRIORITY = 10;
//    private static final int INCREMENT_TAG_PRIORITY = 10;
    private static byte tag = 1;

    private ApplicationId appId;

    private Iterable<Host> availableHosts;
    private ArrayList<FaultTolerantChain> placedChains = new ArrayList<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    private DeviceListener deviceListener = new InnerDeviceListener();
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private ApplicationService applicationService;



    /**
     * The orchestrator sends a MB_INIT message to the replica.
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param ipAddr the IP of the host in which the click-instance must be initialized
     * @throws IOException
     */
    private void init(byte command, byte middleBox, InetAddress ipAddr,
                      byte chainPos, byte firstVlanTag, FaultTolerantChain chain) throws IOException {
        Socket replicaSocket = new Socket(ipAddr, AGENT_PORT);
        OutputStream out = replicaSocket.getOutputStream();
        out.write(Commands.getInitCommand(command, middleBox, chainPos, firstVlanTag, chain));
        out.close();
    }

    /**
     * Place click-instances of a chain. Note that to each host only a single replica of all chains is assigned!
     * @param chain to be deployed
     */
    private void place(FaultTolerantChain chain) throws IOException {
        //TODO: check if we have enough hosts that we can install replicas on them

        for (byte i = 0; i < chain.length(); ++i) {
            Host host = availableHosts.iterator().next();
            Ip4Address ip = host.ipAddresses().iterator().next().getIp4Address();
            init(Commands.MB_INIT, chain.getMB(i), ip.toInetAddress(), i, chain.getFirstTag(), chain);
            availableHosts.iterator().remove();
            chain.replicaMapping.add(ip);
        }//for
        Ip4Address ip = Ip4Address.valueOf("127.0.0.1");
        init(Commands.MB_INIT, chain.getMB(0), ip.toInetAddress(), (byte)0, chain.getFirstTag(), chain);
        chain.replicaMapping.add(ip);

        Ip4Address ip2 = Ip4Address.valueOf("10.20.159.142");
        init(Commands.MB_INIT, chain.getMB(1), ip.toInetAddress(), (byte)1, chain.getFirstTag(), chain);
        chain.replicaMapping.add(ip2);
    }

    private boolean isIn(Set<Host> hosts, Host host) {
        boolean result = false;
        for (Host h : hosts) {
            if (host.equals(h)) {
                result = true;
                break;
            }//if
        }//for
        return result;
    }

    private void route(FaultTolerantChain chain){
        ArrayList<Ip4Address> chainIpAddresses = chain.getChainIpAddresses();

        // We assume that an IP address is assigned to a single host
        for(byte i = 0; i < chainIpAddresses.size() - 1; ++i) {
            Host s = hostService.getHostsByIp(chainIpAddresses.get(i)).iterator().next();
            Host t = hostService.getHostsByIp(chainIpAddresses.get(i + 1)).iterator().next();

            route(chain, s, t, (short)(chain.getFirstTag() + i));
        }//for
        route(chain, hostService.getHostsByIp(chainIpAddresses.get(
                chainIpAddresses.size()-2)).iterator().next(),
                hostService.getHostsByIp(chainIpAddresses.get(1)).iterator().next(),
                (short)(chainIpAddresses.size() + chain.getFirstTag() - 1));
    }

    private void route(FaultTolerantChain chain, Host s, Host t, short tag) {
        Host src = hostService.getHostsByIp(chain.getSource()).iterator().next();
        Host dst = hostService.getHostsByIp(chain.getDestination()).iterator().next();
        try {
            for (ConnectPoint cp: findPath(s, t)) {
                DeviceId deviceId = cp.deviceId();

                Set<Host> hosts = hostService.getConnectedHosts(deviceId);
                log.info("Hosts connected to {}", deviceId);
                for (Host h: hosts) {
                    log.info(h.id().toString());
                }//for

                boolean sIn = isIn(hosts, s);
                boolean tIn = isIn(hosts, t);

                log.info("sIn: {}, tIn: {}", sIn, tIn);

                if (sIn && s.equals(src)) {
                    forwardRule(deviceId, cp.port(), tag);
                }//if
                else if (tIn && t.equals(dst)) {
                    forwardRule(deviceId, dst.location().port(), tag);
                }//else if
                else if (tIn) {
                    forwardRule(deviceId, t.location().port(), tag);
                }//else if
                else {
                    forwardRule(deviceId, cp.port(), tag);
                }//else
            }//for
        }//try
        catch(NoSuchElementException nseExc) {
            nseExc.printStackTrace();
        }//catch
        // TODO a Host-to-Host intent between the last and fist middlebox
    }
//
//    private void putTagRule(DeviceId deviceId, PortNumber srcPort, PortNumber outputPort) {
//        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
//        TrafficSelector selector = selectorBuilder
//                .matchInPort(srcPort)
//                .matchVlanId(VlanId.NONE)
//                .build();
//
//        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
//        TrafficTreatment treatment = treatmentBuilder
////                .pushVlan()
//                .setVlanId(VlanId.vlanId(tag))
//                .setOutput(outputPort)
//                .build();
//        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
//        FlowRule flowRule = flowBuilder
//                .fromApp(appId)
//                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
//                .makePermanent()
//                .withPriority(PUT_TAG_PRIORITY).build();
//        flowRuleService.applyFlowRules(flowRule);
//    }

    private void forwardRule(DeviceId deviceId, PortNumber outputPort, short tag) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                //.matchEthType((short) 0x8847)
                .matchVlanId(VlanId.vlanId(tag))
                .build();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment treatment = treatmentBuilder
                .setOutput(outputPort)
                .build();
        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
        FlowRule flowRule = flowBuilder
                .fromApp(appId)
                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
                .makePermanent()
                .withPriority(FORWARD_PRIORITY).build();
        flowRuleService.applyFlowRules(flowRule);
    }

//    private void removeTagRule(DeviceId deviceId, PortNumber dstPort) {
//        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
//        TrafficSelector selector = selectorBuilder
//                //.matchEthType((short) 0x8847)
//                .matchVlanId(VlanId.vlanId(tag))
//                .build();
//        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
//        TrafficTreatment treatment = treatmentBuilder
////                .popVlan()
//                .setOutput(dstPort)
//                .build();
//        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
//        FlowRule flowRule = flowBuilder
//                .fromApp(appId)
//                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
//                .makePermanent()
//                .withPriority(REMOVE_TAG_PRIORITY).build();
//        flowRuleService.applyFlowRules(flowRule);
//    }

//    private void incrementTagRule(DeviceId deviceId, PortNumber outputPort) {
//        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
//        TrafficSelector selector = selectorBuilder
//                //.matchEthType((short) 0x8847)
//                .matchVlanId(VlanId.vlanId(tag))
//                .build();
//        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
//        TrafficTreatment treatment = treatmentBuilder
//                .setOutput(outputPort)
//                .build();
//        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
//        FlowRule flowRule = flowBuilder
//                .fromApp(appId)
//                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
//                .makePermanent()
//                .withPriority(INCREMENT_TAG_PRIORITY).build();
//        flowRuleService.applyFlowRules(flowRule);
//    }

//    private Path findPath(Host s, Host t) {
//        Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), s.location().deviceId(), t.location().deviceId());
//        log.info("paths size? {}", paths.size());
//
//        return topologyService.getPaths(topologyService.currentTopology(), s.location().deviceId(), t.location().deviceId()).iterator().next();
//    }

    /**
     * Find the path between two hosts. We assume that the network is connected, therefore there is always a path
     * between two hosts
     * @param s First host
     * @param t Second host
     * @return The ordered ids of devices in the path between s and t
     */
    private ArrayList<ConnectPoint> findPath(Host s, Host t) {
        ArrayList<ConnectPoint> devices = new ArrayList<>();
        try {
            Path path = topologyService.getPaths(
                    topologyService.currentTopology(),
                    s.location().deviceId(),
                    t.location().deviceId()).iterator().next();
            List<Link> links = path.links();
//            devices.add(s.location());
            for (Link l : links) {
                devices.add(l.src());
            }//for
            devices.add(links.get(links.size() - 1).dst());
//            devices.add(t.location());
        }//try
        catch(NoSuchElementException nExp){
            // If no path was found, it means that s and t are connected to a same device
            devices.add(t.location());
        }//catch
        return devices;
    }


    private void reroute(FaultTolerantChain chain, short failedIndex) {
        Host s = hostService.getHostsByIp(chain.replicaMapping.get(
                (failedIndex - 1 + chain.length()) % chain.length())).iterator().next();
        Host t = hostService.getHostsByIp(chain.replicaMapping.get(
                failedIndex)).iterator().next();
        Host u = hostService.getHostsByIp(chain.replicaMapping.get(
                (failedIndex + 1) % chain.length())).iterator().next();

        route(chain, s, t, (short)(chain.getFirstTag() + failedIndex - 1));
        route(chain, t, u, (short)(chain.getFirstTag() + failedIndex));

        if (failedIndex == 0) {
            Host src = hostService.getHostsByIp(chain.getSource()).iterator().next();
            route(chain, src, t, chain.getFirstTag());
        }//if
        else if (failedIndex == chain.length() - 1) {
            Host dst = hostService.getHostsByIp(chain.getDestination()).iterator().next();
            route(chain, t, dst, (short)(chain.getFirstTag() + chain.length()));
        }//else if
    }

    private void deployChain(String srcChainDst, byte f) {
//        try {
            FaultTolerantChain chain = FaultTolerantChain.parse(srcChainDst, f);
            chain.setFirstTag(tag);
//            place(chain);
//            placedChains.add(chain);
            chain.replicaMapping.add(Ip4Address.valueOf("192.168.200.14"));
            route(chain);
            tag += (short)chain.length() + 2;
//        }//try
//        catch (IOException ioExc) {
//
//        }//catch
    }

    private void recover(DeviceEvent deviceEvent) {
        // TODO: find the failed host if any, and remove it from replica mapping, replace with new host
        boolean found = false;
        try {
            HostId hostid = (HostId) deviceEvent.port().element().id();
            short i = 0;
            for(FaultTolerantChain ch : placedChains) {
                for(Host host : hostService.getHostsByIp(ch.replicaMapping.iterator().next())) {
                byte j = 0;
                    if(host.id().equals(hostid)) {
                        Host availableHost = availableHosts.iterator().next();
                        IpAddress hostIp = availableHost.ipAddresses().iterator().next();
                        init(Commands.MB_INIT_AND_FETCH_STATE, ch.getMB(j),
                                hostIp.toInetAddress(), j, ch.getFirstTag(), ch);
                        availableHosts.iterator().remove();
                        ch.replicaMapping.remove(j);
                        ch.replicaMapping.add(j, hostIp.getIp4Address());
                        reroute(ch, j);
                        found = true;
                    }//if
                    ++j;
                }//for
                if (found) break;
                ++i;
            }//for
        }//try
        catch(IOException ioExc) {

        }//catch
    }

    @Activate
    protected void activate()
    {
        this.appId = applicationService.getId("org.orchestrator.app");
        availableHosts = hostService.getHosts();

        // Listen for failures
        deviceService.addListener(deviceListener);

        deployChain("192.168.200.11,0,192.168.200.13", (byte)0);
    }

    @Deactivate
    protected void deactivate()
    {
        log.info("Stopped");
        flowRuleService.removeFlowRulesById(appId);
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
        orch.deployChain("127.0.0.1,0,1,127.0.0.1", (byte)0);
    }
}