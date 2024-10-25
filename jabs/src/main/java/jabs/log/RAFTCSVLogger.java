package jabs.log;

import jabs.consensus.algorithm.RAFT;
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.raft.HeartbeatMessage;
import jabs.ledgerdata.raft.RAFTDecidedMessage;
import jabs.ledgerdata.raft.RAFTSendProposalMessage;
import jabs.ledgerdata.raft.RAFTRequestVoteMessage;
import jabs.ledgerdata.raft.RAFTResponseProposalMessage;
import jabs.ledgerdata.raft.RAFTResponseVoteMessage;
import jabs.simulator.event.Event;
import jabs.simulator.event.PacketDeliveryEvent;
import jabs.network.message.Packet;
import jabs.network.message.VoteMessage;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

public class RAFTCSVLogger extends AbstractCSVLogger {
    public static int numMessage;
    public static long messageSize;
    /**
     * creates an abstract CSV logger
     * @param writer this is output CSV of the logger
     */
    public RAFTCSVLogger(Writer writer) {
        super(writer);
        numMessage = 0;
        messageSize = 0;
    }

    /**
     * creates an abstract CSV logger
     * @param path this is output path of CSV file
     */
    public RAFTCSVLogger(Path path) throws IOException {
        super(path);
        numMessage = 0;
        messageSize = 0;
    }

    @Override
    protected String csvStartingComment() {
        return String.format("RAFT Simulation with %d nodes on %s network", this.scenario.getNetwork().getAllNodes().size(), this.scenario.getNetwork().getClass().getSimpleName());
    }

    @Override
    protected boolean csvOutputConditionBeforeEvent(Event event) {
        return false;
    }

    @Override
    protected boolean csvOutputConditionAfterEvent(Event event) {
        if (event instanceof PacketDeliveryEvent) {
            PacketDeliveryEvent deliveryEvent = (PacketDeliveryEvent) event;
            Packet packet = deliveryEvent.packet;
            return packet.getMessage() instanceof VoteMessage;
        }
        return false;
    }

    @Override
    protected boolean csvOutputConditionFinalPerNode() {
        return false;
    }

    @Override
    protected String[] csvHeaderOutput() {
        return new String[]{"Simulation time, Vote message type", "Voter ID", "From Node", "To Node"};
    }

    @Override
    protected String[] csvEventOutput(Event event) {
        Packet packet = ((PacketDeliveryEvent) event).packet;
        Vote vote = ((VoteMessage) packet.getMessage()).getVote();

        String voteType = "";
        if (vote instanceof RAFTRequestVoteMessage) {
            voteType = "REQUEST_VOTE";
        } else if (vote instanceof RAFTResponseVoteMessage) {
            voteType = "RESPONSE_VOTE";
        } else if (vote instanceof RAFTSendProposalMessage) {
            voteType = "REQUEST_PROPOSAL";
        } else if (vote instanceof RAFTResponseProposalMessage) {
            voteType = "RESPONSE_PROPOSAL";
        } else if (vote instanceof RAFTDecidedMessage) {
            voteType = "DECIDED";
        } else if (vote instanceof HeartbeatMessage) {
            voteType = "HEARTBEAT";
        }

        return new String[]{Double.toString(this.scenario.getSimulator().getSimulationTime()), voteType,
                Integer.toString(vote.getVoter().nodeID), Integer.toString(packet.getFrom().nodeID),
                Integer.toString(packet.getTo().nodeID)};
    }
    
    @Override
    public void logAfterEachEvent(Event event) {
        if (this.csvOutputConditionAfterEvent(event)) {
            if(RAFT.WRITE_SIMULATION_LOGS) {
            	loggerCSV.writeRow(this.csvEventOutput(event));
            }
        	Packet packet = ((PacketDeliveryEvent) event).packet;
            Vote vote = ((VoteMessage) packet.getMessage()).getVote();
            messageSize += vote.getSize();
            numMessage++;
        }
    }
}
