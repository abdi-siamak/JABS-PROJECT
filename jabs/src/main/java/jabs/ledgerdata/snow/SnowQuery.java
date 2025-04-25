package jabs.ledgerdata.snow;

import java.util.ArrayList;
import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class SnowQuery <B extends Block<B>> extends SnowBlockMessage<B> {
    public SnowQuery(Node inquirer, B block, int roundNumber, int cycleNumber, ArrayList<SnowBlock> prefBlockchain) {
        super(block.getHash().getSize() + SNOW_QUERY_SIZE_OVERHEAD, inquirer, block, SnowBlockMessage.QueryType.QUERY, roundNumber, cycleNumber, prefBlockchain);
    }
}
