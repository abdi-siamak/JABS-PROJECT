package jabs.network.networks.becp;
import jabs.consensus.config.ConsensusAlgorithmConfig; 
import jabs.network.networks.GlobalNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.network.stats.NodeGlobalNetworkStats;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;

import java.util.*;

public class BECPBitcoinNetwork <N extends Node, R extends Enum<R>>
        extends GlobalNetwork<N, R> {

    public BECPBitcoinNetwork(RandomnessEngine randomnessEngine, NodeGlobalNetworkStats<R> networkStats) {
        super(randomnessEngine, networkStats);
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig consensusAlgorithmConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig, int neighborCacheSize, double value_i, double weight_i, double value, double weight, double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement, double weightValue) {
    	// Step 1: Generating nodes and assigning an empty neighborsLocalCache & blockLocalCache (by the new BECPNode) for them.
        R region = sampleRegion(); // get a sample for the first node
        N becpNode = (N) createSampleNode(simulator, region, 0, 1, value_i, weight_i, vDataAggregation, wDataAggregation, vConvergence, vAgreement, weightValue);
        this.addBECPNode(becpNode, region); // generating the first node with value = 1 and weight = 1
        for (int i = 1; i < numNodes; i++) { // generating other remaining nodes with value = 1 and weight = 0
            region = sampleRegion();
            becpNode = (N) createSampleNode(simulator, region, i, numNodes, value, weight, vDataAggregation, wDataAggregation, vConvergence, vAgreement, weightValue);
            this.addBECPNode(becpNode, region);
        }
        for (Node node:this.getAllNodes()) {
            node.getP2pConnections().connectToNetwork(this);
        }
        // Step 2: Assigning neighbors to the neighbors Local Cache and Connecting the nodes to the network.
        int degree = neighborCacheSize;
        ArrayList<BECPNode> networkNodes = new ArrayList<>();
        networkNodes.addAll((Collection<? extends BECPNode>) this.getAllNodes());
        for (BECPNode node:networkNodes) {
            while (node.getNeighborsLocalCache().size() < degree) {
                BECPNode randomNeighbor = (BECPNode) getRandomNode();
                if (!node.getNeighborsLocalCache().contains(randomNeighbor) && randomNeighbor != node) {
                    node.getNeighborsLocalCache().add(randomNeighbor);
                }
            }
            node.getP2pConnections().connectToNetwork(this);
        }
    }
    
    /**
     * @param simulator
     * @param region
     * @param nodeID
     * @param numNodes
     * @param value
     * @param weight
     * @return
     */
    public BECPNode createSampleNode(Simulator simulator, R region, int nodeID, int numNodes, double value, double weight, double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement, double weightValue) {
        return new BECPNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(region),
                this.sampleUploadBandwidth(region), value, weight, vDataAggregation, wDataAggregation, vConvergence, vAgreement, weightValue);
    }

    public void addBECPNode(N node, R sampleRegion) {
        nodes.add(node);
        nodeTypes.put(node, sampleRegion);
    }
}
