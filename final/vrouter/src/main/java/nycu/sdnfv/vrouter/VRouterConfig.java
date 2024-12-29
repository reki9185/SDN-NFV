package nycu.sdnfv.vrouter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.ConnectPoint;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
// import org.onlab.packet.Ip6Address;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class VRouterConfig extends Config<ApplicationId> {
    public static final String VRROUTING = "vrrouting";
    public static final String VRROUTING_MAC = "vrrouting-mac";
    public static final String GATEWAY_IP4 = "gateway-ip4";
    public static final String GATEWAY_IP6 = "gateway-ip6";
    public static final String GATEWAY_MAC = "gateway-mac";
    public static final String V4_PEERS = "v4-peers";
    public static final String V6_PEERS = "v6-peers";

    Function<String, String> func = (String e) -> {
        return e;
    };

    @Override
    public boolean isValid() {
        return hasFields(VRROUTING, VRROUTING_MAC, GATEWAY_IP4, GATEWAY_IP6, GATEWAY_MAC, V4_PEERS, V6_PEERS);
    }

    public ConnectPoint getVrroutingCP() {
        return ConnectPoint.fromString(get(VRROUTING, null));
    }

    public MacAddress getVrroutingMac() {
        return MacAddress.valueOf(get(VRROUTING_MAC, null));
    }

    public IpAddress getGatewayIPv4() {
        return IpAddress.valueOf(get(GATEWAY_IP4, null));
    }

    public IpAddress getGatewayIPv6() {
        return IpAddress.valueOf(get(GATEWAY_IP6, null));
    }

    public MacAddress getGatewayMac() {
        return MacAddress.valueOf(get(GATEWAY_MAC, null));
    }

    public ArrayList<IpAddress> getIPv4Peers() {
        List<String> peers = getList(V4_PEERS, func);
        ArrayList<IpAddress> peersIp = new ArrayList<>();

        for (String peerIp : peers) {
            peersIp.add(IpAddress.valueOf(peerIp));
        }

        return peersIp;
    }

    public ArrayList<IpAddress> getIPv6Peers() {
        List<String> peers = getList(V6_PEERS, func);
        ArrayList<IpAddress> peersIp = new ArrayList<>();

        for (String peerIp : peers) {
            peersIp.add(IpAddress.valueOf(peerIp));
        }

        return peersIp;
    }
}