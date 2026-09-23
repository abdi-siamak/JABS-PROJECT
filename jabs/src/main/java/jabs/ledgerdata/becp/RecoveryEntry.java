package jabs.ledgerdata.becp;

import java.util.HashMap;

import jabs.network.node.nodes.Node;

public class RecoveryEntry {
	private final Node sender; // sender
    private int timeout; // RePush timeout
    private final double replicaValue;
    private final double replicaWeight;
    private final int cycleNumber;
	/*
	* Mass received from the sender's critical Push.
	* This is stored separately from replicaValue/replicaWeight,
	* which represent this receiver's outbound Pull mass.
	*/
	private final double incomingPushValue;
	private final double incomingPushWeight;
    private final HashMap<Integer, ReplicaBlock> replicaBlockCache; // blockID -> replica values [vp, wp, va, wa]
	/*
	* Block-specific PTP mass received in the sender's critical Push.
	* replicaBlockCache stores this receiver's outbound Pull half.
	* incomingPushBlockCache stores the sender's incoming Push half.
	*/
	private final HashMap<Integer, ReplicaBlock> incomingPushBlockCache;
	private RecoveryExchangeState recoveryExchangeState = RecoveryExchangeState.PENDING;

	public RecoveryEntry(final Node sender, final int cycleNumber, final int timeout, final double value, final double weight, final HashMap<Integer, ReplicaBlock> replicaBlockCache) {
    	this(sender, cycleNumber, timeout, value, weight, 0.0, 0.0, replicaBlockCache, new HashMap<>());
	}
    public RecoveryEntry(final Node sender, final int cycleNumber, final int timeout, final double value, final double weight, final double incomingPushValue, final double incomingPushWeight, final HashMap<Integer, ReplicaBlock> replicaBlockCache){
    	this(sender, cycleNumber, timeout, value, weight, incomingPushValue, incomingPushWeight, replicaBlockCache, new HashMap<>());
    }
	public RecoveryEntry(final Node sender, final int cycleNumber, final int timeout, final double value, final double weight, final double incomingPushValue, final double incomingPushWeight, final HashMap<Integer, ReplicaBlock> replicaBlockCache, final HashMap<Integer, ReplicaBlock> incomingPushBlockCache) {
		this.timeout = timeout;
		this.replicaValue = value;
		this.replicaWeight = weight;
		this.incomingPushValue = incomingPushValue;
		this.incomingPushWeight = incomingPushWeight;
		this.sender = sender;
		this.cycleNumber = cycleNumber;
		this.replicaBlockCache = replicaBlockCache;
		this.incomingPushBlockCache = incomingPushBlockCache;
	}

    public int getTimeout(){return timeout;}
    public double getReplicaValue(){return replicaValue;}
    public double getReplicaWeight(){return replicaWeight;}
	public double getIncomingPushValue() {return incomingPushValue;}
	public double getIncomingPushWeight() {return incomingPushWeight;}
	public Node getSender() {return sender;}
	public HashMap<Integer, ReplicaBlock> getReplicaBlockCache() {return replicaBlockCache;}
	public HashMap<Integer, ReplicaBlock> getIncomingPushBlockCache() {return incomingPushBlockCache;}

	public int getCycleNumber() {
		return cycleNumber;
	}
	
	public void decrementTimeout() {
		timeout = timeout - 1;
	}

	public void setTimeout(int time) {
		timeout = time;
	}

	public RecoveryExchangeState getRecoveryExchangeState() {
		return recoveryExchangeState;
	}

	/**
	 * Transitions this receiver-side recovery exchange out of PENDING.
	 *
	 * Allowed:
	 * PENDING -> MERGED
	 * PENDING -> RESTORED
	 *
	 * Repeating the same terminal transition returns false.
	 * Changing from one terminal state to the other is forbidden.
	 */
	public boolean transitionRecoveryExchangeState(RecoveryExchangeState newState) {
		if (newState == null) {
			throw new IllegalArgumentException("Recovery exchange state cannot be null.");
		}

		if (newState == RecoveryExchangeState.PENDING) {
			throw new IllegalArgumentException("Recovery exchange cannot transition back to PENDING.");
		}

		if (recoveryExchangeState == newState) {
			return false;
		}

		if (recoveryExchangeState.isTerminal()) {
			throw new IllegalStateException(
					"Recovery exchange already reached terminal state "
					+ recoveryExchangeState
					+ " and cannot transition to "
					+ newState
					+ ".");
		}

		recoveryExchangeState = newState;

		return true;
	}

	public boolean isRecoveryExchangeTerminal() {
		return recoveryExchangeState.isTerminal();
	}
}