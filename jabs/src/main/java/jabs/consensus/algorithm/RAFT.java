package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockTree; 
import jabs.ledgerdata.*;
import jabs.ledgerdata.raft.*;
import jabs.network.message.VoteMessage;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.raft.RAFTNode;
import jabs.network.node.nodes.raft.RAFTNode.NodeState;
import jabs.scenario.RAFTScenario;
import jabs.simulator.event.HeartbeatEvent;
import jabs.simulator.event.TimeoutEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public class RAFT<B extends SingleParentBlock<B>, T extends Tx<T>> extends AbstractChainBasedConsensus<B, T>
        implements VotingBasedConsensus<B, T>, DeterministicFinalityConsensus<B, T> {

    //-------------------------------------------------------------------------------------------------------
    public static final boolean WRITE_SIMULATION_LOGS = false;
    //-------------------------------------------------------------------------------------------------------
    File directory = RAFTScenario.directory;
    private static PrintWriter writer;
    private static HashMap<Node, HashMap<Node, Vote>> electionVotes = new HashMap<>(); // candidate -> {[voter, vote],...}
    private final HashMap<B, HashMap<Node, Vote>> proposalVotes = new HashMap<>(); // candidate block -> {[voter, vote],...}
    private final HashSet<B> proposalBlocks = new HashSet<>();
    private static HashSet<Node> voters = new HashSet<>();
    private int currentConsensusNumber = 0;
    public RAFTPhase raftPhase = RAFTPhase.LEADER_SELECTION;

    public RAFT(LocalBlockTree<B> localBlockTree) {
        super(localBlockTree);
        electionVotes.clear();
        voters.clear();
        
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

    public void newIncomingVote(Vote message) {
    	RAFTNode peer = (RAFTNode)this.peerBlockchainNode;
        if (message instanceof RAFTBlockVote) { // for selecting a leader procedure.
        	RAFTBlockVote blockVote = (RAFTBlockVote) message;
            switch (blockVote.getVoteType()) {
                case REQUEST :
            		if(!peer.isVoted) {
            			if((peer.nodeState == NodeState.FOLLOWER) && (peer.nodeState == NodeState.LEADER)) {
            				this.raftPhase = RAFTPhase.LEADER_SELECTION; 
            			}
                        this.peerBlockchainNode.replyMessage(
                                new VoteMessage(
                                        new RAFTResponseVoteMessage(this.peerBlockchainNode)
                                ),blockVote.getVoter()
                        );
                        peer.isVoted = true;
                        //System.out.println("node "+peer.getNodeID()+" voted "+message.getVoter().nodeID);
            		}
                    break;
                case RESPONSE:
                	checkVotes(peer, blockVote, electionVotes); // received in the candidate node.
                    break;
                case HEARTBEAT:
                	//System.out.println("node "+peer.getNodeID()+" received a heart beat!");
                	if(RAFTScenario.UNIFORM_CRASH) {
            			if(RAFTScenario.nodesToBeCrashed.containsKey(peer)) {
            				if(RAFTScenario.nodesToBeCrashed.get(peer)<=peer.getSimulator().getSimulationTime()) {
            					peer.crash();
                    			peer.isCrashed=true;
                    			//System.out.println("Node "+peer.nodeID+ " was crashed!");
                    			break;
            				}
            			}
                	}
                	peer.isReceivedHeartbeat = true;
                	if(this.raftPhase == RAFTPhase.LEADER_SELECTION) {
                		this.raftPhase = RAFTPhase.LOG_REPLICATION; 
                		peer.isVoted = false;
                		if(peer.nodeState == NodeState.FOLLOWER) {
                    		setElectionTimer(peer);
                    	}
                    	if(peer.nodeState == NodeState.CANDIDATE) {
                    		peer.nodeState = NodeState.FOLLOWER;
                    		setElectionTimer(peer);
                    	}
                    	if(peer.nodeState == NodeState.LEADER) {
                    		setHeartbeat(peer);
                    	}
                	}
                	break;
            }
        }
        if (message instanceof RAFTBlockReplicationVote) { 
        	RAFTBlockReplicationVote<B> blockVote = (RAFTBlockReplicationVote) message;
            switch (blockVote.getVoteType()) {
            	case REQUEST:
                    this.peerBlockchainNode.replyMessage(
                            new VoteMessage(
                                    new RAFTResponseProposalMessage<B>(this.peerBlockchainNode, blockVote.getBlock())
                            ),blockVote.getVoter()
                    );
            		break;
            	case RESPONSE:
            		checkResponses(peer, blockVote, blockVote.getBlock(), proposalVotes, proposalBlocks);
            		break;
            	case DECIDED:
                	RAFTDecidedMessage decidedMessage = (RAFTDecidedMessage) message;
                	this.currentMainChainHead = (B) decidedMessage.getBlock();
                	peer.setLastConfirmedBlockID(decidedMessage.getBlock().getHeight());
                	peer.addToLocalLedger((RAFTBlock)decidedMessage.getBlock());
                    updateChain();
                	RAFTScenario.consensusTimes.add(peer.getSimulator().getSimulationTime()-decidedMessage.getBlock().getCreationTime()); // record the consensus time.
                    writer.println("Consensus occurred in node " + this.peerBlockchainNode.getNodeID() + " for the block " + decidedMessage.getBlock().getHeight() + " at " + this.peerBlockchainNode.getSimulator().getSimulationTime());
                    writer.flush();
                    if((RAFTScenario.GENERATE_BLOCKS)&&(RAFTScenario.GENERATE_IMMEDIATELY)&&(peer.nodeState==NodeState.LEADER)&&(peer.getLastConfirmedBlockID()<RAFTScenario.MAX_NUM_OF_BLOCKS)) {
                    	RAFTBlock newBlock = BlockFactory.sampleRAFTBlock(peer.getSimulator(),
                        		peer.getNetwork().getRandom(), peer, peer.getLastBlock());
               			peer.setLastBlock(newBlock);
               			peer.broadcastMessage(
                                new VoteMessage(
                                        new RAFTSendProposalMessage<>(peer, newBlock)
                                )
                        );
                    }
            		break;
            }
        }
    }

	private static void checkVotes(RAFTNode peer, RAFTBlockVote vote, HashMap<Node, HashMap<Node, Vote>> votes) {
    	if (!votes.containsKey(peer)) { // this is the first vote received for this candidate (new candidate).
            votes.put(peer, new HashMap<>());
            //System.out.println("New candidate added: " + peer.nodeID);
        }
        if(!voters.contains(vote.getVoter())) {
        	votes.get(peer).put(vote.getVoter(), vote);
        	voters.add(vote.getVoter());
        }
        
        if (voters.size() == RAFTScenario.numberOfNodes) {
        	RAFTNode winner = findWinner(votes);
        	System.out.println("node "+winner.getNodeID()+" was elected as a leader at "+peer.getSimulator().getSimulationTime());
        	winner.nodeState =  NodeState.LEADER;
        	winner.shouldSentFirstProposal = true;
        	winner.broadcastMessage(
                    new VoteMessage(
                            new HeartbeatMessage(winner)
                    )
            );
        	
            votes.clear();
            voters.clear();
        }
    }
	
	private static RAFTNode findWinner(HashMap<Node, HashMap<Node, Vote>> votes) {
		RAFTNode winner = null;
	    int maxVotes = Integer.MIN_VALUE;

	    for (Entry<Node, HashMap<Node, Vote>> entry : votes.entrySet()) {
	        Node node = entry.getKey();
	        int nodeVotes = entry.getValue().size();
	        if (nodeVotes > maxVotes) {
	            maxVotes = nodeVotes;
	            winner = (RAFTNode) node;
	        }
	    }

	    return winner;
	}
    
    private void checkResponses(RAFTNode peer, RAFTBlockReplicationVote<B> vote, B block,
		HashMap<B, HashMap<Node, Vote>> votes, HashSet<B> blocks) {
        if (!blocks.contains(block)) {
        	if (!votes.containsKey(block)) { // this the first vote received for this candidate block.
                votes.put(block, new HashMap<>());
            }
        	votes.get(block).put(vote.getVoter(), vote);
            if (votes.get(block).size() > (RAFTScenario.numberOfNodes/2)) {
            	blocks.add(block);
                this.peerBlockchainNode.broadcastMessage(
                        new VoteMessage(
                                new RAFTDecidedMessage<B>(this.peerBlockchainNode, block)
                        )
                );
            }
        }
	}
    
	private void setElectionTimer(Node node) {
		RAFTNode raftNode = (RAFTNode) node;
		node.getSimulator().putEvent(new TimeoutEvent(node), raftNode.getElectionTimeout()); 
	}
	
    private void setHeartbeat(RAFTNode node) {
		node.getSimulator().putEvent(new HeartbeatEvent(node), RAFTScenario.HEARTBEAT_CYCLE);
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

    public enum RAFTPhase {
    	LEADER_SELECTION,
        LOG_REPLICATION
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
        return (this.currentConsensusNumber % RAFTScenario.numberOfNodes);
    }

    public int getNumAllParticipants() {
        return RAFTScenario.numberOfNodes;
    }

    public RAFTPhase getRAFTPhase() {
        return this.raftPhase;
    }

    @Override
    protected void updateChain() {
        this.confirmedBlocks.add(this.currentMainChainHead);
    }
}