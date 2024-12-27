/*
 * Copyright 2024-present Open Networking Foundation
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
package nycu.sdnfv.vrouter;

// import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip6Address;
// import org.onlab.packet.IpAddress;
import org.onlab.packet.IPv4;
// import org.onlab.packet.IPv6;
// import org.onlab.packet.IpPrefix;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
// import org.onosproject.net.packet.OutboundPacket;
// import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;

import org.onosproject.net.intf.InterfaceService;

import org.onosproject.net.host.InterfaceIpAddress;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
// import org.onosproject.net.flow.FlowRule;
// import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flowobjective.FlowObjectiveService;

// import org.onosproject.routeservice.ResolvedRoute;
// import org.onosproject.routeservice.RouteEvent;
import org.onosproject.routeservice.RouteInfo;
// import org.onosproject.routeservice.RouteListener;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.RouteTableId;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final VRouterConfigListener vRouterListener = new VRouterConfigListener();

    private final ConfigFactory<ApplicationId, VRouterConfig> factory = new ConfigFactory<ApplicationId, VRouterConfig>(
            APP_SUBJECT_FACTORY, VRouterConfig.class, "router") {
        @Override
        public VRouterConfig createConfig() {
            return new VRouterConfig();
        }
    };

    private ApplicationId appId;
    // private Map<IpAddress, MacAddress> arpTable = new HashMap<>();
    // private VRouterPacketProcessor processor = new VRouterPacketProcessor();

    ConnectPoint frrCp;
    MacAddress frrMac;
    IpAddress gatewayIP4;
    Ip6Address gatewayIP6;
    MacAddress gatewayMac;

    ArrayList<IpAddress> peers4IP = new ArrayList<IpAddress>();
    ArrayList<ConnectPoint> peers4Cp = new ArrayList<ConnectPoint>();
    ArrayList<Ip6Address> peers6IP = new ArrayList<Ip6Address>();
    ArrayList<ConnectPoint> peers6Cp = new ArrayList<ConnectPoint>();

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Activate
    protected void activate() {
        // register your app
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        cfgService.addListener(vRouterListener);
        cfgService.registerConfigFactory(factory);

        // add a packet processor to packetService
        // packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(vRouterListener);
        cfgService.unregisterConfigFactory(factory);

        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        // packetService.removeProcessor(processor);
        // processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Stopped");
    }

    private class VRouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(VRouterConfig.class)) {

                VRouterConfig config = cfgService.getConfig(appId, VRouterConfig.class);
                if (config != null) {
                    frrCp = config.getFrroutingCP();
                    frrMac = config.getFrroutingMac();
                    gatewayIP4 = config.getGatewayIPv4();
                    gatewayIP6 = config.getGatewayIPv6();
                    gatewayMac = config.getGatewayMac();

                    peers4IP = config.getIPv4Peers();
                    peers6IP = config.getIPv6Peers();

                    log.info("frrMac: " + frrMac);

                    for (IpAddress ip4 : peers4IP) {
                        // Interface peerIntf = interfaceService.getMatchingInterface
                        //    (IpAddress.valueOf("192.168.70.4")).connectPoint();
                        ConnectPoint cp4 = interfaceService.getMatchingInterface(ip4).connectPoint();
                        peers4Cp.add(cp4);

                        // add intent for each peer
                        // R2: frr
                        IpAddress frrIP = (IpAddress) interfaceService.getMatchingInterface(ip4)
                        .ipAddressesList().get(0).ipAddress();
                        bgpIntent4(cp4, frrCp, frrIP);
                        bgpIntent4(frrCp, cp4, ip4);
                    }

                    for (Ip6Address ip6 : peers6IP) {
                        // Interface peerIntf = interfaceService.getMatchingInterface
                        // (IpAddress.valueOf("192.168.70.4")).connectPoint();
                        ConnectPoint cp6 = interfaceService.getMatchingInterface(ip6).connectPoint();
                        peers6Cp.add(cp6);

                        // add intent for each peer
                        // R2: frr
                        Ip6Address frrIP = (Ip6Address) interfaceService.getMatchingInterface(ip6)
                        .ipAddressesList().stream()
                            .filter(ip -> ip.ipAddress().isIp6())
                            .map(InterfaceIpAddress::ipAddress)
                            .findFirst()
                            .orElse(null);
                        bgpIntent6(cp6, frrCp, frrIP);
                        bgpIntent6(frrCp, cp6, ip6);
                    }
                }
            }
        }
    }

    private class VRouterPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            if (ethPkt.getDestinationMAC().isLldp()) {
                return;
            }

            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber recPort = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 payload = (IPv4) ethPkt.getPayload();
                IpAddress dstIP = IpAddress.valueOf(payload.getDestinationAddress());
                IpAddress srcIP = IpAddress.valueOf(payload.getSourceAddress());
                log.info("The packet is from `{}` to `{}`.", recDevId, dstIP);

                inRoute();

                /*if ((IpPrefix("172.16.4.0/24").contains(srcIP)) && (IpPrefix("172.16.4.0/24").contains(dstIP))) {
                    intraDomain();
                }

                if ((IpPrefix("172.16.4.0/24").contains(srcIP)) && (IpPrefix("172.16.4.0/24").contains(dstIP))) {
                    interDomain();
                }*/

            } // else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6)
        }
    }

    private void createIntent(ConnectPoint ingress, ConnectPoint egress, TrafficSelector selector) {

        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();
        FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
        FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

        PointToPointIntent intent = PointToPointIntent.builder()
            .appId(appId)
            .selector(selector)
            .treatment(treatment)
            .filteredIngressPoint(ingressPoint)
            .filteredEgressPoint(egressPoint)
            .priority(300)
            .build();

        intentService.submit(intent);

        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
            ingress.deviceId(), ingress.port(), egress.deviceId(), egress.port());
    }

    private void bgpIntent4(ConnectPoint ingress, ConnectPoint egress, IpAddress dstIP) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(dstIP.toIpPrefix())
                    .build();
        createIntent(ingress, egress, selector);
    }

    private void bgpIntent6(ConnectPoint ingress, ConnectPoint egress, Ip6Address dstIP) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV6)
                    .matchIPDst(dstIP.toIpPrefix())
                    .build();
        createIntent(ingress, egress, selector);
    }

    private void intraDomain() {
        // bridge-app
    }

    private void interDomain(PacketContext context) {
        // 1. Look up the RouteTable
        // 2. Check if the packet's dst = next hop -> intradomain()

        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 payload = (IPv4) ethPkt.getPayload();
        IpAddress srcIP = IpAddress.valueOf(payload.getSourceAddress());
        IpAddress dstIP = IpAddress.valueOf(payload.getDestinationAddress());

        // private RouteService routeService;
        /*if (IpPrefix.valueOf("172.17.4.0/24").contains(dstIP)) {
            Host dstHost = hostService.getHostsByIp(dstIP).iterator().next();
            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(pkt.receivedFrom());

            FilteredConnectPoint egressPoint = new FilteredConnectPoint(
                new ConnectPoint(dstHost.location().deviceId(), dstHost.location().port())
            );

            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf(dstIP.toString() + "/32"))
                .matchEthType(Ethernet.TYPE_IPV4);

            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                .setEthSrc(MacAddress.valueOf());
                .setEthDst(dstHost.mac());

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .priority(300)
                .filteredIngressPoint(ingressPoint)
                .filteredEgressPoint(egressPoint)
                .selector(selector.build())
                .treatment(treatment.build())
                .build();

            intentService.submit(intent);
        // ANS65041 -> ANS65040
        } else {
            // get bgp table in route service
        }*/
    }

    private boolean inRoute() {

        Collection<RouteTableId> routes = routeService.getRouteTables();
        ArrayList<RouteTableId> routeTable = new ArrayList<RouteTableId>(routes);
        for (RouteTableId tableId : routeTable) {
            log.info("Route Table: {}", tableId);
        }

        RouteTableId tableId = new RouteTableId("ipv4");
        Collection<RouteInfo> routes2 = routeService.getRoutes(tableId);
        for (RouteInfo route : routes2) {
            log.info("Route: {}, Next Hop: {}", route.prefix(), route.bestRoute().get().nextHop());
        }
        return true;
    }
}