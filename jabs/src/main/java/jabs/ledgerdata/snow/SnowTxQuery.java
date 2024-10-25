package jabs.ledgerdata.snow;

import jabs.ledgerdata.Tx;
import jabs.network.node.nodes.Node;

public class SnowTxQuery <T extends Tx<T>> extends SnowTransactionMessage<T> {
    public SnowTxQuery(Node inquirer, T tx) {
        super(tx.getHash().getSize() + SNOW_QUERY_SIZE_OVERHEAD, inquirer, tx, SnowTransactionMessage.QueryType.QUERY);
    }
}
