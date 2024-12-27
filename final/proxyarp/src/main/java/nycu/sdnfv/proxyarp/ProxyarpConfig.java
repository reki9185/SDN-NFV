/*
 * Copyright 2023-present Open Networking Foundation
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

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import java.util.function.Function;

public class ProxyarpConfig extends Config<ApplicationId> {
    public static final String GATEWAY_IP4 = "gateway-ip4";
    public static final String GATEWAY_IP6 = "gateway-ip6";
    public static final String GATEWAY_MAC = "gateway-mac";
    Function<String, String> func = (String e) -> {
        return e;
    };

    @Override
    public boolean isValid() {
        return hasFields(GATEWAY_IP4, GATEWAY_IP6, GATEWAY_MAC);
    }

    public Ip4Address getGatewayIPv4() {
        return Ip4Address.valueOf(get(GATEWAY_IP4, null));
    }

    public Ip6Address getGatewayIPv6() {
        return Ip6Address.valueOf(get(GATEWAY_IP6, null));
    }

    public MacAddress getGatewayMac() {
        return MacAddress.valueOf(get(GATEWAY_MAC, null));
    }
}