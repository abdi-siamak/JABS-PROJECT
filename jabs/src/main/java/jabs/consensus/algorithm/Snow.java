package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.ledgerdata.SingleParentBlock;
import jabs.ledgerdata.Tx;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.snow.*;
import jabs.network.message.QueryMessage;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.SnowNode;
import jabs.scenario.SnowScenario;
import jabs.simulator.event.NodeCycleEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */

public class Snow<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements QueryingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {

    static {
        try {
            File file = new File("output/snow-events-log.txt");
            if (file.exists()) {
                file.delete();
            }
            writer = new PrintWriter(new FileWriter(file, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //*-------------------------------------------------------------------------------------------------------
    // settings for the Snow protocols
    public static final int K = 8; // sample size
    private static final int ALPHA = K/2; // quorum size or threshold
    private static final int BETA = 5; // conviction threshold
    public static final double LOOPTIME = 0.3; // (seconds)
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //*-------------------------------------------------------------------------------------------------------
    private static PrintWriter writer;
    private final int numAllParticipants;
    private final HashMap<SnowBlock, HashMap<SnowNode, Query>> commitQueries = new HashMap<>();
    private int numReceivedSamples;
    private int conviction; // cnt
    private SnowPhase snowPhase = SnowPhase.QUERYING;
    public HashMap<SnowBlock, Integer> confidenceValues = new HashMap<>();
    
    public enum SnowPhase {
        QUERYING,
        REPLYING
    }

    public Snow(final LocalBlockTree<B> localBlockTree, final int numAllParticipants) {
        super(localBlockTree);
        this.numAllParticipants = numAllParticipants;
        this.currentMainChainHead = localBlockTree.getGenesisBlock();
    }

    public void newIncomingQuery(final Query query) {
        if (query instanceof SnowBlockMessage) {
        	SnowBlockMessage<B> blockMessage = (SnowBlockMessage<B>) query;
            SnowNode peer = (SnowNode) this.peerBlockchainNode;
            SnowNode inquirer = (SnowNode) blockMessage.getInquirer();
            B block = blockMessage.getBlock();
            switch (blockMessage.getQueryType()) {
                case QUERY:
                    if (!this.localBlockTree.contains(block)) {
                        this.localBlockTree.add(block);
                    }
                    if (this.localBlockTree.getLocalBlock(block).isConnectedToGenesis) {
                        if(peer.getCurrentBlock().getHeight() < block.getHeight()){ // If the node receives a new block, updates its status (detecting new round)
                        	peer.setCurrentBlock((SnowBlock) block); // updates its status
                            if(!confidenceValues.containsKey(block)) {
                           	 confidenceValues.put((SnowBlock)block, 0);
                           }
                        }
                        this.snowPhase = SnowPhase.REPLYING;
                        this.peerBlockchainNode.respondQuery( // replies its status (current block) 
                                new QueryMessage(
                                		new SnowReply<>(this.peerBlockchainNode, peer.getCurrentBlock())
                                ), inquirer
                        );
                    }
                    break;
                case REPLY:
                	checkQueries(query, blockMessage, (SnowBlock)block, this.commitQueries, peer);
                    break;
            }
        }
    }

    private void checkQueries(final Query query, final SnowBlockMessage<B> blockQuery, final SnowBlock block, final HashMap<SnowBlock, HashMap<SnowNode, Query>> queries, final SnowNode peer) {
        numReceivedSamples++;
        if(block.getHeight()>peer.getLastConfirmedBlockID()) { // check only for new conflicting blocks
            if (!queries.containsKey(block)) { // the first query reply received for the block
                queries.put(block, new HashMap<>());
            }
            if(!confidenceValues.containsKey(block)) {
            	 confidenceValues.put(block, 0);
            }
            queries.get(block).put((SnowNode)blockQuery.getInquirer(), query); 
        }
        if (numReceivedSamples == K) { // if the node receives all the query replies-checks the query replies
            numReceivedSamples = 0; 
            for(SnowBlock receivedBlock:queries.keySet()) {
                if ((queries.get(receivedBlock).size() > ALPHA)) { // If a sampling for the block (block that consensus on it should be done) reaches the alpha value
                	int currentValue = confidenceValues.get(receivedBlock);
                	int updatedValue = currentValue + 1;
                	confidenceValues.put(receivedBlock, updatedValue);
                	if(confidenceValues.get(receivedBlock) > confidenceValues.get(peer.getCurrentBlock())) {
                		peer.setCurrentBlock((SnowBlock)receivedBlock);
                	}
                	if(receivedBlock != peer.getLastBlock()) {
                		peer.setLastBlock(receivedBlock);
                		conviction = 0;
                	}else if (++conviction>BETA){ // if the node's conviction value reaches the beta threshold (DECIDED STATUS)
                        peer.setDecided(true); // change to the decided state
                        //confidenceValues.clear();
                        conviction = 0;
                        this.currentMainChainHead = (B)receivedBlock; // accepts the block
                        peer.setLastConfirmedBlockID(receivedBlock.getHeight());
                        SnowScenario.globalConsensus.add(peer);
                        updateChain();
                        writer.println("Node "+peer.nodeID+" was set to the state of block "+peer.getCurrentBlock().getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                        writer.flush();
                	}
                }
            }
            queries.clear();
        } 
    }

    public void newLoop(final SnowNode peer) {
    	peer.addCycleNumber(1);
        List<Node> sampledNeighbors = sampleNeighbors(peer, K);
        peer.getSimulator().putEvent(new NodeCycleEvent(peer), LOOPTIME); // put a "simulationEvent" to determine the next loop start time
        for(Node destination:sampledNeighbors){
        	peer.query(
                    new QueryMessage(
                            new SnowQuery<>(peer, peer.getCurrentBlock())
                    ), destination
            );
        }
    }
    
	private List<Node> sampleNeighbors(final Node node, final int k) {
        List<Node> neighbors = new ArrayList<>();
        neighbors.addAll(node.getP2pConnections().getNeighbors());
        neighbors.remove(node);
        List<Node> sampledNodes = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int randomIndex = this.peerBlockchainNode.getNetwork().getRandom().sampleInt(neighbors.size());
            sampledNodes.add(neighbors.get(randomIndex));
            neighbors.remove(randomIndex);
        }
        return sampledNodes;
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

    public Snow.SnowPhase getSnowPhase() {
        return this.snowPhase;
    }

    public int getCurrentPrimaryNumber() {
        return (this.currentMainChainHead.getHeight() % this.numAllParticipants);
    }

    @Override
    protected void updateChain() {
        this.confirmedBlocks.add(this.currentMainChainHead);
        
    }
}
