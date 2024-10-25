package jabs.network.networks.snow;

import jabs.consensus.config.ConsensusAlgorithmConfig; 
import jabs.network.networks.Network;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.snow.AvalancheNode;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;

public class AvalancheWANNetwork extends Network<AvalancheNode, SingleNodeType> {
    public AvalancheWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    public AvalancheNode createNewAvalancheNode(Simulator simulator, int nodeID, int numAllParticipants) {
        return new AvalancheNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE),
                numAllParticipants);
    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig snowConsensusConfig) {
        for (int i = 0; i < numNodes; i++) {
            this.addNode(createNewAvalancheNode(simulator, i, numNodes), SingleNodeType.WAN_NODE);
        }

        for (Node node:this.getAllNodes()) {
            node.getP2pConnections().connectToNetwork(this);
        }
    }

	@Override
	public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig consensusAlgorithmConfig) {
		// TODO Auto-generated method stub
	}

	@Override
	public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig,
			int neighborCacheSize, double value_i, double weight_i, double value, double weight,
			double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement,
			double weightValue) {
		// TODO Auto-generated method stub
	}
}


