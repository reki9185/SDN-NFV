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

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.Ip4Address;

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

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

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

    private LearningBridgeProcessor processor = new LearningBridgeProcessor();
    private ApplicationId appId;
    // private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();

    @Activate
    protected void activate() {

        // register your app
        appId = coreService.registerApplication("nycu.sdnfv.proxyarp");

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
            ARP arpPkt = (ARP) ethPkt.getPayload();
            short opCode = arpPkt.getOpCode();

            if (ethPkt == null) {
                return;
            }

            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber recPort = pkt.receivedFrom().port();

            // Check if the packet is an ARP reply
            if (opCode == ARP.OP_REPLY) {
                log.info("RECEIVED REPLY. Requested MAC = " + ethPkt.getDestinationMAC().toString());
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {

                Ip4Address srcIP  = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
                Ip4Address dstIP  = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
                MacAddress srcMac = ethPkt.getSourceMAC();
                MacAddress dstMac = arpTable.get(dstIP);

                // Proxy ARP learns IP-MAC mappings of the sender
                arpTable.put(srcIP, srcMac);

                // Look up the arpTable
                if (arpTable.get(dstIP) == null) {
                    log.info("Ipv4 TABLE MISS. Send requset to edge ports");
                    for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                        if (cp.equals(pkt.receivedFrom())) {
                            continue;
                        } else {
                            arpRequest(ethPkt, cp.deviceId(), cp.port());
                        }
                    }
                } else if (opCode == ARP.OP_REQUEST) {
                    log.info("Ipv4 TABLE HIT. Requested MAC = " + dstMac.toString());
                    arpReply(ethPkt, dstIP, dstMac, recDevId, recPort);
                }
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {

                Ip6Address srcIP  = Ip6Address.valueOf(arpPkt.getSenderProtocolAddress());
                Ip6Address dstIP  = Ip6Address.valueOf(arpPkt.getTargetProtocolAddress());
                MacAddress srcMac = ethPkt.getSourceMAC();
                MacAddress dstMac = arpTable.get(dstIP);

                findNDP(ethPkt).ifPresent(ndPayload -> {
                    if (ndPayload instanceof NeighborSolicitation){
                        NeighborSolicitation ns = (NeighborSolicitation) ndPayload;
                        arpTable.put(srcIP, srcMac);

                        if (dstMac == null) {
                            log.info("Ipv6 TABLE MISS. Send requset to edge ports");
                            for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                                if (cp.equals(pkt.receivedFrom())) {
                                    continue;
                                } else {
                                    arpRequest(ethPkt, cp.deviceId(), cp.port());
                                }
                            }
                        } else {
                            log.info("Ipv6 TABLE HIT. Send Neighbor Advertisement");
                            NdpAdv(ethPkt, dstIP, dstMac, recDevId, recPort);
                        }
                    }
                });  
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

    private Optional<NeighborSolicitation>findNDP(Ethernet ethPkt) {
        reutrn Stream.of(packet)
                .filter(Objects::nonNull)
                .map(Ethernet::getPayload)
                .filter(p -> p instanceof Ipv6)
                .filter(Objects::nonNull)
                .map(IPacket::getPayload)
                .filter(p -> p instanceof NeighborSolicitation)
                .map(p -> (NeighborSolicitation) p)
                .findFirst();
    }

    private void NdpAdv(Ethernet ethPkt, Ip6Address dstIP, MacAddress datMac, DeviceId recDevIdId, PortNumber recPort) {
        Ethernet naPkt = NeighborAdvertisement.buildNdpAdv(dstIP, dstMac, ethPkt);
        ByteBuffer buf = ByteBuffer.wrap(naPkt.serialize());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(recPort).build();
        OutboundPacket pkt = new DefaultOutboundPacket(recDevId, treatment, buf);
        packetService.emit(pkt);
    }
}