package jabs.network.message;

import jabs.ledgerdata.Gossip;

public class GossipMessage extends Message {
    private final Gossip gossip;

    public GossipMessage(Gossip gossip) {
        super(gossip.getSize());
        this.gossip = gossip;
    }

    public Gossip getGossip() {
        return gossip;
    }
}
