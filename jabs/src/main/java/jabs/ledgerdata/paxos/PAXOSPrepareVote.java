package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class PAXOSPrepareVote<B extends Block<B>> extends PAXOSBlockVote<B> {
	private final int n;
    public PAXOSPrepareVote(Node voter, int n) {
        super(0, voter, null, VoteType.PREPARE);
        this.n = n;
    }
	public int getN() {
		return n;
	}
}
