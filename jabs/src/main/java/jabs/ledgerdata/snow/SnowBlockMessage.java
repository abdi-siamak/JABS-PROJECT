package jabs.ledgerdata.snow;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Query;
import jabs.network.node.nodes.Node;
public class SnowBlockMessage <B extends Block<B>> extends Query {
    private final B block;
    private final SnowBlockMessage.QueryType queryType;

    public static final int SNOW_QUERY_SIZE_OVERHEAD = 10;

    public enum QueryType {
        QUERY,
        REPLY
    }

    protected SnowBlockMessage(int size, Node inquirer, B block, SnowBlockMessage.QueryType queryType) {
        super(size, inquirer);
        this.block = block;
        this.queryType = queryType;
    }

    public SnowBlockMessage.QueryType getQueryType() {
        return this.queryType;
    }
    public B getBlock() {
        return this.block;
    }
}
