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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;

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
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;

import org.onosproject.net.intf.InterfaceService;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteEvent;
import org.onosproject.routeservice.RouteInfo;
import org.onosproject.routeservice.RouteListener;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.RouteTableId;

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
    private final NameConfigListener cfgListener = new NameConfigListener();

    private final ConfigFactory<ApplicationId, VRouterConfig> factory = new ConfigFactory<ApplicationId, VRouterConfig>(
            APP_SUBJECT_FACTORY, vrouterconfig.class, "router") {
        @Override
        public VRouterConfig createConfig() {
            return new VRouterConfig();
        }
    };

  private ApplicationId appId;
  private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();

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
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        // add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

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

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(vrouterconfig.class)) {
                interfaceService.getMatchingInterface(IpAddress.valueOf("192.168.70.4")).connectPoint();
            }
        }
    }

    private class Processor implements PacketProcessor {
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

            DeviceId recDevId = pkt.receivedFrom().deviceId();
            PortNumber recPort = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            Ip4Address ip1 = Ip4Address.valueOf(cfgService.getConfig(appId, NameConfig.class).ip1());
            Ip4Address ip2 = Ip4Address.valueOf(cfgService.getConfig(appId, NameConfig.class).ip2());
            MacAddress mac1 = MacAddress.valueOf(cfgService.getConfig(appId, NameConfig.class).mac1());
            MacAddress mac2 = MacAddress.valueOf(cfgService.getConfig(appId, NameConfig.class).mac2());

            arpTable.put(ip1, mac1);
            arpTable.put(ip2, mac2);

            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPkt = (ARP) ethPkt.getPayload();
                Ip4Address dstIP  = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
                if (arpPkt.getOpCode() == ARP.OP_REQUEST) {
                    if (arpTable.get(dstIP) != null) {
                        arpReply(ethPkt, dstIP, arpTable.get(dstIP), recDevId, recPort);
                        // log.info("TABLE HIT. Requested MAC = " + arpTable.get(dstIP).toString());
                    } else {
                        // log.info("TABLE MISS. Requested MAC = " + arpTable.get(dstIP).toString());
                    }
                }
            } else if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                ConnectPoint ingress = pkt.receivedFrom();
                String host1 = cfgService.getConfig(appId, NameConfig.class).host1();
                String host2 = cfgService.getConfig(appId, NameConfig.class).host2();
                // ConnectPoint egress1 = new ConnectPoint(DeviceId.deviceId(host2), PortNumber.portNumber(1));
                // ConnectPoint egress2 = new ConnectPoint(DeviceId.deviceId(host1), PortNumber.portNumber(1));
                ConnectPoint egress;

                TrafficSelector selector;

                // create intent h2 to h1
                if (dstMac.equals(MacAddress.valueOf(cfgService.getConfig(appId, NameConfig.class).mac1()))) {
                    // egress = new ConnectPoint(DeviceId.deviceId(host1), PortNumber.portNumber(1));
                    egress = ConnectPoint.deviceConnectPoint(host1);
                // create intent s2 to h2
                } else if (dstMac.equals(MacAddress.valueOf(cfgService.getConfig(appId, NameConfig.class).mac2()))) {
                    //  egress = new ConnectPoint(DeviceId.deviceId(host2), PortNumber.portNumber(1));
                    egress = ConnectPoint.deviceConnectPoint(host2);
                } else {
                    log.info("Egress `{}.`", dstMac);
                    return;
                }

                selector = DefaultTrafficSelector.builder()
                            .matchEthDst(dstMac)
                            .build();
                createIntent(ingress, egress, selector);
            }
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

    private void installRule(Meter meter) {
        TrafficSelector selector1 = DefaultTrafficSelector.builder()
            .matchInPort(PortNumber.portNumber(1))
            .matchEthType(Ethernet.TYPE_IPV4)
            .build();

        TrafficTreatment treatment1 = DefaultTrafficTreatment.builder()
            .group(GroupId.valueOf(1))
            .build();
        FlowRule flowRule1 = DefaultFlowRule.builder()
            .forDevice(DeviceId.deviceId("of:0000000000000001"))
            .withSelector(selector1)
            .withTreatment(treatment1)
            .withPriority(500)
            .fromApp(appId)
            .makePermanent()
            .build();
        flowRuleService.applyFlowRules(flowRule1);

        TrafficSelector selector2 = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchEthSrc(MacAddress.valueOf("00:00:00:00:00:01"))
            .build();

        TrafficTreatment treatment2 = DefaultTrafficTreatment.builder()
            .setOutput(PortNumber.portNumber(2))
            .meter(meter.id())
            .build();
        FlowRule flowRule2 = DefaultFlowRule.builder()
            .forDevice(DeviceId.deviceId("of:0000000000000004"))
            .withSelector(selector2)
            .withTreatment(treatment2)
            .withPriority(500)
            .fromApp(appId)
            .makePermanent()
            .build();
        flowRuleService.applyFlowRules(flowRule2);

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

}