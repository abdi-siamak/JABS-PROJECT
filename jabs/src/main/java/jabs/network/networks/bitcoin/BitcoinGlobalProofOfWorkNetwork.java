package jabs.network.networks.bitcoin;

import jabs.consensus.config.ChainBasedConsensusConfig;
import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.consensus.config.NakamotoConsensusConfig;
import jabs.ledgerdata.bitcoin.BitcoinBlockWithoutTx;
import jabs.network.networks.GlobalProofOfWorkNetwork;
import jabs.network.stats.*;
import jabs.network.node.nodes.bitcoin.BitcoinMinerNode;
import jabs.network.node.nodes.bitcoin.BitcoinNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;

public class BitcoinGlobalProofOfWorkNetwork<R extends Enum<R>> extends
        GlobalProofOfWorkNetwork<BitcoinNode, BitcoinMinerNode, BitcoinBlockWithoutTx, R> {

    public BitcoinGlobalProofOfWorkNetwork(RandomnessEngine randomnessEngine,
                                           ProofOfWorkGlobalNetworkStats<R> networkStats) {
        super(randomnessEngine, networkStats);
    }

    /**
     * @param difficulty the difficulty value of the genesis block
     * @return the genesis block with no parents
     */
    @Override
    public BitcoinBlockWithoutTx genesisBlock(double difficulty) {
        return new BitcoinBlockWithoutTx(0, 0, 0, null, null, difficulty, 0);
    }

    @Override
    public BitcoinNode createSampleNode(Simulator simulator, int nodeID, BitcoinBlockWithoutTx genesisBlock,
                                        ChainBasedConsensusConfig chainBasedConsensusConfig) {
        R region = (R) this.sampleRegion();
        return new BitcoinNode(simulator, this, nodeID, this.sampleDownloadBandwidth(region),
                this.sampleUploadBandwidth(region), genesisBlock, (NakamotoConsensusConfig) chainBasedConsensusConfig);
    }

    @Override
    public BitcoinMinerNode createSampleMiner(Simulator simulator, int nodeID, double hashPower,
                                              BitcoinBlockWithoutTx genesisBlock,
                                              ChainBasedConsensusConfig chainBasedConsensusConfig) {
        R region = (R) this.sampleRegion();
        return new BitcoinMinerNode(simulator, this, nodeID, this.sampleDownloadBandwidth(region),
                this.sampleUploadBandwidth(region), hashPower, genesisBlock,
                (NakamotoConsensusConfig) chainBasedConsensusConfig);
    }

	@Override
	public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig,
			int neighborCacheSize, double value_i, double weight_i, double value, double weight,
			double vDataAggregation, double wDataAggregation, double vConvergence, double vAgreement,
			double weightValue) {
		// TODO Auto-generated method stub
		
	}
}
