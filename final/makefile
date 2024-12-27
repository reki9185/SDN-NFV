all:

deploy:
#	docker run --name onos -d -p 2620:2620 -p 6653:6653 -p 8181:8181 -p 8101:8101 \
-e ONOS_APPS=drivers,fpm,gui2,hostprovider,lldpprovider,openflow,openflow-base,optical-model,route-service \
onosproject/onos:2.7-latest
	docker compose up -d
	sudo ./topo_utils.sh

install:
#	onos-app localhost install! ./lab3/bridge-app/target/bridge-app-1.0-SNAPSHOT.oar
	onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar
	onos-app localhost install! proxyarp/target/proxyarp-1.0-SNAPSHOT.oar
	
config:
	onos-netcfg localhost vrouter.json

uninstall:
#	onos-app localhost deactivate nycu.winlab.bridge
#	onos-app localhost uninstall nycu.winlab.bridge
#	onos-app localhost deactivate nycu.winlab.proxyarp
#	onos-app localhost uninstall nycu.winlab.proxyarp
	onos-app localhost deonactivate nycu.sdnfv.vrouter
	onos-app localhost uninstall nycu.sdnfv.vrouter
	onos-app localhost deonactivate nycu.sdnfv.proxyarp
	onos-app localhost uninstall nycu.sdnfv.proxyarp

clean:
	docker compose down
	sudo ovs-vsctl del-controller ovs1
	sudo ovs-vsctl del-controller ovs2
	sudo ovs-vsctl del-br ovs1
	sudo ovs-vsctl del-br ovs2
	sudo ip link delete vethovs1ovs2
	sudo ip link delete veth0