#!/bin/bash
#set -x

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

# Creates a veth pair
# params: endpoint1 endpoint2
function create_veth_pair {
    ip link add $1 type veth peer name $2
    ip link set $1 up
    ip link set $2 up
}

# Add a container with a certain image
# params: image_name container_name
function add_container {
	docker run -dit --network=none --privileged --cap-add NET_ADMIN --cap-add SYS_MODULE \
		 --hostname $2 --name $2 ${@:3} $1
	pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$2"))
	mkdir -p /var/run/netns
	ln -s /proc/$pid/ns/net /var/run/netns/$pid
}

# Set container interface's ip address and gateway
# params: container_name infname [ipaddress] [gw addr]
function set_intf_container {
    pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$1"))
    ifname=$2
    ipaddr=$3
    echo "Add interface $ifname with ip $ipaddr to container $1"

    ip link set "$ifname" netns "$pid"
    if [ $# -ge 3 ]
    then
        ip netns exec "$pid" ip addr add "$ipaddr" dev "$ifname"
    fi
    ip netns exec "$pid" ip link set "$ifname" up
    if [ $# -ge 4 ]
    then
        ip netns exec "$pid" route add default gw $4
    fi
}

# Set container interface's ipv6 address and gateway
# params: container_name infname [ipaddress] [gw addr]
function set_v6intf_container {
    pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$1"))
    ifname=$2
    ipaddr=$3
    echo "Add interface $ifname with ip $ipaddr to container $1"

    if [ $# -ge 3 ]
    then
        ip netns exec "$pid" ip addr add "$ipaddr" dev "$ifname"
    fi
    ip netns exec "$pid" ip link set "$ifname" up
    if [ $# -ge 4 ]
    then
        ip netns exec "$pid" route -6 add default gw $4
    fi
}

# Connects the bridge and the container
# params: bridge_name container_name [ipaddress] [gw addr]
function build_bridge_container_path {
    br_inf="veth$1$2"
    container_inf="veth$2$1"
    create_veth_pair $br_inf $container_inf
    brctl addif $1 $br_inf
    set_intf_container $2 $container_inf $3 $4
}

# Connects two ovsswitches
# params: ovs1 ovs2
function build_ovs_path {
    inf1="veth$1$2"
    inf2="veth$2$1"
    create_veth_pair $inf1 $inf2
    ovs-vsctl add-port $1 $inf1
    ovs-vsctl add-port $2 $inf2
}

# Connects a container to an ovsswitch
# params: ovs container [ipaddress] [gw addr]
function build_ovs_container_path {
    ovs_inf="veth$1$2"
    container_inf="veth$2$1"
    create_veth_pair $ovs_inf $container_inf
    ovs-vsctl add-port $1 $ovs_inf
    set_intf_container $2 $container_inf $3 $4
}

HOSTIMAGE="sdnfv-final-host"
ROUTERIMAGE="sdnfv-final-frr"

# Build host base image
# docker build containers/host -t "$HOSTIMAGE"
# docker build containers/frr -t "$ROUTERIMAGE"

# TODO Write your own code
# add_container $ROUTERIMAGE R1 -v $(realpath config/R1/frr.conf):/etc/frr/frr.conf -v $(realpath config/daemons):/etc/frr/daemons
# add_container $ROUTERIMAGE R2 -v $(realpath config/R2/frr.conf):/etc/frr/frr.conf -v $(realpath config/daemons):/etc/frr/daemons

# add_container $HOSTIMAGE h1
# add_container $HOSTIMAGE h2

pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=R1"))
mkdir -p /var/run/netns
ln -s /proc/$pid/ns/net /var/run/netns/$pid

pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=R2"))
mkdir -p /var/run/netns
ln -s /proc/$pid/ns/net /var/run/netns/$pid

pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=h1"))
mkdir -p /var/run/netns
ln -s /proc/$pid/ns/net /var/run/netns/$pid

pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=h2"))
mkdir -p /var/run/netns
ln -s /proc/$pid/ns/net /var/run/netns/$pid

create_veth_pair vethR1h2 vethh2R1
set_intf_container R1 vethR1h2 172.17.4.1/24 
set_intf_container h2 vethh2R1 172.17.4.2/24 172.17.4.1
set_v6intf_container R1 vethR1h2 2a0b:4e07:c4:104::1/64
set_v6intf_container h2 vethh2R1 2a0b:4e07:c4:104::2/64 2a0b:4e07:c4:104::1

sudo ovs-vsctl add-br ovs1 -- set bridge ovs1 protocols=OpenFlow14 -- set-controller ovs1 tcp:192.168.100.1:6653
sudo ovs-vsctl add-br ovs2 -- set bridge ovs2 protocols=OpenFlow14 -- set-controller ovs2 tcp:192.168.100.1:6653
# sudo ovs-vsctl add-br ovs1 -- set bridge ovs1 protocols=OpenFlow14 -- set-controller ovs1 tcp:127.0.0.1:6653
# sudo ovs-vsctl add-br ovs2 -- set bridge ovs2 protocols=OpenFlow14 -- set-controller ovs2 tcp:127.0.0.1:6653

sudo ovs-docker add-port ovs1 vethonos R2 --ipaddress=192.168.100.3/24
sudo ovs-docker add-port ovs1 eth1 R1 --ipaddress=192.168.63.2/24
docker exec -it R1 ip -6 addr add fd63::2/64 dev eth1
sudo ovs-docker add-port ovs1 eth2 R2 --ipaddress=192.168.63.1/24
docker exec -it R2 ip -6 addr add fd63::1/64 dev eth2
sudo ovs-docker add-port ovs1 eth3 R2 --ipaddress=172.16.4.69/24
docker exec -it R2 ip -6 addr add 2a0b:4e07:c4:4::69/64 dev eth3
sudo ovs-docker add-port ovs2 eth4 R2 --ipaddress=192.168.70.4/24
docker exec -it R2 ip -6 addr add fd70::4/64 dev eth4
# sudo ovs-docker add-port ovs2 eth5 R2 --ipaddress=192.168.61.4/24
build_ovs_container_path ovs2 h1 172.16.4.2/24 172.16.4.69
set_v6intf_container h1 vethh1ovs2 2a0b:4e07:c4:4::2/64 2a0b:4e07:c4:4::69
build_ovs_path ovs1 ovs2

create_veth_pair veth0 veth1
sudo ovs-vsctl add-port ovs2 veth0
sudo ip a add 192.168.100.1/24 dev veth1

sudo ovs-vsctl add-port ovs2 TO_TA_VXLAN -- set interface TO_TA_VXLAN type=vxlan options:remote_ip=192.168.60.4