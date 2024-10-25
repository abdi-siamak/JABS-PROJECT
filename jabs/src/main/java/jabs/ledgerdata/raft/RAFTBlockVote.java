package jabs.ledgerdata.raft;

import jabs.ledgerdata.Vote;
import jabs.network.node.nodes.Node;

public abstract class RAFTBlockVote extends Vote {
    private final VoteType voteType;
    
    public static final int RAFT_VOTE_SIZE_OVERHEAD = 10;

    public enum VoteType {
    	REQUEST,
    	RESPONSE,
    	HEARTBEAT
    }

    protected RAFTBlockVote(int size, Node voter, VoteType voteType) {
        super(size, voter);
        this.voteType = voteType;
    }

    public VoteType getVoteType() {
        return this.voteType;
    }

}
