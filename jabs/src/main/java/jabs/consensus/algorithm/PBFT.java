package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.ledgerdata.*;
import jabs.ledgerdata.pbft.*;
import jabs.network.message.VoteMessage;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.pbft.PBFTNode;
import jabs.scenario.PBFTScenario;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

// based on: https://sawtooth.hyperledger.org/docs/pbft/nightly/master/architecture.html
// another good source: http://ug93tad.github.io/pbft/

public class PBFT<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements VotingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {

    //-------------------------------------------------------------------------------------------------------
    public static final boolean GENERATE_BLOCKS = true; //should nodes generate blocks continuously?
    private static final int MAX_NUM_BLOCKS = 99999999; //the maximum block number nodes should generate.
    public static final boolean GENERATE_IMMEDIATELY = false; // should generate blocks immediately after reaching a consensus on a block?
    private static final int BLOCK_INTERVAL = 10; //the interval between two block generations in seconds.
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //-------------------------------------------------------------------------------------------------------
    File directory = PBFTScenario.directory;
    private static PrintWriter writer;
    private final int numAllParticipants;
    private final HashMap<B, HashMap<Node, Vote>> prepareVotes = new HashMap<>();
    private final HashMap<B, HashMap<Node, Vote>> commitVotes = new HashMap<>();
    private final HashSet<B> preparedBlocks = new HashSet<>();
    private final HashSet<B> committedBlocks = new HashSet<>();
    private int currentViewNumber = 0;

    // TODO: View change should be implemented

    private PBFTMode pbftMode = PBFTMode.NORMAL_MODE;
    private PBFTPhase pbftPhase = PBFTPhase.PRE_PREPARING;

    public enum PBFTMode {
        NORMAL_MODE,
        VIEW_CHANGE_MODE
    }

    public enum PBFTPhase {
        PRE_PREPARING,
        PREPARING,
        COMMITTING
    }

    public PBFT(LocalBlockTree<B> localBlockTree, int numAllParticipants) {
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
    }

    public void newIncomingVote(Vote vote) {
        if (vote instanceof PBFTBlockVote) { // for the time being, the view change votes are not supported
            PBFTBlockVote<B> blockVote = (PBFTBlockVote<B>) vote;
            PBFTNode peer = (PBFTNode)this.peerBlockchainNode;
            B block = blockVote.getBlock();
            switch (blockVote.getVoteType()) {
                case PRE_PREPARE :
                	/*
                    if (!this.localBlockTree.contains(block)) {
                        this.localBlockTree.add(block);
                    }
                    if (this.localBlockTree.getLocalBlock(block).isConnectedToGenesis) {
                        this.pbftPhase = PBFTPhase.PREPARING;
                        this.peerBlockchainNode.broadcastMessage(
                                new VoteMessage(
                                        new PBFTPrepareVote<>(this.peerBlockchainNode, blockVote.getBlock())
                                )
                        );
                    }
                    */
                	if(PBFTScenario.UNIFORM_CRASH) {
                		HashMap<PBFTNode, Double> nodesToBeCrashed = PBFTScenario.getNodesToBeCrashed();
                		if(nodesToBeCrashed.containsKey(peer)) {
            				if(nodesToBeCrashed.get(peer)<=peer.getSimulator().getSimulationTime()) {
            					peer.crash();
                    			peer.isCrashed=true;
                    			//System.out.println("Node "+peer.nodeID+ " was crashed!");
                    			break;
            				}
            			}
                	}
                	peer.addCycleNumber(1);
                    this.pbftPhase = PBFTPhase.PREPARING;
                    this.peerBlockchainNode.broadcastMessage(
                            new VoteMessage(
                                    new PBFTPrepareVote<>(this.peerBlockchainNode, blockVote.getBlock())
                            )
                    );
                    break;
                case PREPARE:
                    checkVotes(peer, blockVote, block, prepareVotes, preparedBlocks, PBFTPhase.COMMITTING);
                    break;
                case COMMIT:
                    checkVotes(peer, blockVote, block, commitVotes, committedBlocks, PBFTPhase.PRE_PREPARING);
                    break;
            }
        }
    }

    private void checkVotes(PBFTNode peer, PBFTBlockVote<B> vote, B block, HashMap<B, HashMap<Node, Vote>> votes, HashSet<B> blocks, PBFTPhase nextStep) {
        if (!blocks.contains(block)) {
            if (!votes.containsKey(block)) { // this the first vote received for this block
                votes.put(block, new HashMap<>());
            }
            votes.get(block).put(vote.getVoter(), vote);
            if (votes.get(block).size() > (((numAllParticipants / 3) * 2) + 1)) {
                blocks.add(block);
                this.pbftPhase = nextStep;
                switch (nextStep) {
                    case PRE_PREPARING:
                        this.currentViewNumber += 1;
                        this.currentMainChainHead = block;
                    	peer.setLastConfirmedBlockID(block.getHeight());
                    	peer.setLastBlock((PBFTBlock)block);
                    	peer.addToLocalLedger((PBFTBlock)block);
                        updateChain();
                    	PBFTScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-this.currentMainChainHead.getCreationTime()); // record the consensus time.
                        writer.println("Consensus occurred in node " + this.peerBlockchainNode.getNodeID() + " for the block " + block.getHeight() + " at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                        writer.flush();
                        if((this.peerBlockchainNode.nodeID == this.getCurrentPrimaryNumber())&&(GENERATE_BLOCKS)&&((peer.getLastConfirmedBlockID()<MAX_NUM_BLOCKS)&&!peer.isCrashed)) {
                        	if (GENERATE_IMMEDIATELY) {
                              	peer.broadcastMessage(
                                        new VoteMessage(
                                                new PBFTPrePrepareVote<>(peer, BlockFactory.samplePBFTBlock(peer.getSimulator(), peer.getNetwork().getRandom(),
                                            			peer, peer.getLastBlock()))
                                        )
                                );
                        	}else {
                        		this.peerBlockchainNode.getSimulator().incrementSimulationDuration(BLOCK_INTERVAL);
                        	 	peer.broadcastMessage(
                                        new VoteMessage(
                                                new PBFTPrePrepareVote<>(peer, BlockFactory.samplePBFTBlock(peer.getSimulator(), peer.getNetwork().getRandom(),
                                            			peer, peer.getLastBlock()))
                                        )
                                );
                        	}
                        	//System.out.println("------------NEW CYCLE STARTED at Node " + peer.getNodeID() + "!--------------");
                        }
                        break;
                    case COMMITTING:
                        this.peerBlockchainNode.broadcastMessage(
                                new VoteMessage(
                                        new PBFTCommitVote<>(this.peerBlockchainNode, block)
                                )
                        );
                        break;
                }
            }
        }
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
    public void newIncomingBlock(B block) {

    }

    /**
     * @param block
     * @return
     */
    @Override
    public boolean isBlockConfirmed(B block) {
        return false;
    }

    /**
     * @param block
     * @return
     */
    @Override
    public boolean isBlockValid(B block) {
        return false;
    }

    public int getCurrentViewNumber() {
        return this.currentViewNumber;
    }

    public int getCurrentPrimaryNumber() {
        return (this.currentViewNumber % this.numAllParticipants);
    }

    public int getNumAllParticipants() {
        return this.numAllParticipants;
    }

    public PBFTPhase getPbftPhase() {
        return this.pbftPhase;
    }

    @Override
    protected void updateChain() {
        this.confirmedBlocks.add(this.currentMainChainHead);
    }
}
