package jabs.ledgerdata.becp;

import jabs.ledgerdata.Tx;

public class BECPTx extends Tx<BECPTx> {
    protected BECPTx(int size, int hashSize) {
        super(size, hashSize);
    }
}
