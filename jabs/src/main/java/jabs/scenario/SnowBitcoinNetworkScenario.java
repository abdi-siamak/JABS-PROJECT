package jabs.scenario;

import jabs.consensus.algorithm.Snow;
import jabs.consensus.config.SnowConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.snow.SnowBlock;
import jabs.ledgerdata.snow.SnowQuery;
import jabs.network.message.QueryMessage;
import jabs.network.networks.snow.SnowBitcoinNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.SnowNode;
import jabs.network.stats.NodeGlobalNetworkStats;
import jabs.network.stats.eightysixcountries.EightySixCountries;
import jabs.network.stats.eightysixcountries.bitcoin.BitcoinSnowGlobalNetworkStats86Countries;

import java.util.ArrayList;
import java.util.List;

import static jabs.network.node.nodes.snow.SnowNode.SNOW_GENESIS_BLOCK;
/**
 * File: Snow.java
 * Description: Implements SnowBall protocol for JABS blockchain simulator.
 * Author: Siamak Abdi
 * Date: January 30, 2024
 */
public class SnowBitcoinNetworkScenario extends AbstractScenario{
    protected double simulationStopTime;

    public SnowBitcoinNetworkScenario(String name, long seed, double simulationStopTime) {
        super(name, seed);
        this.simulationStopTime = simulationStopTime;
    }

    @Override
    public void createNetwork() {
        NodeGlobalNetworkStats<EightySixCountries> networkStats = new BitcoinSnowGlobalNetworkStats86Countries(randomnessEngine);
        //System.out.println(networkStats.totalNumberOfNodes());
        SnowBitcoinNetwork bitCoinNetwork = new SnowBitcoinNetwork(randomnessEngine, networkStats);
        this.network = bitCoinNetwork;
        bitCoinNetwork.populateNetwork(this.simulator, networkStats.totalNumberOfNodes(),
                new SnowConsensusConfig());
    }

    @Override
    protected void insertInitialEvents() {
        List<Node> nodes = network.getAllNodes();
        Node startNode = nodes.get(0); // the first node that starts proposing a block
        SnowNode snowNode = (SnowNode) startNode;
        List<Node> sampledNeighbors = sampleNeighbors(startNode, Snow.K);
        SnowBlock snowBlock = BlockFactory.sampleSnowBlock(simulator, network.getRandom(),
                (SnowNode) startNode, SNOW_GENESIS_BLOCK);
        snowNode.setCurrentBlock(snowBlock);
        for(Node destination:sampledNeighbors){
            startNode.query(
                    new QueryMessage(
                            new SnowQuery<>(startNode, snowBlock)
                    ), destination
            );
        }
        //-----------------------------------
        for(int n=1;n<nodes.size();n++){
            List<Node> samples = sampleNeighbors(nodes.get(n), Snow.K);
            SnowNode otherNodes = (SnowNode) nodes.get(n);
            otherNodes.setCurrentBlock(SNOW_GENESIS_BLOCK);
            for(Node destination:samples){
                nodes.get(n).query(
                        new QueryMessage(
                                new SnowQuery<>(nodes.get(n), SNOW_GENESIS_BLOCK)
                        ), destination
                );
            }
        }
    }
    private List<Node> sampleNeighbors(Node node, int k) {
        List<Node> neighbors = new ArrayList<>();
        neighbors.addAll(node.getP2pConnections().getNeighbors());
        neighbors.remove(node);
        List<Node> sampledNodes = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int randomIndex = randomnessEngine.sampleInt(neighbors.size());
            sampledNodes.add(neighbors.get(randomIndex));
            neighbors.remove(randomIndex);
        }
        return sampledNodes;
    }
    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }

}
