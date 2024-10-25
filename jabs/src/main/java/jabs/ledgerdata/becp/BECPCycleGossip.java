package jabs.ledgerdata.becp;

import jabs.ledgerdata.Gossip; 
import jabs.network.node.nodes.Node;

public abstract class BECPCycleGossip extends Gossip {
    static final int GOSSIP_SIZE = 60;
    private final GossipType gossipType;

    public enum GossipType {
        VIEW_CHANGE,
        NEW_VIEW
    }

    protected BECPCycleGossip(Node sender, GossipType gossipType) {
        super(GOSSIP_SIZE, sender);
        this.gossipType = gossipType;
    }
}
