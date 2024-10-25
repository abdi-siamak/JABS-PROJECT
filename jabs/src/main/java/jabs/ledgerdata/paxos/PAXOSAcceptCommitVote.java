package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block; 
import jabs.network.node.nodes.Node;

public class PAXOSAcceptCommitVote<B extends Block<B>> extends PAXOSBlockVote<B> {
	private final int n;
    public PAXOSAcceptCommitVote(Node voter, int n) {
        super(0, voter, null, VoteType.ACCEPT_OK);
        this.n = n;
    }
	public int getN() {
		return n;
	}
}
