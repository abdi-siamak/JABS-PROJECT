package jabs.ledgerdata.raft;

import jabs.ledgerdata.Tx;

public class RAFTTx extends Tx<RAFTTx> {
    protected RAFTTx(int size, int hashSize) {
        super(size, hashSize);
    }
}
