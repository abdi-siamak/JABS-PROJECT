package jabs.ledgerdata.paxos;

import jabs.ledgerdata.Tx;

public class PAXOSTx extends Tx<PAXOSTx> {
    protected PAXOSTx(int size, int hashSize) {
        super(size, hashSize);
    }
}
