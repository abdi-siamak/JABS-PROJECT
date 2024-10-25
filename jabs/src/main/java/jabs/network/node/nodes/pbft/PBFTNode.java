package jabs.network.node.nodes.pbft;
import jabs.consensus.blockchain.LocalBlockTree;

import java.util.LinkedHashSet;

import jabs.consensus.algorithm.PBFT;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.pbft.PBFTBlock;
import jabs.ledgerdata.pbft.PBFTTx;
import jabs.network.networks.Network;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.network.node.nodes.Node;
import jabs.network.p2p.PBFTP2P;
import jabs.simulator.Simulator;

public class PBFTNode extends PeerBlockchainNode<PBFTBlock, PBFTTx> {
    public static final PBFTBlock PBFT_GENESIS_BLOCK = new PBFTBlock(0, 0, 0, null, null);
    public boolean isCrashed;
    private int cycleNumber = 0; //initial cycle number for the node.
    private int lastConfirmedBlockID; //the ID of the last confirmed block in the blockchain.
    private final LinkedHashSet<PBFTBlock> localLedger; // the local ledger of the node or the blockchain. 
    private PBFTBlock lastBlock; //the last local block.
    
    public PBFTNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth, int numAllParticipants) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new PBFTP2P(),
                new PBFT<>(new LocalBlockTree<>(PBFT_GENESIS_BLOCK), numAllParticipants)
        );
        this.consensusAlgorithm.setNode(this);
        this.localLedger = new LinkedHashSet<>();
        this.lastBlock = PBFT_GENESIS_BLOCK;
        this.lastConfirmedBlockID = 0;
    }
    
	public int getCycleNumber() {
		return cycleNumber;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}

	public LinkedHashSet<PBFTBlock> getLocalLedger() {
		return localLedger;
	}

	public void addToLocalLedger(PBFTBlock block) {
		this.localLedger.add(block);
	}
	
	public int getLastConfirmedBlockID() {
		return lastConfirmedBlockID;
	}

	public void setLastConfirmedBlockID(int lastConfirmedBlockID) {
		this.lastConfirmedBlockID = lastConfirmedBlockID;
	}
	
	public PBFTBlock getLastBlock() {
		return lastBlock;
	}

	public void setLastBlock(PBFTBlock lastBlock) {
		this.lastBlock = lastBlock;
	}
    
    @Override
    protected void processNewTx(PBFTTx tx, Node from) {
        // nothing for now
    }

    @Override
    protected void processNewBlock(PBFTBlock block) {
        // nothing for now
    }

    @Override
    protected void processNewVote(Vote vote) {
        ((PBFT<PBFTBlock, PBFTTx>) this.consensusAlgorithm).newIncomingVote(vote);
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
}
