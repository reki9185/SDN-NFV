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
package nycu.sdnfv.proxyarp;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.ndp.NeighborAdvertisement;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onlab.packet.IPacket;

import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortService;

import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;

import org.onosproject.net.flowobjective.FlowObjectiveService;

import java.nio.ByteBuffer;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ArpConfigListener arpListener = new ArpConfigListener();
    private final ConfigFactory<ApplicationId, ProxyarpConfig> factory
        = new ConfigFactory<ApplicationId, ProxyarpConfig>(
        APP_SUBJECT_FACTORY, ProxyarpConfig.class, "virtualarp") {
        @Override
        public ProxyarpConfig createConfig() {
            return new ProxyarpConfig();
        }
    };

    /** Some configurable property. */

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    private ProxyarpProcessor processor = new ProxyarpProcessor();
    private ApplicationId appId;
    // private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();
    private Map<Ip6Address, MacAddress> ndpTable = new HashMap<>();

    private MacAddress gatewayMac;
    private Ip4Address gatewayIP4;
    private Ip6Address gatewayIP6;

    @Activate
    protected void activate() {

        // register your app
        appId = coreService.registerApplication("nycu.sdnfv.proxyarp");

        cfgService.addListener(arpListener);
        cfgService.registerConfigFactory(factory);

        // add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for ipv4 packet-in
        TrafficSelector.Builder selectorIpv4 = DefaultTrafficSelector.builder();
        selectorIpv4.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selectorIpv4.build(), PacketPriority.REACTIVE, appId);

        // install a flowrule for ipv6 packet-in
        TrafficSelector.Builder selectorIpv6 = DefaultTrafficSelector.builder();
        selectorIpv6.matchEthType(Ethernet.TYPE_IPV6);
        packetService.requestPackets(selectorIpv6.build(), PacketPriority.REACTIVE, appId);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(arpListener);
        cfgService.unregisterConfigFactory(factory);

        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selectorIpv4 = DefaultTrafficSelector.builder();
        selectorIpv4.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selectorIpv4.build(), PacketPriority.REACTIVE, appId);

        TrafficSelector.Builder selectorIpv6 = DefaultTrafficSelector.builder();
        selectorIpv6.matchEthType(Ethernet.TYPE_IPV6);
        packetService.cancelPackets(selectorIpv6.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class ArpConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
            && event.configClass().equals(ProxyarpConfig.class)) {
                ProxyarpConfig config =
                cfgService.getConfig(appId, ProxyarpConfig.class);
                if (config != null) {
                    gatewayMac = config.getGatewayMac();
                    gatewayIP4 = config.getGatewayIPv4();
                    gatewayIP6 = config.getGatewayIPv6();

                    log.info("gateway-mac: " + gatewayMac);
                    log.info("gateway-ip4: " + gatewayIP4);
                    log.info("gateway-ip6: " + gatewayIP6);

                    arpTable.put(gatewayIP4, gatewayMac);
                    ndpTable.put(gatewayIP6, gatewayMac);
                }
            }
        }
    }

    private class ProxyarpProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber recPort = pkt.receivedFrom().port();

            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPkt = (ARP) ethPkt.getPayload();
                short opCode = arpPkt.getOpCode();

                if (opCode == ARP.OP_REPLY) {
                    // log.info("RECEIVED REPLY. Requested MAC = " + ethPkt.getDestinationMAC().toString());
                } else if (opCode == ARP.OP_REQUEST) {
                    Ip4Address srcIP  = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
                    Ip4Address dstIP  = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
                    MacAddress srcMac = ethPkt.getSourceMAC();
                    MacAddress dstMac = arpTable.get(dstIP);

                    // Proxy ARP learns IP-MAC mappings of the sender
                    arpTable.put(srcIP, srcMac);

                    // Look up the arpTable
                    if (arpTable.get(dstIP) == null) {
                        // log.info("Ipv4 TABLE MISS. Send requset to edge ports");
                        for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                            if (cp.equals(pkt.receivedFrom())) {
                                continue;
                            } else {
                                arpRequest(ethPkt, cp.deviceId(), cp.port());
                            }
                        }
                    } else {
                        // log.info("Ipv4 TABLE HIT. Requested MAC = " + dstMac.toString());
                        arpReply(ethPkt, dstIP, dstMac, recDevId, recPort);
                    }
                }
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                findNdp(ethPkt).ifPresentOrElse(
                    ndp -> neighborSolicitation(context, ethPkt, ndp),
                    () -> {
                        if (ethPkt.getPayload() instanceof NeighborAdvertisement) {
                            ndpAdv(context, ethPkt);
                        }
                    }
                );
            }
        }
    }

    private void arpRequest(Ethernet ethPkt, DeviceId devId, PortNumber port) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(port).build();
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());
        OutboundPacket pkt = new DefaultOutboundPacket(devId, treatment, buf);
        packetService.emit(pkt);
    }

    private void arpReply(Ethernet ethPkt, Ip4Address dstIP, MacAddress dstMac, DeviceId recDevId, PortNumber recPort) {
        // Create and send an ARP reply
        // public static Ethernet buildArpReply(Ip4Address srcIp, MacAddress srcMac, Ethernet request)
        Ethernet arpReply = ARP.buildArpReply(dstIP, dstMac, ethPkt);
        ByteBuffer buf = ByteBuffer.wrap(arpReply.serialize());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(recPort).build();
        OutboundPacket pkt = new DefaultOutboundPacket(recDevId, treatment, buf);
        packetService.emit(pkt);
    }

    private Optional<NeighborSolicitation> findNdp(Ethernet ethPkt) {
        return Stream.of(ethPkt)
                .filter(Objects::nonNull)
                .map(Ethernet::getPayload)
                .filter(p -> p instanceof IPv6)
                .filter(Objects::nonNull)
                .map(IPacket::getPayload)
                .filter(p -> p instanceof NeighborSolicitation)
                .map(p -> (NeighborSolicitation) p)
                .findFirst();
    }

    private void neighborSolicitation(PacketContext context, Ethernet ethPkt, NeighborSolicitation ndp) {
        Ip6Address dstIP = Ip6Address.valueOf(ndp.getTargetAddress());
        MacAddress dstMac = ndpTable.get(dstIP);
        ConnectPoint inport = context.inPacket().receivedFrom();

        if (dstMac == null) {
            // log.info("Ipv6 TABLE MISS. Send requset to neighbor solicitation.");
            for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                if (cp.equals(inport)) {
                    continue;
                }
                packetOut(context.inPacket(), cp);
            }
        } else {
            Ethernet naResponse = NeighborAdvertisement.buildNdpAdv(dstIP, dstMac, ethPkt);
            packetService.emit(new DefaultOutboundPacket(
                context.inPacket().receivedFrom().deviceId(),
                DefaultTrafficTreatment.builder().setOutput(context.inPacket().receivedFrom().port()).build(),
                ByteBuffer.wrap(naResponse.serialize())
            ));
        }
    }

    private void packetOut(InboundPacket pkt, ConnectPoint cp) {
        packetService.emit(
            new DefaultOutboundPacket(
                cp.deviceId(),
                DefaultTrafficTreatment.builder().setOutput(cp.port()).build(),
                pkt.unparsed()
            )
        );
    }

    private void ndpAdv(PacketContext context, Ethernet ethPkt) {
        NeighborAdvertisement naPkt = (NeighborAdvertisement) ethPkt.getPayload();
        Ip6Address srcIp = Ip6Address.valueOf(naPkt.getTargetAddress());
        MacAddress srcMac = ethPkt.getSourceMAC();

        ndpTable.put(srcIp, srcMac);
    }
}