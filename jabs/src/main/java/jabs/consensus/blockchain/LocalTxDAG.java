package jabs.consensus.blockchain;

import jabs.ledgerdata.snow.AvalancheTx;  

import static jabs.network.node.nodes.snow.AvalancheNode.AVALANCHE_GENESIS_TX;
import java.util.*;
/**
 * File: Avalanche.java
 * Description: Implements Avalanche protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: December 28, 2023
 */
/**
 * Manages a Directed Acyclic Graph (DAG) of transactions received by a network node.
 *
 * @param <AvalancheTx> The transaction type supported by this DAG.
 * @param <Integer> the transaction identifier.
 */
public class LocalTxDAG {

    protected final HashMap<AvalancheTx, Integer> DAG = new HashMap<>();
    /**
     * The genesis transaction in the local DAG. The transaction with no parents. This should
     * be set at the first. Since genesis transaction is fixed for any network node.
     */
    private int highestID;
    private final AvalancheTx genesisTx;
    public HashMap<Integer, ArrayList<AvalancheTx>> conflicts = new HashMap<>();
    public HashMap<AvalancheTx, AvalancheTx> preferences = new HashMap<>();
    public HashMap<AvalancheTx, AvalancheTx> lastTx = new HashMap<>();
    public HashMap<AvalancheTx, Integer> count = new HashMap<>();
    public HashMap<AvalancheTx, Boolean> acceptance = new HashMap<>();
    public HashMap<AvalancheTx, ArrayList<AvalancheTx>> children = new HashMap<>();

    /**
     * Creates the local DAG transaction of a node.
     *
     * @param genesisLocalTx The transaction with no parents in the network
     */
    public LocalTxDAG() {
        this.DAG.put(AVALANCHE_GENESIS_TX, AVALANCHE_GENESIS_TX.getHeight());
        ArrayList<AvalancheTx> conflictTransactions = new ArrayList<>();
        conflictTransactions.add(AVALANCHE_GENESIS_TX);
        this.conflicts.put(AVALANCHE_GENESIS_TX.getHeight(), conflictTransactions);
        this.preferences.put(AVALANCHE_GENESIS_TX, AVALANCHE_GENESIS_TX);
        this.lastTx.put(AVALANCHE_GENESIS_TX, AVALANCHE_GENESIS_TX);
        this.count.put(AVALANCHE_GENESIS_TX, 1);
        this.acceptance.put(AVALANCHE_GENESIS_TX, true);
        this.genesisTx = AVALANCHE_GENESIS_TX;
        this.highestID = AVALANCHE_GENESIS_TX.getHeight();
    }

    /**
     * Adds a transaction to the local transaction DAG and updates its relationships with parents.
     *
     * @param tx The transaction to be added.
     */
    public void addTx(AvalancheTx tx) {
        this.DAG.put(tx, tx.getHeight());
        ArrayList<AvalancheTx> conflictTransactions = new ArrayList<>();
        conflictTransactions.add(tx);
        this.conflicts.put(tx.getHeight(), conflictTransactions);
        this.preferences.put(tx, tx);
        this.lastTx.put(tx, tx);
        this.count.put(tx, 1);
        this.acceptance.put(tx, false);
        for(AvalancheTx parent:tx.getParents()) {
        	if(DAG.containsKey(parent)){
        		if(!this.children.containsKey(parent)) {
        			ArrayList<AvalancheTx> child = new ArrayList<>();
        			child.add(tx);
        			this.children.put(parent, child);
        		}else {
        			ArrayList<AvalancheTx> children = this.children.get(parent);
        			children.add(tx);
        			this.children.put(parent, children);
        		}
        	}
        }
        if(tx.getHeight()>this.highestID) {
        	this.highestID = tx.getHeight();
        }
    }

    /**
     * Returns the total number of transactions inside the local transaction DAG.
     *
     * @return Number of transactions inside the local transaction DAG.
     */
    public int size() {
        return DAG.size();
    }

    /**
     * Returns the genesis transaction (transaction with no parents) set during node initialization.
     *
     * @return Genesis transaction.
     */
    public AvalancheTx getGenesisTx() {
        return this.genesisTx;
    }

    /**
     * Checks if a certain network transaction exists in the local transaction DAG of the node.
     *
     * @param tx The network transaction to check.
     * @return true if the transaction exists in the local DAG, false otherwise.
     */
    public boolean contains(AvalancheTx tx) {
        return this.DAG.containsKey(tx);
    }

    /**
     * Returns a hash set of all ancestors of the input transaction available in the local transaction DAG.
     *
     * @param tx The transaction for which ancestors are requested.
     * @return All ancestors in the local transaction DAG.
     */
    public HashSet<AvalancheTx> getAncestors(AvalancheTx tx) {
        HashSet<AvalancheTx> ancestors = new HashSet<>();
        getAncestorsRecursive(tx, ancestors);
        return ancestors;
    }

    
    private void getAncestorsRecursive(AvalancheTx tx, HashSet<AvalancheTx> ancestors) {
        // Base case: transaction has no parents (genesis transaction).
        if (tx.getParents()==null) {
            return;
        }
        for (AvalancheTx parent : tx.getParents()) {
            ancestors.add(parent);
            getAncestorsRecursive(parent, ancestors);
        }
    }
    
    /**
     * Performs a breadth-first search (BFS) on the DAG to return accepted transactions at each level.
     *
     * @return An ArrayList containing internal ArrayLists representing accepted transactions at each level.
     */
    public ArrayList<ArrayList<AvalancheTx>> getAcceptedTx() { // TO DO: should be checked!
        ArrayList<ArrayList<AvalancheTx>> result = new ArrayList<>();
        // Check if the DAG is empty
        if (DAG.isEmpty()) {
            return result;
        }
        Queue<AvalancheTx> queue = new LinkedList<>();
        Set<AvalancheTx> visited = new HashSet<>();
        queue.offer(genesisTx);  // Start BFS from the genesis transaction.
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            ArrayList<AvalancheTx> currentLevel = new ArrayList<>();
            for (int i = 0; i < levelSize; i++) {
                AvalancheTx currentTx = queue.poll();
                visited.add(currentTx);
                if (this.isAccepted(currentTx)) {
                    currentLevel.add(currentTx);
                }
                // Enqueue children for the next level
                if (this.getChildren(currentTx) != null) {
                    for (AvalancheTx child : this.getChildren(currentTx)) {
                        if (!visited.contains(child) && !queue.contains(child)) {
                            queue.offer(child);
                        }
                    }
                }
            }
            if (!currentLevel.isEmpty()) {
                result.add(currentLevel);
            }
        }
        return result;
    }
    
    /**
     * Returns a list of all transactions in the DAG.
     *
     * @return List of all transactions in the DAG.
     */
    public List<AvalancheTx> getDAGTransactions(){
        List<AvalancheTx> arrayList = new ArrayList<>(DAG.keySet());
        return arrayList;
    }
    
    /**
     * Returns the highest transaction ID in the local transaction DAG.
     *
     * @return The highest transaction ID.
     */
    public int getHighestTransactionID() {
    	return this.highestID;
    }
    
	public boolean isAccepted(AvalancheTx tx) {
		return this.acceptance.get(tx);
	}
	
	public void setAcceptance(AvalancheTx tx, boolean acceptanceStatus) {
		this.acceptance.put(tx, acceptanceStatus);
	}
	
	public ArrayList<AvalancheTx> getChildren(AvalancheTx tx){
		return this.children.get(tx);
	}
}
