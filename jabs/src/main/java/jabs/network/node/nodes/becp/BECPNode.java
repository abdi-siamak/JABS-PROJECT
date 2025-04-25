package jabs.network.node.nodes.becp;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.becp.*;
import jabs.ledgerdata.becp.Process;
import jabs.network.networks.Network;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.network.node.nodes.Node;
import jabs.network.p2p.BECPP2P;
import jabs.simulator.Simulator;

import java.util.ArrayList;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class BECPNode extends PeerBlockchainNode<BECPBlock, BECPTx>{
	private final LinkedHashSet<BECPBlock> localLedger; // the local ledger of the node, or the blockchain.
    private BECPBlock currentPreferredBlock; //the current preferred block in the local block cache.
    private BECPBlock lastConfirmedBlock; //the last confirmed block in the blockchain.
    private double lastTriggeredTime; 
    private double value; //(SSEP && REAP)
    private double weight; //(SSEP && REAP)
    private double vDataAggregation; //(ECP protocol)
    private double wDataAggregation; //(ECP protocol)
    private double vDataConvergence; //(ECP protocol)
    private double vDataAgreement; //(ECP protocol)
    private double weightValue; //(ECP protocol)
    private int leader; //(ECP protocol)
    private int cycleNumber = 0; //initial cycle number for the node.
    private double startTime; //start simulation time of the node.
    private ArrayList<BECPNode> neighborsLocalCache; //(NCP protocol)
    private Multimap<BECPNode, Integer> mainNeighborsCache; // keys:[nodeID, createdTime]-(EMP+ protocol)
    private Multimap<BECPNode, Integer> reserveCache; // keys:[nodeID, createdTime]-(EMP+ protocol)
    private Multimap<BECPNode, Integer> historyCache; // keys:[nodeID, createdTime]-(EMP+ protocol)
    private HashMap<Integer, BECPBlock> blockLocalCache; //blockID -> block (PTP & ECP)
    private boolean criticalPushFlag; //(REAP protocol)
    private boolean convergenceFlag; //(REAP protocol)
    private ArrayList<PushEntry> pushEntriesBuffer; //(REAP & REAP+ protocols)
    private HashMap<Key, RecoveryEntry> recoveryCache; //keys:[Sender's Id, cycleNumber]-(REAP & REAP+ protocols)
    private Queue<ArrayList<Double>> reapQueue; //(REAP)-the estimations queue.
    private Queue<ArrayList<Double>> ecpQueue; //(ECP)-the estimations queue.
    private ArrayList<ForwardMessage> forwardMessages; 
    private HashMap<Integer, Process> P; //(ARP)-process: pi.
    private A A; //(ARP)-the process Ai.
    private C C; //(ARP)-the process Ci.
    private int l; //(ARP)-the epoch identifier.
    private Queue<Double> arpQueue; //(ARP)-the queue Qi.    
	private BECPNode.State state;
	public boolean isCrashed; 
	public boolean messageInterleaving; //(EMP+ protocol)
	private int joinCycle;
	private HashSet<BECPNode> crashedNodes = new HashSet<>(); // (REAP+)
	private HashSet<BECPNode> joinedNodes = new HashSet<>(); // (REAP+)
	
    public static final BECPBlock BECP_GENESIS_BLOCK =
            new BECPBlock( 0, 0, 0, 0, null, 0, null, BECPBlock.State.COMMIT, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public BECPNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth, double value, double weight, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new BECPP2P(),
                new BECP<>(new LocalBlockTree<>(BECP_GENESIS_BLOCK))
        );
        this.consensusAlgorithm.setNode(this);
        this.value = value;
        this.weight = weight;
        this.blockLocalCache = new HashMap<>();
        this.neighborsLocalCache = new ArrayList<>(); // is initialized while populating the network.
        this.localLedger = new LinkedHashSet<>();
        this.setLastConfirmedBlock(BECP_GENESIS_BLOCK);
        this.currentPreferredBlock = BECP_GENESIS_BLOCK;
        if(BECP.ECP) {
        	this.state = BECPNode.State.AGGREGATION;
            this.vDataAggregation=vDataAggregation;
            this.wDataAggregation=wDataAggregation;
            this.vDataConvergence=vDataConvergence;
            this.vDataAgreement=vDataAgreement;
            this.weightValue=weightValue;
            this.leader=this.getNodeID();
            this.ecpQueue=new ArrayBlockingQueue<>(BECP.QUEUE_SIZE);
        }
        if(BECP.REAP||BECP.REAP_PLUS){
        	this.criticalPushFlag=false;
        	this.convergenceFlag=false;
        	this.pushEntriesBuffer = new ArrayList<>();
        	this.recoveryCache = new HashMap<>();
        	this.reapQueue=new ArrayBlockingQueue<>(BECP.QUEUE_SIZE);
        }
        if(BECP.ARP) {
        	setL(0); //set the initial epoch identifier to zero.
        	this.P = new HashMap<>();
        	for(int p=1;p<=BECP.NUMBER_OF_PROCESSES;p++) {
        		this.P.put(p, new Process(Integer.MAX_VALUE, 0.0, 0.0));
        	}
        	this.arpQueue = new ArrayBlockingQueue<>(BECP.QUEUE_SIZE);
        	this.A = new A(Integer.MAX_VALUE, 0.0, 0.0);
        	this.C = new C(Integer.MAX_VALUE, 0.0, 0.0);
        }
        if(BECP.EMP_PLUS) {
        	this.mainNeighborsCache = ArrayListMultimap.create();
        	this.reserveCache = ArrayListMultimap.create();
        	this.historyCache = ArrayListMultimap.create();
        	this.forwardMessages = new ArrayList<>();
        }
    }
    
    public enum State{
    	AGGREGATION,
    	CONVERGENCE,
    	AGREEMENT,
    	COMMIT,
    	CONSENSUS
    }
    
    public double getValue() {
        return value;
    }

    public double getWeight() {
        return weight;
    }

    public void setValue(double v) {
        this.value = v;
    }

    public void setWeight(double w) {
        this.weight = w;
    }

    public ArrayList<BECPNode> getNeighborsLocalCache() { return neighborsLocalCache; }

    public void setNeighborsLocalCache(ArrayList<BECPNode> neighborsLocalCache){ this.neighborsLocalCache = neighborsLocalCache; }
    
    public Multimap<BECPNode, Integer> getMainCache() { return mainNeighborsCache; }
    
    public void setMainCache(Multimap<BECPNode, Integer> mainNeighborsCache) {this.mainNeighborsCache = mainNeighborsCache; }

    public void setBlockLocalCache(HashMap<Integer, BECPBlock> blockLocalCache){this.blockLocalCache = blockLocalCache; }

    public HashMap<Integer, BECPBlock> getBlockLocalCache(){ return blockLocalCache; }
    
    /**
     * Retrieves an estimated value from the SSEP, REAP, REAP+ (the system size in the Protocol).
     * 
     * @return The estimated value calculated based on the internal value and weight.
     */
    public double getEstimation() { 
    	return (value/weight)-crashedNodes.size()+joinedNodes.size(); 
    } 
    
    public boolean getCriticalPushFlag(){
        return criticalPushFlag;
    }
    public HashMap<Key, RecoveryEntry> getRecoveryCache(){ return recoveryCache; }
    public Queue<ArrayList<Double>> getReapQueue(){return reapQueue; }
    public void setConvergenceFlag(boolean flag){this.convergenceFlag=flag; }
    public void setCriticalPushFlag(boolean flag){this.criticalPushFlag=flag; }
    public boolean getConvergenceFlag(){return convergenceFlag; }
    public ArrayList<PushEntry> getPushEntriesBuffer(){return pushEntriesBuffer; }
    public double getLastTriggeredTime(){return lastTriggeredTime;}

    public void setLastTriggeredTime(double lastTriggeredTime) {
        this.lastTriggeredTime = lastTriggeredTime;
    }

	public BECPBlock getLastConfirmedBlock() {
		return lastConfirmedBlock;
	}

	public void setLastConfirmedBlock(BECPBlock lastConfirmedBlock) {
		this.lastConfirmedBlock = lastConfirmedBlock;
	}

	public int getCycleNumber() {
		return cycleNumber;
	}
	
	public void setCycleNumber(int cycle) {
		this.cycleNumber = cycle;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}

	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	@Override
	protected void processNewBlock(BECPBlock block) {
		// TODO Auto-generated method stub
	}

	public Queue<ArrayList<Double>> getECPQueue() {
		return ecpQueue;
	}

	public double getVDataAggregation() {
		return vDataAggregation;
	}

	public void setVDataAggregation(double vDataAggregation) {
		this.vDataAggregation = vDataAggregation;
	}

	public double getWDataAggregation() {
		return wDataAggregation;
	}

	public void setWDataAggregation(double wDataAggregation) {
		this.wDataAggregation = wDataAggregation;
	}

	public double getVDataConvergence() {
		return vDataConvergence;
	}

	public void setVDataConvergence(double vConvergence) {
		this.vDataConvergence = vConvergence;
	}

	public double getVDataAgreement() {
		return vDataAgreement;
	}

	public void setVDataAgreement(double vAgreement) {
		this.vDataAgreement = vAgreement;
	}

	public double getWeightValue() {
		return weightValue;
	}

	public void setWeightValue(double weightValue) {
		this.weightValue = weightValue;
	}

	public int getLeader() {
		return leader;
	}

	public void setLeader(int leader) {
		this.leader = leader;
	}

	public void setState(BECPNode.State state){this.state = state; }
    public BECPNode.State getState(){
        return state;
    }

	public Queue<Double> getArpQueue() {
		return arpQueue;
	}

	public int getL() {
		return l;
	}

	public void setL(int l) {
		this.l = l;
	}
	public HashMap<Integer, Process> getP(){
		return P;
	}
	public A getA(){
		return A;
	}
	public C getC(){
		return C;
	}

	public LinkedHashSet<BECPBlock> getLocalLedger() {
		return localLedger;
	}

	public void addToLocalLedger(BECPBlock block) {
		this.localLedger.add(block);
	}
	
	@Override
    protected void processNewTx(BECPTx tx, Node from) {
        // nothing for now
    }

    @Override
    protected void processNewVote(Vote vote) {

    }

    @Override
    protected void processNewGossip(Gossip gossip) {
        ((BECP<BECPBlock, BECPTx>) this.consensusAlgorithm).newIncomingGossip(gossip);
    }

    @Override
    protected void processNewQuery(Query query) {

    }

    @Override
    public void generateNewTransaction() {
        // nothing for now
    }

	public BECPBlock getCurrentPreferredBlock() {
		return currentPreferredBlock;
	}

	public void setCurrentPreferredBlock(BECPBlock currentPreferredBlock) {
		this.currentPreferredBlock = currentPreferredBlock;
	}

	public HashSet<BECPNode> getCrashedNodes() {
		return crashedNodes;
	}

	public HashSet<BECPNode> getJoinedNodes() {
		return joinedNodes;
	}

	public int getJoinCycle() {
		return joinCycle;
	}

	public void setJoinCycle(int joinCycle) {
		this.joinCycle = joinCycle;
	}

	public Multimap<BECPNode, Integer> getReserveCache() {
		return reserveCache;
	}

	public void setReserveCache(Multimap<BECPNode, Integer> reserveCache) {
		this.reserveCache = reserveCache;
	}

	public Multimap<BECPNode, Integer> getHistoryCache() {
		return historyCache;
	}

	public void setHistoryCache(Multimap<BECPNode, Integer> historyCache) {
		this.historyCache = historyCache;
	}

	public ArrayList<ForwardMessage> getForwardMessages() {
		return forwardMessages;
	}
}