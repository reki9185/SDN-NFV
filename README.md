# NYCU 2023 Fall SDN-NFV
[113-1] 軟體定義網路及網路功能虛擬化 (曾建超)

### Lab1: ONOS and Mininet Installation
1. Environment Introduce & Setup
- Overview introduction
- VirtualBox, Bazel, ONOS, Mininet and OVS Installation

2. Building virtual network
- Build ONOS
- Activate control plane function
- Create a topology controlled with Mininet

### Lab2: OpenFlow Protocol Observation and Flow Rule Installation
1. OpenFlow Messages
- Monitor Traffic between ONOS & Switches
- OpenFlow Message Observation

2. Install/Delete Flow Rules
- Rest, JSON file, and Curl introduction
- ONOS and Topology Setup

### Lab3: SDN-enabled Learning Bridge and Proxy ARP
1. Build ONOS Application Project
- Environment Setup
- Create an ONOS Application
- Build, Install, Activate, and Reinstall ONOS Application

2. Learning Bridge Function
- Switch functionality: When receives a packet, matches Destination MAC
    - Matched: Forwards packet via specified port
    - Not matched: Packet-in

- ONOS App functionality: When receives a Packet-in
    - Records Source MAC and incoming port (in forwarding table)
    - Looks up Destination MAC (in forwarding table)
        - Not found: Floods Packet-out.
        - Found: Sends Packet-out via designated port and installs flow rule on switch.

3. Proxy APR
- Sender sends ARP Request
- Edge switch Packet-Ins the Request to controller
- Proxy ARP learns IP-MAC mappings of the sender
- Proxy ARP looks up ARP table (For target IP-MAC mapping)
    - If mapping exist: Fetch target MAC and Packet-Outs ARP Reply (with target MAC) to the sender
    - Else (mapping not exist):
        - Floods ARP Request to edge ports except the port receiving ARP Request
        - When h2 receives ARP Request, h2 will Reply ARP packet.
        - Edge switch Packet-Ins the Reply to controller
        - Proxy ARP learns IP-MAC mapping from h2

### Lab4: Group / Meter / Intent
1. Implement Failover Group Workflow
2. Implement Drop Meter Workflow
3. Intent Service to create Route

### Lab5: Network Function Virtualization --Software Router and Containerization
Connect to ONOS CLI
`ssh -o "StrictHostKeyChecking =no" -o Globa lKnownHostsFile =/de v/null
-o UserK nownHostsFile=/de v/null onos@lo cal host -p 8101`

### Final Project: SDN Network as Virtual Router