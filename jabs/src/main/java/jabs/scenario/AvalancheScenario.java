package jabs.scenario;

import jabs.Main; 
import jabs.consensus.algorithm.Avalanche;
import jabs.consensus.config.SnowConsensusConfig;
import jabs.ledgerdata.TransactionFactory;
import jabs.ledgerdata.snow.AvalancheTx;
import jabs.log.AbstractLogger;
import jabs.log.SnowCSVLogger;
import jabs.network.networks.snow.AvalancheWANNetwork;
import jabs.network.node.nodes.snow.AvalancheNode;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.simulator.event.Event;
import jabs.simulator.event.NodeCycleEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static jabs.network.node.nodes.snow.AvalancheNode.AVALANCHE_GENESIS_TX;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class AvalancheScenario extends AbstractScenario{
 // ---------------------------------------------------------------------------------------
 // Constants defining various time intervals, cycle limits, and network parameters.
 // ---------------------------------------------------------------------------------------
 public static final double START_INTERVAL = WANNetworkStats.MIN_LATENCY; // (D1)(seconds) Interval for node operations initiation.
 public static final double MAX_LATENCY = WANNetworkStats.MAX_LATENCY; // (D2)(seconds) Maximum propagation delay between any pair of nodes in the network.
 public static final double MAX_SYNC_OFFSET = 0.001; // (D3)(seconds) Maximum synchronization offset between any pair of nodes in the network.
 public static final double LOOP_TIME = (START_INTERVAL + 2 * MAX_LATENCY + MAX_SYNC_OFFSET); // (seconds) Next start cycle time.
 private static final int MAX_CYCLES = 9999999; // Maximum number of cycles nodes should operate.
 // ---------------------------------------------------------------------------------------

 // ---------------------------------------------------------------------------------------
 // Transaction Generation and Block Proposal Configuration
 // ---------------------------------------------------------------------------------------
 private static final boolean GENERATE_TX = true; // Should nodes generate transactions?
 private static final boolean SINGLE_PROPOSER = false; // Should only one node propose transactions?
 private static final int TX_GENERATION_INTERVAL = 10; // Interval between two transaction generations in seconds.
 private static final int MAX_TX_GENERATION = 9999999; // Maximum number of transactions nodes should generate (Each transaction needs one subsequent transaction to be accepted).
 private static final boolean RANDOM_TX_GENERATION = true; // Should nodes generate blocks with a probability?
 private static final double TX_GENERATION_PROBABILITY = 0.05; // Probability of generating transactions.
 private static final boolean UNIFORM_CRASH = false; // Should nodes simulate crashes?
 private static final boolean STRESS_CRASH = false; // Indicates another crash scenario where nodes crash simultaneously.
 private static final double CRASH_TIME = 100d; // The time at which the stress crash scenario occurs.
 private static final double CRASH_RATE = 0.6; // The rate at which nodes crash during the simulation.
 public static final boolean TRACKING = true; // Indicates whether the system should record tracking information.
 private static final double TRACKING_TIME = 10; // Specifies the time interval (in seconds) for recording tracking data.
 // ---------------------------------------------------------------------------------------
 private static final boolean RECORD_LOCAL_DAGS = false; // Record logs for local DAGs?
 public static final boolean RECORD_LOCAL_LEDGERS = true; // Record logs for local ledgers?
 // ---------------------------------------------------------------------------------------

    private final int numOfNodes;
    private final double simulationStopTime;
    private PrintWriter writer;
    public static File directory;
    public static ArrayList<Double> consensusTimes = new ArrayList<>();
    private HashMap<AvalancheNode, Double> nodesToBeCrashed = new HashMap<>(); // node -> at simulation time.
    public static LinkedHashMap<Double, ArrayList<Double>> records = new LinkedHashMap<>();
    
    public AvalancheScenario(String name, long seed, int numOfNodes, double simulationStopTime) {
        super(name, seed);
        this.numOfNodes = numOfNodes;
        this.simulationStopTime = simulationStopTime;
        if(UNIFORM_CRASH|STRESS_CRASH) {
            directory = new File("output/Avalanche-crash_test/"+"/"+CRASH_RATE+"/"+"Avalanche-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else if(WANNetworkStats.DISTRIBUTION==WANNetworkStats.LatencyDistribution.PARETO) {
        	directory = new File("output/Avalanche-pareto_test/"+"/"+WANNetworkStats.alpha+"/"+"Avalanche-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else{
            directory = new File("output/Avalanche-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }
        File file = new File(directory, "Blockchain-localLedgers.txt");
        consensusTimes.clear();
        
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
        network = new AvalancheWANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, this.numOfNodes, new SnowConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() { 
        List<AvalancheNode> nodes = network.getAllNodes();
        nodes = schedulingStartTime(nodes, START_INTERVAL); // schedule the start time of nodes.
        if(STRESS_CRASH|UNIFORM_CRASH) {
            while (nodesToBeCrashed.size() < numOfNodes*CRASH_RATE) {
                AvalancheNode randomNode = nodes.get(randomnessEngine.nextInt(nodes.size()));
                if (!nodesToBeCrashed.containsKey(randomNode)) {
                	double randomTime ;
                	if(STRESS_CRASH) {
                		randomTime = CRASH_TIME;
                		nodesToBeCrashed.put(randomNode, randomTime);
                	}else if(UNIFORM_CRASH){
                		randomTime = randomnessEngine.sampleDouble(simulationStopTime); // Generates a random simulation time.
                		nodesToBeCrashed.put(randomNode, randomTime);
                	}
                }
            }
        }
        for(AvalancheNode node:nodes) {
        	simulator.setSimulationDuration(node.getStartTime()); // set the start time of nodes.
        	Avalanche consensus = (Avalanche) node.getConsensusAlgorithm();
        	node.addCycleNumber(1);
        	if(SINGLE_PROPOSER) {
            	if(node.nodeID==0) {
                	onGenerateTx(node); // generate the first block after the genesis block.
                	node.setSelectedTx(node.getNotQueriedTx()); // chooses a not queried transaction from the set of knownTransactions.
                	node.addQueriedTx(node.getSelectedTx());
            	}
        	}else {
            	onGenerateTx(node);
            	node.setSelectedTx(node.getNotQueriedTx()); // chooses a not queried transaction from the set of knownTransactions.
            	node.addQueriedTx(node.getSelectedTx());
        	}
        	
        	consensus.snowballSampling(node); // starts sampling.
            node.getSimulator().putEvent(new NodeCycleEvent(node), LOOP_TIME); // putting a "simulationEvent" to determine the next loop start time.
        }
    }
    /**
     * Generates a new Avalanche transaction and updates the node's state.
     *
     * @param node The Avalanche node for which the transaction is generated.
     */
	private void onGenerateTx(final AvalancheNode node) {
		AvalancheTx newTx = null;
		ArrayList<AvalancheTx> parents = new ArrayList<>();
		Avalanche consensus = (Avalanche) node.getConsensusAlgorithm();
		//parents = parentSelection(node, node.getknownTransactions()); // TO DO: should be checked!
		parents = ChainParentSelection(node.getknownTransactions());
		if(parents.size()>0) {
			//System.out.println(parents.get(0).getHeight());
			newTx = TransactionFactory.sampleAvalancheTransaction(simulator, randomnessEngine, node, parents, consensus.localTxDAG.getHighestTransactionID());
    		//System.out.println("transaction ID: "+newTx.getHeight()+", creator: "+newTx.getCreator().getNodeID()+" generated at "+simulator.getSimulationTime());
			node.setLastGeneratedTX(newTx); // Update the last generated transaction in the node.
			consensus.onReceiveTx(node, newTx); // Notify the consensus algorithm about the new transaction.
		}
	}
	/**
	 * Performs Chain Parent Selection on a set of known Avalanche transactions.
	 *
	 * @param TSet The set of known Avalanche transactions for parent selection.
	 * @return An ArrayList containing the selected parent transaction(s).
	 */
    public static ArrayList<AvalancheTx> ChainParentSelection(final HashSet<AvalancheTx> TSet) {
        if (TSet.size()==1&&TSet.contains(AVALANCHE_GENESIS_TX)) { // For the first transactions. If empty, return the Genesis transaction as the parent.
        	ArrayList<AvalancheTx> parent= new ArrayList<>();
        	parent.add(AVALANCHE_GENESIS_TX);
            return parent;
        } 
        
        ArrayList<AvalancheTx> result = new ArrayList<>();
        AvalancheTx highestTx = null;
        int HighestTxID = 0;
        // Find the transaction with the highest height in the set.
        for(AvalancheTx tx:TSet) {
        	if(tx.getHeight()>HighestTxID) {
        		highestTx = tx;
        		HighestTxID = tx.getHeight();
        	}
        }
        
        if(highestTx != null) {
        	result.add(highestTx);
        }
        
        return result;
    }
    /**
     * Performs parent selection for generating Avalanche transactions based on consensus preferences.
     *
     * @param node      The Avalanche node for which parent selection is performed.
     * @param knownSet  The set of known Avalanche transactions.
     * @return An ArrayList containing the selected parent transaction(s).
     */
	public static ArrayList<AvalancheTx> parentSelection(final AvalancheNode node, final HashSet<AvalancheTx> knownSet) {
		HashSet<AvalancheTx> Tset = new HashSet<>();
		ArrayList<AvalancheTx> resultList = new ArrayList<>();
		// Copy knownSet and add the Genesis transaction.
		Tset.addAll(knownSet);
		// If there is only one transaction in the set, return it as the result.
	    if (Tset.size() == 1) {
	        resultList.addAll(Tset);
	        return resultList;
	    }
	    
	    ArrayList<AvalancheTx> epsilon = new ArrayList<>();
	    ArrayList<AvalancheTx> epsilonPrime = new ArrayList<>();
	    
	    Avalanche consensus = (Avalanche) node.getConsensusAlgorithm();
	    // Populate epsilon with strongly preferred transactions.
	    for (AvalancheTx tx : Tset) {
	        if (consensus.isStronglyPreferred(node, tx)) {
	            epsilon.add(tx);
	        }
	    }
	    // Populate epsilonPrime based on conflicts and confidence values.
	    for (AvalancheTx tx : epsilon) {
	        if (consensus.getLocalTxDAG().conflicts.get(tx.getHeight()).size() == 1 || consensus.getConfidenceValue(node, tx) > 0) {
	            epsilonPrime.add(tx);
	        }
	    }
	    // Select transactions for the result list based on child relationships.
	    for (AvalancheTx tx : epsilonPrime) {
	    	boolean select=true;
	    	if(consensus.getLocalTxDAG().getChildren(tx)!=null) {
		        for (AvalancheTx tprime : consensus.getLocalTxDAG().getChildren(tx)) {
		        	if(true) {
			            if (!(!epsilonPrime.contains(tprime))) {
			            	select=false;
			            	break;
			            }
		        	}
		        }
	    	}
	        if(select) {
	            resultList.add(tx);
	        }
	    }
	    return resultList;
	}
	/**
	 * Schedules start times for Avalanche nodes based on a random double within a specified maximum time.
	 *
	 * @param nodes   The list of Avalanche nodes to be scheduled.
	 * @param maxTime The maximum time for scheduling start times.
	 * @return A list of Avalanche nodes with scheduled start times.
	 */
	private List<AvalancheNode> schedulingStartTime(List<AvalancheNode> nodes, double maxTime) {
		// Map each node to a random double within the specified maximum time.
		Map<AvalancheNode, Double> nodeToRandomDouble = nodes.stream().collect(Collectors.toMap(node -> node, node ->this.randomnessEngine.sampleDouble(maxTime)));
		// Sort the nodes based on the random doubles.
		List<AvalancheNode> sortedNodes = nodeToRandomDouble.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(entry -> {
			// Set the scheduled start time for each node.
            entry.getKey().setStartTime(entry.getValue());
            return entry.getKey();}).collect(Collectors.toList());
    	return sortedNodes;
	}
    
	@Override
    public void run() throws IOException {
        System.err.printf("Staring %s...\n", this.name);
        this.createNetwork();
        this.insertInitialEvents();
        AvalancheNode node=null;
        for (AbstractLogger logger:this.loggers) {
            logger.setScenario(this);
            logger.initialLog();
        }
        long simulationStartingTime = System.nanoTime();
        long lastProgressMessageTime = simulationStartingTime;
        Event event;
        double realTime;
        double simulationTime;
        double recordTime = 0;
        
        while (!this.simulationStopCondition()&&simulator.isThereMoreEvents()) { 
            event = simulator.peekEvent();
            simulationTime = simulator.getSimulationTime();
        	if (event instanceof NodeCycleEvent) { // simulation event: the event that starts the next loop of the nodes (Avalanche Loop).
        		node = (AvalancheNode) ((NodeCycleEvent) event).getNode();
        		simulator.executeNextEvent();
            	//System.out.println("a simulationEvent received from node "+ node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        		if(UNIFORM_CRASH||STRESS_CRASH) {
        			if(nodesToBeCrashed.containsKey(node)) {
        				if(nodesToBeCrashed.get(node)<=node.getSimulator().getSimulationTime()) {
        					node.crash();
                			node.isCrashed=true;
                			//System.out.println("Node "+node.nodeID+ " was crashed!");
                			continue;
        				}
        			}
        			/*
            		if((node.getNodeID()==10)&&(node.getCycleNumber()==17)) {
            			node.crash();
            			node.isCrashed=true;
            			continue;
            		}
            		
            		if((node.getNodeID()==50)&&(node.getCycleNumber()==78)) {
            			node.crash();
            			node.isCrashed=true;
            			continue;
            		}
            		if((node.getNodeID()==40)&&(node.getCycleNumber()==122)) {
            			node.crash();
            			node.isCrashed=true;
            			continue;
            		}
            		*/
        		}
        		if((GENERATE_TX)&&(simulator.getSimulationTime()-node.getLastTriggeredTime()>=TX_GENERATION_INTERVAL)&&(node.getLastGeneratedTX().getHeight()<=MAX_TX_GENERATION)) {
    				if(SINGLE_PROPOSER) {
    					if(node.nodeID==0) {
    						onGenerateTx(node);
    					}
    				}else if(RANDOM_TX_GENERATION){
    					if(randomnessEngine.nextDouble()<=TX_GENERATION_PROBABILITY){
    						onGenerateTx(node);
    					}
    				}else {
    					onGenerateTx(node);
    				}
    				node.setLastTriggeredTime(simulationTime);
            	}
        		if((node.getCycleNumber() <= MAX_CYCLES)&&!node.isCrashed) {
            		node.addCycleNumber(1);
            		Avalanche consensus = (Avalanche) node.getConsensusAlgorithm();
            		consensus.checkTransactionsAcceptance(); //********* to check transaction acceptance at every cycle.
            		if(node.getNotQueriedTx() != null) {
                    	node.setSelectedTx(node.getNotQueriedTx()); // chooses a not queried transaction.
                    	node.addQueriedTx(node.getSelectedTx());
                        consensus.snowballSampling(node); // starts sampling.
                	}
            		
                    node.getSimulator().putEvent(new NodeCycleEvent(node), LOOP_TIME); 
                    
                    if(TRACKING&&(simulationTime>recordTime)) {
        				recordTime = recordTime + TRACKING_TIME;
        				ArrayList<Integer> currentThroughput = new ArrayList<>();
                        for (AvalancheNode peer : (List<AvalancheNode>) network.getAllNodes()) {
                            if (!peer.isCrashed) {
                            	currentThroughput.add(peer.getLastConfirmedTx().getHeight());
                            }
                        }
                        ArrayList<Double> data = new ArrayList<>();
                        data.add(currentThroughput.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                        data.add(getAverageConsensusTime());
                        data.add(Double.valueOf(SnowCSVLogger.numMessage));
                        data.add(Double.valueOf(SnowCSVLogger.messageSize));
                    	records.put(simulationTime, data);
                    }
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
        if(RECORD_LOCAL_LEDGERS) {
            List<AvalancheNode> nodes = network.getAllNodes();
            for (AvalancheNode avalancheNode : nodes){
                HashSet<AvalancheTx> localLedger = avalancheNode.getLocalLedger();
                writer.println("Local ledger node "+avalancheNode.getNodeID() +":");
                for(AvalancheTx transaction:localLedger){
                    writer.println("transaction: "+transaction.getHeight() + ", Parent: "+transaction.getParents().get(0).getHeight()+", Creator: "+transaction.getCreator().getNodeID()+", Size: "
                            +transaction.getSize()+", Creation_Time: "+transaction.getCreationTime()+", Hash: "+transaction.getHash().hashCode()+" Parent's_Hash: "+transaction.getParents().get(0).getHash().hashCode());}
                writer.println("-----------------------------------------");
                writer.flush();
            }
        }
        if(RECORD_LOCAL_DAGS) {
            List<AvalancheNode> peers = network.getAllNodes();
            writer.println("[Tx-id, Tx-parent, Tx-creator, Hash, Parent's_Hash]");
            for(AvalancheNode peer:peers) {
            	Avalanche consensus = (Avalanche) peer.getConsensusAlgorithm();
            	consensus.checkTransactionsAcceptance();
            }
            for(AvalancheNode peer:peers) {
            	ArrayList<ArrayList<AvalancheTx>> acceptedTxs = peer.getConsensusAlgorithm().getLocalTxDAG().getAcceptedTx();
            	writer.println("Accepted transactions for node: "+peer.getNodeID());
            	this.writeDAGLocalLedger(acceptedTxs);
            }
            writer.flush();
        }
        System.err.printf("Finished %s.\n", this.name+"-Number of Nodes:"+this.numOfNodes+"-Simulation Time:"+this.simulationStopTime);
        ArrayList<Integer> heights = new ArrayList<>();
        for (AvalancheNode avalancheNode : (List<AvalancheNode>) network.getAllNodes()) {
        	//System.out.println("random node is: "+randomNode.nodeID);
    		heights.add(avalancheNode.getLastConfirmedTx().getHeight());
        }
        Main.averageBlockchainHeights.add((int) heights.stream().mapToInt(Integer::intValue).average().orElse(0.0));
        
        if(TRACKING) {
        	try (BufferedWriter writer = new BufferedWriter(new FileWriter(directory+"/trackingRecords.txt"))) {
        		writer.write("simulation_time, throughput, latency, #of_messages, message_size");
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
		List<AvalancheNode> nodes = network.getAllNodes();
		
		for(AvalancheNode node:nodes) {
			if(!node.isCrashed) {
				Iterator<AvalancheTx> iterator = node.getLocalLedger().iterator();
		        if (!iterator.hasNext()) {
		            return; // If the set is empty, do nothing
		        }
		        AvalancheTx previousTx = iterator.next(); // Start with the first block
		        while (iterator.hasNext()) {
		        	AvalancheTx currentTx = iterator.next();

		            if(currentTx.getParents().get(0).getHash().hashCode()!=previousTx.getHash().hashCode()) {
		            	test = false;
		            	failedNodesBlocks.put(node.nodeID, previousTx.getHeight());
		            	break;
		            }
		            // Move to the next element
		            previousTx = currentTx;
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

    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }
    /**
     * Writes the local ledger information of an Avalanche Directed Acyclic Graph (DAG) to the output.
     *
     * @param txs The ArrayList of ArrayLists containing Avalanche transactions organized by levels in the DAG.
     */
	protected void writeDAGLocalLedger(final ArrayList<ArrayList<AvalancheTx>> txs) {
		for(ArrayList<AvalancheTx> level: txs) {
			writer.print("Height ("+txs.indexOf(level)+"):");
			for(AvalancheTx tx:level) {
				writer.print("[" + tx.getHeight() + ", "+((tx.getHeight() != 0) ? tx.getParents().get(0).getHeight() : "null")+", "+((tx.getHeight() != 0) ? tx.getCreator().getNodeID() : "null")+", "+tx.hashCode()+", "+((tx.getHeight() != 0) ? tx.getParents().get(0).hashCode() : "null") + "]");
			}
			writer.print("\n");
		}
		writer.println("------------------------------------------------");
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
