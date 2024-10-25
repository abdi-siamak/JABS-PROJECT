package jabs.ledgerdata.raft;

import jabs.network.node.nodes.Node;

public class RAFTResponseVoteMessage extends RAFTBlockVote {
    public RAFTResponseVoteMessage(Node voter) {
        super(0, voter, VoteType.RESPONSE);
    }
}
