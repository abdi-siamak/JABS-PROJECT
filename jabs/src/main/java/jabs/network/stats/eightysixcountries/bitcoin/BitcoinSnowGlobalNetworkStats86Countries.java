package jabs.network.stats.eightysixcountries.bitcoin;


import jabs.network.stats.SnowGlobalNetworkStats;
import jabs.network.stats.eightysixcountries.EightySixCountries;
import jabs.simulator.randengine.RandomnessEngine;

public class BitcoinSnowGlobalNetworkStats86Countries extends BitcoinNodeGlobalNetworkStats86Countries
        implements SnowGlobalNetworkStats<EightySixCountries> {
    public BitcoinSnowGlobalNetworkStats86Countries(RandomnessEngine randomnessEngine) {
        super(randomnessEngine);
    }
}
