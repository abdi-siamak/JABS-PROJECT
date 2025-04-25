package jabs.scenario;

import jabs.Main;
import jabs.consensus.algorithm.Snow;
import jabs.consensus.config.SnowConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.snow.SnowBlock;
import jabs.log.AbstractLogger;
import jabs.log.SnowCSVLogger;
import jabs.network.networks.snow.SnowWANNetwork;
import jabs.network.node.nodes.snow.SnowNode;
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
/**
 * File: Snow.java
 * Description: Implements Snow protocols for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class SnowScenario extends AbstractScenario{
    //-------------------------------------------------------------------------------------------------------
    public static final double START_INTERVAL = WANNetworkStats.MIN_LATENCY; // (D1)(seconds) Interval for node operations initiation.
    public static final double MAX_LATENCY = WANNetworkStats.MAX_LATENCY; // (D2)(seconds) Maximum propagation delay between any pair of nodes in the network.
    public static final double MAX_SYNC_OFFSET = 0.001; // (D3)(seconds) Maximum synchronization offset between any pair of nodes in the network.
    public static final double LOOP_TIME = (START_INTERVAL + 2 * MAX_LATENCY + MAX_SYNC_OFFSET); // (seconds) Next start cycle time.
    // ---------------------------------------------------------------------------------------
    private static final int MAX_CYCLE = 99999999; // the maximum number of cycles nodes should operate
    private static final boolean GENERATE_BLOCKS = true; // Should nodes generate blocks?
    private static final boolean SINGLE_PROPOSER = false; // Should only one node propose blocks?
    private static final int BLOCK_GENERATION_INTERVAL = 29; // Interval between two block generations in cycles. (10 seconds = 29 cycles)
    private static final int MAX_BLOCK_GENERATION = 9999999; // Maximum number of blocks nodes should generate.
    private static final boolean RANDOM_BLOCK_GENERATION = true; // Should nodes generate blocks with a probability?
    private static final double BLOCK_GENERATION_PROBABILITY = 0.05; // Probability of generating blocks.
    private static final boolean CRASH = false; // Indicates whether nodes should simulate crashes.
    private static final CrashType crashType = CrashType.UNIFORM; // Indicates type of crash scenarios.
    private static final double CRASH_RATE = 0.3; // The rate at which nodes crash during the simulation.
    private static final double CRASH_TIME = 100; // (at cycle 285) The time (in seconds) at which the stress crash scenario occurs.
    public static final boolean TRACKING = true; // Indicates whether the system should record tracking information.
    private static final double TRACKING_TIME = 10; // Specifies the time interval (in seconds) for recording tracking data.
    // ---------------------------------------------------------------------------------------
    private static final boolean WRITE_LOCAL_LEDGERS = true; // Write nodes' local ledgers?
    //-------------------------------------------------------------------------------------------------------
    private final int numOfNodes;
    private final double simulationStopTime;
    private static PrintWriter writer;
    public static ArrayList<Double> consensusTimes = new ArrayList<>();
    private HashMap<SnowNode, Double> nodesToBeCrashed = new HashMap<>(); // node -> at simulation time.
    public static File directory;
    public static LinkedHashMap<Double, ArrayList<Double>> records = new LinkedHashMap<>(); // simulation time -> recorded data
    int max = 0;
    
    private enum CrashType {
        UNIFORM,
        STRESS
    }
    
    public SnowScenario(String name, long seed, int numOfNodes, double simulationStopTime) {
        super(name, seed);
        this.numOfNodes = numOfNodes;
        this.simulationStopTime = simulationStopTime;
        consensusTimes.clear();
        nodesToBeCrashed.clear();
        
        if(CRASH) {
            directory = new File("output/SNOW-crash_tests/"+crashType.toString()+"/"+CRASH_RATE+"/"+"SNOW-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else if(WANNetworkStats.DISTRIBUTION==WANNetworkStats.LatencyDistribution.PARETO) {
        	directory = new File("output/SNOW-pareto_test/"+"/"+WANNetworkStats.alpha+"/"+"SNOW-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
        }else {
        	directory = new File("output/SNOW-" + "seed_" + seed + "-numOfNodes_" + numOfNodes + "-simulationTime_" + (int)simulationStopTime+"/");
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
        network = new SnowWANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, this.numOfNodes, new SnowConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() {
        List<SnowNode> nodes = network.getAllNodes();
        nodes = schedulingStartTime(nodes, START_INTERVAL); // schedule the start time of nodes.
        if(CRASH) {
            while (nodesToBeCrashed.size() < numOfNodes*CRASH_RATE) {
                SnowNode randomNode = nodes.get(randomnessEngine.nextInt(nodes.size()));
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
        for(SnowNode node:nodes) {
        	simulator.setSimulationDuration(node.getStartTime()); // set the start time of nodes.
        	node.addRoundNumber();
        	SnowBlock newBlock = null;
        	Snow consensus = (Snow) node.getConsensusAlgorithm();
			if((GENERATE_BLOCKS)&&(node.getCurrentBlock().getHeight()<MAX_BLOCK_GENERATION)) {
                //***** Generating new blocks*****//
				//System.out.println(node.getCycleNumber());
				//##### Generating new blocks#####//
				if(SINGLE_PROPOSER) {
					if(node.nodeID==0) {
			    		newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastGeneratedBlock()); // generate a new block
			    		//System.out.println("initial block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
			    		//System.out.println(newBlock.getBinaryHash());
			    		node.setLastGeneratedBlock(newBlock);
			    		consensus.blocks.add(newBlock);
			    		StringBuilder newPref = new StringBuilder();
			    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
			    		consensus.currentPref = newPref.toString(); 
						//consensus.pref = H(node.getPrefBlockchain());
						//System.out.println(chain(node.getPrefBlockchain(), consensus.pref).size());
					}
				}else if(RANDOM_BLOCK_GENERATION){
					if(randomnessEngine.nextDouble()<=BLOCK_GENERATION_PROBABILITY){
						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastGeneratedBlock()); // generate a new block
			    		//System.out.println("initial block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
			    		//System.out.println(newBlock.getBinaryHash());
			    		node.setLastGeneratedBlock(newBlock);
			    		consensus.blocks.add(newBlock);
			    		StringBuilder newPref = new StringBuilder();
			    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
			    		consensus.currentPref = newPref.toString(); 
						//consensus.pref = H(node.getPrefBlockchain());
						//System.out.println(chain(node.getPrefBlockchain(), consensus.pref).size()); 
					}
				}else if(!RANDOM_BLOCK_GENERATION){
					newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastGeneratedBlock()); // generate a new block
		    		//System.out.println("initial block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
		    		//System.out.println(newBlock.getBinaryHash());
		    		node.setLastGeneratedBlock(newBlock);
		    		consensus.blocks.add(newBlock);
		    		StringBuilder newPref = new StringBuilder();
		    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
		    		consensus.currentPref = newPref.toString(); 
					//consensus.pref = H(node.getPrefBlockchain());
					//System.out.println(chain(node.getPrefBlockchain(), consensus.pref).size());
				}
			}
        	startNewLoop(node);
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
        double recordTime = 0;
        SnowBlock newBlock = null;
        while (!this.simulationStopCondition()&&simulator.isThereMoreEvents()) {
            event = simulator.peekEvent();
            simulationTime = simulator.getSimulationTime();
        	if (event instanceof NodeCycleEvent) { 
        		node = (SnowNode) ((NodeCycleEvent) event).getNode();
        		simulator.executeNextEvent();
            	//System.out.println("a simulationEvent received from node "+ node.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        		if(node.getCycleNumber()<MAX_CYCLE){
        			if(CRASH) {
            			if(nodesToBeCrashed.containsKey(node)) {
            				if(nodesToBeCrashed.get(node)<=node.getSimulator().getSimulationTime()) {
            					node.crash();
                    			node.isCrashed=true;
                    			nodesToBeCrashed.remove(node);
                    			System.out.println("Node "+node.nodeID+ " was crashed at cycle "+node.getCycleNumber()+" and simulation time "+node.getSimulator().getSimulationTime());
                    			continue;
            				}
            			}
            		}
        			if (Snow.SLUSH) {
        				if ((node.newRound)&&(GENERATE_BLOCKS)&&(node.getCycleNumber()%BLOCK_GENERATION_INTERVAL==0)&&(node.getCurrentBlock().getHeight()<=MAX_BLOCK_GENERATION)) {
        					if(SINGLE_PROPOSER) {
            					if(node.nodeID==0) {
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
                		    		node.setCurrentBlock(newBlock);
                		    		node.newRound = false;
            					}
            				}else if(RANDOM_BLOCK_GENERATION){
            					if(randomnessEngine.nextDouble()<=BLOCK_GENERATION_PROBABILITY){
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
                		    		node.setCurrentBlock(newBlock);
                		    		node.newRound = false;
            					}
            				}else if(!RANDOM_BLOCK_GENERATION){
            					newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
            		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
            		    		node.setCurrentBlock(newBlock);
            		    		node.newRound = false;
            				}
        				}
        			}
        			if (Snow.SNOWBALL||Snow.SNOWFLAKE||Snow.SNOWFLAKE_PLUS) {
        				if((GENERATE_BLOCKS)&&(node.newRound)&&(node.getCycleNumber()%BLOCK_GENERATION_INTERVAL==0)&&(node.getCurrentBlock().getHeight()<MAX_BLOCK_GENERATION)) {
            				//##### Generating new blocks#####//
            				if(SINGLE_PROPOSER) {
            					if(node.nodeID==0) {
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
                        			node.isDecided = false;
                		    		node.setCurrentBlock(newBlock);
                		    		node.newRound = false;
            					}
            				}else if(RANDOM_BLOCK_GENERATION){
            					if(randomnessEngine.nextDouble()<=BLOCK_GENERATION_PROBABILITY){
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
                        			node.isDecided = false;
                		    		node.setCurrentBlock(newBlock);
                		    		node.newRound = false;
            					}
            				}else if(!RANDOM_BLOCK_GENERATION){
            					newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, node.getLastConfirmedBlock()); // generate a new block
            		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
                    			node.isDecided = false;
            		    		node.setCurrentBlock(newBlock);
            		    		node.newRound = false;
            				}
            			}
        			}
        			if (Snow.SNOWMAN) {
        				Snow consensus = (Snow) node.getConsensusAlgorithm();
    					String pref = consensus.pref;
    					SnowBlock lastPrefBlock = consensus.last(consensus.blocks, pref);
        				if((GENERATE_BLOCKS)&&(node.getCycleNumber()%BLOCK_GENERATION_INTERVAL==0)&&(node.getLastGeneratedBlock().getHeight()<MAX_BLOCK_GENERATION)) {
            				//##### Generating new blocks#####//
            				if(SINGLE_PROPOSER) {
            					if(node.nodeID==2||node.nodeID==1||node.nodeID==6) {
            						if ((node.getLastGeneratedBlock()!=SnowNode.SNOW_GENESIS_BLOCK)&&(node.getLastGeneratedBlock().getParent().equals(lastPrefBlock))) {
            							continue;
            						}
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, lastPrefBlock); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
            						//System.out.println(newBlock.getBinaryHash()+ " at " + node.getSimulator().getSimulationTime());
            			    		node.setLastGeneratedBlock(newBlock);
            			    		//System.out.println(newBlock.getHeight());
            			    		consensus.blocks.add(newBlock);
            			    		StringBuilder newPref = new StringBuilder();
            			    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
            			    		consensus.currentPref = newPref.toString(); 
            					}
            				}else if(RANDOM_BLOCK_GENERATION){
            					if(randomnessEngine.nextDouble()<=BLOCK_GENERATION_PROBABILITY){
            						if ((node.getLastGeneratedBlock()!=SnowNode.SNOW_GENESIS_BLOCK)&&(node.getLastGeneratedBlock().getParent().equals(lastPrefBlock))) {
            							continue;
            						}
            						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, lastPrefBlock); // generate a new block
                		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
            						//System.out.println(newBlock.getBinaryHash()+ " at " + node.getSimulator().getSimulationTime());
            			    		node.setLastGeneratedBlock(newBlock);
            			    		//System.out.println(newBlock.getHeight());
            			    		consensus.blocks.add(newBlock);
            			    		StringBuilder newPref = new StringBuilder();
            			    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
            			    		consensus.currentPref = newPref.toString(); 
            					}
            				}else if(!RANDOM_BLOCK_GENERATION){
        						if ((node.getLastGeneratedBlock()!=SnowNode.SNOW_GENESIS_BLOCK)&&(node.getLastGeneratedBlock().getParent().equals(lastPrefBlock))) {
        							continue;
        						}
        						newBlock = BlockFactory.sampleSnowBlock(simulator, randomnessEngine, node, lastPrefBlock); // generate a new block
            		    		//System.out.println("new block with ID  " + newBlock.getHeight() + "  was created by node " + node.nodeID + " at " + node.getSimulator().getSimulationTime());
        						//System.out.println(newBlock.getBinaryHash()+ " at " + node.getSimulator().getSimulationTime());
        			    		node.setLastGeneratedBlock(newBlock);
        			    		//System.out.println(newBlock.getHeight());
        			    		consensus.blocks.add(newBlock);
        			    		StringBuilder newPref = new StringBuilder();
        			    		newPref.append(consensus.currentPref).append(newBlock.getBinaryHash());
        			    		consensus.currentPref = newPref.toString(); 
            				}
            			}
        			}
        			//##### Start a new cycle#####//
        			startNewLoop(node); 
        			if(TRACKING&&(simulationTime>recordTime)) {
        				recordTime = recordTime + TRACKING_TIME;
        				ArrayList<Integer> currentThroughput = new ArrayList<>();
                        for (SnowNode peer : (List<SnowNode>) network.getAllNodes()) {
                        	currentThroughput.add(peer.getLastConfirmedBlock().getHeight());
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
        ArrayList<Integer> heights = new ArrayList<>();
        for (SnowNode snowNode : (List<SnowNode>) network.getAllNodes()) {
    		heights.add(snowNode.getLastConfirmedBlock().getHeight());
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
        
        if(WRITE_LOCAL_LEDGERS) {
        	this.writeLocalLedger();
        }
        testBlockchain();
    }

	private void startNewLoop(final SnowNode node) {
		node.addCycleNumber(1);
		Snow consensus = (Snow) node.getConsensusAlgorithm();
		if (Snow.SLUSH) {
			if (node.getCurrentBlock()!=SnowNode.SNOW_GENESIS_BLOCK) {
				//##### start sampling#####//
				consensus.snowballSampling(node);
				//System.out.println("a new sampling started!");
			}
			if((node.getCycleNumber()%Snow.CONVERGENCE_TIME)==0) { // accepts the block;
				node.newRound = true;
				consensus.setCurrentMainChainHead(node.getCurrentBlock()); 
                node.setLastConfirmedBlock(node.getCurrentBlock());
            	SnowScenario.consensusTimes.add(node.getSimulator().getSimulationTime()-node.getCurrentBlock().getCreationTime()); // record the consensus time.
				//System.out.println("a new round started!");
			}
		}
		if (Snow.SNOWBALL||Snow.SNOWFLAKE||Snow.SNOWFLAKE_PLUS) {
			//System.out.println("a new sampling started!");
			if (!node.isDecided) {
				//##### start sampling#####//
				consensus.snowballSampling(node);
				//System.out.println("a new sampling started!");
			}
		}
		if(Snow.SNOWMAN) {
			//System.out.println("a new sampling started!");
			//##### start sampling#####//
			consensus.snowballSampling(node);
		}
        node.getSimulator().putEvent(new NodeCycleEvent(node), LOOP_TIME); // put a "simulationEvent" to set the next loop start time
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
            	if (block==SnowNode.SNOW_GENESIS_BLOCK) {
            		continue;
            	}
                writer.println("block: "+block.getHeight() + ", Parent: "+block.getParent().getHeight()+", Creator: "+block.getCreator().getNodeID()+", Size: "
                        +block.getSize()+", Creation_Time: "+block.getCreationTime()+", Hash: "+block.hashCode());}
            writer.println("-----------------------------------------");
            writer.flush();
        }
    }
    
	public ArrayList<SnowBlock> chain(ArrayList<SnowBlock> seenBlocks, String sigma) { // returns an ordered blockchain
		ArrayList<SnowBlock> prefBlockChain = new ArrayList<>();
        int numCompleteHashes = sigma.length() / 32;

        for (int i = 0; i < numCompleteHashes; i++) {
            int startIndex = i * 32;
            int endIndex = startIndex + 32;
            String blockHash = sigma.substring(startIndex, endIndex);

            for (SnowBlock block : seenBlocks) {
                if (block.getBinaryHash().equals(blockHash)) {
                	prefBlockChain.add(block);
                    break;
                }
            }
        }
        
        if(prefBlockChain.size()==0) {
        	SnowBlock b_0 = (SnowBlock) SnowNode.SNOW_GENESIS_BLOCK;
        	prefBlockChain.add(b_0);
        }
       
        return prefBlockChain;
	}
    
	private String H(ArrayList<SnowBlock> B) {
		StringBuilder chainHash = new StringBuilder();
	    for (SnowBlock block : B) {
	    	chainHash.append(block.getBinaryHash()); 
	    }
	    
	    return chainHash.toString(); // returns a string of block chain
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
    
	private void testBlockchain() {
		boolean test = true;
		HashMap<Integer, Integer> failedNodesBlocks = new HashMap<>();
		List<SnowNode> nodes = network.getAllNodes();
		
		for(SnowNode node:nodes) {
			 HashSet<SnowBlock> localLedger = node.getConsensusAlgorithm().getConfirmedBlocks();
	            List<SnowBlock> blocks = getOrdered(localLedger);
			Iterator<SnowBlock> iterator = blocks.iterator();
	        if (!iterator.hasNext()) {
	            return; // If the set is empty, do nothing
	        }
	        SnowBlock previousBlock = iterator.next(); // Start with the first block
	        while (iterator.hasNext()) {
	            SnowBlock currentBlock = iterator.next();

	            if(currentBlock.getParent().getHash().hashCode()!=previousBlock.getHash().hashCode()) {
	            	test = false;
	            	failedNodesBlocks.put(node.nodeID, previousBlock.getHeight());
	            	break;
	            }
	            // Move to the next element
	            previousBlock = currentBlock;
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
	 * Schedules start times for Snow nodes based on a random double within a specified maximum time.
	 *
	 * @param nodes   The list of Snow nodes to be scheduled.
	 * @param maxTime The maximum time for scheduling start times.
	 * @return A list of Snow nodes with scheduled start times.
	 */
	private List<SnowNode> schedulingStartTime(List<SnowNode> nodes, double maxTime) {
    	Map<SnowNode, Double> nodeToRandomDouble = nodes.stream().collect(Collectors.toMap(node -> node, node ->this.randomnessEngine.sampleDouble(maxTime)));
    	List<SnowNode> sortedNodes = nodeToRandomDouble.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(entry -> {
            entry.getKey().setStartTime(entry.getValue());
            return entry.getKey();}).collect(Collectors.toList());
    	return sortedNodes;
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
