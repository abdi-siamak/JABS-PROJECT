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
    private RecoveryExchangeId recoveryExchangeId;
    private RecoveryExchangeState recoveryExchangeState = RecoveryExchangeState.PENDING;

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

    public RecoveryExchangeId getRecoveryExchangeId() {
        return recoveryExchangeId;
    }

    /**
     * Assigns the unique recovery exchange ID.
     * The ID may be assigned only once.
     */
    public void setRecoveryExchangeId(RecoveryExchangeId recoveryExchangeId) {
        if (recoveryExchangeId == null) {
            throw new IllegalArgumentException("Recovery exchange ID cannot be null.");
        }

        if (this.recoveryExchangeId != null) {
            if (this.recoveryExchangeId.equals(recoveryExchangeId)) {
                return;
            }

            throw new IllegalStateException("Recovery exchange ID cannot be replaced.");
        }

        this.recoveryExchangeId = recoveryExchangeId;
    }

    public RecoveryExchangeState getRecoveryExchangeState() {
        return recoveryExchangeState;
    }

    /**
     * A recovery exchange may leave PENDING exactly once:
     *
     * PENDING -> MERGED
     * PENDING -> RESTORED
     *
     * Repeating the same terminal transition is harmless and
     * returns false. Attempting to change one terminal state into
     * the other is a safety violation.
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
