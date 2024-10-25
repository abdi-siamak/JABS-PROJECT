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
        this.lastConfirmedBlockID = 0;
    }

    private SnowBlock currentBlock;
    
    private SnowBlock lastBlock;
    private boolean isDecided;
    private int cycleNumber;
    private int lastConfirmedBlockID; // the ID of the last confirmed block
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

	public boolean isDecided() {
		return isDecided;
	}

	public void setDecided(boolean isDecided) {
		this.isDecided = isDecided;
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
	
	public int getLastConfirmedBlockID() {
		return lastConfirmedBlockID;
	}

	public void setLastConfirmedBlockID(int lastConfirmedBlockID) {
		this.lastConfirmedBlockID = lastConfirmedBlockID;
	}

	public int getCycleNumber() {
		return cycleNumber;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}

}
