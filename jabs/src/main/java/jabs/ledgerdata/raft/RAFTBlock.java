package jabs.ledgerdata.raft;

import jabs.ledgerdata.SingleParentBlock;
import jabs.network.node.nodes.Node;

public class RAFTBlock extends SingleParentBlock<RAFTBlock> {
    public static final int RAFT_BLOCK_HASH_SIZE = 32;

    public RAFTBlock(int size, int height, double creationTime, Node creator, RAFTBlock parent) {
        super(size, height, creationTime, creator, parent, RAFT_BLOCK_HASH_SIZE);
    }
}
