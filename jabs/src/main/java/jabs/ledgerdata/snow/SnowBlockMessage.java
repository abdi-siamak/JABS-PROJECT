package jabs.ledgerdata.snow;

import java.util.ArrayList;
import java.util.HashSet;
import jabs.ledgerdata.Block;
import jabs.ledgerdata.Query;
import jabs.network.node.nodes.Node;

public class SnowBlockMessage <B extends Block<B>> extends Query {
    private final B block;
    private final ArrayList<SnowBlock> prefBlockchain;
    private final int roundNumber;
    private final int cycleNumber;
    private final SnowBlockMessage.QueryType queryType;

    public static final int SNOW_QUERY_SIZE_OVERHEAD = 10;

    public enum QueryType {
        QUERY,
        REPLY
    }

    protected SnowBlockMessage(int size, Node inquirer, B block, SnowBlockMessage.QueryType queryType, int roundNumber, int cycleNumber, ArrayList<SnowBlock> prefBlockchain) {
        super(size, inquirer);
        this.block = block;
        this.prefBlockchain = prefBlockchain;
        this.queryType = queryType;
        this.roundNumber = roundNumber;
        this.cycleNumber = cycleNumber;
    }

    public SnowBlockMessage.QueryType getQueryType() {
        return this.queryType;
    }
    public B getBlock() {
        return this.block;
    }

	public int getRoundNumber() {
		return roundNumber;
	}

	public ArrayList<SnowBlock> getPrefBlockchain() {
		return prefBlockchain;
	}

	public int getCycleNumber() {
		return cycleNumber;
	}
}
