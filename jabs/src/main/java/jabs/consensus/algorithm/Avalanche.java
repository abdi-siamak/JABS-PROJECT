package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalTxDAG; 
import jabs.ledgerdata.Tx;
import jabs.ledgerdata.Block;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.snow.*;
import jabs.network.message.QueryMessage;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.AvalancheNode;
import jabs.scenario.AvalancheScenario;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static jabs.network.node.nodes.snow.AvalancheNode.AVALANCHE_GENESIS_TX;
/**
 * File: Avalanche.java
 * Description: Implements Avalanche protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */

public class Avalanche<B extends Block<B>, T extends Tx<T>> extends AbstractDAGBasedConsensus<B, T>
        implements QueryingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {
	
    //*-------------------------------------------------------------------------------------------------------
    // the Avalanche protocol settings:
    private static final int K = 20; // sample size.
    private static final double ALPHA = 0.8; // quorum size threshold.
    private static final int BETA_1 = 50; // confidence threshold.
    private static final int BETA_2 = 100;
    //*-------------------------------------------------------------------------------------------------------
    private static final boolean WRITE_ACCEPTED_LOGS = false; //write logs for the accepted transactions?
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //*-------------------------------------------------------------------------------------------------------
    File directory = AvalancheScenario.directory;
    private static PrintWriter writer;
    private final int numAllParticipants;
    private HashMap<AvalancheTx, HashMap<Integer, Boolean>> queries = new HashMap<>();
    private SnowPhase snowPhase = SnowPhase.QUERYING;
    public HashMap<AvalancheTx, Integer> confidenceValues = new HashMap<>();
    public HashMap<AvalancheTx, Integer> chitValues = new HashMap<>();
    
    public enum SnowPhase {
        QUERYING,
        REPLYING
    }

    public Avalanche(final LocalTxDAG localTxDAG, final int numAllParticipants) {
        super(localTxDAG);
        this.numAllParticipants = numAllParticipants;
        try {
            File file = new File(directory+"/events-log.txt");
            if (file.exists()) {
                file.delete();
            }
            writer = new PrintWriter(new FileWriter(file, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles incoming queries, specifically for Snowball or Avalanche consensus transactions.
     *
     * @param query The incoming query message.
     */
    public void newIncomingQuery(final Query query) { // onQuery
        if (query instanceof SnowTransactionMessage) {
        	SnowTransactionMessage<AvalancheTx> txMessage = (SnowTransactionMessage<AvalancheTx>) query;
            AvalancheNode peer = (AvalancheNode) this.peerDLTNode;
            AvalancheNode inquirer = (AvalancheNode) txMessage.getInquirer();
            AvalancheTx tx = txMessage.getTx();
            // Handle different query types.
            switch (txMessage.getQueryType()) {
                case QUERY:
                    this.snowPhase = SnowPhase.REPLYING;
                    onReceiveTx(peer, tx);
                    this.peerDLTNode.respondQuery( // replies its status (current transaction) .
                            new QueryMessage(
                            		new SnowTxReply<>(this.peerDLTNode, tx, isStronglyPreferred(peer, tx))
                            ), inquirer
                    );
                    break;
                case REPLY:
                	SnowTxReply txReply = (SnowTxReply) txMessage;
                	boolean response = txReply.getResponse();
                	checkQueries(inquirer.getNodeID(), (AvalancheTx)tx, response, peer);
                    break;
            }
        }
    }

    /**
     * Handles the reception of a new transaction by updating the local transaction DAG and known transactions.
     *
     * @param peer The Avalanche node receiving the transaction.
     * @param tx   The new transaction received.
     */
	public void onReceiveTx(final AvalancheNode peer, final AvalancheTx tx) {
	    // Check if the transaction is not already known to the peer.
		if(!peer.getknownTransactions().contains(tx)) {
	        // Check if the transaction is not already present in the local transaction DAG.
			if(!this.localTxDAG.contains(tx)) {
				this.localTxDAG.addTx(tx); // add tx to the DAG, and updates its conflicts, preferences, lastTx, and count.
			}else {
	            // If the transaction is already present, add it to the conflict set.
				this.localTxDAG.conflicts.get(tx.getHeight()).add(tx);
			}
			
	        // Add the transaction to the known transactions of the peer.
			peer.getknownTransactions().add(tx);
	        // Set the initial chit value for the transaction to 0.
			chitValues.put(tx, 0);
		}
	}

	/**
	 * Checks if a given Avalanche node strongly prefers a specific transaction.
	 *
	 * @param peer The Avalanche node in question.
	 * @param tx   The transaction to be checked for strong preference.
	 * @return True if the transaction is strongly preferred, false otherwise.
	 */
	public boolean isStronglyPreferred(final AvalancheNode peer, final AvalancheTx tx) {
		// Genesis transaction is always strongly preferred.
	    if (tx == AVALANCHE_GENESIS_TX) {
	        return true;
	    }
	    // Retrieve the set of known transactions from the peer.
	    HashSet<AvalancheTx> knownTransactions = peer.getknownTransactions();
	    
	    for (AvalancheTx knownTx : knownTransactions) {
	    	// Check if the knownTx is an ancestor of tx.
	        // and if the knownTx is not preferred by the peer.
	        if (this.localTxDAG.getAncestors((AvalancheTx) tx).contains(knownTx) && !isPreferred(peer, knownTx)) {
	            return false;  
	        }
	    }
	    // If no conflicting conditions are found, the transaction tx is strongly preferred.
	    return true; 
	}

	/**
	 * Checks if a given transaction is preferred by a specific Avalanche node.
	 *
	 * @param peer The Avalanche node making the preference decision.
	 * @param tx   The transaction to be checked for preference.
	 * @return True if the transaction is preferred, false otherwise.
	 */
	private boolean isPreferred(final AvalancheNode peer, final AvalancheTx tx) {
		return this.localTxDAG.preferences.get(tx) == tx;
	}
	
	/**
	 * Checks if a given transaction is accepted by a specific Avalanche node.
	 *
	 * @param peer The Avalanche node making the acceptance decision.
	 * @param tx   The transaction to be checked for acceptance.
	 * @return True if the transaction is accepted, false otherwise.
	 */
	private boolean isAccepted(final AvalancheNode peer, final AvalancheTx tx) {
	    // Genesis transaction is always accepted.
	    if (tx == AVALANCHE_GENESIS_TX) {
	        return true;
	    }
	    HashSet<AvalancheTx> knownTransactions = new HashSet<>(peer.getknownTransactions());
	    
	    int confidenceValue;
	    boolean term1 = true;
	    boolean term2 = true;
	    boolean term3 = true;
        boolean term4 = this.localTxDAG.count.get(tx) > BETA_2;
	    if(term4) {
	    	return true;
	    }
	    
	    // Check if the parents of the given transaction are accepted by the node (for term1).
	    for (AvalancheTx knownTx : knownTransactions) {
	        if (tx.getParents().contains(knownTx)) {
	        	if (!isAccepted(peer, knownTx)) {
	        		return false; // If any parent is not accepted, the transaction is not accepted.
	        	}
	        }
	    }
	    
	    // Check if there is exactly one conflicting transaction for the transaction (for term2).
	    if (this.localTxDAG.conflicts.get(tx.getHeight()).size() != 1) {
	    	return false;
	    }
	    // Retrieve or calculate the confidence value for the transaction.
        if(confidenceValues.containsKey(tx)) {
        	confidenceValue = confidenceValues.get(tx);
        } else {
        	confidenceValue = getConfidenceValue(peer, tx);
        }
        
        if(confidenceValue < BETA_1) { // (for term3).
        	return false; // Transaction is not accepted if confidence is below the threshold.
        }
        
        // The transaction is accepted if conditions for terms 1-3 or term 4 are met.
        return (term1 && term2 && term3) || term4;
	}

	/**
	 * Checks and processes queries for a specific Avalanche transaction.
	 *
	 * @param inquirerID The ID of the node making the query.
	 * @param tx         The transaction associated with the queries.
	 * @param response   The response (positive/negative) to the query.
	 * @param peer       The Avalanche node processing the queries.
	 */
	private void checkQueries(final int inquirerID, final AvalancheTx tx, final boolean response, final AvalancheNode peer) {
	    // Ensure the transaction has an entry in the queries map, and if not, create an entry for it.
		queries.computeIfAbsent(tx, key -> new HashMap<>()).putIfAbsent(inquirerID, response);
	    // Check the number of positive votes as soon as it reaches alpha.
	    if (getNumberOfTrue(queries.get(tx)) >= ALPHA*K) {
	        chitValues.put(tx, 1);
	        updateAncestorsPreference(peer, tx);
	        queries.remove(tx); 
	        return; // Exit the method since the condition is satisfied.
	    }
	    
	    // If the required number of votes (K) is reached without meeting alpha conditions.
	    if (queries.get(tx).size() == K) {
	        chitValues.put(tx, 0);
	        queries.remove(tx);
	    }
	}

	/**
	 * Updates preferences and counts for the ancestors of a specific transaction in the local transaction DAG.
	 *
	 * @param peer The Avalanche node initiating the update.
	 * @param tx   The transaction whose ancestors' preferences and counts are being updated.
	 */
	private void updateAncestorsPreference(AvalancheNode peer, AvalancheTx tx) {
	    // Iterate through the known transactions of the peer.
		for (AvalancheTx knownTx : peer.getknownTransactions()) {
	        // Check if the ancestors of the given transaction contain the known transaction, iterate for ancestors of the transaction.
	        if (this.localTxDAG.getAncestors(tx).contains(knownTx)) {
	            // Retrieve confidence values for the known transaction and its preference.
	        	double confidenceValueKnownTx = getConfidenceValue(peer, knownTx);
	            double confidenceValuePrefTx = getConfidenceValue(peer, this.localTxDAG.preferences.get(knownTx));
	            
	            // Update preferences if the confidence value of the known transaction is higher.
	            if (confidenceValueKnownTx > confidenceValuePrefTx) {
	                this.localTxDAG.preferences.put(knownTx, knownTx);;
	            }
	            
	            // Update last transaction and count based on whether it is the last transaction.
	            if (knownTx != this.localTxDAG.lastTx.get(knownTx)) {
	                // If knownTx is not the last transaction, update lastTx and reset count.
	                this.localTxDAG.lastTx.put(knownTx, knownTx);
	                this.localTxDAG.count.put(knownTx, 0);
	            } else {
	                // If knownTx is the last transaction, increment the count.
	                this.localTxDAG.count.put(knownTx, this.localTxDAG.count.get(knownTx) + 1);
	            }
	        }
	    }
	}
	/**
	 * Calculates the number of true (boolean value 'true') entries in a HashMap of votes.
	 *
	 * @param votes The HashMap containing boolean votes.
	 * @return The count of 'true' values in the provided HashMap.
	 */
	private int getNumberOfTrue(final HashMap<Integer, Boolean> votes) {
	    // Use Java Stream API to filter and count 'true' values in the votes HashMap.
	    return (int) votes.values().stream().filter(Boolean::booleanValue).count();
	}
	
	/**
	 * Calculates and returns the confidence value of a specific transaction for a given Avalanche node.
	 *
	 * @param peer The Avalanche node for which the confidence value is calculated.
	 * @param tx   The transaction for which the confidence value is determined.
	 * @return The calculated confidence value for the transaction.
	 */
	public int getConfidenceValue(final AvalancheNode peer, final AvalancheTx tx) {
	    int sumValue = 1; // chit value of tx itself.
	    
	    // Iterate through the known transactions of the peer.
	    for (AvalancheTx knownTx : peer.getknownTransactions()) {
	        // Check if the ancestors of the known transaction contain the given transaction, iterate for children of tx.
	        if (this.localTxDAG.getAncestors(knownTx).contains(tx)) {
	            sumValue += chitValues.getOrDefault(knownTx, 0); // Sum up the chit values for each known child of tx.
	        }
	    }
	    
	    // Cache the calculated confidence value for future use.
	    confidenceValues.put(tx, sumValue);
	    // System.out.println(tx.getHeight() + " " + sumValue);
	    return sumValue;
	}

	/**
	 * Performs snowball sampling by sending queries to a randomly sampled set of neighbors.
	 *
	 * @param peer The Avalanche node initiating the snowball sampling.
	 */
	public void snowballSampling(final AvalancheNode peer) {
	    // Sample K neighbors using the sampleNeighbors method.
        List<Node> sampledNeighbors = sampleNeighbors(peer, K);
        // Iterate through the sampled neighbors and send queries.
        for(Node destination:sampledNeighbors){
    		//System.out.println("node "+peer.getNodeID()+" sent a query for TX "+peer.getSelectedTx().getHeight()+" to node "+destination.getNodeID());
            // Send a query message to the sampled neighbor.
        	peer.query(
                    new QueryMessage(
                            new SnowTxQuery<>(peer, peer.getSelectedTx())
                    ), destination
            );
        }
    }
    
	/**
	 * Samples a specified number of neighbors from the peer's P2P connections.
	 *
	 * @param node The node for which neighbors are being sampled.
	 * @param k    The number of neighbors to be sampled.
	 * @return A list of sampled neighbors.
	 */
	private List<Node> sampleNeighbors(final Node node, final int k) {
	    // Retrieve all neighbors from the peer's P2P connections.
        List<Node> neighbors = new ArrayList<>();
        neighbors.addAll(node.getP2pConnections().getNeighbors());
        // Remove the node itself from the list of neighbors.
        neighbors.remove(node);
        // Create a list to store the sampled nodes.
        List<Node> sampledNodes = new ArrayList<>();
        // Iterate to sample k neighbors randomly.
        for (int i = 0; i < k; i++) {
            int randomIndex = this.peerDLTNode.getNetwork().getRandom().sampleInt(neighbors.size());
            sampledNodes.add(neighbors.get(randomIndex));
            neighbors.remove(randomIndex);
        }
        // Return the list of sampled neighbors.
        return sampledNodes;
    }
	
	public void checkTransactionsAcceptance() {
	    AvalancheNode peer = (AvalancheNode) this.peerDLTNode;
	    List<AvalancheTx> transactions = this.getLocalTxDAG().getDAGTransactions();
	    for (AvalancheTx tx : transactions) {
	        if (tx != AVALANCHE_GENESIS_TX) {
	        	if(!this.getLocalTxDAG().isAccepted(tx)) {
		            if (isAccepted(peer, tx)) { // a consensus occurs!
		            	//System.out.println("A consensus occurred on transaction "+tx.getHeight());
                    	AvalancheScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-tx.getCreationTime()); // record the consensus time.
		            	this.getLocalTxDAG().setAcceptance(tx, true);
		            	if(AvalancheScenario.RECORD_LOCAL_LEDGERS) {
		            		peer.getLocalLedger().add(tx); // add to the local chain ledger.
		            	}
	            		if(tx.getHeight()>peer.getLastConfirmedTx().getHeight()) {
		            		peer.setLastConfirmedTx(tx); // update the highest ID of accepted transactions.
		            	}
		                if (WRITE_ACCEPTED_LOGS) {
		                    writeLogs(tx, peer);
		                }
		            }
	        	}
	        }
	    }
	}
	
	private void writeLogs(final AvalancheTx tx, final AvalancheNode peer) {
	    writer.println("transaction " + tx.getHeight() + ", creator " + tx.getCreator().getNodeID() + ", is accepted at node " + peer.getNodeID()+", at "+peer.getSimulator().getSimulationTime());
        writer.flush();
	}
    
    @Override
    public boolean isBlockFinalized(final B block) {
        return false;
    }

    @Override
    public boolean isTxFinalized(final T tx) {
        return false;
    }

    @Override
    public int getNumOfFinalizedBlocks() {
        return 0;
    }

    @Override
    public int getNumOfFinalizedTxs() {
        return 0;
    }
	
    @Override
    public void newIncomingBlock(final B block) {

    }

    /**
     * @param block
     * @return
     */
    @Override
    public boolean isBlockConfirmed(final B block) {
        return false;
    }

    /**
     * @param block
     * @return
     */
    @Override
    public boolean isBlockValid(final B block) {
        return false;
    }

    public int getNumAllParticipants() {
        return this.numAllParticipants;
    }

    public SnowPhase getSnowPhase() {
        return this.snowPhase;
    }

}
