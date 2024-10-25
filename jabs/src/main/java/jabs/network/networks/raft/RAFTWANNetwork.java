package jabs.network.networks.raft;

import jabs.consensus.config.ConsensusAlgorithmConfig; 
import jabs.network.networks.Network;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.raft.RAFTNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;

public class RAFTWANNetwork extends Network<RAFTNode, SingleNodeType> {
    public RAFTWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    public RAFTNode createNewRAFTNode(Simulator simulator, int nodeID) {
        return new RAFTNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE));
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig pbftConsensusConfig) {
        
    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig raftConsensusConfig) {
        for (int i = 0; i < numNodes; i++) {
            this.addNode(createNewRAFTNode(simulator, i), SingleNodeType.WAN_NODE);
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
