package jabs.simulator.randengine;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.ParetoDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.util.FastMath;

import java.util.Collections;
import java.util.List;

public class RandomnessEngine extends MersenneTwister {
    public RandomnessEngine(long seed) {
        super(seed);
    }
    /**
     * Randomly samples a subset of elements from a given list.
     * @param <E> The type of elements in the list.
     * @param list The list from which to sample the subset.
     * @param n  The number of elements to sample.
     * @return A list containing a randomly selected subset of elements from the input list.
	 * 		   Returns null if the input list is smaller than the specified sample size.
     */
    public <E> List<E> sampleSubset(List<E> list, int n) {
        int length = list.size();
        if (length < n) return null;
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i , this.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }
    /**
     * Randomly samples an element from a given list.
     * @param <E> The type of elements in the list.
     * @param list The list from which to sample an element.
     * @return A randomly selected element from the list.
     */
    public <E> E sampleFromList(List<E> list) {
        return list.get(this.nextInt(list.size()));
    }
    /**
     * Inverse Transform Sampling Technique
     * Sample a value from a custom probability distribution defined by probabilities and map it to bins.
     * @param dist An array of probabilities representing the probability distribution (PMF).
     *        The sum of all probabilities should be approximately 1.
     * @param bins An array of bin ranges corresponding to the distribution.
     * @return The sampled value mapped to the appropriate bin.
     */  
    /*
    public long sampleDistributionWithBins(double[] distribution, long[] bins) {
        double rand = this.nextDouble();
        if (rand <= distribution[0]) {
            double diff = rand / distribution[0];
            long binDifference = bins[0];
            long result = 0 + (long)(diff * binDifference);

            return result;
        }

        for (int k = 1; k < distribution.length; k++) {
            if (sumArrayElements(distribution, 0, k - 1) < rand && rand <= sumArrayElements(distribution, 0, k)) {
                double diff = ((rand-sumArrayElements(distribution, 0, k-1)) / (sumArrayElements(distribution, 0, k)-sumArrayElements(distribution, 0, k-1)));
                long binDifference = bins[k] - bins[k - 1];
                long result = bins[k - 1] + (long)(diff * binDifference);

                return result;
            }
        }
        return -1; // Handle the case where no state is selected
    }
	*/
    
    public long sampleDistributionWithBins(double[] dist, long[] bins) {
        double rand = this.nextDouble(); // 0.0 (inclusive) and 1.0 (exclusive)
        for (int i = 0; i < dist.length-1; i++) {
            if (rand < dist[i]) {
                double diff = rand / dist[i];
                return (bins[i] + (long)(diff * (bins[i+1]-bins[i])));
            } else {
                rand -= dist[i];
            }
        }
        return bins[bins.length-1];
    }
	
    /**
     * Inverse Transform Sampling Technique
     * Sample a value from a custom probability distribution defined by probabilities and map it to bins.
     * The method generates a random value and uses it to sample a value from the specified distribution.
     *
     * @param dist An ordered list of probabilities representing the probability distribution.
     * @param bins An array of bin ranges corresponding to the distribution.
     * @return The sampled value mapped to the appropriate bin.
     */
    /*
    public long sampleDistributionWithBins(List<Double> distribution, long[] bins) {
        double rand = this.nextDouble();
        if (rand <= distribution.get(0)) {
            double diff = rand / distribution.get(0);
            long binDifference = bins[0];
            long result = 0 + (long)(diff * binDifference);

            return result;
        }

        for (int k = 1; k < distribution.size(); k++) {
            if (sumListElements(distribution, 0, k - 1) < rand && rand <= sumListElements(distribution, 0, k)) {
                double diff = ((rand-sumListElements(distribution, 0, k-1)) / (sumListElements(distribution, 0, k)-sumListElements(distribution, 0, k-1)));
                long binDifference = bins[k] - bins[k - 1];
                long result = bins[k - 1] + (long)(diff * binDifference);

                return result;
            }
        }
        return -1; // Handle the case where no state is selected
    }
    */
    
    public long sampleDistributionWithBins(List<Double> dist, long[] bins) {
        double rand = this.nextDouble(); // 0.0 (inclusive) and 1.0 (exclusive)
        for (int i = 0; i < dist.size()-1; i++) {
            if (rand < dist.get(i)) {
                double diff = rand / dist.get(i);
                return (bins[i] + (long)(diff * (bins[i+1]-bins[i])));
            } else {
                rand -= dist.get(i);
            }
        }
        return bins[bins.length-1];
    }
    
    /**
     * Inverse Transform Sampling Technique
     * Samples an integer value from a discrete probability distribution represented by the given array of probabilities.
     *
     * @param dist An array of probabilities, where each element represents the probability of selecting a particular value.
     *             The probabilities should sum to 1.
     * @return An integer representing the selected value based on the provided probability distribution.
     */
    /*
    public int sampleFromDistribution(double[] distribution) {
        double rand = this.nextDouble();
        if (rand <= distribution[0]) {

            return 0;
        }
        for (int k = 1; k < distribution.length; k++) {
            if (sumArrayElements(distribution, 0, k - 1) < rand && rand <= sumArrayElements(distribution, 0, k)) {

                return k;
            }
        }
        return -1; // Handle the case where no state is selected
    }
    */
    
    public int sampleFromDistribution(double[] dist) {
        double rand = this.nextDouble();
        for (int i = 0; i < dist.length-1; i++) {
            if (rand < dist[i]) {
                //double diff = rand / dist[i];
                return i;
            } else {
                rand -= dist[i];
            }
        }
        return dist.length-1;
    }
	
    /**
     * Returns a pseudorandom integer from 0 (inclusive) to the specified maximum (exclusive).
     *
     * @param max The exclusive upper bound for the generated integer.
     * @return A pseudorandom integer in the range [0, max).
     */
    public int sampleInt(int max) {
        return this.nextInt(max);
    }

    /**
     * Returns a pseudorandom double in the range [0, max).
     *
     * @param max The exclusive upper bound for the generated double.
     * @return A pseudorandom double in the range [0, max).
     */
    public double sampleDouble(double max) {
        return this.nextDouble() * max;
    }
    
    /**
     * Returns a pseudorandom double in the range [min, max).
     *
     * @param min The inclusive lower bound for the generated double.
     * @param max The exclusive upper bound for the generated double.
     * @return A pseudorandom double in the range [min, max).
     * @throws IllegalArgumentException if min is greater than or equal to max.
     */
    public double sampleDouble(double min, double max) {
        if (min >= max) {
            throw new IllegalArgumentException("Minimum must be less than maximum");
        }
        
        return min + (this.nextDouble() * (max - min));
    }

    /**
     * Samples a pseudorandom value from an exponential distribution with the specified mean.
     *
     * @param mean The mean (average) of the exponential distribution.
     * @return A pseudorandom value sampled from the exponential distribution.
     * @throws NotStrictlyPositiveException if the mean is less than or equal to 0.
     */
    public double sampleExponentialDistribution(double mean) {
        ExponentialDistribution expDist = new ExponentialDistribution(this, mean);
        return expDist.sample();
    }

    /**
     * Samples a pseudorandom value from a log-normal distribution with the specified median and standard deviation.
     *
     * @param median The median of the log-normal distribution.
     * @param stdDev The standard deviation of the log-normal distribution.
     * @return A pseudorandom value sampled from the log-normal distribution.
     * @throws NotStrictlyPositiveException if the standard deviation is not strictly positive (i.e., if stdDev <= 0).
     */
    public double sampleLogNormalDistribution(double median, double stdDev) {
        LogNormalDistribution expDist = new LogNormalDistribution(this, FastMath.log(median), stdDev);
        return expDist.sample();
    }

    /**
     * Samples a pseudorandom value from a log-normal distribution with the specified median and standard deviation.
     *
     * @param median The median of the log-normal distribution.
     * @param stdDev The standard deviation of the log-normal distribution.
     * @return A pseudorandom value sampled from the log-normal distribution.
     * @throws NotStrictlyPositiveException if the standard deviation is not strictly positive (i.e., if stdDev <= 0).
     */
    public double sampleParetoDistribution(double scale, double shape) {
        ParetoDistribution pareto = new ParetoDistribution(this, scale, shape);
        return pareto.sample();
    }
    
    public static double sumArrayElements(double[] arr, int startIndex, int endIndex) {
        double sum = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            sum += arr[i];
        }
        return sum;
    }
    
    public static double sumListElements(List<Double> list, int startIndex, int endIndex) {
        double sum = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            sum += list.get(i);
        }
        return sum;
    }
}
