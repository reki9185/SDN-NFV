all: container_creation ovs_bridge_creation link_setup

container_creation:
	docker compose up -d

ovs_bridge_creation:
	sudo ovs-vsctl add-br ovsbr

link_setup:
	sudo ovs-vsctl set-controller ovsbr tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge ovsbr protocol=OpenFlow14
	sudo ovs-docker add-port ovsbr eth3 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port ovsbr eth3 R3 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port ovsbr eth3 R4 --ipaddress=172.20.0.4/16

clean:
	docker compose down
	sudo ovs-vsctl del-controller ovsbr
	sudo ovs-vsctl del-br ovsbr