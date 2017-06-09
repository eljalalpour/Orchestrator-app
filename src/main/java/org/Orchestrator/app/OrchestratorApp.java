package org.Orchestrator.app;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.VlanId;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.*;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.security.InvalidParameterException;
import java.util.*;

import static org.onlab.util.Tools.toHex;


@Component(immediate = true)
public class OrchestratorApp {
    private static final int AGENT_PORT = 2222;
    private static final int FORWARD_PRIORITY = 15;
    private static byte tag = 5;
    public static final String DELIM = ",";
    private static final int MIN_SRC_CHAIN_DST_LEN = 3;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    private ArrayList<Host> availableHosts;

    private ArrayList<FaultTolerantChain> placedChains = new ArrayList<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private ApplicationService applicationService;

    private HashMap <Short, ArrayList<FlowRule>> tagFlows;

    private HashMap<InetAddress, InetAddress> privateToPublicAddresses;

    private HostListener hostListener = new InnerHostListener();

//    private DeviceListener deviceListener = new InnerDeviceistener();
//
//    private OvsdbEventListener ovsdbListener = new InnerOvsdbEventListener();

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

    private int findAvailableHost(FaultTolerantChain chain) {
        int index = 0;
        for (; index < availableHosts.size(); ++index) {
            if (availableHosts.get(index).equals(chain.getSource()) ||
                    availableHosts.get(index).equals(chain.getDestination())) continue;
            break;
        }//for
        return index;
    }

    /**
     * Place click-instances of a chain. Note that to each host only a single replica of all chains is assigned!
     * @param chain to be deployed
     */
    private void place(FaultTolerantChain chain) throws Exception {
        //TODO: if we can deploy at source and destination, then we should check only the availableHosts.size()
        //if (chain.length() > availableHosts.size()) {
        if (chain.length() > availableHosts.size() - 2) {
            throw new Exception("not enough available hosts");
        }//if

        for (byte i = 0; i < chain.length(); ++i) {
            int index = findAvailableHost(chain);
            Host host = availableHosts.get(index);
            log.info("Host {} is chosen for the placement of MB {}", host.id(), i);
            Ip4Address ip = host.ipAddresses().iterator().next().getIp4Address();
            log.info("Init command is sent to IP address {}", privateToPublicAddresses.get(ip.toInetAddress()));
            init(Commands.MB_INIT, chain.getMB(i), privateToPublicAddresses.get(ip.toInetAddress()), i, chain.getFirstTag(), chain);
            chain.replicaMapping.add(host);
            availableHosts.remove(index);
        }//for
        placedChains.add(chain);

//        Ip4Address ip = Ip4Address.valueOf("127.0.0.1");
//        init(Commands.MB_INIT, chain.getMB(0), ip.toInetAddress(), (byte)0, chain.getFirstTag(), chain);
//        placedChains.add(chain);
//        chain.replicaMapping.add(ip);
//
//        Ip4Address ip2 = Ip4Address.valueOf("10.20.159.142");
//        init(Commands.MB_INIT, chain.getMB(1), ip2.toInetAddress(), (byte)1, chain.getFirstTag(), chain);
//        chain.replicaMapping.add(ip2);

    }

    private void route(FaultTolerantChain chain){
        // We assume that an IP address is assigned to a single host
        log.info("Routing");
        for(byte i = 0; i < chain.getChainHosts().size() - 1; ++i) {
            Host s = chain.getChainHosts().get(i);
            Host t = chain.getChainHosts().get(i + 1);

            route(s, t, (short)(chain.getFirstTag() + i));
        }//for
        route(chain.getChainHosts().get(
            chain.getChainHosts().size() - 2),
            chain.getChainHosts().get(1),
            (short)(chain.getChainHosts().size() + chain.getFirstTag() - 1));
    }

    private void route(Host s, Host t, short tag) {
        log.info("Routing between the source {} and the target {}", s, t);
        ArrayList<FlowRule> flowRules = new ArrayList<>();
        try {
            for (ConnectPoint cp: findPath(s, t)) {
                DeviceId deviceId = cp.deviceId();
                FlowRule flowRule = forwardRule(deviceId, cp.port(), tag);
                flowRules.add(flowRule);
            }//for
            tagFlows.put(tag, flowRules);
            log.info("adding tag {}", tag);
        }//try
        catch(NoSuchElementException nseExc) {
            nseExc.printStackTrace();
        }//catch
    }

    /**
     *
     * @param deviceId the deviceId of th eswitch
     * @param outputPort the port which matched packets should be sent to
     * @param tag VlanTag which packets are matched against
     */
    private FlowRule forwardRule(DeviceId deviceId, PortNumber outputPort, short tag) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                .matchEthType((short) 0x8100)
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
        return flowRule;
    }

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
            devices.add(t.location());
        }//try
        catch(NoSuchElementException nExp){
            // If no path was found, it means that s and t are connected to a same device
            devices.add(t.location());
        }//catch
        return devices;
    }

    private void reroute(FaultTolerantChain chain, short failedIndex) {
        Host s = chain.getChainHosts().get(failedIndex);
        Host t = chain.getChainHosts().get(failedIndex + 1); // +1 is for the source
        Host u = chain.getChainHosts().get(
                (failedIndex + 2) % (chain.length() + 2)); // +2 is for the source and the item after the failed one

        log.info("removing tags {}",
                (short)(chain.getFirstTag() + failedIndex));
        log.info("removing tags {}",
                (short)(chain.getFirstTag() + failedIndex));

        removeRules((short)(chain.getFirstTag() + failedIndex));

        log.info("removing tags {}",
                (short)(chain.getFirstTag() + failedIndex + 1));
        log.info("removing tags {}",
                (short)(chain.getFirstTag() + failedIndex + 1));

        removeRules((short)(chain.getFirstTag() + failedIndex + 1));

        route(s, t, (short)(chain.getFirstTag() + failedIndex));
        route(t, u, (short)(chain.getFirstTag() + failedIndex + 1));

        if (failedIndex == 0 || failedIndex == chain.length() - 1) {
            short tag = (short)(chain.getFirstTag() + chain.length() + 1);
            log.info("removing tag {}", tag);
            log.info("removing tags {}", tag);
            removeRules(tag);
            route(chain.getChainHosts().get(chain.getChainHosts().size() - 2),
                  chain.getChainHosts().get(1),
                  tag);
        }//if
        log.info("At the end of reroute!");
        log.info("At the end of reroute!");
    }

    private void removeRules(short tag) {
        for (FlowRule flowRule : tagFlows.get(tag)) {
            flowRuleService.removeFlowRules(flowRule);
        }
    }

    private void deployChain(String srcChainDst, byte f) {
        try {
            FaultTolerantChain chain = parse(srcChainDst, f);
            chain.setFirstTag(tag);
            place(chain);
            route(chain);
            tag += (short)chain.length() + 2;
        }//try
        catch (IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
        catch (Exception exc){
            exc.printStackTrace();
        }//catch

//        try {
//
//            Ip4Address ip = Ip4Address.valueOf("127.0.0.1");
//            byte ipsSize = 1;
//            ByteBuffer buffer = ByteBuffer.allocate(5);
//
//            buffer.put((byte) 0);
//            buffer.put((byte) 0);
//            buffer.put((byte) 0);
//            buffer.put((byte) 0);
//            buffer.put((byte) 1);
//
//            Socket replicaSocket = new Socket(Ip4Address.valueOf("127.0.0.1").toInetAddress(), AGENT_PORT);
//            OutputStream out = replicaSocket.getOutputStream();
//            out.write(buffer.array());
//            out.close();
//        }//tru
//        catch(IOException ioExc) {
//            ioExc.printStackTrace();
//        }//catch
    }

    private void recover(HostEvent hostEvent) {
        // TODO: find the failed host if any, and remove it from replica mapping, replace with new host
        log.info("Recovering...");
        log.info("Recovering...");
        boolean found = false;
        try {
//            HostId hostId = (HostId) deviceEvent.port().element().id();
            HostId hostId = hostEvent.subject().id();
            log.info("Host {} is down\n", hostId);
            log.info("Host {} is down", hostId);

            log.info("Searching for the failed chain\n");
            log.info("Searching for the failed chain");
            for(FaultTolerantChain ch : placedChains) {
                log.info("Searching for the failed host in chain\n");
                log.info("Searching for the failed host in chain");
                log.info("Replica mapping size: {}\n", ch.replicaMapping.size());
                log.info("Replica mapping size: {}", ch.replicaMapping.size());
                byte j = 0;
                for(Host host : ch.replicaMapping) {
                    log.info("Check host {}", host.id());
                    log.info("Check host {}", host.id());

                    if(host.id().equals(hostId)) {
                        log.info("The failed host is found!");
                        log.info("The failed host is found!");




                        ch.getMB(j);
                        hostEvent.subject().ipAddresses().iterator().next().toInetAddress();
                        privateToPublicAddresses.get(hostEvent.subject().ipAddresses().iterator().next().toInetAddress());
                        ch.getFirstTag();
                        init(Commands.KILL_MIDDLEBOX, ch.getMB(j), privateToPublicAddresses.get(hostEvent.subject().ipAddresses().iterator().next().toInetAddress()), j, ch.getFirstTag(), ch);

                        int index = findAvailableHost(ch);
                        Host availableHost = availableHosts.get(index);
                        log.info("Host {} is chosen for recovery", availableHost.id());
                        log.info("Host {} is chosen for recovery", availableHost.id());

                        ch.replicaMapping.remove(j);
                        ch.replicaMapping.add(j, availableHost);
                        init(Commands.MB_INIT_AND_FETCH_STATE, ch.getMB(j),
                                privateToPublicAddresses.get(availableHost.ipAddresses().iterator().next().toInetAddress())
                                , j, ch.getFirstTag(), ch);
                        availableHosts.remove(index);
                        reroute(ch, j);
                        found = true;
                        break;
                    }//if
                    ++j;
                }//for
                if (found) break;
            }//for
        }//try
        catch(IOException ioExc) {
            ioExc.printStackTrace();
        }//catch
        log.info("At the end of recovery!");
        log.info("At the end of recovery!");
    }

    public FaultTolerantChain parse(String srcChainDst, byte f) throws InvalidParameterException {
        String[] elems = srcChainDst.split(DELIM);
        if (elems.length < MIN_SRC_CHAIN_DST_LEN)
            throw new InvalidParameterException(String.format("The length of (source + chain + destination) " +
                    "must be higher than %d", MIN_SRC_CHAIN_DST_LEN));

        ArrayList<Byte> MBs = new ArrayList<>(elems.length - 2);
        for (int i = 0; i < elems.length - 2; ++i)
            MBs.add(Byte.parseByte(elems[i + 1]));

        return new FaultTolerantChain(
                hostService.getHostsByIp(Ip4Address.valueOf(elems[0])).iterator().next(),
                hostService.getHostsByIp(Ip4Address.valueOf(elems[elems.length - 1])).iterator().next(),
                MBs,
                f);
    }

    @Activate
    protected void activate() {
        privateToPublicAddresses = new HashMap<>();
        try {
//            privateToPublicAddresses.put(InetAddress.getByName("192.168.200.11"), InetAddress.getByName("10.12.4.11"));
//            privateToPublicAddresses.put(InetAddress.getByName("192.168.200.10"), InetAddress.getByName("10.12.4.3"));
//            //privateToPublicAddresses.put(InetAddress.getByName("192.168.200.15"), InetAddress.getByName("10.12.4.6"));
//            privateToPublicAddresses.put(InetAddress.getByName("192.168.200.17"), InetAddress.getByName("10.12.4.3"));
//            privateToPublicAddresses.put(InetAddress.getByName("192.168.200.12"), InetAddress.getByName("10.12.4.10"));
//            privateToPublicAddresses.put(InetAddress.getByName("192.168.200.18"), InetAddress.getByName("10.12.4.3"));

            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.17"), InetAddress.getByName("10.12.4.15"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.18"), InetAddress.getByName("10.12.4.15"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.4"), InetAddress.getByName("10.12.4.16"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.6"), InetAddress.getByName("10.12.4.17"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.7"), InetAddress.getByName("10.12.4.18"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.5"), InetAddress.getByName("10.12.4.19"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.8"), InetAddress.getByName("10.12.4.20"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.9"), InetAddress.getByName("10.12.4.21"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.10"), InetAddress.getByName("10.12.4.13"));
            privateToPublicAddresses.put(InetAddress.getByName("192.168.201.11"), InetAddress.getByName("10.12.4.14"));

        }catch (UnknownHostException uhExc){
            uhExc.printStackTrace();
        }
        this.appId = applicationService.getId("org.orchestrator.app");
        availableHosts = new ArrayList<>();

        for (Iterator<Host> h = hostService.getHosts().iterator(); h.hasNext(); /*empty*/) {
            Host host = h.next();
            log.info("Host {} is available!", host);
            try {
                privateToPublicAddresses.get(host.ipAddresses().iterator().next().toInetAddress());
                availableHosts.add(host);
            }//try
            catch(NoSuchElementException e) {}//catch
        }//for


        tagFlows = new HashMap<>();

        // Listen for failures
        hostService.addListener(hostListener);
        log.info("host listener added!");

        deployChain("192.168.201.17,0,1,192.168.201.18", (byte)1);
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
    private class InnerHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            log.info("In host event handler!");
            log.info("In host event handler!");
            switch (event.type()) {
                case HOST_ADDED:
                    log.info("Host {} is added!", event.subject());
                    log.info("Host {} is added!\n", event.subject());
                    break;

                case HOST_REMOVED:
                    log.info("Host {} is removed!", event.subject());
                    log.info("Host {} is removed!\n", event.subject());
                    recover(event);
                    break;

                default:
                    break;
            }//switch
        }
    }

//    public static void main(String[] args) {
//        OrchestratorApp orch = new OrchestratorApp();
//        orch.deployChain("127.0.0.1,0,127.0.0.1", (byte)0);
//    }
}