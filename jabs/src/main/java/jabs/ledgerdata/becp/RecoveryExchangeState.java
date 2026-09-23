package jabs.ledgerdata.becp;

/**
 * Lifecycle of a REAP+ recovery exchange.
 *
 * A recovery exchange starts in PENDING and may transition
 * exactly once to one terminal state:
 *
 * PENDING -> MERGED
 * PENDING -> RESTORED
 */
public enum RecoveryExchangeState {
    PENDING,
    MERGED,
    RESTORED;

    public boolean isTerminal() {
        return this == MERGED || this == RESTORED;
    }
}