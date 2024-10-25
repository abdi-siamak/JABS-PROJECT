package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class PAXOSPrepareCommitVote<B extends Block<B>> extends PAXOSBlockVote<B> {
	private final int n;
	private final int n_a;
	private final B v_a;
    public PAXOSPrepareCommitVote(Node voter, int n, int n_a, B block) {
        super(block.getHash().getSize() + PAXOS_VOTE_SIZE_OVERHEAD, voter, block, VoteType.PREPARE_OK);
        this.n = n;
        this.n_a = n_a;
        this.v_a = block;
    }
	public int getN_a() {
		return n_a;
	}
	public B getV_a() {
		return v_a;
	}
	public int getN() {
		return n;
	}
}
