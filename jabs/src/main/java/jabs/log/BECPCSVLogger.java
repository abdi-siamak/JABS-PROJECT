package jabs.log;

import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.becp.BECPPull;
import jabs.ledgerdata.becp.BECPPush;
import jabs.simulator.event.Event;
import jabs.simulator.event.PacketDeliveryEvent;
import jabs.network.message.Packet;
import jabs.network.message.GossipMessage;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

public class BECPCSVLogger extends AbstractCSVLogger {
    public static int numMessage;
    public static long messageSize;
    
    /**
     * creates an abstract CSV logger
     * @param writer this is output CSV of the logger
     */
    public BECPCSVLogger(Writer writer) {
        super(writer);
        numMessage = 0;
        messageSize = 0;
    }

    /**
     * creates an abstract CSV logger
     * @param path this is output path of CSV file
     */
    public BECPCSVLogger(Path path) throws IOException {
        super(path);
        numMessage = 0;
        messageSize = 0;
    }

    @Override
    protected String csvStartingComment() {
        return String.format("BECP Simulation with %d nodes on %s network", this.scenario.getNetwork().getAllNodes().size(), this.scenario.getNetwork().getClass().getSimpleName());
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
            return packet.getMessage() instanceof GossipMessage;
        }
        return false;
    }

    @Override
    protected boolean csvOutputConditionFinalPerNode() {
        return false;
    }

    @Override
    protected String[] csvHeaderOutput() {
        return new String[]{"Simulation_time", "Gossip_message_type", "Message_size", "From_node", "To_node", "Estimated_system_size"};
    }

    @Override
    protected String[] csvEventOutput(Event event) {
        Packet packet = ((PacketDeliveryEvent) event).packet;
        Gossip gossip = ((GossipMessage) packet.getMessage()).getGossip();
        double systemSize = 0;
        String gossipType = "";
        if (gossip instanceof BECPPull) {
            BECPPull aggregationPull = (BECPPull) gossip;
            systemSize = aggregationPull.getSystemSize();
            gossipType = "PULL";

        } else if (gossip instanceof BECPPush) {
            BECPPush aggregationPush = (BECPPush) gossip;
            systemSize = aggregationPush.getSystemSize();
            gossipType = "PUSH";
        }

        return new String[]{Double.toString(this.scenario.getSimulator().getSimulationTime()), gossipType,
                Integer.toString(gossip.getSize()), Integer.toString(packet.getFrom().nodeID),
                Integer.toString(packet.getTo().nodeID), String.valueOf(systemSize)};
    }
    
    @Override
    public void logAfterEachEvent(Event event) {
        if (this.csvOutputConditionAfterEvent(event)) {
            if(BECP.WRITE_SIMULATION_LOGS) {
            	loggerCSV.writeRow(this.csvEventOutput(event));
            }
        	Packet packet = ((PacketDeliveryEvent) event).packet;
            Gossip gossip = ((GossipMessage) packet.getMessage()).getGossip();
            messageSize += gossip.getSize();
            numMessage++;
        }
    }
}
