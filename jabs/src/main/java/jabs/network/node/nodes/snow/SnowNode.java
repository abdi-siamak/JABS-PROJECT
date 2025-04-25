package jabs.network.node.nodes.snow;

import jabs.consensus.algorithm.Snow;
import jabs.consensus.blockchain.LocalBlockTree;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.snow.SnowBlock;
import jabs.ledgerdata.snow.SnowTx;
import jabs.network.networks.Network;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.network.p2p.SnowP2P;
import jabs.simulator.Simulator;

public class SnowNode extends PeerBlockchainNode<SnowBlock, SnowTx> {
    public static final SnowBlock SNOW_GENESIS_BLOCK =
            new SnowBlock(0, 0, 0, null, null);
    
    public SnowNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth, int numAllParticipants) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new SnowP2P(),
                new Snow<>(new LocalBlockTree<>(SNOW_GENESIS_BLOCK), numAllParticipants)
        );
        this.consensusAlgorithm.setNode(this);
        this.currentBlock = SNOW_GENESIS_BLOCK;
        this.lastConfirmedBlock = SNOW_GENESIS_BLOCK;
    	this.isDecided = true;
    	this.roundNumber = 0;
    	this.cycleNumber = 0;
    	this.setLastBlock(SNOW_GENESIS_BLOCK);
    	this.lastGeneratedBlock = SNOW_GENESIS_BLOCK;
    }

    public boolean newRound;
    private int roundNumber;
    private SnowBlock currentBlock;
    private SnowBlock lastBlock;
    public boolean isDecided;
    private int cycleNumber;
    private SnowBlock lastConfirmedBlock; 
    private double startTime; //start simulation time of the node.
    private SnowBlock lastGeneratedBlock;
	public boolean isCrashed;
    
    @Override
    protected void processNewTx(SnowTx tx, Node from) {
        // nothing for now
    }

    @Override
    protected void processNewBlock(SnowBlock block) {
        // nothing for now
    }
    @Override
    protected void processNewVote(Vote vote) {

    }

    @Override
    protected void processNewGossip(Gossip gossip) {

    }

    @Override
    protected void processNewQuery(Query query) {
        ((Snow<SnowBlock, SnowTx>) this.consensusAlgorithm).newIncomingQuery(query);
    }
    @Override
    public void generateNewTransaction() {
        // nothing for now
    }

	public SnowBlock getCurrentBlock() {
		return currentBlock;
	}

	public void setCurrentBlock(SnowBlock currentBlock) {
		this.currentBlock = currentBlock;
	}

	public SnowBlock getLastBlock() {
		return lastBlock;
	}

	public void setLastBlock(SnowBlock lastBlock) {
		this.lastBlock = lastBlock;
	}
	
	public SnowBlock getLastConfirmedBlock() {
		return lastConfirmedBlock;
	}

	public void setLastConfirmedBlock(SnowBlock lastConfirmedBlock) {
		this.lastConfirmedBlock = lastConfirmedBlock;
	}

	public int getCycleNumber() {
		return cycleNumber;
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

	public int getRoundNumber() {
		return roundNumber;
	}

	public void addRoundNumber() {
		this.roundNumber = this.roundNumber + 1;
	}

	public SnowBlock getLastGeneratedBlock() {
		return lastGeneratedBlock;
	}

	public void setLastGeneratedBlock(SnowBlock lastGeneratedBlock) {
		this.lastGeneratedBlock = lastGeneratedBlock;
	}

}
