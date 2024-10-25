package jabs.network.stats.eightysixcountries.bitcoin;

import jabs.network.stats.BECPGlobalNetworkStats;
import jabs.network.stats.eightysixcountries.EightySixCountries;
import jabs.simulator.randengine.RandomnessEngine;

public class BitcoinBECPGlobalNetworkStats86Countries extends BitcoinNodeGlobalNetworkStats86Countries
        implements BECPGlobalNetworkStats<EightySixCountries> {
    public BitcoinBECPGlobalNetworkStats86Countries(RandomnessEngine randomnessEngine) {
        super(randomnessEngine);
    }
}