package jabs.log;

import jabs.consensus.algorithm.Avalanche; 
import jabs.consensus.algorithm.Snow;
import jabs.ledgerdata.Query;
import jabs.ledgerdata.snow.SnowQuery;
import jabs.ledgerdata.snow.SnowReply;
import jabs.ledgerdata.snow.SnowTxQuery;
import jabs.ledgerdata.snow.SnowTxReply;
import jabs.network.message.Packet;
import jabs.network.message.QueryMessage;
import jabs.simulator.event.Event;
import jabs.simulator.event.PacketDeliveryEvent;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

public class SnowCSVLogger extends AbstractCSVLogger {
    public static int numMessage;
    public static long messageSize;
    
    /**
     * creates an abstract CSV logger
     * @param writer this is output CSV of the logger
     */
    public SnowCSVLogger(Writer writer) {
        super(writer);
        numMessage = 0;
        messageSize = 0;
    }

    /**
     * creates an abstract CSV logger
     * @param path this is output path of CSV file
     */
    public SnowCSVLogger(Path path) throws IOException {
        super(path);
        numMessage = 0;
        messageSize = 0;
    }

    @Override
    protected String csvStartingComment() {
        return String.format("Snow Simulation with %d nodes on %s network", this.scenario.getNetwork().getAllNodes().size(), this.scenario.getNetwork().getClass().getSimpleName());
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
            return packet.getMessage() instanceof QueryMessage;
        }
        return false;
    }

    @Override
    protected boolean csvOutputConditionFinalPerNode() {
        return false;
    }

    @Override
    protected String[] csvHeaderOutput() {
        return new String[]{"Simulation time", "Query message type", "Inquirer ID", "From Node", "To Node"};
    }

    @Override
    protected String[] csvEventOutput(Event event) {
        Packet packet = ((PacketDeliveryEvent) event).packet;
        Query query = ((QueryMessage) packet.getMessage()).getQuery();

        String queryType = "";
        if (query instanceof SnowReply||query instanceof SnowTxReply) {
            queryType = "REPLY";
        } else if (query instanceof SnowQuery||query instanceof SnowTxQuery) {
            queryType = "QUERY";
        }

        return new String[]{Double.toString(this.scenario.getSimulator().getSimulationTime()), queryType,
                Integer.toString(query.getInquirer().nodeID), Integer.toString(packet.getFrom().nodeID),
                Integer.toString(packet.getTo().nodeID)};
    }
    
    @Override
    public void logAfterEachEvent(Event event) {
        if (this.csvOutputConditionAfterEvent(event)) {
            if(Avalanche.WRITE_SIMULATION_LOGS || Snow.WRITE_SIMULATION_LOGS) {
            	loggerCSV.writeRow(this.csvEventOutput(event));
            }
        	Packet packet = ((PacketDeliveryEvent) event).packet;
            Query gossip = ((QueryMessage) packet.getMessage()).getQuery();
            messageSize += gossip.getSize();
            numMessage++;
        }
    }
}
