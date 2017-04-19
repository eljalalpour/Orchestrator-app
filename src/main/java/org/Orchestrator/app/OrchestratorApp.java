package org.Orchestrator.app;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MplsLabel;
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

@Component(immediate = true)
public class OrchestratorApp {
    private static final int AGENT_PORT = 2222;
    private static final int PUT_TAG_PRIORITY = 20;
    private static final int FORWARD_PRIORITY = 10;
    private static final int REMOVE_TAG_PRIORITY = 10;
    private static final int INCREMENT_TAG_PRIORITY = 10;
    private static int tag = 1;
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

    /**
     * The orchestrator sends a MB_INIT message to the replica.
     * @param command either initialize Commands.MB_INIT or Commands.MB_INIT_AND_FETCH_STATE
     * @param middleBox the middlebox that the agent should initialize inside the click-instance
     * @param ipAddr the IP of the host in which the click-instance must be initialized
     * @throws IOException
     */
    private void init(byte command, byte middleBox, InetAddress ipAddr, FaultTolerantChain chain) throws IOException {
        Socket replicaSocket = new Socket(ipAddr, AGENT_PORT);
        OutputStream out = replicaSocket.getOutputStream();
        out.write(Commands.getInitCommand(command, middleBox, chain));
        out.close();
    }

    /**
     * Place click-instances of a chain. Note that to each host only a single replica of all chains is assigned!
     * @param chain to be deployed
     */
    private void place(FaultTolerantChain chain) throws IOException {
        //TODO: check if we have enough hosts that we can install replicas on them

//        for (int i = 0; i < chain.length(); ++i) {
//            Host host = availableHosts.iterator().next();
//            Ip4Address ip = host.ipAddresses().iterator().next().getIp4Address();
//            init(Commands.MB_INIT, chain.getMB(i), ip.toInetAddress(), chain);
//            availableHosts.iterator().remove();
//            chain.replicaMapping.add(ip);
//        }//for
        Ip4Address ip = Ip4Address.valueOf("127.0.0.1");
        init(Commands.MB_INIT, chain.getMB(0), ip.toInetAddress(), chain);
        chain.replicaMapping.add(ip);

        Ip4Address ip2 = Ip4Address.valueOf("10.20.159.142");
        init(Commands.MB_INIT, chain.getMB(1), ip2.toInetAddress(), chain);
        chain.replicaMapping.add(ip2);
    }

    private void route(FaultTolerantChain chain){
      ArrayList<Ip4Address> chainIpAddresses = chain.getChainIpAddresses();
      Host src = hostService.getHostsByIp(chain.getSource()).iterator().next();
      Host dst = hostService.getHostsByIp(chain.getDestination()).iterator().next();
      for(byte i = 0; i < chainIpAddresses.size(); ++i) {
          Host s = hostService.getHostsByIp(chainIpAddresses.get(i)).iterator().next();
          Host t = hostService.getHostsByIp(chainIpAddresses.get(i+1)).iterator().next();
          Path path = findPath(s, t);
          for(Link l : path.links()) {
              DeviceId deviceId = l.src().deviceId();
              if(hostService.getConnectedHosts(deviceId).iterator().next().equals(s) && s.equals(src)) {
                    putTagRule(deviceId, src.location().port());
                    log.info("putTagRule on {} ", deviceId);
              }
              else if(hostService.getConnectedHosts(deviceId).iterator().next().equals(t) && t.equals(dst)) {
                    removeTagRule(deviceId, dst.location().port());
                    log.info("removeTagRule on {}", deviceId);
              }
              else if(hostService.getConnectedHosts(deviceId).iterator().next().equals(t)) {
                    incrementTagRule(deviceId, t.location().port());
                    log.info("incrementTagRule on {}", deviceId);
              }
              else {
                    forwardRule(deviceId, l.src().port());
                    log.info("forwardRule on {}", deviceId);
              }
          }
      }
    }

    private void putTagRule(DeviceId deviceId, PortNumber srcPort) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                .matchInPort(srcPort)
                .build();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment treatment = treatmentBuilder
                .pushMpls()
                .setMpls(MplsLabel.mplsLabel(tag))
                .build();
        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
        FlowRule flowRule = flowBuilder
                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
                .makePermanent()
                .withPriority(PUT_TAG_PRIORITY).build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private void forwardRule(DeviceId deviceId, PortNumber outputPort) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                .matchEthType((short) 0x8847)
                .matchMplsLabel(MplsLabel.mplsLabel(tag))
                .build();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment treatment = treatmentBuilder
                .setOutput(outputPort)
                .build();
        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
        FlowRule flowRule = flowBuilder
                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
                .makePermanent()
                .withPriority(FORWARD_PRIORITY).build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private void removeTagRule(DeviceId deviceId, PortNumber dstPort) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                .matchEthType((short) 0x8847)
                .matchMplsLabel(MplsLabel.mplsLabel(tag))
                .build();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment treatment = treatmentBuilder
                .popMpls()
                .setOutput(dstPort)
                .build();
        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
        FlowRule flowRule = flowBuilder
                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
                .makePermanent()
                .withPriority(REMOVE_TAG_PRIORITY).build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private void incrementTagRule(DeviceId deviceId, PortNumber outputPort) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector selector = selectorBuilder
                .matchEthType((short) 0x8847)
                .matchMplsLabel(MplsLabel.mplsLabel(tag))
                .build();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment treatment = treatmentBuilder
                .setMpls(MplsLabel.mplsLabel(++tag))
                .setOutput(outputPort)
                .build();
        FlowRule.Builder flowBuilder = new DefaultFlowRule.Builder();
        FlowRule flowRule = flowBuilder
                .forDevice(deviceId).withSelector(selector).withTreatment(treatment)
                .makePermanent()
                .withPriority(INCREMENT_TAG_PRIORITY).build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private Path findPath(Host s, Host t) {
        Path path = topologyService.getPaths(topologyService.currentTopology(), s.location().deviceId(), t.location().deviceId()).iterator().next();
        return path;
    }


    private void reroute(FaultTolerantChain chain) {
        //TODO
    }

    private void deployChain(String srcChainDst, byte f) {
        try {
            FaultTolerantChain chain = FaultTolerantChain.parse(srcChainDst, f);
            place(chain);
            placedChains.add(chain);
            route(chain);
        }//try
        catch (IOException ioExc) {

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

        log.info(" building a new falut tolerance chain - 1");
        OrchestratorApp orch = new OrchestratorApp();
        log.info(" building a new falut tolerance chain - 2");
        orch.deployChain("127.0.0.1,0,1,127.0.0.1", (byte)1);
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
        orch.deployChain("127.0.0.1,0,1,127.0.0.1", (byte)1);
    }
}