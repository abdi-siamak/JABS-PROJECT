package jabs.network.networks.pbft;

import java.util.List; 

import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.network.networks.Network;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.pbft.PBFTNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;

public class PBFTWANNetwork extends Network {
    public PBFTWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    public PBFTNode createNewPBFTNode(Simulator simulator, int nodeID, int numAllParticipants) {
        return new PBFTNode(simulator, this, nodeID,
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
            this.addNode(createNewPBFTNode(simulator, i, numNodes), SingleNodeType.WAN_NODE);
        }
        List<Node> nodes = this.getAllNodes();
        for (Node node:nodes) {
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
