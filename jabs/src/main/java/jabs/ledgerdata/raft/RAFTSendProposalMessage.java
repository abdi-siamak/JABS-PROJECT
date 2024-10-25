package jabs.ledgerdata.raft;

import jabs.ledgerdata.Block; 
import jabs.network.node.nodes.Node;

public class RAFTSendProposalMessage <B extends Block<B>> extends RAFTBlockReplicationVote<B>{
    public RAFTSendProposalMessage(Node sender,  B block) {
        super(block.getHash().getSize() + RAFT_VOTE_SIZE_OVERHEAD, sender, block, VoteType.REQUEST);
    }
}
