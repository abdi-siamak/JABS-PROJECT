package jabs.network.networks.snow;

import jabs.consensus.config.ConsensusAlgorithmConfig; 
import jabs.network.networks.GlobalNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.SnowNode;
import jabs.network.stats.NodeGlobalNetworkStats;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;

public class SnowBitcoinNetwork <N extends Node, R extends Enum<R>>
        extends GlobalNetwork<N, R> {

    public SnowBitcoinNetwork(RandomnessEngine randomnessEngine, NodeGlobalNetworkStats<R> networkStats) {
        super(randomnessEngine, networkStats);
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig consensusAlgorithmConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig snowConsensusConfig) {
        R region = sampleRegion(); // get a sample for the first node
        N snowNode = (N) createSampleNode(simulator, region, 0, 1);
        this.addSnowNode(snowNode, region); // generating the first node with value = 1 and weight = 1
        for (int i = 1; i < numNodes; i++) { // generating other remaining nodes with value = 1 and weight = 0
            region = sampleRegion();
            snowNode = (N) createSampleNode(simulator, region, i, numNodes);
            this.addSnowNode(snowNode, region);
        }

        for (Node node:this.getAllNodes()) {
            node.getP2pConnections().connectToNetwork(this);
        }
    }

    public SnowNode createSampleNode(Simulator simulator, R region, int nodeID, int numNodes) {
        return new SnowNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(region),
                this.sampleUploadBandwidth(region),
                numNodes);
    }

    public void addSnowNode(N node, R sampleRegion) {
        nodes.add(node);
        nodeTypes.put(node, sampleRegion);
    }

	@Override
	public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig,
			int neighborCacheSize, double value_i, double weight_i, double value, double weight,
			double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement,
			double weightValue) {
		// TODO Auto-generated method stub
	}
}
