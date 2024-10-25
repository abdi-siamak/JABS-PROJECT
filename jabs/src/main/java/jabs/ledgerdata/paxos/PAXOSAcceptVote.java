package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block; 
import jabs.network.node.nodes.Node;

public class PAXOSAcceptVote<B extends Block<B>> extends PAXOSBlockVote<B> {
	private final int n;
	private final B v_a;
    public PAXOSAcceptVote(Node voter,  B block, int n) {
        super(block.getHash().getSize() + PAXOS_VOTE_SIZE_OVERHEAD, voter, block, VoteType.ACCEPT);
        this.n = n;
        this.v_a = block;
    }
	public int getN() {
		return n;
	}
	
	public B getV_a() {
		return v_a;
	}
}
