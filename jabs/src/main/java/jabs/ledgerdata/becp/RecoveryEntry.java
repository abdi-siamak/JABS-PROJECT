package jabs.ledgerdata.becp;

import java.util.HashMap;

import jabs.network.node.nodes.Node;

public class RecoveryEntry {
	private final Node sender; // sender
    private int timeout; // RePush timeout
    private final double replicaValue;
    private final double replicaWeight;
    private final int cycleNumber;
    private final HashMap<Integer, ReplicaBlock> replicaBlockCache; // blockID -> replica values [vp, wp, va, wa]

    public RecoveryEntry(final Node sender, final int cycleNumber, final int timeout, final double value, final double weight, final HashMap<Integer, ReplicaBlock> replicaBlockCache){
    	this.timeout = timeout;
        this.replicaValue = value;
        this.replicaWeight = weight;
        this.sender = sender;
        this.cycleNumber = cycleNumber;
        this.replicaBlockCache = replicaBlockCache;
    }

    public int getTimeout(){return timeout;}
    public double getReplicaValue(){return replicaValue;}
    public double getReplicaWeight(){return replicaWeight;}
	public Node getSender() {
		return sender;
	}

	public HashMap<Integer, ReplicaBlock> getReplicaBlockCache() {
		return replicaBlockCache;
	}

	public int getCycleNumber() {
		return cycleNumber;
	}
	
	public void decrementTimeout() {
		timeout = timeout - 1;
	}

	public void setTimeout(int time) {
		timeout = time;
	}
}