package jabs.scenario;

import jabs.consensus.algorithm.Snow;
import jabs.consensus.config.SnowConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.snow.SnowBlock;
import jabs.log.AbstractLogger;
import jabs.network.networks.snow.SnowWANNetwork;
import jabs.network.node.nodes.snow.SnowNode;
import jabs.simulator.event.Event;
import jabs.simulator.event.NodeCycleEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class SnowScenario extends AbstractScenario{
    static {
        try {
            File file = new File("output/snow-WANNetwork-localLedgers-log.txt");
            if (file.exists()) {
                file.delete();
            }
            writer = new PrintWriter(new FileWriter(file, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //-------------------------------------------------------------------------------------------------------
    private final boolean generateBlocks = true; // should nodes generate blocks continuously?
    //private final int maxCycle = 99999999; // the maximum number of cycles nodes should work
    //-------------------------------------------------------------------------------------------------------
    private final int numNodes;
    private final double simulationStopTime;
    public static ArrayList<SnowNode> globalConsensus = new ArrayList<>();
    private static PrintWriter writer;

    public SnowScenario(String name, long seed, int numNodes, double simulationStopTime) {
        super(name, seed);
        this.numNodes = numNodes;
        this.simulationStopTime = simulationStopTime;
    }

    @Override
    public void createNetwork() {
        network = new SnowWANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, this.numNodes, new SnowConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() {
    	globalConsensus.clear();
        List<SnowNode> nodes = network.getAllNodes();
        for(SnowNode node:nodes) {
        	node.setLastConfirmedBlockID(node.getLastConfirmedBlockID()); 
        	node.setDecided(false);
        	node.setLastBlock(node.getCurrentBlock());
        	SnowBlock chosedBlock = blockInitializing(node);
        	node.setCurrentBlock(chosedBlock);
        	startNewLoop(node);
        }
    }
    
    private SnowBlock blockInitializing(final SnowNode node) {
        int nodeID = node.getNodeID();

        switch (nodeID) {
            case 1, 2, 5: // nodes that generate conflicting blocks
            	SnowBlock newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastBlock());
                //System.out.println("block ID: "+newBlock.getHeight()+", creator: "+newBlock.getCreator().getNodeID()+", hash: "+ newBlock.hashCode()+" is generated in node "+node.getNodeID());
                Snow consensus = (Snow) node.getConsensusAlgorithm();
                consensus.getLocalBlockTree().add(newBlock);
                consensus.confidenceValues.put(newBlock, 0);
                return newBlock;
            default:
            	SnowBlock lastBlock = node.getLastBlock();
                Snow consensusDefault = (Snow) node.getConsensusAlgorithm();
                if (!consensusDefault.getLocalBlockTree().contains(lastBlock)) {
                    consensusDefault.getLocalBlockTree().add(lastBlock);
                }
                return lastBlock;
        }
    }

	@Override
    public void run() throws IOException {
        System.err.printf("Staring %s...\n", this.name);
        this.createNetwork();
        this.insertInitialEvents();
        SnowNode node=null;
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        long simulationStartingTime = System.nanoTime();
        long lastProgressMessageTime = simulationStartingTime;
        Event event;
        double realTime;
        double simulationTime;
        while (!this.simulationStopCondition()&&simulator.isThereMoreEvents()) {
            event = simulator.peekEvent();
        	if (event instanceof NodeCycleEvent) { 
        		node = (SnowNode) ((NodeCycleEvent) event).getNode();
        		simulator.executeNextEvent();
            	//System.out.println("a simulationEvent received from node "+ node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        		if(globalConsensus.size()==numNodes&&generateBlocks){ // start new consensus round
        			this.insertInitialEvents();
        			continue;
        		}
        		if(!node.isDecided()&&(node.getCurrentBlock().getHeight()>node.getLastConfirmedBlockID())) {
            		//*****Start a new Cycle*****//
        			//System.out.println("New Cycle Started in node "+node.getNodeID());
            		startNewLoop(node); 
            		//##### Start a new Cycle#####//
        		}else {
        	        node.getSimulator().putEvent(new NodeCycleEvent(node), Snow.LOOPTIME); // put a "simulationEvent" to determine the next loop start time
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
        this.writeLocalLedger();
        System.err.printf("Finished %s.\n", this.name);
        System.out.println("number of cycles for node " +node.nodeID + ": "+node.getCycleNumber());
        System.out.println("number of confirmed blocks for node "+ +node.nodeID+ ": "+node.getLastConfirmedBlockID());
    }

	private void startNewLoop(final SnowNode node) {
		//System.out.println("a new loop started!");
		Snow consensus = (Snow) node.getConsensusAlgorithm();
        consensus.newLoop(node);
	}
    
    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }

    protected void writeLocalLedger() {
        List<SnowNode> nodes = network.getAllNodes();
        for (SnowNode node : nodes){
            HashSet<SnowBlock> localLedger = node.getConsensusAlgorithm().getConfirmedBlocks();
            List<SnowBlock> blocks = getOrdered(localLedger);
            writer.println("Local ledger node "+node.getNodeID() +":");
            for(SnowBlock block:blocks){
                writer.println("block: "+block.getHeight() + ", Parent: "+block.getParent().getHeight()+", Creator: "+block.getCreator().getNodeID()+", Size: "
                        +block.getSize()+", Creation_Time: "+block.getCreationTime()+", Hash: "+block.hashCode());}
            writer.println("-----------------------------------------");
            writer.flush();
        }
    }
    
    private List<SnowBlock> getOrdered(final HashSet<SnowBlock> localLedger) {
        class BlockHeightComparator implements Comparator<SnowBlock> {
            @Override
            public int compare(SnowBlock block1, SnowBlock block2) {
                int height1 = block1.getHeight();
                int height2 = block2.getHeight();
                return Integer.compare(height1, height2);
            }
        }
        List<SnowBlock> blockList = new ArrayList<>(localLedger);
        Collections.sort(blockList, new BlockHeightComparator());
        return blockList;
    }
}
