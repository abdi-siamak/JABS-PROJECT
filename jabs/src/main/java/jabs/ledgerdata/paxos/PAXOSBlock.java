package jabs.ledgerdata.paxos;

import jabs.ledgerdata.SingleParentBlock;
import jabs.network.node.nodes.Node;

public class PAXOSBlock extends SingleParentBlock<PAXOSBlock> {
    public static final int PAXOS_BLOCK_HASH_SIZE = 32;

    public PAXOSBlock(int size, int height, double creationTime, Node creator, PAXOSBlock parent) {
        super(size, height, creationTime, creator, parent, PAXOS_BLOCK_HASH_SIZE);
    }
}
