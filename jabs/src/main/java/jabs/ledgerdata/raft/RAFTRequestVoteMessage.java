package jabs.ledgerdata.raft;

import jabs.network.node.nodes.Node;

public class RAFTRequestVoteMessage extends RAFTBlockVote {
    public RAFTRequestVoteMessage(Node requester) {
        super(0, requester, VoteType.REQUEST);
    }
}
