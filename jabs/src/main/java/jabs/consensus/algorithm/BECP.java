package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.ledgerdata.*;
import jabs.ledgerdata.becp.*;
import jabs.ledgerdata.becp.Process;
import jabs.network.message.GossipMessage;
import jabs.network.message.GossipMessageBuilder;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;
import jabs.simulator.Simulator;
import jabs.simulator.event.NodeCycleEvent;
import jabs.simulator.randengine.RandomnessEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
/**
 * File: BECP.java
 * Description: Implements BECP protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class BECP<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements GossipBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {
    
    //---------------------------------------------------------------------------------------------
    /**
     * BECP Protocol Settings:
     * Available protocol combinations:
     * - SSEP & NCP
     * - SSEP & NCP & PTP
     * - SSEP & NCP & ECP
     * - REAP/REAP+ & NCP
     * - REAP/REAP+ & NCP & PTP
     * - REAP/REAP+ & NCP & ECP
     * - ARP & NCP
     * - ARP & NCP & PTP
     * - ARP & NCP & ECP
     */
	/**
	 * Configuration for recommender parameters:
	 * For SSEP, REAP, REAP+, and ECP:
	 * - EPSILON_1 & EPSILON_2: 0.05
	 * - MIN_CONSECUTIVE_CYCLES_THRESHOLD: 5
	 * 
	 * For ARP:
	 * - EPSILON_1 & EPSILON_2: 0.5
	 * - MIN_CONSECUTIVE_CYCLES_THRESHOLD: 3
	 * 
	 * For Uniform latency:
	 * - REPUSH_TIMEOUT: 2
	 * - PULL_TIMEOUT: 1
	 * 
	 * For Pareto latency:
	 * - REPUSH_TIMEOUT: 9
	 * - PULL_TIMEOUT: 8
	 */
    public static final boolean SSEP = false; // System Size Protocol (estimation protocol)
    public static final boolean REAP = false; // Robust Epidemic Aggregation Protocol (estimation and fail detection protocol) [Note: Still not functional for consensus]
    public static final boolean REAP_PLUS = true;
    public static final boolean ARP = false; // Adaptive Restart Protocol (an adaptive restart mechanism for continuous epidemic systems)
    public static final boolean NCP = true; // Node Cache Protocol
    public static final boolean PTP = true; // Phase Transition Protocol (Information Dissemination consensus protocol)
    public static final boolean ECP = false; // Epidemic Consensus Protocol (Data Aggregation consensus protocol)
    //--------------------------------------------------------------------------------------------------
    private static final double EPSILON_1 = 0.05d; // (ECP, REAP, PTP, ARP) - Error value (epsilon) for estimation.
    private static final double EPSILON_2 = 0.05d; // (ECP, ARP) - Error value (epsilon) for estimation.
    private static final int MIN_CONSECUTIVE_CYCLES_THRESHOLD = 5; // (ECP, REAP, PTP, ARP) - Minimum number of consecutive cycles threshold.
    private static final int REPUSH_TIMEOUT = 2; // (REAP & REAP+ protocols) - Maximum timeout value in cycles. (minimum value should = 2)
    public static final int PULL_TIMEOUT = 1; // (REAP+ protocol) - Maximum wait time value in cycles for a Pull message (PULL_TIMEOUT < REPUSH_TIMEOUT).
    public static final int QUEUE_SIZE = 10; // (ECP, REAP, ARP) - Length of the history queue.
    public static final int NUMBER_OF_PROCESSES = 5; // (ARP) - Number of processes working in parallel.
    public static final int WAIT_INFORM_TIME = 5; // (REAP+) Duration (in cycles) a node should wait before informing others with the corrected system size.
    //--------------------------------------------------------------------------------------------------
    private static final boolean WRITE_CONSENSUS_LOGS = false; // Write logs for the occurring consensus.
    private static final boolean RECORD_LEDGERS = true; // Record logs for the local ledgers.
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //----------------------------------------------------------------------------------------------------------------------------
    private static PrintWriter writer;
    File directory = BECPScenario.directory;
    private double numOfParticipants; // stores the estimated system size.
    private GossipType gossipType = GossipType.PUSHING;
    private int aggregationCyclesECP; 
   	private int unchangedLeaderCyclesECP;
   	private int convergenceCyclesECP;
   	private int agreementCyclesECP;
   	private int lastTimeLeader=-1; //(ECP)
    private int aggregationCyclesARP; 
	private int consensusCyclesARP;
	private HashMap<BECPBlock, Integer> propagationCyclesPTP = new HashMap<>(); //(PTP protocol)
	private HashMap<BECPBlock, Integer> agreementCyclesPTP = new HashMap<>(); //(PTP protocol)
	private int ConvergedCycles; // (REAP protocol)
	private HashSet<Integer> removedCachedBlocks = new HashSet<>();
    private Stack<ArrayList<BECPNode>> arrayListPool1 = new Stack<>(); //(NCP) for local neighbour cache.
    private Stack<HashMap<Integer, BECPBlock>> arrayListPool2 = new Stack<>(); //(PTP) for local block cache.
    private Stack<HashMap<Integer, Process>> arrayListPool3 = new Stack<>();//(ARP) for P.
    private LinkedHashMap<PushEntry, HashMap<BECPNode, ArrayList<BECPBlock>>> tempCrashedEvents_1 = new LinkedHashMap<>(); // (REAP+)
    private LinkedHashMap<RecoveryEntry, HashMap<BECPNode, ArrayList<BECPBlock>>> tempCrashedEvents_2 = new LinkedHashMap<>(); // (REAP+)
    HashMap<BECPBlock, BECPBlock> tempJoinedEvent = new HashMap<>();
    HashMap<BECPNode, Integer> WAIT_INFORM_TIMS = new HashMap<>();
    static int count;
    
    public BECP(LocalBlockTree<B> localBlockTree) {
        super(localBlockTree);
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
    }
    
    private enum GossipType {
        PUSHING,
        PULLING
    }

    public void newIncomingGossip(final Gossip gossip) {
        if (gossip instanceof BECPBlockGossip) {
        	LinkedHashSet<BECPBlock> updatedLedger = null;
        	HashMap<Integer, BECPBlock> senderBlockLocalCache;
        	HashMap<Integer, BECPBlock> peerBlockLocalCache;
        	HashMap<Integer, BECPBlock> copyBlockCache = null;
        	HashMap<Integer, Process> copiedP = null;
        	A copiedA = null;
        	C copiedC = null;
        	HashMap<Integer, Process> senderP;
        	ArrayList<BECPNode> copyNeighborCache;
        	ArrayList<BECPNode> senderNeighborCache;
        	ArrayList<BECPNode> peerNeighborCache;
        	BECPPull<B> senderPull;
        	BECPPush<B> senderPush;
        	BECPNode peer;
        	BECPNode sender;
        	BECPBlockGossip<B> blockGossip;
        	double peerWeight = 0;
        	double peerValue = 0;
        	double senderWeight = 0;
        	double senderValue = 0;
        	double Cv;
        	RandomnessEngine randomnessEngine = this.peerBlockchainNode.getNetwork().getRandom();
            sender = (BECPNode) gossip.getSender();
            blockGossip = (BECPBlockGossip<B>) gossip;
            peer = (BECPNode) this.peerBlockchainNode;
            switch (blockGossip.getGossipType()){
                case PUSH:
                    senderPush = (BECPPush<B>) blockGossip;
                    if (true){
                        //*System.out.println("received a push from "+sender.getNodeID()+" in the node "+peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime()); //*
                        gossipType = GossipType.PULLING;
                        //****************Joining a Node*********************
                        if(senderPush.isNewJoined()&&!peer.isCrashed) { // prevent sending the updates by a crashed node.
                        	updatedLedger = peer.getLocalLedger();
                        }
                        if(peer.isCrashed) { // prevent running a joined node before getting updates.
                        	break;
                        }
                        //****************Joining a Node*********************
                        //***** SSEP (System Size Estimation Protocol)*****//
                        if(SSEP){
                            peerValue = peer.getValue()/2;
                            peerWeight = peer.getWeight()/2;
                            peer.setValue(peerValue);
                            peer.setWeight(peerWeight);
                        }
                        //##### SSEP (System Size Estimation Protocol)#####//
                        //***** REAP (Robust Epidemic Aggregation Protocol)*****//
                        if (REAP) {
                        	Key key = new Key(senderPush.getSender().nodeID, senderPush.getCycleNumber());
                        	if(peer.getRecoveryCache().containsKey(key)) { // received a duplicate push message (detects a RePush).
                            	//*System.out.println("received a RePush from " +sender.getNodeID()+" in the node "+ peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime()); //*                
                        		peer.getRecoveryCache().remove(key);
                        		break;
                        	}
                       	 	peerValue = peer.getValue()/2;
                       	 	peerWeight = peer.getWeight()/2;
                       	 	peer.setValue(peerValue);
                       	 	peer.setWeight(peerWeight);
                        }
                        //##### REAP (Robust Epidemic Aggregation Protocol)#####//
                        //***** REAP_PLUS (Robust Epidemic Aggregation Protocol)*****//
                        if (REAP_PLUS) {
                        	Key key = new Key(senderPush.getSender().nodeID, senderPush.getCycleNumber());
                        	if(senderPush.getCriticalPushFlag()) {
                        		if(peer.getRecoveryCache().containsKey(key)) { // received a duplicate push message (detects a RePush).
                                	//*System.out.println("received a RePush from " +sender.getNodeID()+" in the node "+ peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime()); //*                
                        			if(senderPush.isReceivedPull()) {
                        				peer.getRecoveryCache().remove(key); // do nothing
                        			}else {
                        				peer.getRecoveryCache().get(key).setTimeout(1); // to restore the masses.
                        			}
                            		break;
                            	}else if(senderPush.getCycleNumber()<peer.getCycleNumber()){ // discard invalid RePush messages.
                            		break;
                            	}
                        	}
                        	
                        	peer.getCrashedNodes().addAll(senderPush.getCrashedNodes()); // update the list of crashed nodes.
                       	 	peer.getJoinedNodes().addAll(senderPush.getJoinedNodes()); // update the list of joined nodes.
                        	peerValue = peer.getValue()/2;
                       	 	peerWeight = peer.getWeight()/2;
                       	 	peer.setValue(peerValue);
                       	 	peer.setWeight(peerWeight);
                        }
                        //##### REAP_PLUS (Robust Epidemic Aggregation Protocol)#####//
                        //***** ARP (Adaptive Restart Protocol)*****//
                        if(ARP) {
                        	if(peer.getL()>senderPush.getL()) {break; };
                        	resolveEpoch(peer, peer.getCycleNumber(), blockGossip, randomnessEngine);
                        	for(Integer processID : peer.getP().keySet()) {
                        		Process process = peer.getP().get(processID);
                            	process.setValue(process.getValue()/2);
                            	process.setWeight(process.getWeight()/2);
                            }
                        	C c = peer.getC();
                			c.setValue(c.getValue()/2);
                			c.setWeight(c.getWeight()/2);
                            copiedP = getArrayListFromPool3();
                            copiedP = copyP(peer, copiedP);
                            copiedA = peer.getA().clone();
                            copiedC = peer.getC().clone();
                        }
                        //##### ARP (Adaptive Restart Protocol)#####//
                        //***** PTP (Phase Transition Protocol)*****//
                        if(PTP){
                            peerBlockLocalCache = peer.getBlockLocalCache();
                            for (BECPBlock becpBlock:peerBlockLocalCache.values()){
                                becpBlock.setVPropagation(becpBlock.getVPropagation()/2);
                                becpBlock.setWPropagation(becpBlock.getWPropagation()/2);
                                becpBlock.setVAgreement(becpBlock.getVAgreement()/2);
                                becpBlock.setWAgreement(becpBlock.getWAgreement()/2);
                            }
                            copyBlockCache = getArrayListFromPool2(); // send a copy of peerBlockLocalCache.
                            if(peerBlockLocalCache.size()>0){
                                for (BECPBlock peerBlock:peerBlockLocalCache.values()) {
                                    copyBlockCache.put(peerBlock.getHeight(), peerBlock.clone());
                                }
                            }
                        }
                        //##### PTP (Phase Transition Protocol)#####//
                        //***** ECP(Epidemic Consensus Protocol)*****//
                        if(ECP){
                        	peerBlockLocalCache = peer.getBlockLocalCache();
                        	Map.Entry<Integer, BECPBlock> entry = peer.getBlockLocalCache().entrySet().iterator().next();
                        	BECPBlock becpBlock = entry.getValue();
                        	becpBlock.setVDataAggregation(becpBlock.getVDataAggregation()/2);
                            becpBlock.setWDataAggregation(becpBlock.getWDataAggregation()/2);
                            becpBlock.setVDataConvergence(becpBlock.getVDataConvergence()/2);
                            becpBlock.setVDataAgreement(becpBlock.getVDataAgreement()/2);
                            becpBlock.setWeightValue(becpBlock.getWeightValue()/2);
                            becpBlock.setLeader(becpBlock.getLeader()); 
                            copyBlockCache = getArrayListFromPool2(); // send a copy of peerBlockLocalCache.
                            if(peerBlockLocalCache.size()>0){
                                for (BECPBlock peerBlock:peerBlockLocalCache.values()) {
                                    copyBlockCache.put(peerBlock.getHeight(), peerBlock.clone());
                                }
                            }
                        }
                        //##### ECP(Epidemic Consensus Protocol)#####//
                        //***** NCP (Node Cache Protocol)*****//
                        if(NCP){
                        	peerNeighborCache = peer.getNeighborsLocalCache();
                        	copyNeighborCache = getArrayListFromPool1();
                            copyNeighborCache.addAll(peerNeighborCache);
                            copyNeighborCache.remove(sender);
                        }
                        //##### NCP (Node Cache Protocol)#####//
                        this.peerBlockchainNode.gossipMessage(
                                new GossipMessage(
                                        new BECPPull<>(this.peerBlockchainNode, peer.getCycleNumber(), getSizeOfBlocks(copyBlockCache), peerValue, peerWeight, copyNeighborCache, copyBlockCache, false, peer.getL(), copiedP, copiedA, copiedC, peer.getCrashedNodes(), peer.getJoinedNodes(), updatedLedger)
                                ),sender
                        );
                		//new GossipMessageBuilder().setCycleNumber(peer.getCycleNumber()).setValue(peerValue).setWeight(peerWeight).setNeighborsLocalCache(copyNeighborCache).setBlockLocalCache(copyBlockCache).setCriticalPushFlag(false).setCrashedNodes(peer.getCrashedNodes()).setJoinedNodes(peer.getJoinedNodes()).setLocalLedger(updatedLedger).buildPullGossip(this.peerBlockchainNode, getSizeOfBlocks(copyBlockCache))
                        //System.out.println("sent a pull from "+peer.getNodeID()+" to "+ sender.getNodeID()+" at "+ peer.getSimulator().getSimulationTime()); //*
                        //***** SSEP (System Size Estimation Protocol)*****//
                        if(SSEP){
                            senderValue = senderPush.getValue();
                            senderWeight = senderPush.getWeight();
                            peer.setValue(peerValue + senderValue);
                            peer.setWeight(peerWeight + senderWeight);
                        }
                        //##### SSEP (System Size Estimation Protocol)#####//
                        //***** REAP (Robust Epidemic Aggregation Protocol)*****//
                        if(REAP){
                      	 	senderValue = senderPush.getValue();
                       	 	senderWeight = senderPush.getWeight();
                        	peer.setValue(peerValue + senderValue); // update aggregation pair.
                            peer.setWeight(peerWeight + senderWeight);
                            //-----------------------------------------------
                        	ArrayList<Double> estimates = new ArrayList<>(); // the enqueue of estimates.
                            estimates.add(peerValue/peerWeight);
                            estimates.add(senderValue/senderWeight);
                            if(peer.getReapQueue().size()==QUEUE_SIZE){ // if the queue is full.
                                peer.getReapQueue().poll();
                                peer.getReapQueue().add(estimates);
                            }else{
                                peer.getReapQueue().add(estimates);
                            }
                            //-----------------------------------------------
                            if(senderPush.getCriticalPushFlag()){ // replicate critical pairs (v, w, [vp, wp, va, wa]).
                            	//System.out.println("node " + sender.getNodeID()+" with cycle "+senderPush.getCycleNumber()+" recorded in "+ peer.getNodeID());
                            	Key key = new Key(senderPush.getSender().nodeID, senderPush.getCycleNumber());
                            	HashMap<Integer, ReplicaBlock> replicaBlockCache = new HashMap<>();
                            	peer.getRecoveryCache().put(key, new RecoveryEntry(senderPush.getSender(), peer.getCycleNumber(), REPUSH_TIMEOUT, peer.getValue(), peer.getWeight(), replicaBlockCache));
                            	//System.out.println("Recovery cache size is "+peer.getRecoveryCache().size()+ " for node "+ peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime());
                            }
                            //-----------------------------------------------
                            Cv = coefficientOfVariance(peer.getReapQueue());
                            if(Cv <= EPSILON_1){
                                ConvergedCycles++;
                                if(ConvergedCycles == MIN_CONSECUTIVE_CYCLES_THRESHOLD){ // detect convergence.
                                    peer.setConvergenceFlag(true);
                                    ConvergedCycles = 0;
                                }
                            }else{
                                ConvergedCycles = 0; // reset the counter.
                            }
                            
                        }
                        //##### REAP (Robust Epidemic Aggregation Protocol)#####//
                        //***** REAP_PLUS (Robust Epidemic Aggregation Protocol)*****//
                        if(REAP_PLUS){
                      	 	senderValue = senderPush.getValue();
                       	 	senderWeight = senderPush.getWeight();
                        	peer.setValue(peerValue + senderValue); 
                            peer.setWeight(peerWeight + senderWeight);
                            if(senderPush.getCriticalPushFlag()){ // replicate critical pairs (v, w, [vp, wp, va, wa]).
                            	//System.out.println("node " + sender.getNodeID()+" with cycle "+senderPush.getCycleNumber()+" recorded in "+ peer.getNodeID());
                            	Key key = new Key(senderPush.getSender().nodeID, senderPush.getCycleNumber());
                            	//-------------------------------------------------------------------
                            	HashMap<Integer, ReplicaBlock> replicaBlockCache = new HashMap<>();
                            	peerBlockLocalCache = peer.getBlockLocalCache();
                            	if(peerBlockLocalCache.size()>0){
                                    for (BECPBlock peerBlock:peerBlockLocalCache.values()) {
                                    	ReplicaBlock replicaBlock = new ReplicaBlock(); 
                                    	replicaBlock.setVPropagation(peerBlock.getVPropagation());
                                    	replicaBlock.setWPropagation(peerBlock.getWPropagation());
                                    	replicaBlock.setVAgreement(peerBlock.getVAgreement());
                                    	replicaBlock.setWAgreement(peerBlock.getWAgreement());
                                    	replicaBlock.setBlockCreator(peerBlock.getCreator());
                                    	replicaBlockCache.put(peerBlock.getHeight(), replicaBlock);
                                    }
                                }
                            	//-------------------------------------------------------------------
                            	peer.getRecoveryCache().put(key, new RecoveryEntry(senderPush.getSender(), peer.getCycleNumber(), REPUSH_TIMEOUT, peer.getValue(), peer.getWeight(), replicaBlockCache));
                            	//System.out.println("Recovery cache size is "+peer.getRecoveryCache().size()+ " for node "+ peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime());
                            }
                        }
                        //##### REAP_PLUS (Robust Epidemic Aggregation Protocol)#####//
                        //***** ARP (Adaptive Restart Protocol)*****//
                        if(ARP) {
                        	senderP = senderPush.getP();
                        	if (senderPush.getL() == peer.getL()) { // Update local tuples in all processes
                        	    for (Integer pEntry : peer.getP().keySet()) {
                        	    	if (peer.getP().get(pEntry).getIdentifier() == senderP.get(pEntry).getIdentifier()) { // compares same process IDs.
                    	                double oldValue = peer.getP().get(pEntry).getValue();
                    	                double oldWeight = peer.getP().get(pEntry).getWeight();
                    	                peer.getP().get(pEntry).setValue(oldValue + senderP.get(pEntry).getValue());
                    	                peer.getP().get(pEntry).setWeight(oldWeight + senderP.get(pEntry).getWeight());
                    	            }
                        	    }
                        	    if(peer.getC().getIdentifier()== senderPush.getC().getIdentifier()) {
                        	    	C c = peer.getC();
                        			c.setValue(c.getValue()+senderPush.getC().getValue());
                        			c.setWeight(c.getWeight()+senderPush.getC().getWeight());
                        	    }
                        	}
                        }
                        //##### ARP (Adaptive Restart Protocol)#####//
                        //***** NCP (Node Cache Protocol)*****//
                        if(NCP){
                            senderNeighborCache = senderPush.getNeighborsLocalCache();
                            peerNeighborCache = union(peerNeighborCache, senderNeighborCache);
                            peerNeighborCache = union(peerNeighborCache, sender);
                            trimCache(peer.getNeighborsLocalCache().size(), peerNeighborCache, this.peerBlockchainNode.getNetwork().getRandom());
                            peer.setNeighborsLocalCache(peerNeighborCache);
                        }
                        //##### NCP (Node Cache Protocol)#####//
                        //***** PTP (Phase Transition Protocol)*****//
                        if(PTP){
                            senderBlockLocalCache = senderPush.getBlockLocalCache();
                            for(BECPBlock blockSender:senderBlockLocalCache.values()){
                            	if(!tempJoinedEvent.containsKey(blockSender)&&blockSender.getCycleNumber()<=peer.getJoinCycle()) { // check only for rejoined nodes
                            		tempJoinedEvent.put(blockSender, blockSender);
                            	}
                            	resolveDuplication(blockSender, peer);
                            }
                        }
                        //##### PTP (Phase Transition Protocol)#####//
                        //***** ECP (Epidemic Consensus Protocol)*****//
                        if(ECP) {
                        	 senderBlockLocalCache = senderPush.getBlockLocalCache();
                        	 Map.Entry<Integer, BECPBlock> senderEntry = senderBlockLocalCache.entrySet().iterator().next();
                         	 BECPBlock senderBlock = senderEntry.getValue();
                         	 Map.Entry<Integer, BECPBlock> entry = peer.getBlockLocalCache().entrySet().iterator().next();
                        	 BECPBlock becpBlock = entry.getValue();
                         	 
                         	 ArrayList<Double> estimates = new ArrayList<>(); // the enqueue of estimates.
                             estimates.add(becpBlock.getVDataAggregation()/becpBlock.getWDataAggregation());
                             estimates.add(senderBlock.getVDataAggregation()/senderBlock.getWDataAggregation());
                             if(peer.getECPQueue().size()==QUEUE_SIZE){ // if the queue is full.
                                 peer.getECPQueue().poll();
                                 peer.getECPQueue().add(estimates);
                             }else{
                                 peer.getECPQueue().add(estimates);
                             }
                         	 becpBlock.setVDataAggregation(becpBlock.getVDataAggregation()+senderBlock.getVDataAggregation());
                         	 becpBlock.setWDataAggregation(becpBlock.getWDataAggregation()+senderBlock.getWDataAggregation());
                         	 becpBlock.setVDataConvergence(becpBlock.getVDataConvergence()+senderBlock.getVDataConvergence());
                         	 becpBlock.setVDataAgreement(becpBlock.getVDataAgreement()+senderBlock.getVDataAgreement());
                         	 becpBlock.setWeightValue(becpBlock.getWeightValue()+senderBlock.getWeightValue());
                         	 becpBlock.setLeader(Math.max(becpBlock.getLeader(), senderBlock.getLeader()));
                        }
                        //***** ECP (Epidemic Consensus Protocol)*****//
                        releaseArrayListToPool1(senderNeighborCache);
                        if(PTP||ECP) {
                        	releaseArrayListToPool2(senderBlockLocalCache);
                        }
                        if(ARP) {
                        	releaseArrayListToPool3(senderP);
                        }
                    }
                    break;
                case PULL:
                    senderPull = (BECPPull<B>) blockGossip;
                    if (true) {
                		//*System.out.println("received a pull from "+sender.getNodeID()+" in the node "+peer.getNodeID()+" at "+peer.getSimulator().getSimulationTime()); //*
                    	//***** SSEP (System Size Estimation Protocol)*****//
                        if (SSEP) {
                            peerValue = peer.getValue();
                            peerWeight = peer.getWeight();
                            senderValue = senderPull.getValue();
                            senderWeight = senderPull.getWeight();
                            peer.setValue(peerValue + senderValue);
                            peer.setWeight(peerWeight + senderWeight);
                        }
                        //##### SSEP (System Size Estimation Protocol)#####//
                        //***** REAP (Robust Epidemic Aggregation Protocol)*****//
                        if(REAP){
                            peerValue = peer.getValue();
                            peerWeight = peer.getWeight();
                            senderValue = senderPull.getValue();
                            senderWeight = senderPull.getWeight();
                            peer.setValue(peerValue + senderValue);
                            peer.setWeight(peerWeight + senderWeight);
                            //-----------------------------------------------
                            ArrayList<Double> estimates = new ArrayList<>();
                            estimates.add(peerValue/peerWeight);
                            estimates.add(senderValue/senderWeight);
                            if(peer.getReapQueue().size()==QUEUE_SIZE){
                                peer.getReapQueue().poll();
                                peer.getReapQueue().add(estimates);
                            }else{
                                peer.getReapQueue().add(estimates);
                            }
                            Cv = coefficientOfVariance(peer.getReapQueue());
                            if(Cv <= EPSILON_1){
                                ConvergedCycles++;
                                if(ConvergedCycles == MIN_CONSECUTIVE_CYCLES_THRESHOLD){ // detect convergence.
                                    peer.setConvergenceFlag(true);
                                    ConvergedCycles = 0;
                                }
                            }else{
                                ConvergedCycles = 0; // reset the counter.
                            }
                        }
                        //##### REAP (Robust Epidemic Aggregation Protocol)#####//
                        //***** REAP_PLUS (Robust Epidemic Aggregation Protocol)*****//
                        if(REAP_PLUS){
                        	if(peer.isCrashed) {
                        		//System.out.println("node "+peer.nodeID+" received updates from " +sender.nodeID);
                    			peer.isCrashed = false;
                    			agreementCyclesPTP.clear();
                    			propagationCyclesPTP.clear();
                    			tempCrashedEvents_1.clear();
                    			tempCrashedEvents_2.clear();
                        		peer.getLocalLedger().clear();
                        		peer.getLocalLedger().addAll(senderPull.getLocalLedger()); // update the local ledger.
                        		BECPBlock[] blockArray = peer.getLocalLedger().toArray(new BECPBlock[0]);
                        		BECPBlock lastConfirmedBlock = blockArray[blockArray.length - 1];
                        		peer.setLastConfirmedBlock(lastConfirmedBlock);
                        		peer.setCurrentPreferredBlock(lastConfirmedBlock);
                        		peer.setCycleNumber(senderPull.getCycleNumber());
                        		peer.setJoinCycle(senderPull.getCycleNumber());
                        		//System.out.println("the updated cycle is "+ peer.getCycleNumber());
                        		for (BECPBlock cashBlock:senderPull.getBlockLocalCache().values()) {
                        			boolean addToCash = true;
                        			for (BECPBlock commitBlock:peer.getLocalLedger()) {
                        				if (cashBlock.getHeight()==commitBlock.getHeight()) {
                        					addToCash = false;
                        					break;
                            			}
                        			}
                        			if(addToCash) {
                        				tempJoinedEvent.put(cashBlock, cashBlock);
                        				//System.out.println(cashBlock.getHeight()+" "+cashBlock.getCycleNumber()+" "+ cashBlock.getState());
                        			}
                            	}
                        		/*
                        		if(tempJoinedEvent.size()==0) {
                            		peer.getJoinedNodes().add(peer); // update the list of joined nodes to inform others.
                    			}
                    			*/
                             //****************Joining a Node*********************
                    		}else {
                    			peerValue = peer.getValue();
                                peerWeight = peer.getWeight();
                                senderValue = senderPull.getValue();
                                senderWeight = senderPull.getWeight();
                                peer.setValue(peerValue + senderValue);
                                peer.setWeight(peerWeight + senderWeight);
                    		}
                        	peer.getCrashedNodes().addAll(senderPull.getCrashedNodes()); // update the list of crashed nodes.
                        	peer.getJoinedNodes().addAll(senderPull.getJoinedNodes()); // update the list of joined nodes.
                        	for(PushEntry pushEntry:peer.getPushEntriesBuffer()){
                             	if((pushEntry.getDestination()==senderPull.getSender())&&(pushEntry.getCycleNumber()==senderPull.getCycleNumber())) {
                             		pushEntry.setReceivedPull(true);
                             	}
                             }
                        }
                        //##### REAP_PLUS (Robust Epidemic Aggregation Protocol)#####//
                        //***** ARP (Adaptive Restart Protocol)*****//
                        if(ARP) {
                        	senderP = senderPull.getP();
                            resolveEpoch(peer, peer.getCycleNumber(), blockGossip, randomnessEngine);
                            if (senderPull.getL() == peer.getL()) { // Update local tuples in all processes
                                for (Integer pEntry : peer.getP().keySet()) {
                                	if (peer.getP().get(pEntry).getIdentifier() == senderP.get(pEntry).getIdentifier()) { // compares same process IDs.
                                        double oldValue = peer.getP().get(pEntry).getValue();
                                        double oldWeight = peer.getP().get(pEntry).getWeight();
                                        peer.getP().get(pEntry).setValue(oldValue + senderP.get(pEntry).getValue());
                                        peer.getP().get(pEntry).setWeight(oldWeight + senderP.get(pEntry).getWeight());
                                    }
                                }
                                if(peer.getC().getIdentifier()== senderPull.getC().getIdentifier()) {
                        	    	C c = peer.getC();
                        			c.setValue(c.getValue()+senderPull.getC().getValue());
                        			c.setWeight(c.getWeight()+senderPull.getC().getWeight());
                        	    }
                            }
                        }
                        //##### ARP (Adaptive Restart Protocol)#####//
                        //***** NCP (Node Cache Protocol)*****//
                        if (NCP) {
                            peerNeighborCache = peer.getNeighborsLocalCache();
                            senderNeighborCache = senderPull.getNeighborsLocalCache();
                            peerNeighborCache = union(peerNeighborCache, senderNeighborCache);
                            peerNeighborCache = union(peerNeighborCache, sender);
                            trimCache(peer.getNeighborsLocalCache().size(), peerNeighborCache, this.peerBlockchainNode.getNetwork().getRandom());
                            peer.setNeighborsLocalCache(peerNeighborCache);
                        }
                        //##### NCP (Node Cache Protocol)#####//
                        //***** PTP (Phase Transition Protocol)*****//
                        if (PTP) {
                            senderBlockLocalCache = senderPull.getBlockLocalCache();
                            for (BECPBlock blockSender:senderBlockLocalCache.values()) {
                            	if(!tempJoinedEvent.containsKey(blockSender)&&blockSender.getCycleNumber()<=peer.getJoinCycle()) { // check only for rejoined nodes
                            		tempJoinedEvent.put(blockSender, blockSender);
                            	}
                            	resolveDuplication(blockSender, peer);
                            }
                        }
                        //##### PTP (Phase Transition Protocol)#####//
                        //***** ECP (Epidemic Consensus Protocol)*****//
                        if (ECP) {
                        	 senderBlockLocalCache = senderPull.getBlockLocalCache();
                        	 Map.Entry<Integer, BECPBlock> senderEntry = senderBlockLocalCache.entrySet().iterator().next();
                         	 BECPBlock senderBlock = senderEntry.getValue();
                        	 Map.Entry<Integer, BECPBlock> peerEntry = peer.getBlockLocalCache().entrySet().iterator().next();
                         	 BECPBlock becpBlock = peerEntry.getValue();
                         	 
                             ArrayList<Double> estimates = new ArrayList<>(); // the enqueue of estimates.
                             estimates.add(becpBlock.getVDataAggregation()/becpBlock.getWDataAggregation());
                             estimates.add(senderBlock.getVDataAggregation()/senderBlock.getWDataAggregation());
                             if(peer.getECPQueue().size()==QUEUE_SIZE){ // if the queue is full.
                                 peer.getECPQueue().poll();
                                 peer.getECPQueue().add(estimates);
                             }else{
                                 peer.getECPQueue().add(estimates);
                             }
                             becpBlock.setVDataAggregation(becpBlock.getVDataAggregation()+senderBlock.getVDataAggregation());
                             becpBlock.setWDataAggregation(becpBlock.getWDataAggregation()+senderBlock.getWDataAggregation());
                             becpBlock.setVDataConvergence(becpBlock.getVDataConvergence()+senderBlock.getVDataConvergence());
                             becpBlock.setVDataAgreement(becpBlock.getVDataAgreement()+senderBlock.getVDataAgreement());
                             becpBlock.setWeightValue(becpBlock.getWeightValue()+senderBlock.getWeightValue());
                             becpBlock.setLeader(Math.max(becpBlock.getLeader(), senderBlock.getLeader()));
                        }
                        //***** ECP (Epidemic Consensus Protocol)*****//
                        releaseArrayListToPool1(senderNeighborCache);
                        if(PTP||ECP) {
                        	releaseArrayListToPool2(senderBlockLocalCache);
                        }
                        if(ARP) {
                        	releaseArrayListToPool3(senderP);
                        }
                    }
                    break;
            }
        }
    }

	public void newCycle(final BECPNode peer){
		HashMap<Integer, BECPBlock> peerBlockLocalCache = peer.getBlockLocalCache();
		HashMap<Integer, BECPBlock>  copyBlockCache = null;
    	HashMap<Integer, Process> copiedP = null;
    	A copiedA = null;
    	C copiedC = null;
    	ArrayList<BECPNode> copyNeighborCache;
    	BECPNode destination;
    	double wAgreement;
    	double vAgreement;
    	double wPropagation;
    	double vPropagation;
    	double peerWeight = 0;
    	double peerValue = 0;
    	Simulator simulator = this.peerBlockchainNode.getSimulator();
    	RandomnessEngine randomnessEngine = this.peerBlockchainNode.getNetwork().getRandom();
    	double currentTime = simulator.getSimulationTime();
    	ArrayList<PushEntry> temp = new ArrayList<>();
    	ArrayList<Key> temp2 = new ArrayList<>();
    	peer.addCycleNumber(1);
    	//System.out.println(peer.getValue()+"  "+peer.getWeight()+"  "+peer.getEstimation());
    	//***** SSEP (System Size Estimation Protocol)*****//
        if (SSEP) {
            peerValue = peer.getValue()/2;
            peerWeight = peer.getWeight()/2;
            peer.setValue(peerValue);
            peer.setWeight(peerWeight);
        }
        //##### SSEP (System Size Estimation Protocol)#####//
        //***** REAP (Robust Epidemic Aggregation Protocol)*****//
        if (REAP) {
            if(peer.getWeight()>0 && !peer.getConvergenceFlag()){ // detects Propagation phase
                peer.setCriticalPushFlag(true);
                //System.out.println("entered into a propagation phase at cycle "+ peer.getCycleNumber());
            } else if(peer.getConvergenceFlag()){
            	//peer.setCriticalPushFlag(false); 
            }
            peerValue = peer.getValue()/2;
            peerWeight = peer.getWeight()/2;
            peer.setValue(peerValue);
            peer.setWeight(peerWeight);
        }
        //##### REAP (Robust Epidemic Aggregation Protocol)#####//
        //***** REAP_PLUS (Robust Epidemic Aggregation Protocol)*****//
        if (REAP_PLUS) {
            peer.setCriticalPushFlag(true); // set Propagation phase for system size (always is active)
        	//****************Joining a Node*********************
        	if(tempJoinedEvent.size()>0) { // only check for NEWLY JOINED nodes
            	boolean correctSystemSize = true;
            	for(BECPBlock tempBlock:tempJoinedEvent.keySet()) {
            		BECPBlock peerCashBlock = peer.getBlockLocalCache().get(tempBlock.getHeight());
        			if(peerCashBlock.getState()!=BECPBlock.State.COMMIT) {
        				correctSystemSize = false;
        				break;
        			}
        		}
            	if(correctSystemSize) {
              		if(WAIT_INFORM_TIMS.get(peer)==null) {
            			WAIT_INFORM_TIMS.put(peer, 1);
            		}else {
            			int value = WAIT_INFORM_TIMS.get(peer);
            			WAIT_INFORM_TIMS.put(peer, value + 1);
            		}
            		
            		if(WAIT_INFORM_TIMS.get(peer)==WAIT_INFORM_TIME) {
                		peer.getJoinedNodes().add(peer); // update the list of joined nodes to inform others.
                		//System.out.println("the system size corrected!");
                		tempJoinedEvent.clear();
                		WAIT_INFORM_TIMS.remove(peer);
            		}
            	}
        	}
        	//****************Joining a Node*********************
            //-------------------------------------------- 
            //check whether if it's time to correct the system size and inform others (CRASH).
            Iterator<Map.Entry<PushEntry, HashMap<BECPNode, ArrayList<BECPBlock>>>> iterator_1 = tempCrashedEvents_1.entrySet().iterator();
            while (iterator_1.hasNext()) {
                Map.Entry<PushEntry, HashMap<BECPNode, ArrayList<BECPBlock>>> crashEvent = iterator_1.next();
                HashMap<BECPNode, ArrayList<BECPBlock>> nodeBlocksMap = crashEvent.getValue();
              	boolean correctSystemSize = true;
              	BECPNode crashedNode = nodeBlocksMap.keySet().iterator().next();
              	ArrayList<BECPBlock> tempBlocks = nodeBlocksMap.get(crashedNode);
            	for(BECPBlock tempBlock:tempBlocks) {
            		BECPBlock peerCashBlock = peer.getBlockLocalCache().get(tempBlock.getHeight());
            		if(peerCashBlock.getState()!=BECPBlock.State.COMMIT) {
            			correctSystemSize = false;
            			break;
            		}
            	}
            	if(correctSystemSize) { // correct the system size.
            		if(WAIT_INFORM_TIMS.get(crashedNode)==null) {
            			WAIT_INFORM_TIMS.put(crashedNode, 1);
            		}else {
            			int value = WAIT_INFORM_TIMS.get(crashedNode);
            			WAIT_INFORM_TIMS.put(crashedNode, value + 1);
            		}
            		
            		if(WAIT_INFORM_TIMS.get(crashedNode)==WAIT_INFORM_TIME) {
            			peer.getCrashedNodes().add(crashedNode); // update the list of crashed nodes to inform others.
                		iterator_1.remove();
                		WAIT_INFORM_TIMS.remove(crashedNode);
            		}
            	}
            }
            //-------------------------------------------- 
            //System.out.println("entered into a propagation phase at cycle "+ peer.getCycleNumber());
            Iterator<PushEntry> iterator_2 = peer.getPushEntriesBuffer().iterator();
            while(iterator_2.hasNext()){
            	PushEntry pushEntry = iterator_2.next();
            	if((!pushEntry.isReceivedPull())) {
            		pushEntry.decrementTimeout();
            		if(pushEntry.getTimeout()==0) {
            			//System.out.println("A churn was detected (by PULL MESSAGE) in node "+peer.getNodeID()+" (for node "+pushEntry.getDestination().getNodeID()+" from cycle "+ pushEntry.getCycleNumber()+") at cycle "+peer.getCycleNumber()+"; mass restoration was done!");
                		peerValue = peer.getValue()+pushEntry.getAggregationValue();
                        peerWeight = peer.getWeight()+pushEntry.getAggregationWeight();
                        peer.setValue(peerValue);
                        peer.setWeight(peerWeight);
                        //------------------------------------- ***** *****
                        ArrayList<BECPBlock> tempBlocks = new ArrayList<>();
                        for(Integer blockID:pushEntry.getReplicaBlockCache().keySet()) {
                        	ReplicaBlock replicaBlock = pushEntry.getReplicaBlockCache().get(blockID);
                        	if(peerBlockLocalCache.containsKey(blockID)) {
                            	BECPBlock becpBlock = peerBlockLocalCache.get(blockID);
                            	if(becpBlock.getCreator()==replicaBlock.getBlockCreator()) {
                            		if(becpBlock.getCycleNumber()<=pushEntry.getCycleNumber()) {
                            			tempBlocks.add(becpBlock);
                            		}
                                	becpBlock.setVPropagation(becpBlock.getVPropagation()+replicaBlock.getVPropagation());
                            		becpBlock.setWPropagation(becpBlock.getWPropagation()+replicaBlock.getWPropagation());
                            		becpBlock.setVAgreement(becpBlock.getVAgreement()+replicaBlock.getVAgreement());
                            		becpBlock.setWAgreement(becpBlock.getWAgreement()+replicaBlock.getWAgreement());
                            	}
                        	}
                        }
                        if(tempBlocks.size()>0) {
                        	HashMap<BECPNode, ArrayList<BECPBlock>> crashedNodeBlocks = new HashMap<>();
                        	crashedNodeBlocks.put((BECPNode) pushEntry.getDestination(), tempBlocks);
                            tempCrashedEvents_1.put(pushEntry, crashedNodeBlocks);
                        }else {
                    		peer.getCrashedNodes().add((BECPNode) pushEntry.getDestination()); // update the list of crashed nodes to inform others.
                        }
                        
                        if(peer.getNeighborsLocalCache().contains(pushEntry.getDestination())) {
                            peer.getNeighborsLocalCache().remove(pushEntry.getDestination()); //********** (added) update the neighbours local cache
                        }
                        
            		}
            	}
            }
          
            peerValue = peer.getValue()/2;
            peerWeight = peer.getWeight()/2;
            peer.setValue(peerValue);
            peer.setWeight(peerWeight);
        }
        //##### REAP_PLUS (Robust Epidemic Aggregation Protocol)#####//
        //***** ARP (Adaptive Restart Protocol)*****//
        if(ARP) {
        	detectConvergence(peer, peer.getCycleNumber(), randomnessEngine);
            BECPScenario.push(peer, peer.getCycleNumber());
            copiedP = getArrayListFromPool3();
            copiedP = copyP(peer, copiedP);
            copiedA = peer.getA().clone();
            copiedC = peer.getC().clone();
        }
        //##### ARP (Adaptive Restart Protocol)#####//
        //***** NCP (Node Cache Protocol)*****//
        if(NCP) {
        	destination = getRandomNeighbor(peer, randomnessEngine); // the function getNode() for NCP (Node Cache Protocol).
        	copyNeighborCache = getArrayListFromPool1();
            copyNeighborCache.addAll(peer.getNeighborsLocalCache());
            copyNeighborCache.remove(destination);
        }
        //##### NCP (Node Cache Protocol)#####//
        //***** PTP (Phase Transition Protocol)*****//
        if (PTP) {
            for (BECPBlock becpBlock:peerBlockLocalCache.values()) {
                becpBlock.setVPropagation(becpBlock.getVPropagation()/2);
                becpBlock.setWPropagation(becpBlock.getWPropagation()/2);
                becpBlock.setVAgreement(becpBlock.getVAgreement()/2);
                becpBlock.setWAgreement(becpBlock.getWAgreement()/2);
            }
            copyBlockCache = getArrayListFromPool2();
            if(peerBlockLocalCache.size()>0){
                for (BECPBlock peerBlock:peerBlockLocalCache.values()) {
                    copyBlockCache.put(peerBlock.getHeight(), peerBlock.clone());
                }
            }
        }
        //##### PTP (Phase Transition Protocol)#####//
        //***** ECP(Epidemic Consensus Protocol)*****//
        if (ECP) {
            for (BECPBlock becpBlock:peerBlockLocalCache.values()) {
                becpBlock.setVDataAggregation(becpBlock.getVDataAggregation()/2);
                becpBlock.setWDataAggregation(becpBlock.getWDataAggregation()/2);
                becpBlock.setVDataConvergence(becpBlock.getVDataConvergence()/2);
                becpBlock.setVDataAgreement(becpBlock.getVDataAgreement()/2);
                becpBlock.setWeightValue(becpBlock.getWeightValue()/2);
                becpBlock.setLeader(becpBlock.getLeader());
                becpBlock.setCycleNumber(peer.getCycleNumber()); // REAP protocol
            }
            copyBlockCache = getArrayListFromPool2();
            if(peerBlockLocalCache.size()>0){
                for (BECPBlock peerBlock:peerBlockLocalCache.values()) {
                    copyBlockCache.put(peerBlock.getHeight(), peerBlock.clone());
                }
            }
        }
        //##### ECP(Epidemic Consensus Protocol)#####//
        this.peerBlockchainNode.gossipMessage( // the function getNode() for NCP (Node Cache Protocol).
                new GossipMessage(
                        new BECPPush<>(this.peerBlockchainNode, peer.getCycleNumber(), getSizeOfBlocks(copyBlockCache), peerValue, peerWeight, copyNeighborCache, copyBlockCache, peer.getCriticalPushFlag(), false, peer.getL(), copiedP, copiedA, copiedC, peer.getCrashedNodes(), peer.getJoinedNodes(), false)
                ), destination);
        simulator.putEvent(new NodeCycleEvent<BECPNode>(peer), BECPScenario.CYCLE_TIME);
        //System.out.println("next event was set to "+simulator.getSimulationTime()+BECPScenario.CYCLETIME+" for node "+peer.getNodeID());
        //*System.out.println("sent a push from "+peer.getNodeID()+" to "+destination.getNodeID()+" at cycle "+peer.getCycleNumber());//*
        //System.out.println("a simulationEvent inserted by node "+peer.getNodeID()+" (queue size: "+simulator.getNumOfEvents()+") at "+simulator.getSimulationTime());
        //***** REAP (Robust Epidemic Aggregation Protocol)*****//
        if (REAP) {
            if(peer.getCriticalPushFlag()){ // insert critical push in PushEntriesBuffer.
            	HashMap<Integer, ReplicaBlock> replicaBlockCache = new HashMap<>();
                peer.getPushEntriesBuffer().add(new PushEntry(destination, peer.getCycleNumber(), PULL_TIMEOUT, peer.getValue(), peer.getWeight(),replicaBlockCache));
            }
            temp.clear();
            for(PushEntry pushEntry:peer.getPushEntriesBuffer()){
                if(pushEntry.getCycleNumber()<peer.getCycleNumber()){
            		this.peerBlockchainNode.gossipMessage( // perform RePush.
                            new GossipMessage(
                                    new BECPPush<>(this.peerBlockchainNode, pushEntry.getCycleNumber(), getSizeOfBlocks(copyBlockCache), pushEntry.getAggregationValue(), pushEntry.getAggregationWeight(), copyNeighborCache, copyBlockCache, true, false, peer.getL(), copiedP, copiedA, copiedC, null, null, false)
                            ), pushEntry.getDestination());
                    temp.add(pushEntry);
                    //*System.out.println("sent a RePush from "+peer.getNodeID()+" to "+pushEntry.getDestination().getNodeID()+ " for cycle "+pushEntry.getCycleNumber());//*
                }
            }
            if(temp.size()>0){
                for(PushEntry pushEntry:temp){
                    peer.getPushEntriesBuffer().remove(pushEntry);
                }
            }
            temp2.clear();
            for (Key keyEntry:peer.getRecoveryCache().keySet()) { // perform churn detection and mass correction.
            	RecoveryEntry valueEntry = peer.getRecoveryCache().get(keyEntry);
            	valueEntry.decrementTimeout();
                if(valueEntry.getTimeout()==0){ // timeout value expired, failure detection, correct mass.
                	//System.out.println("A churn was detected in node "+peer.getNodeID()+" (for node "+valueEntry.getSender().getNodeID()+" from cycle "+ keyEntry.getCycleNumber()+") at cycle "+peer.getCycleNumber()+"; mass restoration was done!");
                    peerValue = peer.getValue()+valueEntry.getReplicaValue();
                    peerWeight = peer.getWeight()+valueEntry.getReplicaWeight();
                    peer.setValue(peerValue);
                    peer.setWeight(peerWeight);
                    temp2.add(keyEntry);
                }
            }
            if(temp2.size()>0){
                for(Key entry:temp2){
                    peer.getRecoveryCache().remove(entry);
                }
            }
        }
        //##### REAP (Robust Epidemic Aggregation Protocol)#####//
        //***** REAP_PLUS (Robust Epidemic Aggregation Protocol)*****//
        if (REAP_PLUS) {
            if(peer.getCriticalPushFlag()){ // insert critical push in PushEntriesBuffer.
            	//------------------------------------- ***** *****
            	HashMap<Integer, ReplicaBlock> replicaBlockCache = new HashMap<>();
            	if(peerBlockLocalCache.size()>0) {
                	for (BECPBlock becpBlock:peerBlockLocalCache.values()) {
                		ReplicaBlock replicaBlock = new ReplicaBlock();
                		replicaBlock.setVPropagation(becpBlock.getVPropagation());
                		replicaBlock.setWPropagation(becpBlock.getWPropagation());
                		replicaBlock.setVAgreement(becpBlock.getVAgreement());
                		replicaBlock.setWAgreement(becpBlock.getWAgreement());
                		replicaBlock.setBlockCreator(becpBlock.getCreator());
                		replicaBlockCache.put(becpBlock.getHeight(), replicaBlock);
                	}
            	}
            	System.out.print("NEW BRANCH");
            	//------------------------------------- ***** *****
                peer.getPushEntriesBuffer().add(new PushEntry(destination, peer.getCycleNumber(), PULL_TIMEOUT, peer.getValue(), peer.getWeight(),replicaBlockCache));
            }
            temp.clear();
            for(PushEntry pushEntry:peer.getPushEntriesBuffer()){
                if(pushEntry.getCycleNumber()<peer.getCycleNumber()){
                	if(pushEntry.isReceivedPull()) { 
                		this.peerBlockchainNode.gossipMessage( // perform a RePush.
                                new GossipMessage(
                                        new BECPPush<>(this.peerBlockchainNode, pushEntry.getCycleNumber(), getSizeOfBlocks(copyBlockCache), pushEntry.getAggregationValue(), pushEntry.getAggregationWeight(), copyNeighborCache, copyBlockCache, true, pushEntry.isReceivedPull(), peer.getL(), copiedP, copiedA, copiedC, peer.getCrashedNodes(), peer.getJoinedNodes(), false)
                                ), pushEntry.getDestination());
                        
                        temp.add(pushEntry);
                        //*System.out.println("sent a RePush from "+peer.getNodeID()+" to "+pushEntry.getDestination().getNodeID()+ " for cycle "+pushEntry.getCycleNumber());//*
                	}else if(pushEntry.getTimeout()==0){
                		this.peerBlockchainNode.gossipMessage( // perform a notification RePush.
                				new GossipMessage(
                                        new BECPPush<>(this.peerBlockchainNode, pushEntry.getCycleNumber(), getSizeOfBlocks(copyBlockCache), pushEntry.getAggregationValue(), pushEntry.getAggregationWeight(), copyNeighborCache, copyBlockCache, true, pushEntry.isReceivedPull(), peer.getL(), copiedP, copiedA, copiedC, peer.getCrashedNodes(), peer.getJoinedNodes(), false)
                				), pushEntry.getDestination());
                
                        temp.add(pushEntry);
                        //*System.out.println("sent a notification RePush from "+peer.getNodeID()+" to "+pushEntry.getDestination().getNodeID()+ " for cycle "+pushEntry.getCycleNumber());//*
                	}
                }
            }
            if(temp.size()>0){
                for(PushEntry pushEntry:temp){
                    peer.getPushEntriesBuffer().remove(pushEntry);
                }
            }
            //--------------------------------------------- 
            //check whether if it's time to correct the system size and inform others.
            Iterator<Map.Entry<RecoveryEntry, HashMap<BECPNode, ArrayList<BECPBlock>>>> iterator = tempCrashedEvents_2.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<RecoveryEntry, HashMap<BECPNode, ArrayList<BECPBlock>>> crashEvent = iterator.next();
                HashMap<BECPNode, ArrayList<BECPBlock>> nodeBlockMap = crashEvent.getValue();
              	boolean correctSystemSize = true;
              	BECPNode crashedNode = nodeBlockMap.keySet().iterator().next();
              	ArrayList<BECPBlock> tempBlocks = nodeBlockMap.get(crashedNode);
            	for(BECPBlock tempBlock:tempBlocks) {
            		BECPBlock peerCashBlock = peer.getBlockLocalCache().get(tempBlock.getHeight());
            		if(peerCashBlock.getState()!=BECPBlock.State.COMMIT) {
            			correctSystemSize = false;
            			break;
            		}
            	}
            	if(correctSystemSize) { // correct the system size.
            		if(WAIT_INFORM_TIMS.get(crashedNode)==null) {
            			WAIT_INFORM_TIMS.put(crashedNode, 1);
            		}else {
            			int value = WAIT_INFORM_TIMS.get(crashedNode);
            			WAIT_INFORM_TIMS.put(crashedNode, value + 1);
            		}
            		
            		if(WAIT_INFORM_TIMS.get(crashedNode)==WAIT_INFORM_TIME) {
            			peer.getCrashedNodes().add(crashedNode); // update the list of crashed nodes to inform others.
                		iterator.remove();
                		WAIT_INFORM_TIMS.remove(crashedNode);
            		}
            	}
            }
            //--------------------------------------------- 
            temp2.clear();
            for (Key keyEntry:peer.getRecoveryCache().keySet()) { // perform "CHURN DETECTION" and "mass correction".
            	RecoveryEntry recoveryEntry = peer.getRecoveryCache().get(keyEntry);
            	recoveryEntry.decrementTimeout();
                if(recoveryEntry.getTimeout()==0){ // timeout value expired, FAILURE DETECTION, correct mass.
                	//System.out.println("A churn was detected in node "+peer.getNodeID()+" (for node "+recoveryEntry.getSender().getNodeID()+" from cycle "+ keyEntry.getCycleNumber()+") at cycle "+peer.getCycleNumber()+"; mass restoration was done!");
                	temp2.add(keyEntry);
                	if(peerBlockLocalCache.size()>0) {
                		peerValue = peer.getValue()+recoveryEntry.getReplicaValue(); // restore the Masses for the system size.
                        peerWeight = peer.getWeight()+recoveryEntry.getReplicaWeight();
                        peer.setValue(peerValue);
                        peer.setWeight(peerWeight);
                        //------------------------------------- ***** *****
                        ArrayList<BECPBlock> tempBlocks = new ArrayList<>();
                        for(Integer entry:recoveryEntry.getReplicaBlockCache().keySet()) {
                        	ReplicaBlock replicaBlock = recoveryEntry.getReplicaBlockCache().get(entry);
                        	if(peerBlockLocalCache.containsKey(entry)) {
                        		BECPBlock becpBlock = peerBlockLocalCache.get(entry);
                        		if(becpBlock.getCreator()==replicaBlock.getBlockCreator()) { // restore the Masses for blocks.
                        			if(becpBlock.getCycleNumber()<=recoveryEntry.getCycleNumber()) {
                        				tempBlocks.add(becpBlock);
                        			}
                                	double vp = becpBlock.getVPropagation()+replicaBlock.getVPropagation();
                                    double wp = becpBlock.getWPropagation()+replicaBlock.getWPropagation();
                                    double va = becpBlock.getVAgreement()+replicaBlock.getVAgreement();
                                    double wa = becpBlock.getWAgreement()+replicaBlock.getWAgreement();
                                    becpBlock.setVPropagation(vp);
                                    becpBlock.setWPropagation(wp);
                                    becpBlock.setVAgreement(va);
                                    becpBlock.setWAgreement(wa);
                        		}
                        	}
                        }
                    	HashMap<BECPNode, ArrayList<BECPBlock>> crashedNodeBlocks = new HashMap<>();
                    	crashedNodeBlocks.put((BECPNode) recoveryEntry.getSender(), tempBlocks);
                        tempCrashedEvents_2.put(recoveryEntry, crashedNodeBlocks);
                      //------------------------------------- ***** *****
                	}else if(peerBlockLocalCache.size()==0){
                		peerValue = peer.getValue()+recoveryEntry.getReplicaValue();
                        peerWeight = peer.getWeight()+recoveryEntry.getReplicaWeight();
                        peer.setValue(peerValue);
                        peer.setWeight(peerWeight);
                		peer.getCrashedNodes().add((BECPNode) recoveryEntry.getSender()); // update the list of crashed nodes.
                	}
                	//------------------------------------- ***** *****
                	
                    if(peer.getNeighborsLocalCache().contains(recoveryEntry.getSender())) {
                        peer.getNeighborsLocalCache().remove(recoveryEntry.getSender()); //********** (added) update the neighbours local cache
                    }
                    
                }
            }
            if(temp2.size()>0){
                for(Key entry:temp2){
                    peer.getRecoveryCache().remove(entry);
                }
            }
            //-------------------------------------
        }
        //##### REAP_PLUS (Robust Epidemic Aggregation Protocol)#####//
        //***** PTP (Phase Transition Protocol)*****//
        if (PTP) {
            numOfParticipants = peer.getEstimation(); // get the actual system size, including the number of crashed nodes..
            //System.out.println(numOfParticipants);
            for (BECPBlock becpBlock:peerBlockLocalCache.values()) {
                vPropagation = becpBlock.getVPropagation();
                wPropagation = becpBlock.getWPropagation();
                vAgreement = becpBlock.getVAgreement();
                wAgreement = becpBlock.getWAgreement();
                switch (becpBlock.getState()) {
                    case PROPAGATION:
                        if ((numOfParticipants > 0) && (Math.abs((numOfParticipants - (vPropagation / wPropagation))/numOfParticipants) <= EPSILON_1)) {
                        	int currentValue = propagationCyclesPTP.getOrDefault(becpBlock, 0);
                        	propagationCyclesPTP.put(becpBlock, currentValue + 1);
                            if (propagationCyclesPTP.get(becpBlock) == MIN_CONSECUTIVE_CYCLES_THRESHOLD) { // AGREEMENT STATE
                                //System.out.println("Agreement occurred in node "+peer.getNodeID()+" for block "+ becpBlock.getHeight()+" at "+simulator.getSimulationTime());
                            	becpBlock.setState(BECPBlock.State.AGREEMENT);
                            	if(!tempJoinedEvent.containsKey(becpBlock)) {
                            		becpBlock.setVAgreement(vAgreement + 1);
                            	}
                                propagationCyclesPTP.put(becpBlock, 0);
                            }
                        } else {
                        	propagationCyclesPTP.put(becpBlock, 0); // reset the counter.
                        }
                        break;
                    case AGREEMENT:
                    	if ((numOfParticipants > 0) && (Math.abs((numOfParticipants - (vAgreement / wAgreement))/numOfParticipants) <= EPSILON_2)) {
                    		int currentValue = agreementCyclesPTP.getOrDefault(becpBlock, 0);
                        	agreementCyclesPTP.put(becpBlock, currentValue + 1);
                            if (agreementCyclesPTP.get(becpBlock) == MIN_CONSECUTIVE_CYCLES_THRESHOLD) { // COMMIT STATE
                            	BECPScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-becpBlock.getCreationTime()); // record the consensus time.
                            	becpBlock.setState(BECPBlock.State.COMMIT);
                            	//**********************Some application-specific action********************//
                                //System.out.println("Consensus occurred in the node "+peer.getNodeID()+" for block "+ becpBlock.getHeight()+" hash: "+becpBlock.getHash().hashCode());
                            	if(RECORD_LEDGERS) {
                                    this.confirmedBlocks.add((B) becpBlock); // update the blockchain-local ledger.
                                    peer.addToLocalLedger(becpBlock);
                                    orderLedger(peer, peer.getLocalLedger());
                                }
                                if (WRITE_CONSENSUS_LOGS) { // write logs for peers.
                                    writer.println("Consensus occurred in node " + peer.getNodeID() + " for the block " + becpBlock.getHeight() + " at " + currentTime);
                                    writer.flush();
                                }
                                if(becpBlock.getHeight()>peer.getLastConfirmedBlock().getHeight()) { // update the last confirmed block with the highest ID.
                                	peer.setLastConfirmedBlock(becpBlock);
                                }
                                agreementCyclesPTP.put(becpBlock, 0);
                            }
                        } else {
                        	agreementCyclesPTP.put(becpBlock, 0); // reset the counter.
                        }
                        break;
                }
            }
            if(peerBlockLocalCache.size() > 0) {
            	//System.out.println(peerBlockLocalCache.size());
                Iterator<Map.Entry<Integer, BECPBlock>> iterator = peerBlockLocalCache.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, BECPBlock> entry = iterator.next();
                    BECPBlock block = entry.getValue();
                    if ((block.getState()==BECPBlock.State.COMMIT)&&(block.getHeight()+30<peer.getLastConfirmedBlock().getHeight())) {
                    	//iterator.remove(); *********TO DO**********
                        //removedCachedBlocks.add(block.getHeight());
                    }
                }
            }
        }
        //##### PTP (Phase Transition Protocol)#####//
        //***** ECP (Epidemic Consensus Protocol)*****//
        if(ECP) {
        	Map.Entry<Integer, BECPBlock> peerEntry = peer.getBlockLocalCache().entrySet().iterator().next();
         	BECPBlock becpBlock = peerEntry.getValue();
            double Vc = becpBlock.getVDataConvergence();
            double Va = becpBlock.getVDataAgreement();
            double W = becpBlock.getWeightValue();
            int newLeader = becpBlock.getLeader();
            switch (peer.getState()){
            	case AGGREGATION:
            		if(coefficientOfVariance(peer.getECPQueue())<=EPSILON_1) {
            			aggregationCyclesECP++;
            			if(aggregationCyclesECP==MIN_CONSECUTIVE_CYCLES_THRESHOLD) {
            				peer.setState(BECPNode.State.CONVERGENCE);
            				becpBlock.setVDataConvergence(Vc+1);
            				aggregationCyclesECP=0;
            			}
            		}else {
            			aggregationCyclesECP=0; // reset the counter.
            		}
                    if (lastTimeLeader == newLeader) {
                    	unchangedLeaderCyclesECP++;  
                    	//System.out.println(unchangedLeaderCyclesECP);
                        if (unchangedLeaderCyclesECP == MIN_CONSECUTIVE_CYCLES_THRESHOLD-1) {
               				unchangedLeaderCyclesECP=0;
            				if(newLeader==this.peerBlockchainNode.getNodeID()) {
            					becpBlock.setWeightValue(1);
            					System.out.println("the leader is: "+peer.getLeader()); //**************
            				}
                        }
                    } else {
                    	lastTimeLeader = newLeader;  // Update the leader.
                        unchangedLeaderCyclesECP=0;  // Reset the unchanged cycles counter
                    }
            		break;
            	case CONVERGENCE:
            		if(Math.abs((peer.getEstimation()-(Vc/W))/peer.getEstimation())<=EPSILON_2) {
            			convergenceCyclesECP++;
            			if(convergenceCyclesECP==MIN_CONSECUTIVE_CYCLES_THRESHOLD) {
            				peer.setState(BECPNode.State.AGREEMENT);
            				becpBlock.setVDataAgreement(Va+1);
            				convergenceCyclesECP=0;
            			}
            		}else {
            			convergenceCyclesECP=0; // reset the counter.
            		}
            		break;
            	case AGREEMENT:
            		if(Math.abs((peer.getEstimation()-(Va/W))/peer.getEstimation())<=EPSILON_2) {
            			agreementCyclesECP++;
            			if(agreementCyclesECP==MIN_CONSECUTIVE_CYCLES_THRESHOLD) {
            				agreementCyclesECP=0;
            				peer.setState(BECPNode.State.COMMIT);
            				peer.setLastConfirmedBlock(becpBlock);
            				this.confirmedBlocks.add((B) becpBlock); // update the blockchain-local ledger.
            				peer.addToLocalLedger(becpBlock);
            				BECPScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-becpBlock.getCreationTime()); // record the consensus time.
            				writer.println("The node "+peer.getNodeID()+" has reached the COMMIT state on the value: "+peer.getVDataAggregation());
            				writer.flush();
            			}
            		}else {
            			agreementCyclesECP=0;
            		}
            		break;
            }
        }
        //##### ECP (Epidemic Consensus Protocol)#####//
        setCurrentMainChainHead(this.confirmedBlocks);
    	//System.out.println("New Cycle: Recovery cache size is "+peer.getRecoveryCache().size()+ " for node "+ peer.nodeID+" at "+peer.getSimulator().getSimulationTime());
    	//System.out.println("Push Entry cache size is "+peer.getPushEntriesBuffer().size()+ " for node "+ peer.nodeID);
    }
	/**
	 * Resolves duplication issues in the blockLocalCache for a BECPNode.
	 *
	 * @param peerBlockLocalCache  The HashMap containing Hash-to-BECPBlock mappings for the peer node.
	 * @param blockSender          The BECPBlock sent by another node for resolution.
	 * @param peer                 The BECPNode receiving and resolving the duplication.
	 */
	private void resolveDuplication(final BECPBlock blockSender, final BECPNode peer){
		if(removedCachedBlocks.contains(blockSender.getHeight())) {
			return;
		}
		// Check if the peerBlockLocalCache contains a block with the same ID as the received block.
		HashMap<Integer, BECPBlock> peerBlockLocalCache = peer.getBlockLocalCache();
		if((peerBlockLocalCache.containsKey(blockSender.getHeight()))){ // Resolve duplicate blocks.
    		BECPBlock blockPeer = peerBlockLocalCache.get(blockSender.getHeight());
            // Check if cycle number and creator node ID match for both blocks. (the own block)
    		if((blockSender.getCycleNumber()==blockPeer.getCycleNumber()) && (blockSender.getCreator().getNodeID())==blockPeer.getCreator().getNodeID()){
                // Update values in the existing block in case of a match.
    			blockPeer.setVPropagation(blockPeer.getVPropagation() + blockSender.getVPropagation());
                blockPeer.setWPropagation(blockPeer.getWPropagation() + blockSender.getWPropagation());
                blockPeer.setVAgreement(blockPeer.getVAgreement() + blockSender.getVAgreement());
                blockPeer.setWAgreement(blockPeer.getWAgreement() + blockSender.getWAgreement());
            }else if((((blockSender.getCycleNumber()==blockPeer.getCycleNumber()) && (blockSender.getCreator().getNodeID()<blockPeer.getCreator().getNodeID())))||(blockSender.getCycleNumber()<blockPeer.getCycleNumber())){
                // Remove the existing block and add the received block if conditions are met.
            	resolveFork(peerBlockLocalCache, blockPeer, peer); // remove the invalid block and its children from the local block cache.
            	//peerBlockLocalCache.remove(blockPeer.getHeight());
            	if(!tempJoinedEvent.containsKey(blockSender)) {
            		blockSender.setVPropagation(blockSender.getVPropagation() + 1);
            	}
                peerBlockLocalCache.put(blockSender.getHeight(), blockSender);
                peer.setBlockLocalCache(peerBlockLocalCache);
                peer.setCurrentPreferredBlock(blockSender); // update the new preferred block.
            }
        }else{
            // Add the received block to the cache if conditions are met.
        	if((blockSender.getHeight()==1)||((blockSender.getHeight()==peer.getCurrentPreferredBlock().getHeight()+1)&&(blockSender.getParent().getCreator().nodeID==peer.getCurrentPreferredBlock().getCreator().nodeID))) {
        		if(!tempJoinedEvent.containsKey(blockSender)) {
        			blockSender.setVPropagation(blockSender.getVPropagation() + 1);
        		}
                peerBlockLocalCache.put(blockSender.getHeight(), blockSender);
                peer.setBlockLocalCache(peerBlockLocalCache);
                peer.setCurrentPreferredBlock(blockSender);
        	}
        }
    }
	/**
	 * Resolves a blockchain fork by recursively removing all blocks in the forked branch 
	 * starting from the specified block. This method updates the local block cache to 
	 * reflect the removal of the entire branch.
	 *
	 * @param peerBlockLocalCache the local cache of blocks, keyed by block height.
	 * @param blockPeer the block from which the forked branch starts.
	 * @param peer the node that contains the blockPeer.
	 */
	private static void resolveFork(HashMap<Integer, BECPBlock> peerBlockLocalCache, BECPBlock blockPeer, BECPNode peer) {
	    HashSet<BECPBlock> children = blockPeer.getChildren();
	    int descendentNumber = 0;
	    
	    for (BECPBlock child : children) {
	        resolveFork(peerBlockLocalCache, child, peer);
	        descendentNumber++;
	    }

	    HashMap<Integer, Metadata> blockMap = BECPScenario.peerBlockMap.getOrDefault(peer.nodeID, new HashMap<>());
	    
	    Metadata metadata = blockMap.getOrDefault(blockPeer.getHeight(), new Metadata());
	    metadata.addTimes();  // Increment call count
	    metadata.addHeight(descendentNumber);  // Add descendant height

	    blockMap.put(blockPeer.getHeight(), metadata);  // Update the blockMap

	    BECPScenario.peerBlockMap.put(peer.nodeID, blockMap);  // Update peerBlockMap

	    // Remove the block from the local cache
	    peerBlockLocalCache.remove(blockPeer.getHeight());
	}

	/**
	 * Determines whether a BECPBlock should be added to the blockLocalCache of a BECPNode.
	 *
	 * @param peer          The BECPNode receiving and processing the block.
	 * @param blockSender   The BECPBlock being considered for addition to the cache.
	 * @return              True if the block should be added, false otherwise.
	 */
    public boolean shouldAddToCache(BECPNode peer, BECPBlock blockSender) {
        // Check if the parent hash of the block matches the hash of the last confirmed block.
    	if(blockSender.getParent().hashCode()==peer.getLastConfirmedBlock().hashCode()) { //check if it's directly connected to the last confirmed block.
    		return true;
    	} else if(blockSender.getParent().hashCode()!=peer.getLastConfirmedBlock().hashCode()){ 
            // Iterate through the blockLocalCache and check if the block is connected to an unaccomplished block.
    		for(BECPBlock block:peer.getBlockLocalCache().values()) {
    			if((block.hashCode()==blockSender.getParent().hashCode())&&(!this.confirmedBlocks.contains(block))) { // check if it's connected to an unaccomplished block.
    				return true;
    			}
    		}
    	}
    		
        return false;
    }
    /**
     * Orders the local ledger of a BECPNode based on the height of the blocks.
     *
     * @param peer        The BECPNode for which the local ledger is ordered.
     * @param localLedger The LinkedHashSet representing the local ledger of the BECPNode.
     */
	private void orderLedger(BECPNode peer, LinkedHashSet<BECPBlock> localLedger) {
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
        peer.getLocalLedger().clear();
        peer.getLocalLedger().addAll(blockList);
	}
	/**
	 * Calculates the average of the elements in the given ArrayList of Doubles.
	 *
	 * @param list The ArrayList of Doubles for which the average is calculated.
	 * @return The average of the elements in the ArrayList.
	 */
	private double getAverage(final ArrayList<Double> list) {
		double average = 0;
		for(double e:list) {
			average +=e;
		}
		return average/list.size();
	}
	/**
	 * Calculates the standard error of the elements in the given ArrayList of Doubles.
	 *
	 * @param list The ArrayList of Doubles for which the standard error is calculated.
	 * @return The standard error of the elements in the ArrayList.
	 * @throws IllegalArgumentException if the list has less than two elements.
	 */
	private double getSE(final ArrayList<Double> list) {
	    if (list.size() <= 1) {
	        throw new IllegalArgumentException("List should have at least two elements for standard error calculation.");
	    }
	    double sum = 0;
	    double average = getAverage(list);
	    for (int i = 0; i < list.size(); i++) {
	        sum += Math.pow(list.get(i) - average, 2);
	    }
	    return Math.sqrt(sum / (list.size() - 1)) / Math.sqrt(list.size());
	}

	private void detectConvergence(final BECPNode peer, final int cycleNumber, final RandomnessEngine randomnessEngine) {
		ArrayList<Double> listedQueue; // used temporary instead of arpQueue.
		switch(peer.getState()) {
			case AGGREGATION:
				ArrayList<Double> estimateValues = new ArrayList<>(); // p.e
				for(Integer processID : peer.getP().keySet()) {
					Process process = peer.getP().get(processID);
					double estimateValue = process.getValue()/process.getWeight();
					if(!Double.isNaN(estimateValue)&&Double.isFinite(estimateValue)) {
						estimateValues.add(estimateValue);
					}
				}
				double average = getAverage(estimateValues); // Calculates the mean of the estimation values for the processes.
				if(peer.getArpQueue().size()==QUEUE_SIZE){ // check if the queue is full.
                	peer.getArpQueue().poll();
                	peer.getArpQueue().add(average); 
                }else {
                	peer.getArpQueue().add(average); 
                }
				listedQueue = convertToListQueue(peer.getArpQueue());
				if(listedQueue.size()>1 && getSE(listedQueue)<EPSILON_1) { // Detect local convergence.
					aggregationCyclesARP++;
					if(aggregationCyclesARP==MIN_CONSECUTIVE_CYCLES_THRESHOLD) {
						//System.out.println("Detect local convergence in node "+peer.getNodeID()+" at epoch "+peer.getL());
						if(getSE(estimateValues)>EPSILON_2) { // Detect divergence.
							//System.out.println("Detected a divergence in node "+peer.getNodeID()+" at epoch "+peer.getL());
							BECPScenario.restart(peer.getL()+1, peer, cycleNumber); // Start a new epoch.
						}else{ // Make transition to CONSENSUS phase.
							if(BECPScenario.FAlpha(peer.nodeID, cycleNumber)<peer.getC().getIdentifier()) {
								peer.getC().setIdentifier(BECPScenario.FAlpha(peer.nodeID, cycleNumber));
								peer.getC().setValue(1);
								peer.getC().setWeight(1);
							}else {
								double value = peer.getC().getValue();
								peer.getC().setValue(value+1);
							}
							peer.setState(BECPNode.State.CONSENSUS);
						}
						aggregationCyclesARP=0;
					}
				}else {
					aggregationCyclesARP=0; // reset the counter.
				}
				break;
			case CONSENSUS:
				double estimate = peer.getC().getValue()/peer.getC().getWeight();
				if(!Double.isNaN(estimate)&&Double.isFinite(estimate)) {
	                if(peer.getArpQueue().size()==QUEUE_SIZE){ // if the queue is full.
	                	peer.getArpQueue().poll();
	                	peer.getArpQueue().add(estimate); 
	                }else{
	                	peer.getArpQueue().add(estimate); 
	                	if(peer.getArpQueue().size()==1) {
	                    	break;
	                    }
	                }
				}
				listedQueue = convertToListQueue(peer.getArpQueue());
				if(listedQueue.size()>1 && getSE(listedQueue)<EPSILON_1) { // Detect global convergence.
					consensusCyclesARP++;
					if(consensusCyclesARP==MIN_CONSECUTIVE_CYCLES_THRESHOLD) {
						//System.out.println("Detected a global convergence in node "+peer.getNodeID()+" at epoch "+peer.getL());
						consensusCyclesARP=0;
						if(peer.getL()>0&&peer.nodeID==10) {
			               	System.out.println("epoch "+peer.getL());
			        		//System.out.println("node "+peer.nodeID+": "+"process A"+", "+peer.getA().getValue()/peer.getA().getWeight());
			        		//System.out.println("node "+peer.nodeID+": "+"process C "+peer.getC().getIdentifier()+", "+peer.getC().getValue()/peer.getC().getWeight());
			            	for(Integer p:peer.getP().keySet()) {
			            		System.out.println("node "+peer.nodeID+": "+"process "+peer.getP().get(p).getIdentifier()+", "+peer.getP().get(p).getValue()/peer.getP().get(p).getWeight());
			            	}
			            	System.out.println("---------");
			        	}
						BECPScenario.restart(peer.getL()+1, peer, cycleNumber); // Start a new epoch.
					}
				}else {
					consensusCyclesARP=0; // reset the counter.
				}
				break;
		}
	}
    
	private void resolveEpoch(final BECPNode peer, final int cycleNumber, BECPBlockGossip<B> blockGossip, final RandomnessEngine randomnessEngine) {
    	switch (blockGossip.getGossipType()){
    		case PUSH:
    			BECPPush<B> senderPush = (BECPPush<B>) blockGossip;
    			if(senderPush.getL()>peer.getL()) { // New epoch discovered.
    				BECPScenario.restart(senderPush.getL(), peer, cycleNumber);
    				break; // ADDED
    				//System.out.println("new epoch "+senderPush.getL()+" discovered at node "+peer.getNodeID());
    			}
    			if(senderPush.getL()==peer.getL()) { // Resolve seed elements.
    				if(senderPush.getA().getIdentifier()<peer.getA().getIdentifier()) {
    					peer.getA().setIdentifier(senderPush.getA().getIdentifier());
    					peer.getA().setValue(BECPScenario.A_V_I);
    					peer.getA().setWeight(0);
    				}
    				if(senderPush.getC().getIdentifier()<peer.getC().getIdentifier()) {
    					peer.getC().setIdentifier(senderPush.getC().getIdentifier());
    					peer.getC().setValue(0);
    					peer.getC().setWeight(0);
    					if(peer.getState()==BECPNode.State.CONSENSUS) {
    						peer.getC().setValue(1);
    					}
    				}
    				for (Integer pEntry : peer.getP().keySet()) {
    					if (senderPush.getP().get(pEntry).getIdentifier() < peer.getP().get(pEntry).getIdentifier()) { // compares same process IDs.
    						peer.getP().get(pEntry).setIdentifier(senderPush.getP().get(pEntry).getIdentifier());
    						peer.getP().get(pEntry).setValue(1);
    						peer.getP().get(pEntry).setWeight(0);
				        }
    				}
    			}
    			break;
    		case PULL:
    			BECPPull<B> senderPull = (BECPPull<B>) blockGossip;
    			if(senderPull.getL()>peer.getL()) { // New epoch discovered.
    				BECPScenario.restart(senderPull.getL(), peer, cycleNumber);
    				break; // ADDED
    				//System.out.println("new epoch "+senderPull.getL()+" discovered at node "+peer.getNodeID());
    			}
    			if(senderPull.getL()==peer.getL()) { // Resolve seed elements.
    				if(senderPull.getA().getIdentifier()<peer.getA().getIdentifier()) {
    					peer.getA().setIdentifier(senderPull.getA().getIdentifier());
    					peer.getA().setValue(BECPScenario.A_V_I);
    					peer.getA().setWeight(0);
    				}
    				if(senderPull.getC().getIdentifier()<peer.getC().getIdentifier()) {
    					peer.getC().setIdentifier(senderPull.getC().getIdentifier());
    					peer.getC().setValue(0);
    					peer.getC().setWeight(0);
    					if(peer.getState()==BECPNode.State.CONSENSUS) {
    						peer.getC().setValue(1);
    					}
    				}
    				for (Integer pEntry : peer.getP().keySet()) {
    					if (senderPull.getP().get(pEntry).getIdentifier() < peer.getP().get(pEntry).getIdentifier()) { // compares same process IDs.
    						peer.getP().get(pEntry).setIdentifier(senderPull.getP().get(pEntry).getIdentifier());
    						peer.getP().get(pEntry).setValue(1);
    						peer.getP().get(pEntry).setWeight(0);
				        }
    				}
    			}
    			break;
    	}
    }
    
	private double coefficientOfVariance(final Queue<ArrayList<Double>> queue) {
        // Step 1: Calculate the arithmetic mean (average).
        float sum1 = 0, sum2 = 0;
        for (ArrayList<Double> pair : queue) {
            sum1 += pair.get(0);
            sum2 += pair.get(1);
        }
        float mean1 = sum1 / queue.size();
        float mean2 = sum2 / queue.size();

        // Step 2: Calculate the standard deviation.
        float sumOfSquaredDifferences1 = 0, sumOfSquaredDifferences2 = 0;
        for (ArrayList<Double> pair : queue) {
        	double diff1 = pair.get(0) - mean1;
        	double diff2 = pair.get(1) - mean2;
            sumOfSquaredDifferences1 += diff1 * diff1;
            sumOfSquaredDifferences2 += diff2 * diff2;
        }
        float variance1 = sumOfSquaredDifferences1 / queue.size();
        float variance2 = sumOfSquaredDifferences2 / queue.size();
        double standardDeviation1 = Math.sqrt(variance1);
        double standardDeviation2 = Math.sqrt(variance2);

        // Step 3: Calculate the coefficient of variance for both components.
        double coefficientOfVariance1 = standardDeviation1 / mean1;
        double coefficientOfVariance2 = standardDeviation2 / mean2;

        // Calculate the overall coefficient of variance (you can choose to use either component).
        double overallCoefficientOfVariance = (coefficientOfVariance1 + coefficientOfVariance2) / 2;

        return overallCoefficientOfVariance;
    }
    
	private ArrayList<Double> convertToListQueue(Queue<Double> queue) {
        ArrayList<Double> arrayList = new ArrayList<>(queue.size());
        for (Double element : queue) {
            arrayList.add(element);
        }
        return arrayList;
	}
	
	private HashMap<Integer, Process> copyP(BECPNode peer, HashMap<Integer,Process> copiedP) {
        for (Integer processID : peer.getP().keySet()) {
        	Process process = peer.getP().get(processID);
            Process clonedProcess = process.clone(); 
            copiedP.put(processID, clonedProcess);
        }
		return copiedP;
	}
	
    // Method to release an ArrayList back to the pool.
    private void releaseArrayListToPool1(final ArrayList<BECPNode> arrayList) {
        arrayList.clear(); // Clear the ArrayList before returning it to the pool.
        arrayListPool1.push(arrayList);
    }
    // Method to get an ArrayList from the pool or create a new one if the pool is empty.
    private ArrayList<BECPNode> getArrayListFromPool1() {
        return arrayListPool1.isEmpty() ? new ArrayList<>() : arrayListPool1.pop();
    }
    private void releaseArrayListToPool2(final HashMap<Integer, BECPBlock> arrayList) {
        arrayList.clear();
        arrayListPool2.push(arrayList);
    }
    private HashMap<Integer, BECPBlock> getArrayListFromPool2() {
        return arrayListPool2.isEmpty() ? new HashMap<Integer, BECPBlock>() : arrayListPool2.pop();
    }
    private HashMap<Integer, Process> getArrayListFromPool3() {
        return arrayListPool3.isEmpty() ? new HashMap<>() : arrayListPool3.pop();
    }
    private void releaseArrayListToPool3(final HashMap<Integer, Process> arrayList) {
    	arrayList.clear();
        arrayListPool3.add(arrayList);
    }
    /**
     * Sets the currentMainChainHead based on the provided HashSet of confirmed blocks.
     *
     * @param confirmedBlocks The HashSet of confirmed blocks to determine the currentMainChainHead.
     */
    public void setCurrentMainChainHead(final HashSet<B> confirmedBlocks) {
        B maxHeightBlock = null;
        int maxHeight = Integer.MIN_VALUE;

        for (B block : confirmedBlocks) {
            int height = block.getHeight();
            if (height > maxHeight) {
                maxHeight = height;
                maxHeightBlock = block;
            }
        }
        this.currentMainChainHead = maxHeightBlock;

    }
    /**
     * Retrieves a random neighbor node for the Node Cache Protocol (NCP).
     * The function getNode() 
     * @param peer The reference node used to obtain neighboring nodes.
     * @param randomnessEngine The engine providing randomness for node selection.
     * @return A neighboring BECPNode selected randomly for gossiping.
     */
    private BECPNode getRandomNeighbor(final BECPNode peer, final RandomnessEngine randomnessEngine) {
    	BECPNode randomNeighbor;
    	ArrayList<BECPNode> cache = peer.getNeighborsLocalCache();
    	List<BECPNode> filteredCache = cache.stream().filter(node -> node.nodeID!=peer.nodeID).collect(Collectors.toList());

    	if (filteredCache.isEmpty()) {
    		System.out.println("No valid neighbors available!");
    		return null;
    	}

    	randomNeighbor = filteredCache.get(randomnessEngine.nextInt(filteredCache.size()));
    	return randomNeighbor;
    }

    public ArrayList<BECPNode> union(final ArrayList<BECPNode> list1, final BECPNode node) {
        // Create a HashSet from list1.
        HashSet<BECPNode> set = new HashSet<>(list1);
        // Add elements from list2 to the set and result list.
        ArrayList<BECPNode> result = new ArrayList<>(list1);
        if (!set.contains(node)) {
            result.add(node);
            set.add(node);
        }

        return result;
    }

    public ArrayList<BECPNode> union(final ArrayList<BECPNode> list1, final ArrayList<BECPNode> list2) {
        // Create a HashSet from list1.
        HashSet<BECPNode> set = new HashSet<>(list1);
        // Add elements from list2 to the set and result list.
        ArrayList<BECPNode> result = new ArrayList<>(list1);
        for (BECPNode element:list2) {
            if (!set.contains(element)) {
                result.add(element);
                set.add(element);
            }
        }

        return result;
    }
	/**
	 * Trims the local cache of neighbors for a BECPNode to the specified maximum size.
	 *
	 * @param maxSize              The maximum size to which the local cache should be trimmed.
	 * @param neighborsLocalCache  The ArrayList containing neighbors to be trimmed.
	 */
    private void trimCache(final int maxSize, final ArrayList<BECPNode> neighborsLocalCache, final RandomnessEngine randomnessEngine) {
        while (neighborsLocalCache.size() > maxSize){
            int randomIndex = randomnessEngine.nextInt(neighborsLocalCache.size()); // pick a random node to remove.
            neighborsLocalCache.remove(randomIndex);
        }
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

    @Override
    public boolean isBlockFinalized(B block) {
        return false;
    }

    @Override
    public boolean isTxFinalized(T tx) {
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

    public double getNumAllParticipants() {
        return this.numOfParticipants;
    }

    @Override
    protected void updateChain() {
        // nothing for this consensus algorithm
    }
}
