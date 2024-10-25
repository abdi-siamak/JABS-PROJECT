package jabs.scenario;

import jabs.consensus.algorithm.BECP;
import jabs.consensus.config.BECPConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.Hash;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.BECPPush;
import jabs.ledgerdata.becp.PushEntry;
import jabs.ledgerdata.becp.ReplicaBlock;
import jabs.log.AbstractLogger;
import jabs.network.message.GossipMessage;
import jabs.network.networks.becp.BECPBitcoinNetwork;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.network.stats.NodeGlobalNetworkStats;
import jabs.network.stats.eightysixcountries.EightySixCountries;
import jabs.network.stats.eightysixcountries.bitcoin.BitcoinBECPGlobalNetworkStats86Countries;
import jabs.simulator.event.Event;
import jabs.simulator.event.NodeCycleEvent;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.randengine.RandomnessEngineAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static jabs.network.node.nodes.becp.BECPNode.BECP_GENESIS_BLOCK;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class BECPBitcoinNetworkScenario extends AbstractScenario {
    static {
        try {
            File file = new File("output/becp-BitcoinNetwork-localLedgers-log.txt");
            if (file.exists()) {
                file.delete();
            }
            writer = new PrintWriter(new FileWriter(file, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //-------------------------------------------------------------------------------------------------------
    private final boolean generateBlocks = true; // should nodes generate blocks?
    private final int blockGenerationCycle = 200; // the block generation interval
    private final boolean writeLocalLedgers = true; // write nodes' local ledgers
    //------------SSEP & REAP-----------------
    private static final double VALUEI = 1; //the "v" value of the leader node.
    private static final double WEIGHTI = 1; //the "w" value of the leader node. 
    private static final double VALUE = 1; //the "v" value of nodes.
    private static final double WEIGHT = 0; //the "w" value of nodes.
    //------------PTP-----------------
    private static final double VP = 1; //the propagation value
    private static final double WP = 1; //the propagation weight
    private static final double VA = 0; //the aggregation value
    private static final double WA = 1; //the aggregation weight
    //------------ECP-----------------
    private static final double vDataAggregation = 1.5; //the data aggregation value (node's value)
    private static final double wDataAggregation = 1; //the data aggregation weight
    private static final double vDataConvergence = 0; //the convergence value
    private static final double vDataAgreement = 0; //the agreement value
    private static final double weightValue = 0; //the weight value
    //-------------------------------
    private final boolean crashNode = false;
    //private final double numBlocks = 10;
    private final int maxCycle = 1000000; // the maximum number of cycles nodes should work
    public static final double offsetTime = 0.00001; // an offset for starting time of the nodes, it is added to every node, 10us (0.00001)
    public static final double cycleTime = 0.3; // 300 ms, The next start cycle time
    double epsilon = 1e-9; // A small tolerance value
    //-------------------------------------------------------------------------------------------------------
    private int numConfirmedBlocks;
    private final double simulationStopTime;
    private final int neighborCacheSize;
    private static PrintWriter writer;
    RandomnessEngineAdapter adapter;

    /**
     * creates a global network scenario with parameters close to real-world but excluding transaction simulation for
     * better simulation speed
     * @param name                 determines the name of simulation scenario
     * @param seed                 this value gives the simulation seed value for randomness engine
     * @param simulationStopTime   this determines how many seconds of simulation world time should it last.
     * @param neighborCacheSize    The size of local neighbor cache size
     */
    public BECPBitcoinNetworkScenario(String name, long seed, double simulationStopTime, int neighborCacheSize) {
        super(name, seed);
        this.simulationStopTime = simulationStopTime;
        this.neighborCacheSize = neighborCacheSize;
        adapter = new RandomnessEngineAdapter(randomnessEngine);
    }

    @Override
    protected void createNetwork() {
        NodeGlobalNetworkStats<EightySixCountries> networkStats = new BitcoinBECPGlobalNetworkStats86Countries(randomnessEngine);
        //System.out.println(networkStats.totalNumberOfNodes());
        BECPBitcoinNetwork bitCoinNetwork = new BECPBitcoinNetwork(randomnessEngine, networkStats);
        bitCoinNetwork.populateNetwork(this.simulator, networkStats.totalNumberOfNodes(),
                new BECPConsensusConfig(), neighborCacheSize, VALUEI, WEIGHTI, VALUE, WEIGHT, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue);
        this.network = bitCoinNetwork;
    }

    @Override
    protected void insertInitialEvents() {
        List<BECPNode> nodes = network.getAllNodes();
        ArrayList<PushEntry> temp = new ArrayList<>();
        Collections.shuffle(nodes, adapter);
        for (BECPNode node : nodes) {
        	node.addCycleNumber(1);
            //double randomDelay = randomnessEngine.nextDouble()* offsetTime; // starting nodes at various simulation times
            simulator.incrementSimulationDuration(offsetTime); // increase simulation time by "offsetTime"
            BECP nodeConsensus = (BECP) node.getConsensusAlgorithm();
            //***** Initializing the variables of the protocols*****//
            double nodeValue = 0;
            double nodeWeight = 0;
            //***** SSEP (System Size Estimation Protocol)*****//
            if (nodeConsensus.SSEP) {
                nodeValue = node.getValue() / 2;
                nodeWeight = node.getWeight() / 2;
                node.setValue(nodeValue);
                node.setWeight(nodeWeight);
            }
            //##### SSEP (System Size Estimation Protocol)#####//
            //***** REAP (*Robust Epidemic Aggregation Protocol)*****//
            if(nodeConsensus.REAP){
                if(node.getWeight()>0 && !node.getConvergenceFlag()){
                    node.setCriticalPushFlag(true);
                }
                nodeValue = node.getValue() / 2;
                nodeWeight = node.getWeight() / 2;
                node.setValue(nodeValue);
                node.setWeight(nodeWeight);
            }
            //##### REAP (Robust Epidemic Aggregation Protocol)#####//
            //***** NCP (Node Cache Protocol)*****//
            ArrayList<BECPNode> copyNeighborCache = null;
            BECPNode destination = null;
            if (nodeConsensus.NCP) {
                trimCache(neighborCacheSize, node.getNeighborsLocalCache());
                destination = getRandomNeighbor(node, randomnessEngine); // the function getNode() for NCP (Node Cache Protocol)
                copyNeighborCache = new ArrayList<>(node.getNeighborsLocalCache()); // sends a copy of neighborsLocalCache
                copyNeighborCache.remove(destination);
            }
            //##### NCP (Node Cache Protocol)#####//
            BECPBlock block = BlockFactory.sampleBECPBlock(simulator, network.getRandom(), node, node.getLeader(), BECP_GENESIS_BLOCK, VP, WP, VA, WA, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue); // generate a new block.
            //node.setLastTriggeredTime(simulator.getSimulationTime()); // ***********TO DO***********
            //node.setLastGeneratedBlock(block);
            //***** PTP (Phase Transition Protocol)*****//
            HashMap<Integer, BECPBlock> copyBlockCache = null;
            if (nodeConsensus.PTP) {
                copyBlockCache = initializeBlockLocalCache(node, block);
            }
            //##### PTP (Phase Transition Protocol)#####//
            node.gossipMessage(
                    new GossipMessage(
                            new BECPPush<>(node, node.getCycleNumber(), getSizeOfBlocks(copyBlockCache), nodeValue, nodeWeight, copyNeighborCache, copyBlockCache, node.getCriticalPushFlag(), false, node.getL(), node.getP(), node.getA(), node.getC(), node.getCrashedNodes(), node.getJoinedNodes(), false)
                    ), destination
            );
            simulator.putEvent(new NodeCycleEvent(node), cycleTime); // putting a "simulationEvent" to determine the next cycle start time
            //System.out.println("a simulationEvent inserted by node "+node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
            //*System.out.println("Initializing, sent a push from "+copyBlockCache.get(0).getCreator().getNodeID()+" to "+destination.getNodeID()+" at "+simulator.getSimulationTime());//*
            //***** REAP (Robust Epidemic Aggregation Protocol)*****//
            if (nodeConsensus.REAP) {
                if(node.getCriticalPushFlag()){ // insert critical push in PushEntriesBuffer.
                	HashMap<Integer, ReplicaBlock> replicaBlockCache = null;
                	if(node.getBlockLocalCache().size()>0) {
                		replicaBlockCache = new HashMap<>();
                    	for (BECPBlock becpBlock:node.getBlockLocalCache().values()) {
                    		ReplicaBlock replicaBlock = new ReplicaBlock();
                    		replicaBlock.setVPropagation(becpBlock.getVPropagation());
                    		replicaBlock.setWPropagation(becpBlock.getWPropagation());
                    		replicaBlock.setVAgreement(becpBlock.getVAgreement());
                    		replicaBlock.setWPropagation(becpBlock.getWPropagation());
                    		
                    		replicaBlockCache.put(becpBlock.getHeight(), replicaBlock);
                    	}
                	}
                	
                    node.getPushEntriesBuffer().add(new PushEntry(destination, node.getCycleNumber(), BECP.PULL_TIMEOUT, node.getValue(), node.getWeight(),replicaBlockCache));
                }
                temp.clear();
                if (temp.size() > 0) {
                    for (PushEntry pushEntry:temp) {
                        node.getPushEntriesBuffer().remove(pushEntry);
                    }
                }
            }
            //##### REAP (Robust Epidemic Aggregation Protocol)#####//
            //##### Initializing the variables of the protocols#####//
        }
    }
    
    @Override
    public void run() throws IOException {
        System.err.printf("Staring %s...\n", this.name);
        this.createNetwork();
        this.insertInitialEvents();
        long simulationStartingTime = System.nanoTime();
        long lastProgressMessageTime = simulationStartingTime;
        Event event;
        double realTime;
        double simulationTime;
        BECPNode node=null;
        
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        while (!this.simulationStopCondition()&&simulator.isThereMoreEvents()) {
        	if(simulator.isThereMoreEvents()) {
        		 //System.out.println("queue size: "+simulator.getNumOfEvents());
        	}
            event = simulator.peekEvent();
        	if (event instanceof NodeCycleEvent) { 
        		node = (BECPNode) ((NodeCycleEvent) event).getNode();
        		simulator.executeNextEvent();
            	//System.out.println("a simulationEvent received from node "+ node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        		if(node.getCycleNumber()<maxCycle) {
            		if((node.getNodeID()==1)&&(node.getCycleNumber()==500)&&crashNode) {
            			node.crash();
            			continue;
            		}
        			if(generateBlocks&&(node.getCycleNumber()%blockGenerationCycle==0)) {
                        //***** Generating new blocks*****//
        				//generateNewBlock(node, node.getSentBlock());
                        //##### Generating new blocks#####//
        			}
            		//*****Start a new Cycle*****//
        			//System.out.println("New Cycle Started in node "+node.getNodeID());
            		startNewCycle(node); 
            		//##### Start a new Cycle#####//
        		}
        	}else {
                for (AbstractLogger logger:this.loggers) {
                    logger.logBeforeEachEvent(event);
                }
                simulator.executeNextEvent();
                for (AbstractLogger logger:this.loggers) {
                    logger.logAfterEachEvent(event);
                }
        	}
            if (System.nanoTime() - lastProgressMessageTime > this.progressMessageIntervals) {
                realTime = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - simulationStartingTime);
                simulationTime = simulator.getSimulationTime();
                System.err.printf(
                        "Simulation in progress... " +
                                "Elapsed Real Time: %d:%02d:%02d, Elapsed Simulation Time: %d:%02d:%02d\n",
                        (long)(realTime / 3600), (long)((realTime % 3600) / 60), (long)(realTime % 60),
                        (long)(simulationTime / 3600), (long)((simulationTime % 3600) / 60), (long)(simulationTime % 60)
                );
                lastProgressMessageTime = System.nanoTime();
            }
        }
        for (AbstractLogger logger:this.loggers) {
            logger.finalLog();
        }
        System.err.printf("Finished %s.\n", this.name);
        System.out.println("number of cycles for node " +node.nodeID + ": "+node.getCycleNumber());
        System.out.println("number of generated blocks for node "+ +node.nodeID+ ": "+numConfirmedBlocks);
        if(writeLocalLedgers) {
        	this.writeLocalLedger();
        }
    }

    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }

    private int getSizeOfBlocks(final HashMap<Integer, BECPBlock> blockLocalCache){
        if (blockLocalCache == null) {
            return 0; 
        }

        return blockLocalCache.values()
                .stream()
                .filter(Objects::nonNull)
                .mapToInt(BECPBlock::getSize)
                .sum();
    }

    private HashMap<Integer, BECPBlock> initializeBlockLocalCache(BECPNode node, BECPBlock block){
    	HashMap<Integer, BECPBlock> blockLocalCache = node.getBlockLocalCache();
        double v_propagation = block.getVPropagation()/2;
        double w_propagation = block.getWPropagation()/2;
        double v_agreement = block.getVAgreement()/2;
        double w_agreement = block.getWAgreement()/2;
        block.setVPropagation(v_propagation);
        block.setWPropagation(w_propagation);
        block.setVAgreement(v_agreement);
        block.setWAgreement(w_agreement);
        block.setCycleNumber(node.getCycleNumber());
        //System.out.println("a block was generated at "+simulator.getSimulationTime());
        blockLocalCache.put(block.getHeight(), block); // add the block to the block local cache
        node.setBlockLocalCache(blockLocalCache);
        HashMap<Integer, BECPBlock> copyBlockCache = new HashMap<>(); // create a copy to send
        copyBlockCache.put(block.getHeight(), block.clone());
        return copyBlockCache;
    }
/*    
    private void generateNewBlock(BECPNode node, BECPBlock parent) {
        ArrayList<BECPBlock> peerBlockLocalCache = node.getBlockLocalCache();
        BECPBlock newBlock = BlockFactory.sampleBECPBlock(simulator, randomnessEngine, node, parent, VP, WP, VA, WA); // generate a new block
        //System.out.println(newBlock.getCreationTime());
        peerBlockLocalCache.add(newBlock);
        node.setLastGeneratedBlockTime(simulator.getSimulationTime());
        node.setHeight(node.getHeight() + 1);
        node.setSentBlock(newBlock);
        node.setBlockLocalCache(peerBlockLocalCache);
    }
*/   
	public void startNewCycle(BECPNode node) {
		//System.out.println("a new cycle started!");
    	BECP consensus = (BECP) node.getConsensusAlgorithm();
        consensus.newCycle(node);
        numConfirmedBlocks = node.getLastConfirmedBlock().getHeight();
	}

    private void trimCache(int maxSize, ArrayList<BECPNode> neighborsLocalCache) {
        while (neighborsLocalCache.size() > maxSize){
            int randomIndex = randomnessEngine.nextInt(neighborsLocalCache.size()); // pick a random node to remove
            neighborsLocalCache.remove(randomIndex);
        }
    }

    private static BECPNode getRandomNeighbor(BECPNode peer, RandomnessEngine randomnessEngine) {
    	BECPNode randomNeighbor;
    	ArrayList<BECPNode> cache = peer.getNeighborsLocalCache();
        randomNeighbor = cache.get(randomnessEngine.nextInt(cache.size())); // pick a random node to gossip
        if(randomNeighbor==peer) {
        	System.out.println("destination is the same as the sender!");
        	return null;
        }
        
        return randomNeighbor;
    }

    protected void writeLocalLedger(){
        List<BECPNode> nodes = network.getAllNodes();
        for (BECPNode node : nodes){
            HashSet<BECPBlock> localLedger = node.getConsensusAlgorithm().getConfirmedBlocks();
            List<BECPBlock> blocks = getOrdered(localLedger);
            writer.println("Local ledger node "+node.getNodeID() +":");
            for(BECPBlock block:blocks){
                writer.println("block: "+block.getHeight() + ", Parent: "+block.getParent().getHeight()+", Creator: "+block.getCreator().getNodeID()+", Size: "
                        +block.getSize()+", Creation_Time: "+block.getCreationTime()+", Hash: "+block.hashCode());
            }
            writer.println("-----------------------------------------");
            writer.flush();
        }
    }

    private List<BECPBlock> getOrdered(HashSet<BECPBlock> localLedger) {
        class BlockHeightComparator implements Comparator<BECPBlock> {
            @Override
            public int compare(BECPBlock block1, BECPBlock block2) {
                int height1 = block1.getHeight();
                int height2 = block2.getHeight();
                return Integer.compare(height1, height2);
            }
        }
        List<BECPBlock> blockList = new ArrayList<>(localLedger);
        Collections.sort(blockList, new BlockHeightComparator());
        return blockList;
    }
}
