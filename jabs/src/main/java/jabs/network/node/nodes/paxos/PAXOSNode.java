package jabs.network.node.nodes.paxos;
import jabs.consensus.blockchain.LocalBlockTree; 

import java.util.LinkedHashSet;

import jabs.consensus.algorithm.PAXOS;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.paxos.PAXOSBlock;
import jabs.ledgerdata.paxos.PAXOSTx;
import jabs.network.networks.Network;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.network.node.nodes.Node;
import jabs.network.p2p.PAXOSP2P;
import jabs.simulator.Simulator;

public class PAXOSNode extends PeerBlockchainNode<PAXOSBlock, PAXOSTx> {
                        public static final PAXOSBlock PAXOS_GENESIS_BLOCK =
            new PAXOSBlock(0, 0, 0, null, null);
                        
    private int n_p; // the highest prepare number seen.
    private int n_a; // the highest accept number seen.
    private PAXOSBlock v_a; // the highest accept value (block) seen.
    private int cycleNumber = 0; //initial cycle number for the node.
    private int lastConfirmedBlockID; //the ID of the last confirmed block in the blockchain.
    public boolean isCrashed;
    public boolean isLeader;
    private final LinkedHashSet<PAXOSBlock> localLedger; // the local ledger of the node or the blockchain.
    public PAXOSNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth, int numAllParticipants) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new PAXOSP2P(),
                new PAXOS<>(new LocalBlockTree<>(PAXOS_GENESIS_BLOCK), numAllParticipants)
        );
        this.consensusAlgorithm.setNode(this);
        this.localLedger = new LinkedHashSet<>();
        this.lastConfirmedBlockID = 0;
        n_p = 0;
        n_a = 0;
        v_a = PAXOS_GENESIS_BLOCK;
    }
    
	public int getCycleNumber() {
		return cycleNumber;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}
    
    @Override
    protected void processNewTx(PAXOSTx tx, Node from) {
        // nothing for now
    }

    @Override
    protected void processNewBlock(PAXOSBlock block) {
        // nothing for now
    }

    @Override
    protected void processNewVote(Vote vote) {
        ((PAXOS<PAXOSBlock, PAXOSTx>) this.consensusAlgorithm).newIncomingVote(vote);
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
    
	public LinkedHashSet<PAXOSBlock> getLocalLedger() {
		return localLedger;
	}

	public void addToLocalLedger(PAXOSBlock block) {
		this.localLedger.add(block);
	}
	
	public int getLastConfirmedBlockID() {
		return lastConfirmedBlockID;
	}

	public void setLastConfirmedBlockID(int lastConfirmedBlockID) {
		this.lastConfirmedBlockID = lastConfirmedBlockID;
	}

	public int getN_p() {
		return n_p;
	}

	public void setN_p(int n_p) {
		this.n_p = n_p;
	}

	public int getN_a() {
		return n_a;
	}

	public void setN_a(int n_a) {
		this.n_a = n_a;
	}

	public PAXOSBlock getV_a() {
		return v_a;
	}

	public void setV_a(PAXOSBlock v_a) {
		this.v_a = v_a;
	}
}
