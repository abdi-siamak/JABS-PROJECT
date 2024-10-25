package jabs.ledgerdata.becp;

import java.util.HashMap;

import jabs.network.node.nodes.Node;

public class PushEntry {
    private final Node destination; // destination
    private final int cycleNumber; // current cycle
    private final double aggregationValue;
    private final double aggregationWeight;
    private final HashMap<Integer, ReplicaBlock> replicaBlockCache; // blockID -> replica values [vp, wp, va, wa]
    private boolean isReceivedPull;
    private int timeout;

    public PushEntry(final Node destination, final int cycleNumber, final int timeout, final double value, final double weight, final HashMap<Integer, ReplicaBlock> replicaBlockCache) {
        this.destination = destination;
        this.cycleNumber = cycleNumber;
        this.aggregationValue = value;
        this.aggregationWeight = weight;
        this.replicaBlockCache = replicaBlockCache;
        this.timeout = timeout;
    }
    public Node getDestination(){return destination; }
    public int getCycleNumber(){return cycleNumber;}
    public double getAggregationValue(){return aggregationValue;}
    public double getAggregationWeight(){return aggregationWeight;}
	public boolean isReceivedPull() {
		return isReceivedPull;
	}
	public void setReceivedPull(boolean isReceivedPull) {
		this.isReceivedPull = isReceivedPull;
	}
	public HashMap<Integer, ReplicaBlock> getReplicaBlockCache() {
		return replicaBlockCache;
	}
	public int getTimeout() {
		return timeout;
	}
	
	public void decrementTimeout() {
		this.timeout = this.timeout - 1;
	}
}
