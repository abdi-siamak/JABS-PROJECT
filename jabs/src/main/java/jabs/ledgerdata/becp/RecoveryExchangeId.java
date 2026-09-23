package jabs.ledgerdata.becp;

import java.util.Objects;

/**
 * Globally unique identifier for one REAP+ recovery exchange.
 *
 * senderNodeId  = node that created the exchange
 * cycleNumber   = sender cycle when the exchange was created
 * sequenceNumber = monotonically increasing sender-local sequence
 */
public final class RecoveryExchangeId {
    private final int senderNodeId;
    private final int cycleNumber;
    private final long sequenceNumber;

    public RecoveryExchangeId(int senderNodeId, int cycleNumber, long sequenceNumber) {
        if (senderNodeId < 0) {
            throw new IllegalArgumentException("Recovery exchange sender ID cannot be negative.");
        }

        if (cycleNumber < 0) {
            throw new IllegalArgumentException("Recovery exchange cycle number cannot be negative.");
        }

        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("Recovery exchange sequence number cannot be negative.");
        }

        this.senderNodeId = senderNodeId;
        this.cycleNumber = cycleNumber;
        this.sequenceNumber = sequenceNumber;
    }

    public int getSenderNodeId() {
        return senderNodeId;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof RecoveryExchangeId)) {
            return false;
        }

        RecoveryExchangeId other = (RecoveryExchangeId) object;

        return senderNodeId == other.senderNodeId && cycleNumber == other.cycleNumber && sequenceNumber == other.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderNodeId, cycleNumber, sequenceNumber);
    }

    @Override
    public String toString() {
        return senderNodeId
                + ":"
                + cycleNumber
                + ":"
                + sequenceNumber;
    }
}