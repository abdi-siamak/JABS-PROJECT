package jabs.ledgerdata.snow;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class SnowReply <B extends Block<B>> extends SnowBlockMessage<B> {
    public SnowReply(Node inquirer, B block) {
        super(block.getHash().getSize() + SNOW_QUERY_SIZE_OVERHEAD, inquirer, block, QueryType.REPLY);
    }
}
