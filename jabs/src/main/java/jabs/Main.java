package jabs;

import jabs.consensus.algorithm.*;
import jabs.ledgerdata.becp.Metadata;
import jabs.ledgerdata.bitcoin.BitcoinBlockWithoutTx;
import jabs.log.*;
import jabs.scenario.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
/**
 *
 */
public class Main {
	public static HashMap<Integer, ArrayList<Double>> MPEs = new HashMap<>();
	public static HashMap<Integer, ArrayList<ArrayList<Double>>> VARs = new HashMap<>();
	public static ArrayList<Double> averageConsensusDurationTimes = new ArrayList<>();
	public static ArrayList<Integer> averageNumberOfSentMessages = new ArrayList<>();
	public static ArrayList<Long> averageSizeOfSentMessages = new ArrayList<>();
	public static ArrayList<Integer> averageBlockchainHeights = new ArrayList<>();
	public static ArrayList<Double> forkSolutionCalls = new ArrayList<>();
	public static ArrayList<Double> forkHeights = new ArrayList<>();
	private static File directory;
    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        AbstractScenario scenario;
        /*
        // Simulate one day in the life of Bitcoin network
        // Nakamoto protocol with block every 600 seconds
        // Around 8000 nodes with 30 miners
        scenario = new BitcoinGlobalNetworkScenario("Simulation of the life of Bitcoin", 1,
                900, 10, 6); // default 1, 86400, 600, 6
        scenario.AddNewLogger(new BlockConfirmationLogger(Paths.get("output/bitcoin-confirmations-log.csv")));
        scenario.AddNewLogger(new BlockPropagationDelayLogger(
                Paths.get("output/bitcoin-50-propagation-delay-log.csv"),0.5));
        scenario.AddNewLogger(new BlockPropagationDelayLogger(
                Paths.get("output/bitcoin-90-propagation-delay-log.csv"),0.9));
        scenario.AddNewLogger(new BlockchainReorgLogger<BitcoinBlockWithoutTx>(
                Paths.get("output/bitcoin-reorgs-log.csv")));
        scenario.run();
        System.out.println("number of consensus: "+ NakamotoConsensus.numConsensus);
        // Simulate 1 hour in the life of Ethereum network
        // Ghost protocol with blocks every 14 seconds on average
        // Around 6000 nodes with 37 miners
        scenario = new NormalEthereumNetworkScenario("Simulation of the life of Ethereum", 1,
                900, 10); // default 1, 3600, 13.3
        scenario.AddNewLogger(new BlockPropagationDelayLogger(
                Paths.get("output/ethereum-50-propagation-delay-log.csv"), 0.5));
        scenario.AddNewLogger(new BlockPropagationDelayLogger(
                Paths.get("output/ethereum-90-propagation-delay-log.csv"), 0.9));
        scenario.AddNewLogger(new FinalUncleBlocksLogger(
                Paths.get("output/ethereum-uncle-rate.csv")));
        scenario.run();
        System.out.println("number of consensus: "+ GhostProtocol.numConsensus);
        */
        /*
        for (int s = 1; s <= 5; s++) {
            // Simulate PBFT WAN network
            scenario = new PBFTScenario("Simulation of a PBFT WAN Network", s,
                    5000, 300);
            scenario.AddNewLogger(new PBFTCSVLogger(Paths.get(PBFTScenario.directory+"/pbft-simulation-log.csv")));
            scenario.run();
            averageNumberOfSentMessages.add(PBFTCSVLogger.numMessage);
            averageSizeOfSentMessages.add(PBFTCSVLogger.messageSize);
            averageConsensusDurationTimes.add(PBFTScenario.getAverageConsensusTime());
        }
        directory = PBFTScenario.directory;
		*/
        /*
        for (int s = 1; s <= 5 ; s++) {
            // Simulate PAXOS WAN network
            scenario = new PAXOSScenario("Simulation of a PAXOS WAN Network", s,
                    5000, 300);
            scenario.AddNewLogger(new PAXOSCSVLogger(Paths.get(PAXOSScenario.directory+"/paxos-simulation-log.csv")));
            scenario.run();
            averageNumberOfSentMessages.add(PAXOSCSVLogger.numMessage);
            averageSizeOfSentMessages.add(PAXOSCSVLogger.messageSize);
            averageConsensusDurationTimes.add(PAXOSScenario.getAverageConsensusTime());
        }
        directory = PAXOSScenario.directory;
        */
        /*
        for (int s = 1; s <= 5 ; s++) {
            // Simulate RAFT WAN network
            scenario = new RAFTScenario("Simulation of a RAFT WAN Network", s ,300);
            scenario.AddNewLogger(new RAFTCSVLogger(Paths.get(RAFTScenario.directory+"/raft-simulation-log.csv")));
            scenario.run();
            averageNumberOfSentMessages.add(RAFTCSVLogger.numMessage);
            averageSizeOfSentMessages.add(RAFTCSVLogger.messageSize);
            averageConsensusDurationTimes.add(RAFTScenario.getAverageConsensusTime());
        }
        directory = RAFTScenario.directory;
        */
        
        for (int s = 1; s <= 1; s++) {
            //Simulate BECP WAN network and Bitcoin network
            scenario = new BECPScenario("Simulation of a BECP WAN Network", s, 200,
                    600, 100);
            //scenario = new BECPBitcoinNetworkScenario("One hour of a BECP Bitcoin Network", 1,
            //        3600, 50);
            scenario.AddNewLogger(new BECPCSVLogger(Paths.get(BECPScenario.directory+"/becp-simulation-log.csv")));
            scenario.run();
            averageNumberOfSentMessages.add(BECPCSVLogger.numMessage);
            averageSizeOfSentMessages.add(BECPCSVLogger.messageSize);
            averageConsensusDurationTimes.add(BECPScenario.getAverageConsensusTime());
            calculateForkResolutionCalls();
        }
        directory = BECPScenario.directory;
        //writeMPEs();
        //writeVARs();
        
        /*
        for (int s = 1; s <= 1; s++) {
            // Simulate Avalanche WAN network
            scenario = new AvalancheScenario("Simulation of an Avalanche WAN Network", s, 5000,
                    300);
            scenario.AddNewLogger(new SnowCSVLogger(Paths.get(AvalancheScenario.directory+"/avalanche-simulation-log.csv")));
            scenario.run();
            averageNumberOfSentMessages.add(SnowCSVLogger.numMessage);
            averageSizeOfSentMessages.add(SnowCSVLogger.messageSize);
            averageConsensusDurationTimes.add(AvalancheScenario.getAverageConsensusTime());
        }
        directory = AvalancheScenario.directory;
        */
        /*
        for (int s = 1; s <= 1; s++) {
            // Simulate Snow WAN network and Bitcoin network
            scenario = new SnowScenario("Simulation of a Snowball WAN Network", s, 100,
                    3600);
            //scenario = new SnowBitcoinNetworkScenario("One hour of a Snowball Bitcoin Network", 1,
            //        3600);
            scenario.AddNewLogger(new SnowCSVLogger(Paths.get("output/snowball-simulation-log.csv")));
            scenario.run();
            System.out.println("total number of sent messages: "+ SnowCSVLogger.numMessage);
            System.out.println("total size of sent messages: "+SnowCSVLogger.messageSize+" (bytes)");
        }
        */
        writeMeasures();
        calculateAverage();
    }
    
    private static void calculateForkResolutionCalls() {
        double totalResolutionCalls = 0;
        double totalForkHeight = 0;
        int numberOfBlocks = 0;

        // Iterate through the blockMap for each peer
        for (HashMap<Integer, Metadata> blockMap : BECPScenario.peerBlockMap.values()) {
            for (Metadata metadata : blockMap.values()) {
                totalResolutionCalls += metadata.getTimes(); // Sum of times the resolution method was called
                totalForkHeight += metadata.getAverageHeight(); // Sum of average fork heights
                numberOfBlocks++;
            }
        }

        // Ensure no division by zero
        if (numberOfBlocks > 0) {
            forkSolutionCalls.add(totalResolutionCalls / numberOfBlocks); // Average resolution calls per block
            forkHeights.add(totalForkHeight / numberOfBlocks); // Average fork height per block
        }
    }


	private static void writeMeasures() { // writes the information for every run.
        File durationTimesFile = new File(directory, "consensus_duration_times.txt");
        File numberOfSentMessagesFile = new File(directory, "number_of_sent_messages.txt");
        File sizeOfSentMessagesFile = new File(directory, "size_of_sent_messages.txt");
        File blockchainHeightFile = new File(directory, "ledgers_heights.txt");
        writeArrayListToFile(averageConsensusDurationTimes, durationTimesFile);
        writeArrayListToFile(averageNumberOfSentMessages, numberOfSentMessagesFile);
        writeArrayListToFile(averageSizeOfSentMessages, sizeOfSentMessagesFile);
        writeArrayListToFile(averageBlockchainHeights, blockchainHeightFile);
	}
	
    public static void writeArrayListToFile(ArrayList<?> arrayList, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Object element : arrayList) {
                writer.write(element.toString());
                writer.newLine();
            }
            //System.out.println("Data written to " + file.getName() + " successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private static void calculateAverage() { // calculates an average of the number of runs.
		int averageHeight = (int) Math.round(averageBlockchainHeights.stream().mapToInt(Integer::intValue).average().orElse(0.0));
		double averageConsensusTime = averageConsensusDurationTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		int averageSentMessages =  (int) Math.round(averageNumberOfSentMessages.stream().mapToInt(Integer::intValue).average().orElse(0.0));
		long averageSizeSentMessages = Math.round(averageSizeOfSentMessages.stream().mapToLong(Long::longValue).average().orElse(0.0));
		double averageForkSolutionCalls = forkSolutionCalls.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double averageForkHeights = forkHeights.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		System.out.println("Average Blockchain height: "+ averageHeight);
		System.out.println("Average time to reach a consensus: "+ averageConsensusTime);
        System.out.println("Average total number of sent messages: "+ averageSentMessages);
        System.out.println("Average total size of sent messages: "+averageSizeSentMessages+" (bytes)");
        System.out.println("Average number of fork resolution calls: "+averageForkSolutionCalls);
        System.out.println("Average fork height: "+averageForkHeights);
	}

	private static void writeVARs() {
        try (FileWriter writer = new FileWriter("output/VARs.csv")) {
        	writer.append("Cycle,VAR,avg,sd\r\n");
        	writer.append("0,0,0,0\r\n");	
        	for(Integer cycle:VARs.keySet()) {
        		double var=0;
        		double average=0;
        		double sd=0;
        		 for(ArrayList<Double> values:VARs.get(cycle)) {
        			 var = var + values.get(0); // variance
        			 average = average + values.get(1); // average of the values
        			 sd = sd + values.get(2); // standard deviation 
        		 }
        		 var = var/(VARs.get(cycle).size());
        		 average = average/(VARs.get(cycle).size());
        		 sd = sd/(VARs.get(cycle).size());
        		 writer.append(String.format("%d,%f,%f,%f%n", cycle, var, average, sd));
        	}
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	private static void writeMPEs() {
        try (FileWriter writer = new FileWriter("output/MPEs.csv")) {
        	writer.append("Cycle,MPE\r\n");
        	writer.append("0,0\r\n");	
        	for(Integer cycle:MPEs.keySet()) {
        		double sum=0;
        		double average=0;
        		 for(Double value:MPEs.get(cycle)) {
        			 sum = sum + value;
        		 }
        		 average = sum/(MPEs.get(cycle).size());
        		 writer.append(String.format("%d,%f%n", cycle, average));
        	}
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}