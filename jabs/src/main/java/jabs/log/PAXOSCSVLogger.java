package jabs.log;

import jabs.consensus.algorithm.PAXOS; 
import jabs.ledgerdata.Vote;
import jabs.ledgerdata.paxos.PAXOSAcceptCommitVote;
import jabs.ledgerdata.paxos.PAXOSAcceptVote;
import jabs.ledgerdata.paxos.PAXOSDecidedVote;
import jabs.ledgerdata.paxos.PAXOSPrepareCommitVote;
import jabs.ledgerdata.paxos.PAXOSPrepareVote;
import jabs.simulator.event.Event;
import jabs.simulator.event.PacketDeliveryEvent;
import jabs.network.message.Packet;
import jabs.network.message.VoteMessage;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

public class PAXOSCSVLogger extends AbstractCSVLogger {
    public static int numMessage;
    public static long messageSize;
    /**
     * creates an abstract CSV logger
     * @param writer this is output CSV of the logger
     */
    public PAXOSCSVLogger(Writer writer) {
        super(writer);
        numMessage = 0;
        messageSize = 0;
    }

    /**
     * creates an abstract CSV logger
     * @param path this is output path of CSV file
     */
    public PAXOSCSVLogger(Path path) throws IOException {
        super(path);
        numMessage = 0;
        messageSize = 0;
    }

    @Override
    protected String csvStartingComment() {
        return String.format("PAXOS Simulation with %d nodes on %s network", this.scenario.getNetwork().getAllNodes().size(), this.scenario.getNetwork().getClass().getSimpleName());
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
        if (vote instanceof PAXOSPrepareVote) {
            voteType = "PREPARE";
        } else if (vote instanceof PAXOSPrepareCommitVote) {
            voteType = "PREPARE_OK";
        } else if (vote instanceof PAXOSAcceptVote) {
            voteType = "ACCEPT";
        }else if (vote instanceof PAXOSAcceptCommitVote) {
            voteType = "ACCEPT_OK";
        }else if (vote instanceof PAXOSDecidedVote) {
            voteType = "DECIDED";
        }

        return new String[]{Double.toString(this.scenario.getSimulator().getSimulationTime()), voteType,
                Integer.toString(vote.getVoter().nodeID), Integer.toString(packet.getFrom().nodeID),
                Integer.toString(packet.getTo().nodeID)};
    }
    
    @Override
    public void logAfterEachEvent(Event event) {
        if (this.csvOutputConditionAfterEvent(event)) {
            if(PAXOS.WRITE_SIMULATION_LOGS) {
            	loggerCSV.writeRow(this.csvEventOutput(event));
            }
        	Packet packet = ((PacketDeliveryEvent) event).packet;
            Vote vote = ((VoteMessage) packet.getMessage()).getVote();
            messageSize += vote.getSize();
            numMessage++;
        }
    }
}
