package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Block; 
import jabs.network.node.nodes.Node;

public class PAXOSDecidedVote <B extends Block<B>> extends PAXOSBlockVote<B>{
	private final B v_a;
    public PAXOSDecidedVote(Node voter,  B block) {
        super(block.getHash().getSize() + PAXOS_VOTE_SIZE_OVERHEAD, voter, block, VoteType.DECIDED);
        this.v_a = block;
    }
    
	public B getV_a() {
		return v_a;
	}
}
