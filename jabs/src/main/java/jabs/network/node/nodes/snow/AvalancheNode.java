package jabs.network.node.nodes.snow;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

import jabs.consensus.algorithm.Avalanche;
import jabs.consensus.blockchain.LocalTxDAG;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.snow.AvalancheTx;
import jabs.ledgerdata.snow.SnowBlock;
import jabs.network.networks.Network;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.PeerDLTNode;
import jabs.network.p2p.SnowP2P;
import jabs.simulator.Simulator;

public class AvalancheNode extends PeerDLTNode<SnowBlock, AvalancheTx> {
    public static final AvalancheTx AVALANCHE_GENESIS_TX =
            new AvalancheTx(0, 0, 0, null, null);
    public AvalancheNode(Simulator simulator, Network network, int nodeID, long downloadBandwidth, long uploadBandwidth, int numAllParticipants) {
        super(simulator, network, nodeID, downloadBandwidth, uploadBandwidth,
                new SnowP2P(),
                new Avalanche<>(new LocalTxDAG(), numAllParticipants)
        );
        this.consensusAlgorithm.setNode(this);
        this.localLedger = new LinkedHashSet<>();
        this.selectedTx = AVALANCHE_GENESIS_TX;
        this.lastConfirmedTx = AVALANCHE_GENESIS_TX; 
        this.lastGeneratedTX = AVALANCHE_GENESIS_TX;
        this.knownTransactions.add(AVALANCHE_GENESIS_TX);
    }
    private final LinkedHashSet<AvalancheTx> localLedger; // the local ledger of the node, or the blockchain.
    private double startTime; //start simulation time of the node.
    private AvalancheTx selectedTx;
    private int cycleNumber;
    private AvalancheTx lastGeneratedTX; //the last generated local block.
    private AvalancheTx lastConfirmedTx; // the last confirmed transaction.
    private HashSet<AvalancheTx> knownTransactions = new HashSet<>();
    private HashSet<AvalancheTx> queriedTransactions = new HashSet<>();
    private double lastTriggeredTime; 
    public boolean isCrashed;
    
	public AvalancheTx getSelectedTx() {
		return this.selectedTx;
	}

	public void setSelectedTx(AvalancheTx currentTx) {
		this.selectedTx = currentTx;
	}
	
	public AvalancheTx getLastConfirmedTx() {
		return this.lastConfirmedTx;
	}

	public void setLastConfirmedTx(AvalancheTx lastConfirmedTx) {
		this.lastConfirmedTx = lastConfirmedTx;
	}

	public int getCycleNumber() {
		return this.cycleNumber;
	}

	public void addCycleNumber(int cycleNumber) {
		this.cycleNumber = this.cycleNumber + cycleNumber;
	}
	
	public AvalancheTx getNotQueriedTx() {
		Iterator<AvalancheTx> iterator = knownTransactions.iterator();
		while (iterator.hasNext()) {
			AvalancheTx element = iterator.next();
			if(!queriedTransactions.contains(element)) {
				return element;
			}
		}
		return null;
	}
	
	public void addQueriedTx(AvalancheTx queriedTx) {
		this.queriedTransactions.add(queriedTx);
	}
	
	public HashSet<AvalancheTx> getknownTransactions() {
		return knownTransactions;
	}
    public void setLastTriggeredTime(double lastTriggeredTime) {
        this.lastTriggeredTime = lastTriggeredTime;
    }

    public double getLastTriggeredTime(){return lastTriggeredTime;}
	
    @Override
    protected void processNewTx(AvalancheTx tx, Node from) {
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
        ((Avalanche<SnowBlock, AvalancheTx>) this.consensusAlgorithm).newIncomingQuery(query);
    }
    @Override
    public void generateNewTransaction() {
        // nothing for now
    }

	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	public AvalancheTx getLastGeneratedTX() {
		return lastGeneratedTX;
	}

	public void setLastGeneratedTX(AvalancheTx lastGeneratedTX) {
		this.lastGeneratedTX = lastGeneratedTX;
	}

	public LinkedHashSet<AvalancheTx> getLocalLedger() {
		return localLedger;
	}
}
