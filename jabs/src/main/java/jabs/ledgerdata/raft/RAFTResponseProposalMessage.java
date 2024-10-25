package jabs.ledgerdata.raft;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class RAFTResponseProposalMessage<B extends Block<B>> extends RAFTBlockReplicationVote<B> {
    public RAFTResponseProposalMessage(Node voter, B block) {
        super(block.getHash().getSize() + RAFT_VOTE_SIZE_OVERHEAD, voter, block, VoteType.RESPONSE);
    }

}
