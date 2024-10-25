package jabs.scenario;

import jabs.Main;
import jabs.consensus.algorithm.BECP;
import jabs.consensus.config.BECPConsensusConfig;
import jabs.ledgerdata.becp.A;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.BECPPush;
import jabs.ledgerdata.becp.C;
import jabs.ledgerdata.becp.PushEntry;
import jabs.ledgerdata.becp.ReplicaBlock;
import jabs.log.AbstractLogger;
import jabs.log.BECPCSVLogger;
import jabs.network.message.GossipMessage;
import jabs.ledgerdata.BlockFactory;
import jabs.network.networks.becp.BECPWANNetwork;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.simulator.event.Event;
import jabs.simulator.event.NodeCycleEvent;
import jabs.simulator.randengine.RandomnessEngineAdapter;
import jabs.ledgerdata.becp.Process;
import jabs.ledgerdata.becp.Metadata;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static jabs.network.node.nodes.becp.BECPNode.BECP_GENESIS_BLOCK;
/**
 * File: BECPScenario.java
 * Description: Implements BECP protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class BECPScenario extends AbstractScenario{
 // ---------------------------------------------------------------------------------------
 // Constants defining various time intervals, cycle limits, and network parameters.
 // ---------------------------------------------------------------------------------------
 public static final double PUSH_INTERVAL = WANNetworkStats.MIN_LATENCY; // (D1)(seconds) Interval for nodes to start push operations.
 public static final double MAX_LATENCY = WANNetworkStats.MAX_LATENCY; // (D2)(seconds) Maximum propagation delay between any pair of nodes in the network.
 public static final double MAX_SYNC_OFFSET = 0.001; // (D3)(seconds) Maximum synchronization offset between any pair of nodes in the network.
 public static final double CYCLE_TIME = (PUSH_INTERVAL + (2 * MAX_LATENCY) + MAX_SYNC_OFFSET); // (seconds) Next start cycle time.
 private static final int MAX_CYCLES = 99999999; // Maximum cycle number nodes should operate.
 // ------------ SSEP & REAP ---------------
 private static final double VALUE_I = 1; // The "v" value of the leader node.
 private static final double WEIGHT_I = 1; // The "w" value of the leader node.
 private static final double VALUE = 1; // The "v" value of nodes.
 private static final double WEIGHT = 0; // The "w" value of nodes.
 // ------------ PTP ----------------------
 private static final double V_PROPAGATION = 1; // Propagation value.
 private static final double W_PROPAGATION = 1; // Propagation weight.
 private static final double V_AGREEMENT = 0; // Agreement value.
 private static final double W_AGREEMENT = 1; // Agreement weight.
 // ------------ ECP ----------------------
 private static final double V_DATA_AGGREGATION = 3.6; // Data aggregation value (node's value).
 private static final double W_DATA_AGGREGATION  = 1; // Data aggregation weight.
 private static final double V_DATA_CONVERGENCE  = 0; // Convergence value.
 private static final double V_DATA_AGREEMENT  = 0; // Agreement value.
 private static final double WEIGHT_VALUE = 0; // Weight value.
 // ------------ ARP ----------------------
 public static final double A_V_I = 1; // Vi value of A.
 public static final double A_W_I = 1; // Wi value of A.
 public static final int C_IDENTIFIER = Integer.MAX_VALUE; // Identifier value of C.
 public static final double C_V_I = 0; // Vi value of C.
 public static final double C_W_I = 0; // Wi value of C.
 public static final double P_V_I = 1; // vi value of a p.
 public static final double P_W_I = 1; // wi value of a p.
 // ----------------------------------------
 public static final boolean CONTINUOUS_BLOCK_GENERATION = true; // Should nodes generate blocks continuously?
 private static final int BLOCK_GENERATION_INTERVAL = 29; // Interval between two block generations in cycles. (10 seconds = 29 cycles)
 private static final int MAX_BLOCKS_IN_LEDGERS = 99999999; // Maximum number of blocks in local ledgers (max blocks generated).
 private static final boolean RANDOM_BLOCK_GENERATION = true; // Should nodes generate blocks with a probability?
 private static final double BLOCK_GENERATION_PROBABILITY = 0.05; // Probability of generating blocks.
 private static final boolean SINGLE_PROPOSER = false; // Should only one node propose blocks?
 private static final boolean CRASH = false; // Indicates whether nodes should simulate crashes.
 private static final CrashType crashType = CrashType.STRESS; // Indicates type of crash scenarios.
 private static final double CRASH_RATE = 0.3; // The rate at which nodes crash during the simulation.
 private static final double CRASH_TIME = 100; //(at cycle 285) The time (in seconds) at which the stress crash scenario occurs.
 private static final boolean NODES_REJOIN = false; // Allows crashed nodes to rejoin the network and resume participation.
 private static final double JOIN_TIME = 100*CYCLE_TIME; // Specifies the duration (in cycles) that nodes must wait to rejoin the network after a crash.
 public static final boolean TRACKING = true; // Indicates whether the system should record tracking information.
 private static final double TRACKING_TIME = 10; // Specifies the time interval (in seconds) for recording tracking data.
 // ---------------------------------------------------------------------------------------
 private static final boolean WRITE_LOCAL_LEDGERS = true; // Write nodes' local ledgers?
 // ---------------------------------------------------------------------------------------
 	RandomnessEngineAdapter adapter;
    public static int numOfNodes; 
    private final double simulationStopTime;
    private final int neighborCacheSize; //(NCP)-Q_max, the maximum size of neighbour local cache.
    private static PrintWriter writer;
    public static File directory;
    private static HashMap<Integer, ArrayList<Double>> estimations = new HashMap<>();
    public static ArrayList<Double> consensusTimes = new ArrayList<>();
    private HashMap<BECPNode, Double> nodesToBeCrashed = new HashMap<>(); // node -> at simulation time.
    public static LinkedHashMap<Double, ArrayList<Double>> records = new LinkedHashMap<>(); // simulation time -> recorded data
    public static HashMap<Integer, HashMap<Integer, Metadata>> peerBlockMap = new HashMap<>(); // NodeID -> [blockID->{times,{heights...}}]
    static int count;
    
    private enum CrashType {
        UNIFORM,
        STRESS
    }
    
    public BECPScenario(String name, long seed, int numOfNodes, double simulationStopTime, int neighborCacheSize) {
        super(name, seed);
        BECPScenario.numOfNodes = numOfNodes;
        this.simulationStopTime = simulationStopTime;
        this.neighborCacheSize = neighborCacheSize;
        consensusTimes.clear();
        estimations.clear();
        peerBlockMap.clear();
        nodesToBeCrashed.clear();
        
        this.adapter = new RandomnessEngineAdapter(this.randomnessEngine);
        if(CRASH) {
            directory = new File("output/BECP-crash_tests/"+crashType.toString()+"/"+CRASH_RATE+"/"+"BECP-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else if(WANNetworkStats.DISTRIBUTION==WANNetworkStats.LatencyDistribution.PARETO) {
        	directory = new File("output/BECP-pareto_test/"+"/"+WANNetworkStats.alpha+"/"+"BECP-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else {
            directory = new File("output/BECP-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }
        File file = new File(directory, "Blockchain-localLedgers.txt");

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
        network = new BECPWANNetwork(this.randomnessEngine);
        network.populateNetwork(simulator, numOfNodes, new BECPConsensusConfig(), neighborCacheSize, VALUE_I, WEIGHT_I, VALUE, WEIGHT, V_DATA_AGGREGATION, W_DATA_AGGREGATION, V_DATA_CONVERGENCE, V_DATA_AGREEMENT, WEIGHT_VALUE);
    }

    @Override
    protected void insertInitialEvents() {
        List<BECPNode> nodes = network.getAllNodes();
        //Collections.shuffle(nodes, adapter);
        nodes = schedulingStartTime(nodes, PUSH_INTERVAL); // schedule the start time of nodes.
        if(CRASH) {
            while (nodesToBeCrashed.size() < numOfNodes*CRASH_RATE) {
                BECPNode randomNode = nodes.get(randomnessEngine.nextInt(nodes.size()));
                if (!nodesToBeCrashed.containsKey(randomNode)) {
                	double randomTime ;
                	if(crashType == CrashType.STRESS) {
                		randomTime = CRASH_TIME;
                		nodesToBeCrashed.put(randomNode, randomTime);
                	}else if(crashType == CrashType.UNIFORM){
                		randomTime = randomnessEngine.sampleDouble(simulationStopTime); // Generates a random simulation time.
                		nodesToBeCrashed.put(randomNode, randomTime);
                	}
                }
            }
        }
        for (BECPNode node : nodes) {
        	node.addCycleNumber(1);
        	simulator.setSimulationDuration(node.getStartTime()); // set the start time of nodes.
        	//System.out.println("node "+node.nodeID+" started at "+simulator.getSimulationTime());
            //***** Initializing the variables of the protocols*****//
            double nodeValue = 0;
            double nodeWeight = 0;
            //System.out.println(node.getValue()+"  "+node.getWeight()+"  "+node.getEstimation());
            //***** SSEP (System Size Estimation Protocol)*****//
            if (BECP.SSEP) {
                nodeValue = node.getValue()/2;
                nodeWeight = node.getWeight()/2;
                node.setValue(nodeValue);
                node.setWeight(nodeWeight);
            }
            //##### SSEP (System Size Estimation Protocol)#####//
            //***** REAP (*Robust Epidemic Aggregation Protocol)*****//
            if(BECP.REAP||BECP.REAP_PLUS){
                if(node.getWeight()>0 && !node.getConvergenceFlag()){
                    node.setCriticalPushFlag(true);
                }
                nodeValue = node.getValue()/2;
                nodeWeight = node.getWeight()/2;
                node.setValue(nodeValue);
                node.setWeight(nodeWeight);
            }
            //##### REAP (Robust Epidemic Aggregation Protocol)#####//
            //***** ARP (Adaptive Restart Protocol)*****//
            HashMap<Integer, Process> copiedP = new HashMap<>();
            A copiedA = null;
            C copiedC = null;
            if(BECP.ARP) {
                restart(1, node, node.getCycleNumber());
                push(node, node.getCycleNumber());
                copiedP = copyP(node, copiedP); // creates a HashMap of copied processes {p1, p2,..}.
                copiedA = node.getA().clone();
                copiedC = node.getC().clone();
            }
            //##### ARP (Adaptive Restart Protocol)#####//
            //***** NCP (Node Cache Protocol)*****//
            ArrayList<BECPNode> copyNeighborCache = null;
            BECPNode destination = null;
            if (BECP.NCP) {
                trimCache(neighborCacheSize, node.getNeighborsLocalCache());
                destination = getRandomNeighbor(node); // the function getNode() for NCP (Node Cache Protocol).
                copyNeighborCache = new ArrayList<>(node.getNeighborsLocalCache()); // send a copy of neighborsLocalCache.
                copyNeighborCache.remove(destination);
            }
            //##### NCP (Node Cache Protocol)#####//
            BECPBlock newBlock = null;
            HashMap<Integer, BECPBlock> copyBlockCache = new HashMap<>();
            //***** ECP(Epidemic Consensus Protocol) & PTP(Phase Transition Protocol)*****//
            if (BECP.PTP||BECP.ECP) {
                // AGGREGATION STATE (ECP)  PROPAGATION STATE (PTP)
            	if(SINGLE_PROPOSER) {
                	if(node.nodeID==0) {
                		newBlock = BlockFactory.sampleBECPBlock(simulator, network.getRandom(), node, node.getLeader(), BECP_GENESIS_BLOCK, V_PROPAGATION, W_PROPAGATION, V_AGREEMENT, W_AGREEMENT, V_DATA_AGGREGATION, W_DATA_AGGREGATION, V_DATA_CONVERGENCE, V_DATA_AGREEMENT, WEIGHT_VALUE); // generate a new block.
                		BECP_GENESIS_BLOCK.addTochildren(newBlock);
                		//System.out.println("block ID: " + newBlock.getHeight() + ", creator: " + newBlock.getCreator().getNodeID() + ", hash: " + newBlock.getHash().hashCode() + " is generated in node " + node.getNodeID()+" at "+simulator.getSimulationTime());
                        node.setCurrentPreferredBlock(newBlock);
                	}
            	}else {
            		newBlock = BlockFactory.sampleBECPBlock(simulator, network.getRandom(), node, node.getLeader(), BECP_GENESIS_BLOCK, V_PROPAGATION, W_PROPAGATION, V_AGREEMENT, W_AGREEMENT, V_DATA_AGGREGATION, W_DATA_AGGREGATION, V_DATA_CONVERGENCE, V_DATA_AGREEMENT, WEIGHT_VALUE); // generate a new block.
            		BECP_GENESIS_BLOCK.addTochildren(newBlock);
            		//System.out.println("block ID: " + newBECPBlock.getHeight() + ", creator: " + newBECPBlock.getCreator().getNodeID() + ", hash: " + newBECPBlock.getHash().hashCode() + " is generated in node " + node.getNodeID()+" at "+simulator.getSimulationTime());
                    node.setCurrentPreferredBlock(newBlock);
            	}
            	node.addToLocalLedger(BECP_GENESIS_BLOCK);
                copyBlockCache = initializeBlockLocalCache(node, newBlock);
            }
            //##### ECP(Epidemic Consensus Protocol) & PTP(Phase Transition Protocol)#####//
            node.gossipMessage(
                    new GossipMessage(
                            new BECPPush<>(node, node.getCycleNumber(), getSizeOfBlocks(copyBlockCache), nodeValue, nodeWeight, copyNeighborCache, copyBlockCache, node.getCriticalPushFlag(), false, node.getL(), copiedP, copiedA, copiedC, node.getCrashedNodes(), node.getJoinedNodes(), false)
                    ), destination
            );
            simulator.putEvent(new NodeCycleEvent<BECPNode>(node), CYCLE_TIME);
            //System.out.println("next event was set to "+simulator.getSimulationTime()+BECPScenario.CYCLETIME+" for node "+node.getNodeID());
            //*System.out.println("Initializing, sent a push from "+node.getNodeID()+" to "+destination.getNodeID()+" for cycle "+node.getCycleNumber());//*
            //***** REAP (Robust Epidemic Aggregation Protocol)*****//
            if (BECP.REAP||BECP.REAP_PLUS) {
                if(node.getCriticalPushFlag()){ // insert critical push in PushEntriesBuffer.
                	HashMap<Integer, ReplicaBlock> replicaBlockCache = new HashMap<>();
                	if(BECP.REAP_PLUS) {
                		if(node.getBlockLocalCache().size()>0) {
                        	for (BECPBlock becpBlock:node.getBlockLocalCache().values()) {
                        		ReplicaBlock replicaBlock = new ReplicaBlock();
                        		replicaBlock.setVPropagation(becpBlock.getVPropagation());
                        		replicaBlock.setWPropagation(becpBlock.getWPropagation());
                        		replicaBlock.setVAgreement(becpBlock.getVAgreement());
                        		replicaBlock.setWAgreement(becpBlock.getWAgreement());
                        		
                        		replicaBlockCache.put(becpBlock.getHeight(), replicaBlock);
                        	}
                    	}
                	}
                	
                    node.getPushEntriesBuffer().add(new PushEntry(destination, node.getCycleNumber(), BECP.PULL_TIMEOUT,node.getValue(), node.getWeight(), replicaBlockCache));
                }
            }
            //##### REAP (Robust Epidemic Aggregation Protocol)#####//
            //##### Initializing the variables of the protocols#####//
        	//System.out.println("Push Entry cache size is "+node.getPushEntriesBuffer().size()+ " for node "+ node.nodeID);
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
        double recordTime = 0;
        BECPNode node=null;
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        while (!this.simulationStopCondition()&&simulator.isThereMoreEvents()) {
            event = simulator.peekEvent();
            simulationTime = simulator.getSimulationTime();
        	if (event instanceof NodeCycleEvent) { 
        		simulator.executeNextEvent();
        		node = (BECPNode) ((NodeCycleEvent) event).getNode();
            	//System.out.println("a simulationEvent received from node "+ node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        		//*****Start a new Cycle*****//
    			//System.out.println("New Cycle Started in node "+node.getNodeID());
        		if((node.getCycleNumber()<MAX_CYCLES)) {
        			//System.out.println(node.getStartTime()+node.getCycleNumber());
        			//System.out.println("------------NEW CYCLE STARTED "+ "at Node "+node.getNodeID()+"!--------------");
        			if((node.isCrashed)&&NODES_REJOIN) {
            			join(node);
            			continue;
            		}
        			if(CRASH) {
            			if(nodesToBeCrashed.containsKey(node)) {
            				if(nodesToBeCrashed.get(node)<=node.getSimulator().getSimulationTime()) {
            					node.crash();
                    			node.isCrashed=true;
                    			nodesToBeCrashed.remove(node);
                    			if(NODES_REJOIN) {
                    				simulator.putEvent(new NodeCycleEvent<BECPNode>(node), JOIN_TIME); // create an event to rejoin the node.
                    			}
                    			//System.out.println("Node "+node.nodeID+ " was crashed at cycle "+node.getCycleNumber()+" and simulation time "+node.getSimulator().getSimulationTime());
                    			continue;
            				}
            			}
            		}
            		/*
            		if((node.getNodeID()==1)&&(node.getCycleNumber()==100)) {
            			node.crash();
            			node.isCrashed=true;
            			node.addCycleNumber(+1);
            			simulator.putEvent(new NodeCycleEvent<BECPNode>(node), JOIN_TIME); //sets the rejoining time.
            			System.out.println("Node "+node.nodeID+ " was crashed "+node.getCycleNumber());
            			continue;
            		}
            		/*
            		if((node.getNodeID()==2)&&(simulationTime>=50)) {
            			node.crash();
            			node.isCrashed=true;
            			System.out.println("Node "+node.nodeID+ " was crashed!");
            			continue;
            		}
            		
            		if((node.getNodeID()==3)&&(simulationTime>=50)) {
            			node.crash();
            			node.isCrashed=true;
            			System.out.println("Node "+node.nodeID+ " was crashed!");
            			continue;
            		}
            		
            		if((node.getNodeID()==4)&&(simulationTime>=50)) {
            			node.crash();
            			node.isCrashed=true;
            			System.out.println("Node "+node.nodeID+ " was crashed!");
            			continue;
            		}
            		
            		if((node.getNodeID()==5)&&(simulationTime>=50)) {
            			node.crash();
            			node.isCrashed=true;
            			System.out.println("Node "+node.nodeID+ " was crashed!");
            			continue;
            		}
            		
            		if((node.getNodeID()==6)&&(simulationTime>=50)) {
            			node.crash();
            			node.isCrashed=true;
            			System.out.println("Node "+node.nodeID+ " was crashed!");
            			continue;
            		}
            		*/
        			if(CONTINUOUS_BLOCK_GENERATION&&(node.getCycleNumber()%BLOCK_GENERATION_INTERVAL==0)&&(node.getCurrentPreferredBlock().getHeight()<MAX_BLOCKS_IN_LEDGERS)) {
                        //***** Generating new blocks*****//
        				//System.out.println(node.getCycleNumber());
        				if(SINGLE_PROPOSER) {
        					if(node.nodeID==0) {
        						generateNewBlock(node);
        					}
        				}else if(RANDOM_BLOCK_GENERATION){
        					if(randomnessEngine.nextDouble()<=BLOCK_GENERATION_PROBABILITY){
        						generateNewBlock(node);
        					}
        				}else if(!RANDOM_BLOCK_GENERATION){
        					generateNewBlock(node);
        				}
                        //##### Generating new blocks#####//
        			}
        			if (estimations.containsKey(node.getCycleNumber())) {
        			    estimations.get(node.getCycleNumber()).add(node.getEstimation());
        			} else {
        			    ArrayList<Double> values = new ArrayList<>();
        			    values.add(node.getEstimation());
        			    estimations.put(node.getCycleNumber(), values);
        			}
        			startNewCycle(node);
        			if(TRACKING&&(simulationTime>recordTime)) {
        				recordTime = recordTime + TRACKING_TIME;
                    	int currentThroughput=0;
                        double Mv = 0;
                    	double Mw = 0;
                    	ArrayList<Double> actualSystemSize = new ArrayList<>();
                        for (BECPNode peer : (List<BECPNode>) network.getAllNodes()) {
                            if (!peer.isCrashed) {
                            	currentThroughput = peer.getLastConfirmedBlock().getHeight();
                            	Mv = Mv + peer.getValue();
                                Mw = Mw + peer.getWeight();
                                actualSystemSize.add(peer.getEstimation());
                            }
                        }
                        double error_v = Double.valueOf(numOfNodes)-Mv;
                        double error_w = 1.0-Mw;
                        //System.out.println("Mv:"+Mv+", Mw:"+Mw+", est:"+Mv/Mw+", error_v:"+error_v+", error_w:"+error_w, actualSystemSize);
                        ArrayList<Double> data = new ArrayList<>();
                        data.add(Double.valueOf(currentThroughput));
                        data.add(getAverageConsensusTime());
                        data.add(Double.valueOf(BECPCSVLogger.numMessage));
                        data.add(Double.valueOf(BECPCSVLogger.messageSize));
                        data.add(Mv);
                        data.add(Mw);
                        data.add(Mv/Mw);
                        data.add(actualSystemSize.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                    	records.put(simulationTime, data);
                    }
        		}
        		//##### Start a new Cycle#####//
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
        for (BECPNode randomNode : (List<BECPNode>) network.getAllNodes()) {
            if (!randomNode.isCrashed) {
            	//System.out.println("random node is: "+randomNode.nodeID);
                Main.averageBlockchainHeights.add(randomNode.getLastConfirmedBlock().getHeight());
                break;
            }
        }
        
        if(TRACKING) {
        	try (BufferedWriter writer = new BufferedWriter(new FileWriter(directory+"/trackingRecords.txt"))) {
        		writer.write("simulation_time, throughput, latency, #of_messages, message_size, Mv, Mw, system_size, actual_system_size");
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
        
        if(WRITE_LOCAL_LEDGERS) {
        	this.writeLocalLedger();
        }
        testBlockchain();
        //calculateMPE();
        //calculateVAR();
    }
	
    private void join(BECPNode node) {
    	//System.out.println("node "+node.nodeID+" was rejoined at cycle "+node.getCycleNumber()+", and simulation time "+node.getSimulator().getSimulationTime());
		node.restore();
		node.getRecoveryCache().clear();
		node.getPushEntriesBuffer().clear();
		node.getBlockLocalCache().clear();
		node.getCrashedNodes().clear();
		node.getJoinedNodes().clear();
		node.getCrashedNodes().clear();
		node.setValue(0);
		node.setWeight(0);
		trimCache(neighborCacheSize, node.getNeighborsLocalCache());
        BECPNode destination = getRandomNeighbor(node); 
        ArrayList<BECPNode> copyNeighborCache = new ArrayList<>(node.getNeighborsLocalCache()); 
        copyNeighborCache.remove(destination);
		HashMap<Integer, BECPBlock> copyBlockCache = new HashMap<>();
        //*******************************
		node.gossipMessage( // sends an update request message to a random neighbour.
                new GossipMessage(
                        new BECPPush<>(node, node.getCycleNumber(), getSizeOfBlocks(copyBlockCache), node.getValue(), node.getWeight(), copyNeighborCache, copyBlockCache, false, false, node.getL(), null, null, null, node.getCrashedNodes(), node.getJoinedNodes(), true)
                ),destination
        );
		simulator.putEvent(new NodeCycleEvent<BECPNode>(node), CYCLE_TIME);
		//System.out.println("update request were sent from "+node.nodeID+" to "+randomNeighbour.nodeID);
	}

	private void testBlockchain() {
		boolean test = true;
		HashMap<Integer, Integer> failedNodesBlocks = new HashMap<>();
		List<BECPNode> nodes = network.getAllNodes();
		
		for(BECPNode node:nodes) {
			if(!node.isCrashed) {
				Iterator<BECPBlock> iterator = node.getLocalLedger().iterator();
		        if (!iterator.hasNext()) {
		            return; // If the set is empty, do nothing
		        }
		        BECPBlock previousBlock = iterator.next(); // Start with the first block
		        while (iterator.hasNext()) {
		            BECPBlock currentBlock = iterator.next();

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
     * Creates a deep copy of the process elements associated with a BECPNode.
     *
     * @param node     The BECPNode from which the process element is copied.
     * @param copiedP  The ArrayList to store the copied processes.
     * @return A ArrayList containing cloned processes with their respective identifiers.
     */
	private HashMap<Integer, Process> copyP(BECPNode node, HashMap<Integer, Process> copiedP) {
        for (Integer processID : node.getP().keySet()) {
        	Process process = node.getP().get(processID);
            Process clonedProcess = process.clone(); 
            copiedP.put(processID, clonedProcess);
        }
		return copiedP;
	}
	/**
	 * Performs a push operation on the processes associated with a BECPNode.
	 *
	 * @param node         The BECPNode on which the push operation is performed.
	 * @param cycleNumber  The current cycle number for reference.
	 */
	public static void push(final BECPNode peer, final int cycleNumber) {
		for(Integer processID : peer.getP().keySet()) { // Divide data elements and copy tuples.
			Process process = peer.getP().get(processID);
			process.setValue(process.getValue()/2);
			process.setWeight(process.getWeight()/2);
		}
		C c = peer.getC();
		c.setValue(c.getValue()/2);
		c.setWeight(c.getWeight()/2);
	}
	/**
	 * Restarts the state and values of a BECPNode for a new cycle.
	 *
	 * @param l            The next epoch number.
	 * @param node         The BECPNode to be restarted.
	 * @param cycleNumber  The current cycle number for reference.
	 */
	public static void restart(final int l, final BECPNode node, final int cycleNumber) {
		node.setL(l); // set epoch identifier
		node.setState(BECPNode.State.AGGREGATION);
		node.getA().setIdentifier(FAlpha(node.nodeID, cycleNumber)); 
		node.getA().setValue(A_V_I); // xi
		node.getA().setWeight(A_W_I);
		node.getC().setIdentifier(C_IDENTIFIER);
		node.getC().setValue(C_V_I);
		node.getC().setWeight(C_W_I);
		for(Integer processID : node.getP().keySet()) {
			Process process = node.getP().get(processID);
			process.setIdentifier(FBeta(node));
			process.setValue(1);
			process.setWeight(1);
		}
	}
	/**
	 * Schedules start times for BECP nodes based on a random double within a specified maximum time.
	 *
	 * @param nodes   The list of BECP nodes to be scheduled.
	 * @param maxTime The maximum time for scheduling start times.
	 * @return A list of BECP nodes with scheduled start times.
	 */
	private List<BECPNode> schedulingStartTime(List<BECPNode> nodes, double maxTime) {
    	Map<BECPNode, Double> nodeToRandomDouble = nodes.stream().collect(Collectors.toMap(node -> node, node ->this.randomnessEngine.sampleDouble(maxTime)));
    	List<BECPNode> sortedNodes = nodeToRandomDouble.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(entry -> {
            entry.getKey().setStartTime(entry.getValue());
            return entry.getKey();}).collect(Collectors.toList());
    	return sortedNodes;
	}
	/**
	 * Generates a deterministic positive integer identifier based on the provided node ID 
	 * and cycle number. The same inputs will always produce the same output.
	 *
	 * @param nodeID the node's unique identifier
	 * @param cycleNumber the associated cycle number
	 * @return a positive unique identifier derived from the node ID and cycle number
	 */
	public static Integer FAlpha(int nodeID, int cycleNumber) {
        // Combine nodeID and cycleNumber into a single seed
        long seed = ((long) nodeID << 32) | (cycleNumber & 0xFFFFFFFFL);
        // Create a Random instance with the combined seed
        Random random = new Random(seed);
        // Generate a random integer and ensure it's positive
        int uniqueId = random.nextInt() & 0x7FFFFFFF;
        
        return uniqueId;
	}
	/**
	 * Generates a positive integer identifier based on a randomly generated UUID.
	 *
	 * @param node the BECPNode instance (currently unused)
	 * @return a positive integer derived from the least significant bits of a UUID
	 */
	public static Integer FBeta(BECPNode node) {
        UUID uuid = UUID.randomUUID();
        // Get the least significant bits and mask to ensure it's positive
        int uniqueId = (int) (uuid.getLeastSignificantBits() & 0x7FFFFFFF);
        return uniqueId;
    }
	/*
    public static Integer FBeta(BECPNode node) { // produces the same result for each run based on node.nodeID
        byte[] nodeIdBytes = ByteBuffer.allocate(4).putInt(node.nodeID).array();
        // Generate a UUID from the byte array
        UUID uuid = UUID.nameUUIDFromBytes(nodeIdBytes);
        // Get the least significant bits and mask to ensure it's positive
        int uniqueId = (int) (uuid.getLeastSignificantBits() & 0x7FFFFFFF);
        
        return uniqueId;
    }
    */
    
    private void calculateMPE() {
    	double vj = 0;
    	List<BECPNode> nodes = network.getAllNodes();
    	for(BECPNode node:nodes) {
    		vj = vj + node.getValue();
    	}
    	double m = numOfNodes; // the global true value *****
        for (Entry<Integer, ArrayList<Double>> entry : estimations.entrySet()) { // for loop for cycles
            Integer cycle = entry.getKey(); 
            List<Double> values = entry.getValue(); 
            double sum = 0;
            double MPE = 0;
            for (double value : values) { // for loop for the values of each cycle
                sum += Math.abs(((m - value) / m)); // sum of errors
                //System.out.println(value);
            }
            MPE = sum / numOfNodes;
            if (!Main.MPEs.containsKey(cycle)) {
                Main.MPEs.put(cycle, new ArrayList<Double>());
            }
            ArrayList<Double> oldValues = Main.MPEs.get(cycle);
            oldValues.add(MPE);
            Main.MPEs.put(cycle, oldValues);
        }
	}
    
    private void calculateVAR() {
    	double vj = 0;
    	List<BECPNode> nodes = network.getAllNodes();
    	for(BECPNode node:nodes) {
    		vj = vj + node.getValue();
    	}
    	double m = numOfNodes; // the global true average *****
        for (Entry<Integer, ArrayList<Double>> entry : estimations.entrySet()) { // for loop for cycles
            Integer cycle = entry.getKey();
            ArrayList<Double> values = entry.getValue(); //estimated values of nodes
            double sum = 0;
            double avg = 0;
            double VAR = 0;
            double sd = 0;
            
            for (double estimatedValue : values) { // for loop for the nodes' estimated values of each cycle
                sum = sum + Math.pow((m - estimatedValue),2); // sum of errors
                avg = avg + estimatedValue;
            }
            VAR = sum/(numOfNodes-1); // variance of the estimated values
            avg = avg/values.size(); // average of the estimated values
            sd = Math.sqrt(VAR); // standard deviation of the estimated values
           
            if(!Main.VARs.containsKey(cycle)) {
            	Main.VARs.put(cycle, new ArrayList<ArrayList<Double>>()); 
            }
            ArrayList<ArrayList<Double>> oldValues = Main.VARs.get(cycle);
            ArrayList<Double> newValues = new ArrayList<Double>();
            newValues.add(VAR);
            newValues.add(avg);
            newValues.add(sd);

            oldValues.add(newValues); // append new values at each running time
            Main.VARs.put(cycle, oldValues); // update the values
        }
    }

	@Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }
	/**
	 * Calculates the total size of blocks in the provided blockLocalCache.
	 *
	 * @param blockLocalCache  The HashMap containing Hash-to-BECPBlock mappings.
	 * @return The total size of all non-null BECPBlocks in the blockLocalCache.
	 */
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
    /**
     * Initializes the blockLocalCache of a BECPNode with the provided BECPBlock and returns a copy of the cache to be sent.
     *
     * @param node   The BECPNode for which the blockLocalCache is initialized.
     * @param block  The BECPBlock to be added to the blockLocalCache.
     * @return A copy of the blockLocalCache with the added block, or an empty HashMap if the provided block is null.
     */
    private HashMap<Integer, BECPBlock> initializeBlockLocalCache(final BECPNode node, final BECPBlock block){
    	HashMap<Integer, BECPBlock> blockLocalCache = node.getBlockLocalCache();
    	HashMap<Integer, BECPBlock> copyBlockCache = new HashMap<>(); // a copy to be send.
    	if(block!=null) {
    		// Adjust protocol-specific values in the block
            block.setVPropagation(block.getVPropagation()/2);//PTP protocol
            block.setWPropagation(block.getWPropagation()/2);//PTP protocol
            block.setVAgreement(block.getVAgreement()/2);//PTP protocol
            block.setWAgreement(block.getWAgreement()/2);//PTP protocol
            block.setVDataAggregation(block.getVDataAggregation()/2);//ECP protocol
            block.setWDataAggregation(block.getWDataAggregation()/2);//ECP protocol
            block.setVDataConvergence(block.getVDataConvergence()/2);//ECP protocol
            block.setVDataAgreement(block.getVDataAgreement()/2);//ECP protocol
            block.setWeightValue(block.getWeightValue()/2);//ECP protocol
            block.setCycleNumber(node.getCycleNumber()); // REAP protocol
            //System.out.println("a block was generated at "+simulator.getSimulationTime());
            
            // Add the block to the blockLocalCache
            blockLocalCache.put(block.getHeight(), block); // add the block to the block local cache.
            node.setBlockLocalCache(blockLocalCache);
            // Create a copy of the blockLocalCache to be sent
            copyBlockCache.put(block.getHeight(), block.clone());

            return copyBlockCache;
    	}
    	return copyBlockCache;
    }
    /**
     * Generates a new BECPBlock for the given BECPNode, considering the last preferred block.
     *
     * @param node               The BECPNode for which a new block is generated.
     * @param lastPreferredBlock The last Preferred BECPBlock in the network.
     */
    private void generateNewBlock(final BECPNode node) {
    	HashMap<Integer, BECPBlock> peerBlockLocalCache = node.getBlockLocalCache();
    	if(!peerBlockLocalCache.containsKey(node.getCurrentPreferredBlock().getHeight()+1)) {
    		BECPBlock newBlock = BlockFactory.sampleBECPBlock(simulator, randomnessEngine, node, node.getLeader(), node.getCurrentPreferredBlock(), V_PROPAGATION, W_PROPAGATION, V_AGREEMENT, W_AGREEMENT, V_DATA_AGGREGATION, W_DATA_AGGREGATION, V_DATA_CONVERGENCE, V_DATA_AGREEMENT, WEIGHT_VALUE); // generate a new block
    		//System.out.println("block ID: "+newBlock.getHeight()+", creator: "+newBlock.getCreator().getNodeID()+" generated at "+simulator.getSimulationTime());
    		// Update local block cache and current preferred Block in the node.
    		node.getCurrentPreferredBlock().addTochildren(newBlock);
    		peerBlockLocalCache.put(newBlock.getHeight(), newBlock);
            node.setBlockLocalCache(peerBlockLocalCache);
            node.setCurrentPreferredBlock(newBlock);
    	}
    }
    /**
     * Initiates a new cycle for the given BECPNode within the BECP consensus algorithm.
     *
     * @param node  The BECPNode for which a new cycle is started.
     */
	public void startNewCycle(final BECPNode node) {
		//System.out.println("a new cycle started!");
		// Obtain the BECP consensus algorithm from the node and initiate a new cycle.
    	BECP consensus = (BECP) node.getConsensusAlgorithm();
        consensus.newCycle(node);
	}
	/**
	 * Trims the local cache of neighbors for a BECPNode to the specified maximum size.
	 *
	 * @param maxSize              The maximum size to which the local cache should be trimmed.
	 * @param neighborsLocalCache  The ArrayList containing neighbors to be trimmed.
	 */
    private void trimCache(final int maxSize, final ArrayList<BECPNode> neighborsLocalCache) {
        while (neighborsLocalCache.size() > maxSize){
            int randomIndex = this.randomnessEngine.nextInt(neighborsLocalCache.size()); // pick a random node to remove.
            neighborsLocalCache.remove(randomIndex);
        }
    }
    /**
     * Retrieves a random neighbor node for the Node Cache Protocol (NCP).
     * The function getNode() 
     * @param peer The reference node used to obtain neighboring nodes.
     * @param randomnessEngine The engine providing randomness for node selection.
     * @return A neighboring BECPNode selected randomly for gossiping.
     */
    private BECPNode getRandomNeighbor(final BECPNode peer) {
    	BECPNode randomNeighbor;
    	ArrayList<BECPNode> cache = peer.getNeighborsLocalCache();
        randomNeighbor = cache.get(this.randomnessEngine.nextInt(cache.size())); // pick a random node to gossip.
        if(randomNeighbor==peer) {
        	System.out.println("destination is the same as the sender!");
        	return null;
        }
        
        return randomNeighbor;
    }
    /**
     * Writes the local ledger information for each BECPNode in the network to an output stream.
     */
    protected void writeLocalLedger() {
        List<BECPNode> nodes = network.getAllNodes();
        for (BECPNode node : nodes) {
            HashSet<BECPBlock> localLedger = node.getLocalLedger();
            writer.println("Local ledger node " + node.getNodeID() + ":");
            for (BECPBlock block : localLedger) {
                if (block.getParent() == null) {
                    writer.println("block: " + block.getHeight() + ", Parent: null" + ", Creator: null" + ", Size: "
                            + block.getSize() + ", Creation_Time: " + block.getCreationTime() + ", Hash: " + block.getHash().hashCode() + " Parent's_Hash: null");
                    writer.flush();
                } else {
                    writer.println("block: " + block.getHeight() + ", Parent: " + block.getParent().getHeight() + ", Creator: " + block.getCreator().getNodeID() + ", Size: "
                            + block.getSize() + ", Creation_Time: " + block.getCreationTime() + ", Hash: " + block.getHash().hashCode() + " Parent's_Hash: " + block.getParent().getHash().hashCode());
                    writer.flush();
                }
            }
            writer.println("-----------------------------------------");
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
}
