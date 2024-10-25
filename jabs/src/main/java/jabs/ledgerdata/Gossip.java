package jabs.ledgerdata;

import jabs.network.node.nodes.Node;

public abstract class Gossip extends BasicData {
    private final Node sender;

    protected Gossip(int size, Node sender) {
        super(size);
        this.sender = sender;
    }

    public Node getSender() {
        return sender;
    }
}
