package jabs.scenario;

import jabs.Main;
import jabs.consensus.algorithm.RAFT;
import jabs.consensus.algorithm.RAFT.RAFTPhase;
import jabs.consensus.config.RAFTConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.raft.HeartbeatMessage;
import jabs.ledgerdata.raft.RAFTBlock;
import jabs.ledgerdata.raft.RAFTRequestVoteMessage;
import jabs.ledgerdata.raft.RAFTSendProposalMessage;
import jabs.log.AbstractLogger;
import jabs.log.RAFTCSVLogger;
import jabs.network.message.VoteMessage;
import jabs.network.networks.raft.RAFTWANNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.raft.RAFTNode;
import jabs.network.node.nodes.raft.RAFTNode.NodeState;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.simulator.event.Event;
import jabs.simulator.event.HeartbeatEvent;
import jabs.simulator.event.TimeoutEvent;
import jabs.simulator.randengine.RandomnessEngine;

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
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class RAFTScenario extends AbstractScenario {

    //-------------------------------------------------------------------------------------------------------
    public static int numberOfNodes = 5000;
    public static final double HEARTBEAT_CYCLE = 0.351;
    private static final int MAX_CYCLE = 9999999; //the maximum cycle number nodes should work.
    private static final double MINIMUM_TIMEOUT = 1.1; // minimum time range to detect leader failure.
    private static final double MAXIMUM_TIMEOUT = 1.2;// maximum time range to detect leader failure.
    public static final boolean GENERATE_BLOCKS = true; //should nodes generate blocks continuously?
    public static final int MAX_NUM_OF_BLOCKS = 9999999; // the maximum number of blocks in local ledgers.
    public static final boolean GENERATE_IMMEDIATELY = false; // should generate blocks immediately after reaching a consensus on a block?
    private static final int BLOCK_INTERVAL = 10; //the interval between two block generations in seconds.
    public static final boolean UNIFORM_CRASH = false; // Should nodes simulate crashes?
    private static final double CRASH_RATE = 0.6; // The nodes crash rate (during the simulation time).
    private static final boolean TRACKING = true; // Indicates whether the system should record tracking information.
    private static final double TRACKING_TIME = 10; // Specifies the time interval (in seconds) for recording tracking data.
    private static final boolean WRITE_LOCAL_LEDGERS = true; //write nodes' local ledgers?
    //-------------------------------------------------------------------------------------------------------
    public static File directory;
    private static PrintWriter writer;
    protected double simulationStopTime;
    public boolean firstProposal;
    public static ArrayList<Double> consensusTimes = new ArrayList<>();
    public static HashMap<RAFTNode, Double> nodesToBeCrashed = new HashMap<>(); // node -> at simulation time
    public static LinkedHashMap<Double, ArrayList<Double>> records = new LinkedHashMap<>();
    
    public RAFTScenario(String name, long seed, double simulationStopTime) {
        super(name, seed);
        this.simulationStopTime = simulationStopTime;
        if(UNIFORM_CRASH) {
            directory = new File("output/RAFT-crash_test/"+"/"+CRASH_RATE+"/"+ "RAFT-" + "seed_" + seed + "-numOfNodes_" + numberOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else if(WANNetworkStats.DISTRIBUTION==WANNetworkStats.LatencyDistribution.PARETO) {
        	directory = new File("output/RAFT-pareto_test/"+"/"+WANNetworkStats.alpha+"/"+"RAFT-" + "seed_" + seed + "-numOfNodes_" + numberOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else {
            directory = new File("output/RAFT-" + "seed_" + seed + "-numOfNodes_" + numberOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
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
        network = new RAFTWANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, numberOfNodes, new RAFTConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() {
        List<Node> nodes = (List<Node>) network.getAllNodes();
        while (nodesToBeCrashed.size() < numberOfNodes*CRASH_RATE) {
            RAFTNode randomNode = (RAFTNode) nodes.get(randomnessEngine.nextInt(nodes.size()));
            if (!nodesToBeCrashed.containsKey(randomNode)) {
            	double randomTime = randomnessEngine.sampleDouble(simulationStopTime); // Generates a random simulation time.
                nodesToBeCrashed.put(randomNode, randomTime);
                }
        }
        assignElectionTimeouts(nodes);
    }
    
	private void assignElectionTimeouts(List<Node> nodes) {
        double timeoutRangeMin = MINIMUM_TIMEOUT;
        double timeoutRangeMax = MAXIMUM_TIMEOUT; 
        TreeMap<Double, Node> timeouts = new TreeMap<>(); // Set to store used timeouts.

        for (Node node : nodes) {
            double timeout;
            RandomnessEngine random = node.getNetwork().getRandom();
            do {
                timeout = (random.nextDouble() * (timeoutRangeMax - timeoutRangeMin) + timeoutRangeMin);
            } while (timeouts.containsKey(timeout)); // Ensure uniqueness.

            timeouts.put(timeout, node); // Mark timeout as used.
            RAFTNode raftNode = (RAFTNode)node;
            raftNode.setElectionTimeout(timeout); // set node's election timeout.
        }
        for (Map.Entry<Double, Node> entry : timeouts.entrySet()) {
        	this.simulator.putEvent(new TimeoutEvent(entry.getValue()), entry.getKey()); 
            //System.out.println("node "+ entry.getValue().nodeID+":"+ entry.getKey());
        }
	}
	
	private void setElectionTimer(Node node) {
		RAFTNode raftNode = (RAFTNode) node;
		this.simulator.putEvent(new TimeoutEvent(node), raftNode.getElectionTimeout()); 
	}
	
    private void setHeartbeat(RAFTNode node) {
    	//System.out.println(node.getSimulator().getSimulationTime()+HEARTBEAT_CYCLE);
		this.simulator.putEvent(new HeartbeatEvent(node), HEARTBEAT_CYCLE);
	}

	@Override
    public void run() throws IOException {
    	double recordTime = 0;
    	double simulationTime;
        System.err.printf("Staring %s...\n", this.name);
        this.createNetwork();
        this.insertInitialEvents();
        RAFTNode eventNode_1=null;
        RAFTNode eventNode_2=null;
        RAFT consensus_1=null;
        RAFT consensus_2=null;
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        long simulationStartingTime = System.nanoTime();
        long lastProgressMessageTime = simulationStartingTime;
        while (this.simulator.isThereMoreEvents() && !this.simulationStopCondition()) {
        	simulationTime = this.simulator.getSimulationTime();
            Event event = this.simulator.peekEvent();
            if (event instanceof TimeoutEvent) {  // the node is triggered with its timer.
            	this.simulator.executeNextEvent();
            	eventNode_1 = (RAFTNode) ((TimeoutEvent<RAFTNode>) event).getNode();
        		if((eventNode_1.getCycleNumber()<MAX_CYCLE)&&!eventNode_1.isCrashed) {
        			if(eventNode_1.nodeState==NodeState.FOLLOWER) {
            			if(!eventNode_1.isReceivedHeartbeat) {
            				//*****start the leader election process*****//
            				//System.out.println("node "+eventNode_1.getNodeID()+" detects leader failure, a new election started!");
            				consensus_1 = (RAFT) eventNode_1.getConsensusAlgorithm();
            				consensus_1.raftPhase = RAFTPhase.LEADER_SELECTION;
            				eventNode_1.nodeState =  NodeState.CANDIDATE;
            				eventNode_1.broadcastMessage(
            		                new VoteMessage(
            		                        new RAFTRequestVoteMessage(eventNode_1)
            		                ));
            			}else {
            				//System.out.println("timer of node "+eventNode_1.getNodeID()+" was reset!");
            				eventNode_1.isReceivedHeartbeat = false;
            				setElectionTimer(eventNode_1);
            			}
        			}
        		}
            }else if(event instanceof HeartbeatEvent){
            	eventNode_2 = (RAFTNode) ((HeartbeatEvent<RAFTNode>) event).getNode();
               	if(eventNode_2.nodeState == NodeState.LEADER) {
               		this.simulator.executeNextEvent();
               		consensus_2 = (RAFT) eventNode_2.getConsensusAlgorithm();

               		eventNode_2.broadcastMessage(
    	                    new VoteMessage(
    	                            new HeartbeatMessage(eventNode_2)
    	                    )
    	            );
               		if((GENERATE_IMMEDIATELY)&&(consensus_2.raftPhase == RAFTPhase.LOG_REPLICATION)&&(eventNode_2.shouldSentFirstProposal)) {
               			eventNode_2.shouldSentFirstProposal = false;
               			//System.out.println("first proposal sent!");
               			RAFTBlock newBlock = BlockFactory.sampleRAFTBlock(eventNode_2.getSimulator(),
                        		eventNode_2.getNetwork().getRandom(), eventNode_2, eventNode_2.getLastBlock());
               			eventNode_2.setLastBlock(newBlock);
               			eventNode_2.setLastGeneratedBlockTime(simulator.getSimulationTime());
               			eventNode_2.broadcastMessage(
                                new VoteMessage(
                                        new RAFTSendProposalMessage<>(eventNode_2, newBlock)
                                )
                        );
               		}
               		if((GENERATE_BLOCKS)&&(!GENERATE_IMMEDIATELY)&&(eventNode_2.getLastConfirmedBlockID()<MAX_NUM_OF_BLOCKS)&&(consensus_2.raftPhase == RAFTPhase.LOG_REPLICATION)&&(eventNode_2.getSimulator().getSimulationTime()-eventNode_2.getLastGeneratedBlockTime()>=BLOCK_INTERVAL)) {
               			RAFTBlock newBlock = BlockFactory.sampleRAFTBlock(eventNode_2.getSimulator(),
                        		eventNode_2.getNetwork().getRandom(), eventNode_2, eventNode_2.getLastBlock());
               			eventNode_2.setLastBlock(newBlock);
               			eventNode_2.setLastGeneratedBlockTime(simulator.getSimulationTime());
               			eventNode_2.broadcastMessage(
                                new VoteMessage(
                                        new RAFTSendProposalMessage<>(eventNode_2, newBlock)
                                )
                        );
        			}
               		if(TRACKING&&(simulationTime>recordTime)) {
        				recordTime = recordTime + TRACKING_TIME;
        				ArrayList<Integer> currentThroughput = new ArrayList<>();
                        for (RAFTNode peer : (List<RAFTNode>) network.getAllNodes()) {
                            if (!peer.isCrashed) {
                            	currentThroughput.add(peer.getLastConfirmedBlockID());
                            }
                        }
                        ArrayList<Double> data = new ArrayList<>();
                        data.add(currentThroughput.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                        data.add(getAverageConsensusTime());
                        data.add(Double.valueOf(RAFTCSVLogger.numMessage));
                        data.add(Double.valueOf(RAFTCSVLogger.messageSize));
                    	records.put(simulationTime, data);
                    }
                   	eventNode_2.addCycleNumber(1);
                   	//System.out.println(eventNode_2.getCycleNumber());
                   	setHeartbeat(eventNode_2);
            	}
            }else {
                for (AbstractLogger logger:this.loggers) {
                    logger.logBeforeEachEvent(event);
                }
                this.simulator.executeNextEvent();
                for (AbstractLogger logger:this.loggers) {
                    logger.logAfterEachEvent(event);
                }
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
        System.err.printf("Finished %s.\n", this.name+"-Number of Nodes:"+numberOfNodes+"-Simulation Time:"+this.simulationStopTime);
        for (RAFTNode randomNode : (List<RAFTNode>) network.getAllNodes()) {
            if (!randomNode.isCrashed) {
                Main.averageBlockchainHeights.add(randomNode.getLastConfirmedBlockID());
                break;
            }
        }
        ArrayList<Integer> heights = new ArrayList<>();
        for (RAFTNode raftNode : (List<RAFTNode>) network.getAllNodes()) {
        	//System.out.println("random node is: "+randomNode.nodeID);
    		heights.add(raftNode.getLastConfirmedBlockID());
        }
        Main.averageBlockchainHeights.add((int) heights.stream().mapToInt(Integer::intValue).average().orElse(0.0));
        
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
		List<RAFTNode> nodes = network.getAllNodes();
		
		for(RAFTNode node:nodes) {
			if(!node.isCrashed) {
				Iterator<RAFTBlock> iterator = node.getLocalLedger().iterator();
		        if (!iterator.hasNext()) {
		            return; // If the set is empty, do nothing
		        }
		        RAFTBlock previousBlock = iterator.next(); // Start with the first block
		        while (iterator.hasNext()) {
		        	RAFTBlock currentBlock = iterator.next();

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
        List<RAFTNode> nodes = network.getAllNodes();
        for (RAFTNode node : nodes){
            HashSet<RAFTBlock> localLedger = node.getLocalLedger();
            writer.println("Local ledger node "+node.getNodeID() +":");
            for(RAFTBlock block:localLedger){
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
        return (this.simulator.getSimulationTime() > this.simulationStopTime);
    }
}