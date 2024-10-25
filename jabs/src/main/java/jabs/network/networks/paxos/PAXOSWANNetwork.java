package jabs.network.networks.paxos;

import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.network.networks.Network;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.paxos.PAXOSNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;

public class PAXOSWANNetwork extends Network<PAXOSNode, SingleNodeType> {
    public PAXOSWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    public PAXOSNode createNewPAXOSNode(Simulator simulator, int nodeID, int numAllParticipants) {
        return new PAXOSNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE),
                numAllParticipants);
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig pbftConsensusConfig) {
        populateNetwork(simulator, 40, pbftConsensusConfig);
    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig pbftConsensusConfig) {
        for (int i = 0; i < numNodes; i++) {
            this.addNode(createNewPAXOSNode(simulator, i, numNodes), SingleNodeType.WAN_NODE);
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
