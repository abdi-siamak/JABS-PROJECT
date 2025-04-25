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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */

public class Snow<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements QueryingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {

    //*-------------------------------------------------------------------------------------------------------
	public static final boolean SLUSH = false; 
	public static final boolean SNOWFLAKE = false; 
	public static final boolean SNOWBALL = false; 
	public static final boolean SNOWFLAKE_PLUS = false;
	public static final boolean SNOWMAN = true;
    //**********************Parameter Settings for Snow protocols*********************
    public static final int K = 20; // sample size
    private static final int ALPHA_1 = 10; // ALPHA_1 > k/2-quorum size or threshold 
    private static final int ALPHA_2 = 15; // ALPHA_2 >= ALPHA_1-quorum size or threshold for finalizing a block
    private static final int BETA = 15; // conviction threshold
    public static final int CONVERGENCE_TIME = 200; // Slush convergence time in cycle
    //--------------------------------------------------------------------------------------------------
    private static final boolean WRITE_CONSENSUS_LOGS = false; // Write logs for the occurring consensus.
    private static final boolean RECORD_LEDGERS = true; // Record logs for the local ledgers.
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //*-------------------------------------------------------------------------------------------------------
    private static PrintWriter writer;
    private final int numAllParticipants;
    File directory = SnowScenario.directory;
    private final HashMap<SnowBlock, HashMap<SnowNode, Query>> commitQueries = new HashMap<>();
    public HashMap<SnowBlock, Integer> confidenceValues = new HashMap<>();
    private int numReceivedSamples;
    private int conviction = 0; // cnt or count
    //*------------------------------------------
    private final HashMap<SnowNode, HashMap<String, ArrayList<SnowBlock>>> responseQueries = new HashMap<>(); // rpref
    public ArrayList<SnowBlock> blocks = new ArrayList<>(); 
    private HashSet<SnowBlock> E = new HashSet<>();
    private String val; // val(pref)-one char
    private HashMap<String, Integer> count; // count(sigma)
    public String pref;
    public String currentPref;
    private String Final;
    private String sigma;
    private SnowPhase snowPhase = SnowPhase.QUERYING;
    
    public enum SnowPhase {
        QUERYING,
        REPLYING
    }

    public Snow(final LocalBlockTree<B> localBlockTree, final int numAllParticipants) {
        super(localBlockTree);
        this.numAllParticipants = numAllParticipants;
        this.currentMainChainHead = localBlockTree.getGenesisBlock();
        try {
            File file = new File(directory+"/events-log.txt");
            if (file.exists()) {
                file.delete();
            }
            writer = new PrintWriter(new FileWriter(file, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(SNOWMAN) {
            this.val = null;
            this.count = new HashMap<>();
            this.pref = SnowNode.SNOW_GENESIS_BLOCK.getBinaryHash();
            this.currentPref = SnowNode.SNOW_GENESIS_BLOCK.getBinaryHash();
            this.Final = SnowNode.SNOW_GENESIS_BLOCK.getBinaryHash();
            this.blocks.add(SnowNode.SNOW_GENESIS_BLOCK);
            this.count.put(pref, 0);
        }
    }

    public void newIncomingQuery(final Query query) {
        if (query instanceof SnowBlockMessage) {
        	SnowBlockMessage<B> blockMessage = (SnowBlockMessage<B>) query;
            SnowNode peer = (SnowNode) this.peerBlockchainNode;
            SnowNode inquirer = (SnowNode) blockMessage.getInquirer();
            B block = blockMessage.getBlock();
            ArrayList<SnowBlock> senderPrefBlockchain = blockMessage.getPrefBlockchain();
            if(blockMessage.getCycleNumber()<peer.getCycleNumber()) {
            	//System.out.println("delayed message received!");
            	return;
            }
            switch (blockMessage.getQueryType()) {
            case QUERY:
                if (!this.localBlockTree.contains(block)) {
                    this.localBlockTree.add(block);
                }
                if (this.localBlockTree.getLocalBlock(block).isConnectedToGenesis) {
                	this.snowPhase = SnowPhase.REPLYING;
                	if(SLUSH||SNOWFLAKE||SNOWBALL||SNOWFLAKE_PLUS) {
                		if((peer.getCurrentBlock().getHeight()<block.getHeight())){ // If the node receives a new block, updates its status (detecting new block)
                        	peer.setCurrentBlock((SnowBlock) block); // updates its status (current block)
                        	peer.isDecided = false; // node can start sampling
                        }
                	}
                    if(SNOWMAN) {
               		 	//System.out.println(senderPrefBlockchain);
                        StringBuilder newPref = new StringBuilder();
                        for(SnowBlock senderBlock:senderPrefBlockchain) {
                        	if(senderBlock.getHeight() > getHighestHeightBlock(blocks).getHeight()) {
                        		blocks.add(senderBlock); // update automatically the node's seen blocks-blocks
                        		newPref.append(currentPref).append(senderBlock.getBinaryHash());
                        		currentPref = newPref.toString();
                        		peer.setLastGeneratedBlock(senderBlock);
                        	}
                        }
                   	//System.out.println(inquirer.nodeID+" "+senderPrefBlockchain);
                   	//System.out.println("node "+peer.nodeID+"  "+chain(blocks, pref));
                    }
                    this.peerBlockchainNode.respondQuery( // replies its status (current block) 
                            new QueryMessage(
                            		new SnowReply<>(this.peerBlockchainNode, peer.getCurrentBlock(), peer.getRoundNumber(), peer.getCycleNumber(), chain(blocks, currentPref))
                            ), inquirer
                    );
                }
                break;
            case REPLY:
                if(false) {
           		 	//System.out.println(senderPrefBlockchain);
                    StringBuilder newPref = new StringBuilder();
                    for(SnowBlock senderBlock:senderPrefBlockchain) {
                    	if(senderBlock.getHeight() > getHighestHeightBlock(blocks).getHeight()) {
                    		blocks.add(senderBlock); // update automatically the node's seen blocks-blocks
                    		newPref.append(currentPref).append(senderBlock.getBinaryHash());
                    		currentPref = newPref.toString();
                    		peer.setLastGeneratedBlock(senderBlock);
                    	}
                    }
               	//System.out.println(inquirer.nodeID+" "+senderPrefBlockchain);
               	//System.out.println("node "+peer.nodeID+"  "+chain(blocks, pref));
                }
            	checkQueries(query, blockMessage, (SnowBlock)block, senderPrefBlockchain, this.commitQueries, this.responseQueries , peer);
                break;
            }
        }
    }

	private void checkQueries(final Query query, final SnowBlockMessage<B> blockQuery, final SnowBlock block, final ArrayList<SnowBlock> senderPrefBlockchain, final HashMap<SnowBlock, HashMap<SnowNode, Query>> queries, final HashMap<SnowNode, HashMap<String, ArrayList<SnowBlock>>> responseQueries, final SnowNode peer) {
        numReceivedSamples++;
        if (SLUSH) {
        	if(block.getHeight() == peer.getLastConfirmedBlock().getHeight() + 1) { // check only for new conflicting blocks
                if (!queries.containsKey(block)) { // the first query reply received for the block
                    queries.put(block, new HashMap<>());
                }
                queries.get(block).put((SnowNode)blockQuery.getInquirer(), query); 
            }
            if (numReceivedSamples == K) { // if the node receives all the query replies-checks the query replies
                numReceivedSamples = 0; 
                for(SnowBlock receivedBlock:queries.keySet()) {
                    if ((queries.get(receivedBlock).size() >= ALPHA_1)) { // If a sampling for a block (block that consensus on it should be done) reaches the alpha threshold
                    	peer.setCurrentBlock((SnowBlock)receivedBlock);
                    	if(RECORD_LEDGERS) {
                    		updateChain();
                    	}
                        if (WRITE_CONSENSUS_LOGS) { 
                        	 writer.println("Node "+peer.nodeID+" was set to the state of block "+peer.getCurrentBlock().getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                             writer.flush();
                        }
                    }
                }
                queries.clear();
            } 
        }
        if (SNOWFLAKE) {
        	if(block.getHeight() == peer.getLastConfirmedBlock().getHeight() + 1) { // check only for new conflicting blocks
                if (!queries.containsKey(block)) { // the first query reply received for the block
                    queries.put(block, new HashMap<>());
                }
                queries.get(block).put((SnowNode)blockQuery.getInquirer(), query); 
            }
            if (numReceivedSamples == K) { // if the node receives all the query replies-checks the query replies
                numReceivedSamples = 0; 
                for(SnowBlock receivedBlock:queries.keySet()) {
                    if ((queries.get(receivedBlock).size() >= ALPHA_1)) { // If a sampling for the block (block that consensus on it should be done) reaches the alpha threshold
                    	if(receivedBlock != peer.getCurrentBlock()) {
                    		peer.setCurrentBlock((SnowBlock)receivedBlock);
                    		conviction = 0;
                    	}else if (++conviction>BETA){ // if the node's conviction value reaches the beta threshold (DECIDED STATUS)
                            peer.isDecided = true; // change to the decided state
                            peer.newRound = true;
                            //confidenceValues.clear();
                            conviction = 0;
                            setCurrentMainChainHead(receivedBlock); // accepts the block
                            peer.setLastConfirmedBlock(receivedBlock);
                        	SnowScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-receivedBlock.getCreationTime()); // record the consensus time.
                        	if(RECORD_LEDGERS) {
                        		updateChain();
                        	}
                            if (WRITE_CONSENSUS_LOGS) { 
                            	 writer.println("Node "+peer.nodeID+" was set to the state of block "+peer.getCurrentBlock().getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                                 writer.flush();
                            }
                    	}
                    }
                }
                queries.clear();
            } 
        }
        if (SNOWBALL) {
        	if(block.getHeight() == peer.getLastConfirmedBlock().getHeight() + 1) { // check only for new conflicting blocks
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
                if(!confidenceValues.containsKey(peer.getCurrentBlock())) {
                  	 confidenceValues.put(peer.getCurrentBlock(), 0);
                }
                for(SnowBlock receivedBlock:queries.keySet()) {
                    if ((queries.get(receivedBlock).size() >= ALPHA_1)) { // If a sampling for the block (block that consensus on it should be done) reaches the alpha threshold
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
                            peer.isDecided = true; // change to the decided state
                            peer.newRound = true;
                            //confidenceValues.clear();
                            conviction = 0;
                            setCurrentMainChainHead(receivedBlock); // accepts the block
                            peer.setLastConfirmedBlock(receivedBlock);
                        	SnowScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-receivedBlock.getCreationTime()); // record the consensus time.
                        	if(RECORD_LEDGERS) {
                        		updateChain();
                        	}
                            if (WRITE_CONSENSUS_LOGS) { 
                            	 writer.println("Node "+peer.nodeID+" was set to the state of block "+peer.getCurrentBlock().getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                                 writer.flush();
                            }
                    	}
                    }
                }
                queries.clear();
            } 
        }
        if (SNOWFLAKE_PLUS) {
        	if(block.getHeight() == peer.getLastConfirmedBlock().getHeight() + 1) { // check only for new conflicting blocks
                if (!queries.containsKey(block)) { // the first query reply received for the block
                    queries.put(block, new HashMap<>());
                }
                queries.get(block).put((SnowNode)blockQuery.getInquirer(), query); 
            }
            if (numReceivedSamples == K) { // if the node receives all the query replies-checks the query replies
                numReceivedSamples = 0; 
                Optional<SnowBlock> foundBlock = queries.entrySet().stream()
                        .filter(entry -> {
                            SnowBlock key = entry.getKey();
                            int valueSize = entry.getValue().size();
                            return valueSize >= ALPHA_1 && !key.equals(peer.getCurrentBlock()); 
                        })
                        .map(Map.Entry::getKey) 
                        .findFirst();
                foundBlock.ifPresent(b -> {
                    peer.setCurrentBlock(b); 
                    conviction = 0; 
                });
               
                if (queries.containsKey(peer.getCurrentBlock()) && queries.get(peer.getCurrentBlock()).size() < ALPHA_2) {
                    conviction = 0;
                }
                if (queries.containsKey(peer.getCurrentBlock()) && queries.get(peer.getCurrentBlock()).size() >= ALPHA_2) {
                	conviction = conviction + 1;
                }
                if(conviction >= BETA) {
                	peer.isDecided = true; // change to the decided state (terminate)
                    peer.newRound = true;
                    peer.addRoundNumber();
                    //confidenceValues.clear();
                    conviction = 0;
                    setCurrentMainChainHead(peer.getCurrentBlock()); // accepts the block
                    peer.setLastConfirmedBlock(peer.getCurrentBlock());
                	SnowScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-peer.getCurrentBlock().getCreationTime()); // record the consensus time.
                	if(RECORD_LEDGERS) {
                		updateChain();
                	}
                    if (WRITE_CONSENSUS_LOGS) { 
                    	 writer.println("Node "+peer.nodeID+" was set to the state of block "+peer.getCurrentBlock().getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                         writer.flush();
                    }
                }
                
                queries.clear();
            } 
        }
        if (SNOWMAN) {
        	HashMap<String, ArrayList<SnowBlock>> preferredHashBlocks= new HashMap<>();
        	preferredHashBlocks.put(H(senderPrefBlockchain), senderPrefBlockchain);
            responseQueries.put((SnowNode) blockQuery.getInquirer(), preferredHashBlocks);
            if (numReceivedSamples == K) { // if the node receives all the query replies-checks the query replies
            	numReceivedSamples = 0;
            	int end = 0;
                pref = Final;
                while(end==0){
                	//System.out.println(blocks);
                	E.clear(); // E stores preferred chains of the nodes
                    for(SnowBlock b:blocks) {
                    	//System.out.println(b+" "+b.getHeight());
                    	if (b == SnowNode.SNOW_GENESIS_BLOCK) {
                    	    continue;
                    	}
                    	boolean firstCondition = b.getParent().equals(last(blocks, pref));
                    	StringBuilder builder = new StringBuilder();
                    	String concatenatedHash = builder.append(reduct(blocks, pref)).append(b.getBinaryHash()).toString();
                    	boolean secondCondition = concatenatedHash.startsWith(pref); 
                    	if(firstCondition && secondCondition) {
                    		E.add(b); 
                    		//System.out.println(b+"   "+b.getCreator().nodeID + "  "+b.getHeight());
                    	}
                    }
                    //System.out.println(last(blocks, pref).getBinaryHash());
                    //System.out.println(pref+"  "+reduct(blocks, pref));
                    if (E.size()==0) {
                    	end = 1;
                    } else {
                        SnowBlock selectedBlock = null;
                        Optional<SnowBlock> b = E.stream().findFirst(); 
                        if (b.isPresent()) {
                            selectedBlock = b.get();
                            E.remove(selectedBlock); // Remove the element from E
                        }
                        //System.out.println(selectedBlock.getBinaryHash());
                        if(val==null) {
                            StringBuilder extended = new StringBuilder();
                            extended.append(reduct(blocks, pref)).append(selectedBlock.getBinaryHash()).toString(); // reduct(pref)*H(b)
                            int n_th_bit = (pref.length());
                            //System.out.println(extended);
                            if(n_th_bit<extended.length()) {
                                val = String.valueOf(extended.charAt(n_th_bit)); // set val(pref)
                         		if(!count.containsKey(pref)) {
                         			count.put(pref, 0);
                         			//System.out.println(pref);
                         		}
                            } else if(n_th_bit==extended.length()){
                            	val = extended.substring(extended.length() - 1);
                            }
                        } 
                        StringBuilder builder = new StringBuilder();
                 		sigma = builder.append(pref).append(val).toString();
                        condition_2(responseQueries, sigma); // If |{ 𝑗 ∈ [1, 𝑘] : rpref( 𝑗, 𝑠) ⊇ pref∗ 1−val(pref)}| ≥ 𝛼1
                        condition_3(responseQueries, sigma);// |{ 𝑗 ∈ [1, 𝑘] : rpref( 𝑗, 𝑠) ⊇ pref∗ val(pref)}| 𝛼2
                 		if(condition_4(responseQueries, sigma)) { // |{ 𝑗 ∈ [1, 𝑘] : rpref( 𝑗, 𝑠) ⊇ pref∗ val(pref)}| ≥ 𝛼2
                        	int value = count.get(pref);
                        	value = value + 1;
                        	count.put(pref, value);
                        }
                        //System.out.println(pref);
                        //System.out.println(sigma);
                        //System.out.println(condition_4(responseQueries, sigma));
                        if(count.get(pref) >= BETA) { // count(pref) ≥ 𝛽
                        	StringBuilder stringBuilder_1 = new StringBuilder();
                        	Final = stringBuilder_1.append(pref).append(val).toString();
                        	//System.out.println(last(blocks, Final).getBinaryHash());
                            if(!this.confirmedBlocks.contains(last(blocks, Final))) { // records the first consensus on blocks
                            	setCurrentMainChainHead(last(blocks, Final)); // accepts the block
                                peer.setLastConfirmedBlock(last(blocks, Final));
                            	//System.out.println(peer.getSimulator().getSimulationTime()+"   "+last(blocks, pref).getCreationTime());
                            	SnowScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-last(blocks, pref).getCreationTime()); // record the consensus time.
                            	updateChain();
                                if (WRITE_CONSENSUS_LOGS) { 
                                 	writer.println("Node "+peer.nodeID+" was set to the state of block "+last(blocks, Final).getHeight()+" at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                                    writer.flush();
                                }
                            }
                        }
                        //System.out.println(pref+"  "+val);
                        StringBuilder stringBuilder_2 = new StringBuilder();
                        pref = stringBuilder_2.append(pref).append(val).toString();
                        //System.out.println(peer.nodeID + " "+ pref);
                        //System.out.println(last(seenBlocks, pref).getBinaryHash());
                        //System.out.println(pref+" "+val+ " "+E.size());
                        //System.out.println(val);
                        val = null;
                    }
                }
              responseQueries.clear();
            }
        }
    }
	
	private boolean condition_2(HashMap<SnowNode, HashMap<String, ArrayList<SnowBlock>>> responseQueries, String sigmaPref) {
		int matched = 0;
		String lastChar;
		String invertedLastChar = ""; // 1 - val(pref)
		String invertedSigmaPref = "";
		if (sigmaPref.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
   	 	lastChar = sigmaPref.substring(sigmaPref.length() - 1);
   	 	invertedLastChar = lastChar.equals("0") ? "1" : "0";
   	 	invertedSigmaPref = sigmaPref.substring(0, sigmaPref.length() - 1) + invertedLastChar;
   	    
		for(HashMap<String, ArrayList<SnowBlock>> response:responseQueries.values()) {
			String rpref = response.keySet().iterator().next();
			if(rpref.startsWith(invertedSigmaPref)) { // rpref( 𝑗, 𝑠) ⊇ pref∗1−val(pref)
				matched = matched + 1; // gets how many nodes has this rpref
			}
			if(matched >= ALPHA_1) {
				val = invertedLastChar;
				StringBuilder builder = new StringBuilder();
         		sigma = builder.append(pref).append(val).toString();
         		blocks.clear();
         		blocks.addAll(response.get(rpref));
         		currentPref = rpref;
				for(String rSigma:count.keySet()) {
					if(rSigma.startsWith(pref)) {
						count.put(rSigma, 0);
					}
				}
				return true;
			}
		}
		return false;
	}
	
	private boolean condition_3(HashMap<SnowNode, HashMap<String, ArrayList<SnowBlock>>> responseQueries, String sigmaPref) {
		int matched = 0;
		if (sigmaPref.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
		for(HashMap<String, ArrayList<SnowBlock>> response:responseQueries.values()) {
			String rpref = response.keySet().iterator().next();
			//System.out.println(sigmaPref +"  "+ rpref+" "+rpref.startsWith(sigmaPref));
			if(rpref.startsWith(sigmaPref)) { // rpref( 𝑗, 𝑠) ⊇ pref∗val(pref)
				matched = matched + 1; // gets how many nodes has this rpref
			}
		}
		//System.out.println(matched);
		if(matched < ALPHA_2) {
			for(String sigma:count.keySet()) {
				if(sigma.startsWith(pref)) { 
					count.put(sigma, 0);
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	private boolean condition_4(HashMap<SnowNode, HashMap<String, ArrayList<SnowBlock>>> responseQueries, String sigmaPref) {
		int matched = 0;
		if (sigmaPref.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
		for(HashMap<String, ArrayList<SnowBlock>> response:responseQueries.values()) {
			String rpref = response.keySet().iterator().next();
			if(rpref.startsWith(sigmaPref)) { // rpref( 𝑗, 𝑠) ⊇ pref∗val(pref)
				matched = matched + 1; // gets how many nodes has this rpref
			}
			if(matched >= ALPHA_2) {
				return true;
			}
		}
		
		return false;
	}
	
	public ArrayList<SnowBlock> chain(ArrayList<SnowBlock> seenBlocks, String sigma) { // returns an ordered blockchain
		ArrayList<SnowBlock> prefBlockChain = new ArrayList<>();
		if (sigma.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
		int numCompleteHashes = sigma.length() / 32; // discards the fractional part
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
	
	private String reduct(ArrayList<SnowBlock> seenBlocks, String sigma) {
		if (sigma.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
		StringBuilder blockchainHash = new StringBuilder();
        int numCompleteHashes = sigma.length() / 32; // discards the fractional part
        for (int i = 0; i < numCompleteHashes; i++) {
            int startIndex = i * 32;
            int endIndex = startIndex + 32;
            String blockHash = sigma.substring(startIndex, endIndex);
            blockchainHash.append(blockHash);
        }
        if(blockchainHash.length()==0) {
        	blockchainHash.append(SnowNode.SNOW_GENESIS_BLOCK);
        }
       
        return blockchainHash.toString();
	}
	
	public SnowBlock last(ArrayList<SnowBlock> seenBlocks, String sigma) {
		if (sigma.isEmpty()) {
			throw new IllegalArgumentException("sigmaPref cannot be empty.");
		}
	    if (sigma.length() < 32) {
	        return (SnowBlock) SnowNode.SNOW_GENESIS_BLOCK;
	    }
	    if (sigma.length() % 32 != 0) {
	        sigma = sigma.substring(0, sigma.length() - (sigma.length() % 32));
	    }
	    String last32BitChunk = sigma.substring(sigma.length() - 32);
	    for (SnowBlock block : seenBlocks) {
	        if (block.getBinaryHash().equals(last32BitChunk)) {
	            return block; 
	        }
	    }

	    return (SnowBlock) SnowNode.SNOW_GENESIS_BLOCK;
	}
	
	private String H(ArrayList<SnowBlock> B) {
		StringBuilder chainHash = new StringBuilder();
	    for (SnowBlock block : B) {
	    	chainHash.append(block.getBinaryHash()); 
	    }
	    
	    return chainHash.toString(); // returns a string of block chain
	}
	
    public static String getHighestHeightBlockHash(HashSet<SnowBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("The set of blocks is empty or null.");
        }

        Optional<SnowBlock> maxBlock = blocks.stream()
                                             .max((b1, b2) -> Integer.compare(b1.getHeight(), b2.getHeight()));

        return maxBlock.orElseThrow(() -> new RuntimeException("Unexpected error: No block found.")).getBinaryHash();
    }
    
    public static SnowBlock getHighestHeightBlock(ArrayList<SnowBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("The set of blocks is empty or null.");
        }

        Optional<SnowBlock> maxBlock = blocks.stream()
                                             .max((b1, b2) -> Integer.compare(b1.getHeight(), b2.getHeight()));

        return maxBlock.orElseThrow(() -> new RuntimeException("Unexpected error: No block found."));
    }
	/*
    public boolean isSubset(String str1, String str2) { // str1 ⊆ str2
        if (str1.length() > str2.length()) {
            return false;
        }

        for (int i = 0; i < str1.length(); i++) {
            if (str1.charAt(i) != str2.charAt(i)) {
                return false; 
            }
        }

        return true;
    }
*/	
	private List<Node> sampleNeighbors(final Node node, final int k) {
        List<Node> neighbors = new ArrayList<>();
        neighbors.addAll(node.getP2pConnections().getNeighbors()); // gets all nodes
        neighbors.remove(node);
        List<Node> sampledNodes = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int randomIndex = this.peerBlockchainNode.getNetwork().getRandom().sampleInt(neighbors.size());
            sampledNodes.add(neighbors.get(randomIndex));
            neighbors.remove(randomIndex);
        }
        return sampledNodes;
    }
	
    public void snowballSampling(final SnowNode peer) {
    	numReceivedSamples = 0;
        List<Node> sampledNeighbors = sampleNeighbors(peer, K);
        for(Node destination:sampledNeighbors){
        	peer.query(
                    new QueryMessage(
                            new SnowQuery<>(peer, peer.getCurrentBlock(), peer.getRoundNumber(), peer.getCycleNumber(), chain(blocks, currentPref))
                    ), destination
            );
			//System.out.println(peer.getSimulator().getSimulationTime()+"  "+peer.getCycleNumber());
        }
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
    
    public void setCurrentMainChainHead(SnowBlock block) {
    	this.currentMainChainHead = (B) block; 
    }
}
