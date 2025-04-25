package jabs.network.stats.wan;

import jabs.network.stats.NetworkStats;
import jabs.simulator.randengine.RandomnessEngine;

public class WANNetworkStats implements NetworkStats<SingleNodeType> {
    protected final RandomnessEngine randomnessEngine;
    protected static final long BANDWIDTH = Long.MAX_VALUE; //if "processingTime" assumed zero, "bandwidth" should be considered infinity.
    public static final double MAX_LATENCY = 0.15d; // The maximum latency for one message.
    public static final double MIN_LATENCY = 0.05d;
    private static final double xm = 0.05d; // Scale parameter, the minimum possible value for the variable.
    public static final double alpha = 2d; // Shape parameter, controls the "fatness" of the tail. Smaller values of α lead to heavier tails, meaning more extreme values (i.e., higher variability).
    public static final LatencyDistribution DISTRIBUTION = LatencyDistribution.UNIFORM;
    
    public WANNetworkStats(RandomnessEngine randomnessEngine) {
        this.randomnessEngine = randomnessEngine;
    }
    
    public enum LatencyDistribution {
        UNIFORM,
        PARETO
    }

    /**
     * @param fromPosition 
     * @param toPosition
     * @return
     */
    @Override
    public double getLatency(SingleNodeType fromPosition, SingleNodeType toPosition) {
    	double latency = 0;
    	if(DISTRIBUTION == LatencyDistribution.UNIFORM) {
    		latency = randomnessEngine.sampleDouble(MIN_LATENCY, MAX_LATENCY);// draws samples from a Uniform[min, max).
    	}
    	if(DISTRIBUTION == LatencyDistribution.PARETO) {
        	double u = randomnessEngine.nextDouble();
        	latency = xm / Math.pow(u, 1.0 / alpha);
    	}
    	if(latency>0.15) {
    		//System.out.println("latency: "+latency);
    	}
    	return latency; 
    }

    /**
     * @param position 
     * @return
     */
    @Override
    public long sampleDownloadBandwidth(SingleNodeType position) {
        return BANDWIDTH;
    }

    /**
     * @param position 
     * @return
     */
    @Override
    public long sampleUploadBandwidth(SingleNodeType position) {
        return BANDWIDTH;
    }
}
