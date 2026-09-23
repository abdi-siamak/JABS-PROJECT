package jabs.network.node.nodes.becp;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Hash;
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
	private HashMap<Integer, MembershipSnapshot> membershipSnapshots; // fixed M_h for each unresolved height
	private HashMap<Integer, Hash> persistentFinalVotes; // V_i(h): durable final vote per height
    private HashSet<Integer> unavailableFinalVoteHeights; // heights whose durable vote state could not be restored
	private HashMap<Integer, HashMap<Hash, HashSet<Integer>>> finalConfirmations;
    private boolean criticalPushFlag; //(REAP protocol)
    private boolean convergenceFlag; //(REAP protocol)
    private ArrayList<PushEntry> pushEntriesBuffer; //(REAP & REAP+ protocols)
    private HashMap<Key, RecoveryEntry> recoveryCache; //keys:[Sender's Id, cycleNumber]-(REAP & REAP+ protocols)
	private HashMap<RecoveryExchangeId, RecoveryEntry> recoveryExchangeCache; // REAP+ exact exchange tracking
	private long recoveryExchangeSequence = 0; // persistent sender-local sequence for unique REAP+ exchange IDs
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
		this.membershipSnapshots = new HashMap<>();
		this.persistentFinalVotes = new HashMap<>();
		this.unavailableFinalVoteHeights = new HashSet<>();
		this.finalConfirmations = new HashMap<>();
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
			this.recoveryExchangeCache = new HashMap<>();
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

	public HashMap<Integer, MembershipSnapshot> getMembershipSnapshots() {
    return membershipSnapshots;
}

	public MembershipSnapshot getMembershipSnapshot(int height) {
		return membershipSnapshots.get(height);
	}

	public MembershipSnapshot getOrCreateMembershipSnapshot(int height) {
		MembershipSnapshot existingSnapshot = membershipSnapshots.get(height);
		if (existingSnapshot != null) {
			return existingSnapshot;
		}

		HashSet<Integer> memberIds = new HashSet<>();
		for (Object networkNode : this.getNetwork().getAllNodes()) {
			memberIds.add(((Node) networkNode).getNodeID());
		}

		MembershipSnapshot newSnapshot = new MembershipSnapshot(height, memberIds);
		addMembershipSnapshot(newSnapshot);

		return newSnapshot;
	}

	public void addMembershipSnapshot(MembershipSnapshot snapshot) {
		if (snapshot == null) {
			throw new IllegalArgumentException("Membership snapshot cannot be null.");
		}
		int height = snapshot.getHeight();
		MembershipSnapshot existingSnapshot = membershipSnapshots.get(height);

		if (existingSnapshot != null) {
			if (!existingSnapshot.getSnapshotId().equals(snapshot.getSnapshotId())) {
				throw new IllegalStateException("Membership snapshot for height " + height + " cannot be replaced.");
			}

			// Same snapshot already stored: nothing to change.
			return;
		}

		membershipSnapshots.put(height, snapshot);
	}

	public Hash getPersistentFinalVote(int height) {
		return persistentFinalVotes.get(height);
	}

	public boolean hasPersistentFinalVote(int height) {
		return persistentFinalVotes.containsKey(height);
	}

	public boolean isFinalVoteStateUnavailable(int height) {
		return unavailableFinalVoteHeights.contains(height);
	}

	/**
	 * Records V_i(h), the node's one and only final confirmation
	 * for the given height.
	 *
	 * @return true if the vote was recorded for the first time;
	 *         false if the node must not issue another confirmation.
	 */
	public boolean recordPersistentFinalVote(int height, BECPBlock block) {
		if (height < 1) {
			throw new IllegalArgumentException("Final confirmation height must be greater than 0.");
		}

		if (block == null) {
			throw new IllegalArgumentException("Final confirmation block cannot be null.");
		}

		if (block.getHeight() != height) {
			throw new IllegalArgumentException("Final confirmation block height does not match vote height.");
		}

		// If durable state for this height could not be restored, the node is forbidden from issuing another final confirmation.
		if (unavailableFinalVoteHeights.contains(height)) {
			return false;
		}

		Hash existingVote = persistentFinalVotes.get(height);
		if (existingVote != null) {
			// The node already issued its final confirmation for this height.
			if (existingVote == block.getHash()) {
				return false;
			}

			// Attempting to vote for a different block at the same height is a safety violation.
			throw new IllegalStateException(
					"Node "
					+ getNodeID()
					+ " attempted to issue two different final confirmations"
					+ " at height "
					+ height
					+ ".");
		}

		persistentFinalVotes.put(height, block.getHash());

		return true;
	}

	/**
	 * Models a recovery in which V_i(h) cannot be restored.
	 * The node is then permanently prevented from issuing another final confirmation at that height.
	 */
	public void markFinalVoteStateUnavailable(int height) {
		persistentFinalVotes.remove(height);
		unavailableFinalVoteHeights.add(height);
	}

	/**
	 * Records one valid final confirmation.
	 *
	 * Confirmations are grouped by:
	 * height -> block hash -> distinct confirmer node IDs.
	 *
	 * @return true if this confirmer was added for the first time;
	 *         false if the same confirmer was already counted.
	 */
    public boolean recordFinalConfirmation(int height, BECPBlock block, int confirmerNodeId) {
		if (height < 1) {
			throw new IllegalArgumentException("Final confirmation height must be greater than 0.");
		}

		if (block == null) {
			throw new IllegalArgumentException("Final confirmation block cannot be null.");
		}

		if (block.getHeight() != height) {
			throw new IllegalArgumentException("Final confirmation block height does not match confirmation height.");
		}

		Hash blockHash = block.getHash();
		HashMap<Hash, HashSet<Integer>> confirmationsAtHeight = finalConfirmations.computeIfAbsent(height,key -> new HashMap<>());
		/*
		* A member may contribute at most one final confirmation at a given height.
		* If this confirmer already appears under another block
		* hash at the same height, that is an equivocation and
		* therefore a safety violation.
		*/
		for (Hash existingBlockHash : confirmationsAtHeight.keySet()) {
			HashSet<Integer> existingConfirmers = confirmationsAtHeight.get(existingBlockHash);
			if (existingConfirmers.contains(confirmerNodeId) && existingBlockHash != blockHash) {
				throw new IllegalStateException(
						"Safety violation: node "
						+ confirmerNodeId
						+ " issued final confirmations for two "
						+ "different blocks at height "
						+ height
						+ ".");
			}
		}
		HashSet<Integer> confirmingNodes = confirmationsAtHeight.computeIfAbsent(blockHash, key -> new HashSet<>());

		return confirmingNodes.add(confirmerNodeId);
	}

	public int getFinalConfirmationCount(int height, BECPBlock block) {
		if (block == null) {
			return 0;
		}

		HashMap<Hash, HashSet<Integer>> confirmationsAtHeight = finalConfirmations.get(height);

		if (confirmationsAtHeight == null) {
			return 0;
		}

		HashSet<Integer> confirmingNodes = confirmationsAtHeight.get(block.getHash());

		if (confirmingNodes == null) {
			return 0;
		}

		return confirmingNodes.size();
	}

	public boolean hasFinalConfirmationFrom(int height, BECPBlock block, int confirmerNodeId) {
		if (block == null) {
			return false;
		}

		HashMap<Hash, HashSet<Integer>> confirmationsAtHeight = finalConfirmations.get(height);
		if (confirmationsAtHeight == null) {
			return false;
		}

		HashSet<Integer> confirmingNodes = confirmationsAtHeight.get(block.getHash());

		return confirmingNodes != null && confirmingNodes.contains(confirmerNodeId);
	}

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
	public HashMap<RecoveryExchangeId, RecoveryEntry> getRecoveryExchangeCache() {
    	return recoveryExchangeCache;
	}
	public RecoveryExchangeId createRecoveryExchangeId() {
		RecoveryExchangeId exchangeId = new RecoveryExchangeId(getNodeID(), getCycleNumber(), recoveryExchangeSequence);
		recoveryExchangeSequence++;

		return exchangeId;
	}
	public long getRecoveryExchangeSequence() {
		return recoveryExchangeSequence;
	}
	/**
	 * Terminates recovery exchanges that were still PENDING when this
	 * node crashed.
	 * The node's volatile aggregation state is discarded during restart
	 * and reconstructed from another node. Therefore, these old exchanges
	 * must never be allowed to time out later and modify the reconstructed
	 * mass.
	 * Receiver-side entries are retained as RESTORED tombstones so that
	 * delayed duplicate Push/RePush messages cannot reactivate them.
	 *
	 * Sender-side entries are also terminalized before the restart code
	 * removes the old push buffer.
	 */
	public void terminalizePendingRecoveryExchangesForRestart() {
		if (pushEntriesBuffer != null) {
			for (PushEntry pushEntry : pushEntriesBuffer) {
				if (!pushEntry.isRecoveryExchangeTerminal()) {
					pushEntry.transitionRecoveryExchangeState(RecoveryExchangeState.RESTORED);
				}
			}
		}

		if (recoveryExchangeCache != null) {
			for (RecoveryEntry recoveryEntry : recoveryExchangeCache.values()) {
				if (!recoveryEntry.isRecoveryExchangeTerminal()) {
					recoveryEntry.transitionRecoveryExchangeState(RecoveryExchangeState.RESTORED);
				}
			}
		}
	}
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