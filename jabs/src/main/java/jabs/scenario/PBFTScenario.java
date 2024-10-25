package jabs.scenario;

import jabs.Main; 
import jabs.consensus.config.PBFTConsensusConfig;
import jabs.ledgerdata.pbft.PBFTBlock;
import jabs.ledgerdata.pbft.PBFTPrePrepareVote;
import jabs.log.AbstractLogger;
import jabs.log.PBFTCSVLogger;
import jabs.network.message.VoteMessage;
import jabs.ledgerdata.BlockFactory;
import jabs.network.networks.pbft.PBFTWANNetwork;
import jabs.network.node.nodes.pbft.PBFTNode;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.simulator.event.Event;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class PBFTScenario extends AbstractScenario {
    //-------------------------------------------------------------------------------------------------------
    private static final boolean WRITE_LOCAL_LEDGERS = false; //write nodes' local ledgers?
    public static final boolean UNIFORM_CRASH = false; // Should nodes simulate crashes?
    private static final double CRASH_RATE = 0.6; // The nodes crash rate (during the simulation time).
    public static final boolean TRACKING = true; // Indicates whether the system should record tracking information.
    private static final double TRACKING_TIME = 10; // Specifies the time interval (in seconds) for recording tracking data.
    //-------------------------------------------------------------------------------------------------------
    public static File directory;
    private static PrintWriter writer;
    protected int numOfNodes;
    protected double simulationStopTime;
    public static ArrayList<Double> consensusTimes = new ArrayList<>();
    private static HashMap<PBFTNode, Double> nodesToBeCrashed = new HashMap<>(); // node -> at simulation time.
    public static LinkedHashMap<Double, ArrayList<Double>> records = new LinkedHashMap<>();
    
    public PBFTScenario(String name, long seed, int numOfNodes, double simulationStopTime) {
        super(name, seed);
        this.numOfNodes = numOfNodes;
        this.simulationStopTime = simulationStopTime;
        if(UNIFORM_CRASH) {
            directory = new File("output/PBFT-crash_test/"+"/"+CRASH_RATE+"/"+"PBFT-" +"seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else if(WANNetworkStats.DISTRIBUTION==WANNetworkStats.LatencyDistribution.PARETO) {
        	directory = new File("output/PBFT-pareto_test/"+"/"+WANNetworkStats.alpha+"/"+"PBFT-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else {
            directory = new File("output/PBFT-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }
        File file = new File(directory, "Blockchain-localLedgers.txt");
        consensusTimes.clear();
        nodesToBeCrashed.clear();
        
        try {
            // Create the directory and all its parent directories if they don't exist.
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Failed to create directory.");
            }

            // Create the file and override if it already exists.
            writer = new PrintWriter(new FileWriter(file, false));

        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception or exit the method.
            return;
        }
    }

    @Override
    public void createNetwork() {
        network = new PBFTWANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, this.numOfNodes, new PBFTConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() {
    	List<PBFTNode> nodes = network.getAllNodes();
    	if(UNIFORM_CRASH){
        	while (nodesToBeCrashed.size() < numOfNodes*CRASH_RATE) {
        		PBFTNode randomNode = nodes.get(randomnessEngine.nextInt(nodes.size()));
        		if (!nodesToBeCrashed.containsKey(randomNode)) {
                    double randomTime = randomnessEngine.sampleDouble(simulationStopTime); // Generates a random simulation time.
                    nodesToBeCrashed.put(randomNode, randomTime);
                }
            }
    	}
    	PBFTNode node = (PBFTNode) network.getAllNodes().get(0); // the first Leader node.
        node.broadcastMessage(
                new VoteMessage(
                        new PBFTPrePrepareVote<>(node, BlockFactory.samplePBFTBlock(node.getSimulator(), node.getNetwork().getRandom(),
                    			node, node.getLastBlock()))
                )
        );
    }

    @Override
    public void run() throws IOException {
    	double recordTime = 0;
    	double simulationTime;
        System.err.printf("Staring %s...\n", this.name);
        this.createNetwork();
        this.insertInitialEvents();
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        long simulationStartingTime = System.nanoTime();
        long lastProgressMessageTime = simulationStartingTime;
        while (simulator.isThereMoreEvents() && !this.simulationStopCondition()) {
        	simulationTime = this.simulator.getSimulationTime();
			if(TRACKING&&(simulationTime>recordTime)) {
				recordTime = recordTime + TRACKING_TIME;
            	int currentThroughput=0;
                for (PBFTNode peer : (List<PBFTNode>) network.getAllNodes()) {
                    if (!peer.isCrashed) {
                    	currentThroughput = peer.getLastConfirmedBlockID();
                    }
                }
                ArrayList<Double> data = new ArrayList<>();
                data.add(Double.valueOf(currentThroughput));
                data.add(getAverageConsensusTime());
                data.add(Double.valueOf(PBFTCSVLogger.numMessage));
                data.add(Double.valueOf(PBFTCSVLogger.messageSize));
            	records.put(simulationTime, data);
            }
            Event event = simulator.peekEvent();
            for (AbstractLogger logger:this.loggers) {
                logger.logBeforeEachEvent(event);
            }
            simulator.executeNextEvent();
            for (AbstractLogger logger:this.loggers) {
                logger.logAfterEachEvent(event);
            }
            if (System.nanoTime() - lastProgressMessageTime > this.progressMessageIntervals) {
                double realTime = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - simulationStartingTime);
                simulationTime = this.simulator.getSimulationTime();

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
        System.err.printf("Finished %s.\n", this.name+"-Number of Nodes:"+this.numOfNodes+"-Simulation Time:"+this.simulationStopTime);
        for (PBFTNode randomNode : (List<PBFTNode>) network.getAllNodes()) {
            if (!randomNode.isCrashed) {
                Main.averageBlockchainHeights.add(randomNode.getLastConfirmedBlockID());
                break;
            }
        }
        
        if(WRITE_LOCAL_LEDGERS) {
        	this.writeLocalLedger();
        }
        if(TRACKING) {
        	try (BufferedWriter writer = new BufferedWriter(new FileWriter(directory+"/trackingRecords.txt"))) {
        		writer.write("simulation_time, throughput, latency, #of_messages, message_size, Mv, Mw, system_size");
        		writer.newLine();
        		for (Map.Entry<Double, ArrayList<Double>> entry : records.entrySet()) {
                    Double key = entry.getKey();
                    ArrayList<Double> values = entry.getValue();
                    writer.write(key + ", ");

                    // Writing the list of values separated by commas
                    for (int i = 0; i < values.size(); i++) {
                        writer.write(values.get(i).toString());
                        if (i < values.size() - 1) {
                            writer.write(", ");
                        }
                    }

                    writer.newLine(); // Move to the next line
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        testBlockchain();
    }
    
	private void testBlockchain() {
		boolean test = true;
		HashMap<Integer, Integer> failedNodesBlocks = new HashMap<>();
		List<PBFTNode> nodes = network.getAllNodes();
		
		for(PBFTNode node:nodes) {
			if(!node.isCrashed) {
				Iterator<PBFTBlock> iterator = node.getLocalLedger().iterator();
		        if (!iterator.hasNext()) {
		            return; // If the set is empty, do nothing
		        }
		        PBFTBlock previousBlock = iterator.next(); // Start with the first block
		        while (iterator.hasNext()) {
		        	PBFTBlock currentBlock = iterator.next();

		            if(currentBlock.getParent().getHash().hashCode()!=previousBlock.getHash().hashCode()) {
		            	test = false;
		            	failedNodesBlocks.put(node.nodeID, previousBlock.getHeight());
		            	break;
		            }
		            // Move to the next element
		            previousBlock = currentBlock;
		        }
			}
		}
		if(test) {
			System.out.println("test successful!");
		}else {
			System.out.println("test failed in nodes/blocks:");
			for(Map.Entry<Integer, Integer> entry:failedNodesBlocks.entrySet()) {
				System.out.println("["+entry.getKey().toString()+"/"+entry.getValue().toString()+"]");
			}
		}
	}
    /**
     * Writes the local ledger information for each BECPNode in the network to an output stream.
     */
    protected void writeLocalLedger(){
        List<PBFTNode> nodes = network.getAllNodes();
        for (PBFTNode node : nodes){
            HashSet<PBFTBlock> localLedger = node.getLocalLedger();
            writer.println("Local ledger node "+node.getNodeID() +":");
            for(PBFTBlock block:localLedger){
                writer.println("block: "+block.getHeight() + ", Parent: "+block.getParent().getHeight()+", Creator: "+block.getCreator().getNodeID()+", Size: "
                        +block.getSize()+", Creation_Time: "+block.getCreationTime()+", Hash: "+block.getHash().hashCode()+" Parent's_Hash: "+block.getParent().getHash().hashCode());}
            writer.println("-----------------------------------------");
            writer.flush();
        }
    }
    
    /**
     * Calculates the average consensus time based on the recorded times of confirmed blocks.
     *
     * @return The calculated average consensus time, or 0.0 if no consensus times are available.
     */
    public static double getAverageConsensusTime() {
    	return consensusTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }

	public static HashMap<PBFTNode, Double> getNodesToBeCrashed() {
		return nodesToBeCrashed;
	}
}
