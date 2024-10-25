package jabs.ledgerdata.snow;

import jabs.ledgerdata.Tx; 
import jabs.ledgerdata.Query;
import jabs.network.node.nodes.Node;
 
public class SnowTransactionMessage <T extends Tx<T>> extends Query {
    private final T tx;
    private final SnowTransactionMessage.QueryType queryType;

    public static final int SNOW_QUERY_SIZE_OVERHEAD = 10;

    public enum QueryType {
        QUERY,
        REPLY
    }

    protected SnowTransactionMessage(int size, Node inquirer, T tx, SnowTransactionMessage.QueryType queryType) {
        super(size, inquirer);
        this.tx = tx;
        this.queryType = queryType;
    }

    public SnowTransactionMessage.QueryType getQueryType() {
        return this.queryType;
    }
    public T getTx() {
        return this.tx;
    }
}
