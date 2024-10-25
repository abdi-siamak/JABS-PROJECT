package jabs.consensus.algorithm;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Tx;
import jabs.ledgerdata.Gossip;
import jabs.network.networks.Network;
import jabs.simulator.Simulator;

public interface GossipBasedConsensus <B extends Block<B>, T extends Tx<T>> extends ConsensusAlgorithm<B, T> {
    void newIncomingGossip(Gossip gossip);
}
