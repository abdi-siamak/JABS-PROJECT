package jabs.ledgerdata.snow;

import java.util.ArrayList;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

public class SnowReply <B extends Block<B>> extends SnowBlockMessage<B> {
    public SnowReply(Node inquirer, B block, int roundNumber, int cycleNumber, ArrayList<SnowBlock> prefBlockchain) {
        super(block.getHash().getSize() + SNOW_QUERY_SIZE_OVERHEAD, inquirer, block, QueryType.REPLY, roundNumber, cycleNumber, prefBlockchain);
    }
}
