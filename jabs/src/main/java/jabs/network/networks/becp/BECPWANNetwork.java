package jabs.network.networks.becp;

import java.util.List; 

import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.network.networks.Network;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;


public class BECPWANNetwork extends Network{
    public BECPWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig becpConsensusConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig becpConsensusConfig, int neighborCacheSize, double value_i, double weight_i, double value, double weight, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
    	// Step 1: Generating nodes
        this.addNode(new BECPNode(simulator, this, 0, 
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE), value_i, weight_i, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue), SingleNodeType.WAN_NODE);
    	for (int i = 1; i < numNodes; i++) {
            this.addNode(createNewBECPNode(simulator, i, value, weight, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue), SingleNodeType.WAN_NODE);
        }
        // Step 2: Assigning neighbors to the neighbors Local Cache and Connecting the nodes to the network.
        int degree = neighborCacheSize;
        List<BECPNode> nodes = this.getAllNodes();
        for (BECPNode node:nodes) {
            while (node.getNeighborsLocalCache().size() < degree) { // building a k-regular graph
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
     * @param nodeID
     * @return
     */
    public BECPNode createNewBECPNode(Simulator simulator, int nodeID, double value, double weight, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
        return new BECPNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE), value, weight, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue);
    }
}
