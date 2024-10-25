package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Vote;
import jabs.network.node.nodes.Node;

public abstract class PAXOSBlockVote<B extends Block<B>> extends Vote {
    private final B block;
    private final VoteType voteType;

    public static final int PAXOS_VOTE_SIZE_OVERHEAD = 10;

    public enum VoteType {
    	PREPARE,
    	PREPARE_OK,
    	ACCEPT,
    	ACCEPT_OK,
        DECIDED
    }

    protected PAXOSBlockVote(int size, Node voter, B block, VoteType voteType) {
        super(size, voter);
        this.block = block;
        this.voteType = voteType;
    }

    public VoteType getVoteType() {
        return this.voteType;
    }
    public B getBlock() {
        return this.block;
    }
}
