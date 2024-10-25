package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockTree;
import jabs.ledgerdata.*;
import jabs.ledgerdata.paxos.*;
import jabs.network.message.VoteMessage;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.paxos.PAXOSNode;
import jabs.scenario.PAXOSScenario;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

public class PAXOS<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements VotingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {

    //-------------------------------------------------------------------------------------------------------
    public static final boolean GENERATE_BLOCKS = true; //should nodes generate blocks continuously?
    private static final int MAX_NUM_BLOCKS = 9999999; //the maximum block number nodes should work.
    public static final boolean GENERATE_IMMEDIATELY = false; // should generate blocks immediately after reaching a consensus on a block?
    private static final int BLOCK_INTERVAL = 10; //the interval between two block generations in seconds.
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //-------------------------------------------------------------------------------------------------------
    File directory = PAXOSScenario.directory;
    private static PrintWriter writer;
    private final int numAllParticipants;
    private final HashMap<B, HashMap<Node, Vote>> prepareVotes = new HashMap<>();
    private final HashMap<B, HashMap<Node, Vote>> commitVotes = new HashMap<>();
    private final HashSet<B> preparedBlocks = new HashSet<>();
    private final HashSet<B> committedBlocks = new HashSet<>();
    private int currentConsensusNumber = 0;
    private PAXOSPhase paxosPhase = PAXOSPhase.PROMISING;

    public PAXOS(LocalBlockTree<B> localBlockTree, int numAllParticipants) {
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
        if (vote instanceof PAXOSBlockVote) { 
        	PAXOSNode peer = (PAXOSNode)this.peerBlockchainNode;
        	PAXOSBlockVote<B> blockVote = (PAXOSBlockVote<B>) vote;
            B block = blockVote.getBlock();
            switch (blockVote.getVoteType()) {
                case PREPARE :
                	if(PAXOSScenario.UNIFORM_CRASH) {
                		HashMap<PAXOSNode, Double> nodesToBeCrashed = PAXOSScenario.getNodesToBeCrashed();
                		if(nodesToBeCrashed.containsKey(peer)) {
            				if(nodesToBeCrashed.get(peer)<=peer.getSimulator().getSimulationTime()) {
            					peer.crash();
                    			peer.isCrashed=true;
                    			//System.out.println(peer.isLeader ? "Node " + peer.nodeID + " ,the leader was crashed!" : "Node " + peer.nodeID + " was crashed!");
                    			break;
            				}
            			}
                	}
                	peer.addCycleNumber(1);
                	PAXOSPrepareVote paxosPrepareVote = (PAXOSPrepareVote)blockVote;
                	if(paxosPrepareVote.getN()>peer.getN_p()) {
                		peer.setN_p(paxosPrepareVote.getN());
                		B v_a = (B)peer.getV_a();
                        this.peerBlockchainNode.replyMessage(
                                new VoteMessage(
                                        new PAXOSPrepareCommitVote<>(this.peerBlockchainNode, paxosPrepareVote.getN(), peer.getN_a(), v_a)
                                ),blockVote.getVoter()
                        );
                	}
                    break;
                case PREPARE_OK:
                    checkVotes(peer, blockVote, block, prepareVotes, preparedBlocks, PAXOSPhase.COMMITTING);
                    break;
                case ACCEPT:
                	PAXOSAcceptVote paxosAcceptVote = (PAXOSAcceptVote)blockVote;
                	if(paxosAcceptVote.getN()>=peer.getN_p()) {
                		peer.setN_p(paxosAcceptVote.getN());
                		peer.setN_a(paxosAcceptVote.getN());
                		peer.setV_a((PAXOSBlock)block);
                        this.peerBlockchainNode.replyMessage(
                                new VoteMessage(
                                        new PAXOSAcceptCommitVote<>(this.peerBlockchainNode, paxosAcceptVote.getN())
                                ), blockVote.getVoter()
                        );
                	}
                	break;
                case ACCEPT_OK:
                	checkVotes(peer, blockVote, block, commitVotes, committedBlocks, PAXOSPhase.PROMISING);
                	break;
                case DECIDED:
                	PAXOSDecidedVote paxosDecidedVote = (PAXOSDecidedVote)blockVote;
                	this.currentMainChainHead = (B)paxosDecidedVote.getV_a();
                	currentConsensusNumber += 1;
                	peer.setLastConfirmedBlockID(currentConsensusNumber);
                	peer.addToLocalLedger((PAXOSBlock)paxosDecidedVote.getV_a());
                    updateChain();
                	PAXOSScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-this.currentMainChainHead.getCreationTime()); // record the consensus time.
                    writer.println("Consensus occurred in node " + this.peerBlockchainNode.getNodeID() + " for the block " + this.currentMainChainHead.getHeight() + " at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                    writer.flush();
                    if((this.peerBlockchainNode.nodeID == this.getCurrentPrimaryNumber())&&(GENERATE_BLOCKS)&&((peer.getLastConfirmedBlockID()<MAX_NUM_BLOCKS)&&!peer.isCrashed)) {
                    	peer.isLeader = true;
                    	if (GENERATE_IMMEDIATELY) {
                    	    peer.broadcastMessage(
                    	            new VoteMessage(
                    	                    new PAXOSPrepareVote<>(peer, peer.getN_p() + 1)
                    	            ));
                    	} else {
                    	    this.peerBlockchainNode.getSimulator().incrementSimulationDuration(BLOCK_INTERVAL);
                    	    peer.broadcastMessage(
                    	            new VoteMessage(
                    	                    new PAXOSPrepareVote<>(peer, peer.getN_p() + 1)
                    	            ));
                    	}
                    	//System.out.println("------------NEW CYCLE STARTED at Node " + peer.getNodeID() + "!--------------");
            		}
                    if(this.peerBlockchainNode.nodeID != this.getCurrentPrimaryNumber()){
                    	peer.isLeader = false;
                    }
                    break;
            }
        }
    }

    private void checkVotes(PAXOSNode peer, PAXOSBlockVote<B> vote, B block, HashMap<B, HashMap<Node, Vote>> votes, HashSet<B> blocks, PAXOSPhase nextStep) {
        if (!blocks.contains(block)) {
        	if (!votes.containsKey(block)) { // this the first vote received for this block.
                votes.put(block, new HashMap<>());
            }
            votes.get(block).put(vote.getVoter(), vote);
            if (votes.get(block).size() > (numAllParticipants/2)) {
            	blocks.add(block);
                this.paxosPhase = nextStep;
                switch (nextStep) {
                    case PROMISING:
                        this.peerBlockchainNode.broadcastMessage(
                                new VoteMessage(
                                        new PAXOSDecidedVote<>(this.peerBlockchainNode, peer.getV_a())
                                )
                        );
                        break;
                    case COMMITTING:
                    	PAXOSPrepareCommitVote paxosVote = (PAXOSPrepareCommitVote)vote;
                    	PAXOSBlock highestN_aBlock = findBlockWithHighestN_a(votes);
                    	if(peer.getN_p()+1>highestN_aBlock.getHeight()) {
                    		highestN_aBlock = BlockFactory.samplePAXOSBlock(peer.getSimulator(), peer.getNetwork().getRandom(),
                                    peer, peer.getV_a());
                    	}
                        this.peerBlockchainNode.broadcastMessage(
                                new VoteMessage(
                                        new PAXOSAcceptVote<>(this.peerBlockchainNode, highestN_aBlock, paxosVote.getN())
                                )
                        );
                        break;
                }
                //votes.clear();
            }
        }
    }
    
    private PAXOSBlock findBlockWithHighestN_a(HashMap<B, HashMap<Node, Vote>> votes) {
        PAXOSBlock highestIDBlock = null;
        int highestID = Integer.MIN_VALUE;

        for (HashMap<Node, Vote> innerMap : votes.values()) {
            for (Vote vote : innerMap.values()) {
                if (vote instanceof PAXOSPrepareCommitVote) {
                    PAXOSPrepareCommitVote paxosVote = (PAXOSPrepareCommitVote) vote;
                    int currentID = paxosVote.getN_a();
                    if (currentID > highestID) {
                        highestID = currentID;
                        highestIDBlock = (PAXOSBlock) paxosVote.getV_a();
                    }
                }
            }
        }

        return highestIDBlock;
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

    public enum PAXOSPhase {
    	PROMISING,
        COMMITTING
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
    
    public int getCurrentPrimaryNumber() {
        return (this.currentConsensusNumber % this.numAllParticipants);
    }

    public int getNumAllParticipants() {
        return this.numAllParticipants;
    }

    public PAXOSPhase getPaxosPhase() {
        return this.paxosPhase;
    }

    @Override
    protected void updateChain() {
        this.confirmedBlocks.add(this.currentMainChainHead);
    }
}
