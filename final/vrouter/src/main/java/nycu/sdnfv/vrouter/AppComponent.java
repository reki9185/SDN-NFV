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
import java.util.Set;
import java.util.HashSet;
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
import org.onosproject.net.intent.MultiPointToSinglePointIntent;

import org.onosproject.net.intf.InterfaceService;

import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.host.HostService;
import org.onosproject.net.Host;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
// import org.onosproject.net.flow.FlowRule;
// import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.routeservice.ResolvedRoute;
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
    private final VRouterConfigListener vRouterConfigListener = new VRouterConfigListener();
    // private final VRouterListener vRouterListener = new VRouterListener();

    private final ConfigFactory<ApplicationId, VRouterConfig> factory = new ConfigFactory<ApplicationId, VRouterConfig>(
            APP_SUBJECT_FACTORY, VRouterConfig.class, "router") {
        @Override
        public VRouterConfig createConfig() {
            return new VRouterConfig();
        }
    };

    private ApplicationId appId;
    // private Map<IpAddress, MacAddress> arpTable = new HashMap<>();
    private VRouterPacketProcessor processor = new VRouterPacketProcessor();

    ConnectPoint vrrCp;
    MacAddress vrrMac;
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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService  hostService;

    @Activate
    protected void activate() {
        // register your app
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        cfgService.addListener(vRouterConfigListener);
        cfgService.registerConfigFactory(factory);

        // routeService.addListener(vRouterListener);

        // add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(3));

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(vRouterConfigListener);
        cfgService.unregisterConfigFactory(factory);

        // routeService.removeListener(vRouterListener);

        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

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
                    vrrCp = config.getVrroutingCP();
                    vrrMac = config.getVrroutingMac();
                    gatewayIP4 = config.getGatewayIPv4();
                    gatewayIP6 = config.getGatewayIPv6();
                    gatewayMac = config.getGatewayMac();

                    peers4IP = config.getIPv4Peers();
                    peers6IP = config.getIPv6Peers();

                    log.info("vrrMac: " + vrrMac);

                    for (IpAddress ip4 : peers4IP) {
                        // Interface peerIntf = interfaceService.getMatchingInterface
                        //    (IpAddress.valueOf("192.168.70.4")).connectPoint();
                        ConnectPoint cp4 = interfaceService.getMatchingInterface(ip4).connectPoint();
                        peers4Cp.add(cp4);

                        // add intent for each peer
                        // R2: frr
                        IpAddress vrrIP = (IpAddress) interfaceService.getMatchingInterface(ip4)
                        .ipAddressesList().get(0).ipAddress();
                        log.info("Creating IPv4 Intent.");
                        bgpIntent4(cp4, vrrCp, vrrIP);
                        bgpIntent4(vrrCp, cp4, ip4);
                    }

                    for (Ip6Address ip6 : peers6IP) {
                        // Interface peerIntf = interfaceService.getMatchingInterface
                        // (IpAddress.valueOf("192.168.70.4")).connectPoint();
                        ConnectPoint cp6 = interfaceService.getMatchingInterface(ip6).connectPoint();
                        peers6Cp.add(cp6);

                        // add intent for each peer
                        // R2: frr
                        Ip6Address vrrIP = (Ip6Address) interfaceService.getMatchingInterface(ip6)
                        .ipAddressesList().stream()
                            .filter(ip -> ip.ipAddress().isIp6())
                            .map(InterfaceIpAddress::ipAddress)
                            .findFirst()
                            .orElse(null);
                        log.info("Creating IPv6 Intent.");
                        bgpIntent6(cp6, vrrCp, vrrIP);
                        bgpIntent6(vrrCp, cp6, ip6);
                    }
                }
            }
        }
    }

    private class VRouterPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                log.info("Hi.");
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
                // ResolvedRoute route = inRoute(context, dstIP);
                log.info("The packet is from `{}` to `{}`.", recDevId, dstIP);

                context.block();
                interDomain(context);
                context.send();

            } // else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6)
        }
    }

    private void createIntent(ConnectPoint ingress, ConnectPoint egress,
        TrafficSelector selector, TrafficTreatment treatment) {

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
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();
        createIntent(ingress, egress, selector, treatment);
    }

    private void bgpIntent6(ConnectPoint ingress, ConnectPoint egress, Ip6Address dstIP) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV6)
                    .matchIPDst(dstIP.toIpPrefix())
                    .build();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();
        createIntent(ingress, egress, selector, treatment);
    }

    private void intraDomain() {
        // bridge-app
        return;
    }

    private void interDomain(PacketContext context) {
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
            IPv4 payload = (IPv4) ethPkt.getPayload();
            IpAddress srcIP = IpAddress.valueOf(payload.getSourceAddress());
            IpAddress dstIP = IpAddress.valueOf(payload.getDestinationAddress());

            MacAddress dstMac = context.inPacket().parsed().getDestinationMAC();

            if (dstMac.equals(vrrMac)) {
                // external -> vrr
                // srcMac: gatewayMac | dstMac: dstMac
                Host dstHost = hostService.getHostsByIp(dstIP).iterator().next();

                // dstHost not exist -> in other area
                if (dstHost == null) {
                    transiant(context);
                } else {
                    ConnectPoint ingress = pkt.receivedFrom();
                    ConnectPoint egress = new ConnectPoint(dstHost.location().deviceId(), dstHost.location().port());

                    TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchIPDst(dstIP.toIpPrefix())
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .build();

                    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setEthSrc(gatewayMac)
                        .setEthDst(dstHost.mac())
                        .build();

                    createIntent(ingress, egress, selector, treatment);
                }
            } else {
                // vrr -> external
                // srcMac: vrrMac | dstMac: nextHop
                // Check here the next hop is
                ResolvedRoute route = inRoute(context, dstIP);
                if (route == null) {
                    log.info("Next hop doesn't exist.");
                    return;
                }

                ConnectPoint ingress = pkt.receivedFrom();
                ConnectPoint egress = interfaceService.getMatchingInterface(route.nextHop()).connectPoint();

                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchIPDst(dstIP.toIpPrefix())
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(vrrMac)
                    .setEthDst(route.nextHopMac())
                    .build();

                createIntent(ingress, egress, selector, treatment);
            }
        }
    }

    private void transiant(PacketContext context) {
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
            IPv4 payload = (IPv4) ethPkt.getPayload();
            IpAddress srcIP = IpAddress.valueOf(payload.getSourceAddress());
            IpAddress dstIP = IpAddress.valueOf(payload.getDestinationAddress());

            ResolvedRoute route = inRoute(context, dstIP);
            if (route != null) {
                ConnectPoint egress = interfaceService.getMatchingInterface(route.nextHop()).connectPoint();
                FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);
                Set<FilteredConnectPoint> ingresses = new HashSet<FilteredConnectPoint>();

                for (ConnectPoint ingress : peers4Cp) {
                    if (!ingress.equals(egress)) {
                        ingresses.add(new FilteredConnectPoint(ingress));
                        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                            ingress.deviceId(), ingress.port(), egress.deviceId(), egress.port());
                    }
                }

                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchIPDst(route.nextHop().toIpPrefix())
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(vrrMac)
                    .setEthDst(route.nextHopMac())
                    .build();

                MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                            .appId(appId)
                            .selector(selector)
                            .treatment(treatment)
                            .filteredIngressPoints(ingresses)
                            .filteredEgressPoint(egressPoint)
                            .priority(300)
                            .build();
                intentService.submit(intent);
            }
        } // else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6)
    }

    private ResolvedRoute inRoute(PacketContext context, IpAddress dstIP) {
        // Lookup the RouteTable to find the best route
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
            RouteTableId tableId = new RouteTableId("ipv4");
            Collection<RouteInfo> routes = routeService.getRoutes(tableId);
            for (RouteInfo routeInfo : routes) {
                ResolvedRoute route = routeInfo.bestRoute().get();
                if (route.prefix().contains(dstIP)) {
                    log.info("Dst: {}, Next Hop: {}", routeInfo.prefix(), routeInfo.bestRoute().get().nextHop());
                    return route;
                }
            }
        }
        return null;
    }
}