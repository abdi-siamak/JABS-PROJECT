package jabs.network.node.nodes.raft;
import jabs.consensus.blockchain.LocalBlockTree; 

import jabs.consensus.algorithm.RAFT;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.raft.RAFTBlock;
import jabs.ledgerdata.raft.RAFTTx;
import jabs.network.networks.Network;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.network.node.nodes.Node;
import jabs.network.p2p.RAFTP2P;
import jabs.simulator.Simulator;

import java.util.LinkedHashSet;

public class RAFTNode extends PeerBlockchainNode<RAFTBlock, RAFTTx> {
                        public static final RAFTBlock RAFT_GENESIS_BLOCK =
            new RAFTBlock(0, 0, 0, null, null);
                        
    private int cycleNumber = 0; //initial cycle number for the node.
    private int lastConfirmedBlockID; //the ID of the last confirmed block in the blockchain.
    private final LinkedHashSet<RAFTBlock> localLedger; // the local ledger of the node or the blockchain.
    private double lastGeneratedBlockTime; //the time of the last generated block.
    private RAFTBlock lastBlock; //the last local block.
    private double electionTimeout;
    public NodeState nodeState;
	public boolean isReceivedHeartbeat;
	public boolean isVoted;
	public boolean isCrashed;
	public boolean shouldSentFirstProposal;
    
    public RAFTNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new RAFTP2P(),
                new RAFT<>(new LocalBlockTree<>(RAFT_GENESIS_BLOCK))
        );
        this.consensusAlgorithm.setNode(this);
        this.localLedger = new LinkedHashSet<>();
        this.nodeState = NodeState.FOLLOWER;
        this.lastBlock = RAFT_GENESIS_BLOCK;
        this.lastConfirmedBlockID = 0;
    }
    
    public enum NodeState {
    	FOLLOWER,
        CANDIDATE,
        LEADER
    }
    
	public int getCycleNumber() {
		return cycleNumber;
	}
	
	public void resetCycleNumber() {
		this.cycleNumber = 0;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}
    
    @Override
    protected void processNewTx(RAFTTx tx, Node from) {
        // nothing for now
    }

    @Override
    protected void processNewBlock(RAFTBlock block) {
        // nothing for now
    }

    @Override
    protected void processNewVote(Vote vote) {
        ((RAFT<RAFTBlock, RAFTTx>) this.consensusAlgorithm).newIncomingVote(vote);
    }

    @Override
    protected void processNewGossip(Gossip gossip) {

    }

    @Override
    protected void processNewQuery(Query query) {

    }

    @Override
    public void generateNewTransaction() {
        // nothing for now
    }
    
	public LinkedHashSet<RAFTBlock> getLocalLedger() {
		return localLedger;
	}

	public void addToLocalLedger(RAFTBlock block) {
		this.localLedger.add(block);
	}
	
	public int getLastConfirmedBlockID() {
		return lastConfirmedBlockID;
	}

	public void setLastConfirmedBlockID(int lastConfirmedBlockID) {
		this.lastConfirmedBlockID = lastConfirmedBlockID;
	}

	public double getElectionTimeout() {
		return electionTimeout;
	}

	public void setElectionTimeout(double electionTimeout) {
		this.electionTimeout = electionTimeout;
	}

	public double getLastGeneratedBlockTime() {
		return lastGeneratedBlockTime;
	}

	public void setLastGeneratedBlockTime(double lastGeneratedBlockTime) {
		this.lastGeneratedBlockTime = lastGeneratedBlockTime;
	}

	public RAFTBlock getLastBlock() {
		return lastBlock;
	}

	public void setLastBlock(RAFTBlock lastBlock) {
		this.lastBlock = lastBlock;
	}
}