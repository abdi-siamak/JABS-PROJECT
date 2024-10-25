package jabs.ledgerdata.snow;

import jabs.ledgerdata.Tx;
import jabs.network.node.nodes.Node;

public class SnowTxReply <T extends Tx<T>> extends SnowTransactionMessage<T> {
	private final boolean response;
    public SnowTxReply(Node inquirer, T tx, boolean response) {
        super(tx.getHash().getSize() + SNOW_QUERY_SIZE_OVERHEAD, inquirer, tx,SnowTransactionMessage.QueryType.REPLY);
        this.response = response;
    }
    
	public boolean getResponse() {
		return response;
	}
    
}
