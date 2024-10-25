package jabs.network.networks.snow;

import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.network.networks.Network;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.SnowNode;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.stats.wan.SingleNodeType;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;


public class SnowWANNetwork extends Network<SnowNode, SingleNodeType> {
    public SnowWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    public SnowNode createNewSnowNode(Simulator simulator, int nodeID, int numAllParticipants) {
        return new SnowNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE),
                numAllParticipants);
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig snowConsensusConfig) {
        populateNetwork(simulator, 40, snowConsensusConfig);
    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig snowConsensusConfig) {
        for (int i = 0; i < numNodes; i++) {
            this.addNode(createNewSnowNode(simulator, i, numNodes), SingleNodeType.WAN_NODE);
        }

        for (Node node:this.getAllNodes()) {
            node.getP2pConnections().connectToNetwork(this);
        }
    }

	@Override
	public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig,
			int neighborCacheSize, double value_i, double weight_i, double value, double weight,
			double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement,
			double weightValue) {
		// TODO Auto-generated method stub
	}
}

