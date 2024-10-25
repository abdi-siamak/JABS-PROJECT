package jabs.network.p2p;

import jabs.network.networks.Network;
import jabs.network.node.nodes.Node;

public class BECPP2P extends AbstractP2PConnections {
    @Override
    public void connectToNetwork(Network network) {
        this.peerNeighbors.addAll(network.getAllNodes()); // assuming a fully-connected network
        this.getNode().getNodeNetworkInterface().connectNetwork(network, network.getRandom());
    }

    @Override
    public boolean requestConnection(Node node) {
        return false;
    }
}
