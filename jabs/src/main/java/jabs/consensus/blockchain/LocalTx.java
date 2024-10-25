package jabs.consensus.blockchain;

import jabs.ledgerdata.Tx;

import java.util.HashSet;

/**
 * LocalTx is used for transactions that resides inside a node memory.
 * LocalTx have more information attached to them. Like whether
 * they are connected by other available local transactions to genesis
 * or not.
 *
 * @param <T> any Transaction received by a node can be converted into a
 *           LocalTx
 */
public class LocalTx<T extends Tx<T>> {
    /**
     * The transaction that is received by node
     */
    final public T tx;

    /**
     * All children that the transaction has inside node local memory
     */
    final public HashSet<T> children;

    /**
     * Is the received transaction connected to genesis transaction by other
     * LocalTxs available in nodes memory or not.
     */
    public boolean isConnectedToGenesis = false;

    /**
     * Creates a Local transaction by taking a normal received transaction
     * @param transaction a normal transaction or network transaction that is
     *              received by the node.
     */
    public LocalTx(T transaction) {
        this.tx = transaction;
        this.children = new HashSet<>();
    }
}
