package jabs.ledgerdata.raft;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class RAFTDecidedMessage<B extends Block<B>> extends RAFTBlockReplicationVote<B> {
	
    public RAFTDecidedMessage(Node sender,  B block) {
        super(block.getHash().getSize() + RAFT_VOTE_SIZE_OVERHEAD, sender, block, VoteType.DECIDED);
    }
}
