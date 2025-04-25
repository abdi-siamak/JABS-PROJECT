package jabs.ledgerdata.becp;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Gossip;
import jabs.network.node.nodes.Node;

public abstract class BECPBlockGossip<B extends Block<B>> extends Gossip {
    private final GossipType gossipType;

    public static final int BECP_GOSSIP_SIZE_OVERHEAD = 10;

    public enum GossipType {
        PUSH,
        PULL,
        INFORM,
        REQUEST_UPDATE,
        RECEIVE_UPDATE
    }

    protected BECPBlockGossip(int size, Node sender, GossipType gossipType) {
        super(size, sender);
        this.gossipType = gossipType;
    }

    public GossipType getGossipType() {
        return this.gossipType;
    }
}
