package jabs.ledgerdata.raft;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Vote;
import jabs.network.node.nodes.Node;

public class RAFTBlockReplicationVote <B extends Block<B>> extends Vote{
	private final B block;
    private final VoteType voteType;

    public static final int RAFT_VOTE_SIZE_OVERHEAD = 10;

    public enum VoteType {
    	REQUEST,
    	RESPONSE,
    	DECIDED
    }

    protected RAFTBlockReplicationVote(int size, Node sender, B block, VoteType voteType) {
        super(size, sender);
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
