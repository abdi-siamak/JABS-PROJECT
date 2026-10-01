package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Hash;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.log.BECPCSVLogger;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;

/**
 * Experiment 7:
 * Normal-operation performance under controlled offered load.
 *
 * A seeded Poisson proposal process feeds one ingress/proposer node. This
 * keeps offered load separate from same-height proposal contention, which is
 * already evaluated in Experiments 1-2.
 *
 * A generated height is counted as quorum-finalised when at least
 * floor(N/2)+1 nodes have locally committed that height.
 */
public class PerformanceScenario extends BECPScenario {

    public static final int DEFAULT_NODES = 1000;
    public static final double DEFAULT_LAMBDA = 0.10;
    public static final double DEFAULT_WARMUP = 20.0;
    public static final double DEFAULT_LOAD_DURATION = 180.0;
    public static final double DEFAULT_DRAIN = 40.0;
    public static final double DEFAULT_MONITOR_INTERVAL = 0.10;
    private static final int PROPOSER_ID = 0;
    private final long experimentSeed;
    private final int experimentNodes;
    private final double nominalLambda;
    private final double warmup;
    private final double loadDuration;
    private final double drainDuration;
    private final double monitorInterval;
    private final double loadEnd;
    private final double experimentEnd;
    private final int quorumSize;
    private final Random workloadRandom;
    private final File experimentDirectory;
    private BECPNode proposer;
    private final LinkedHashMap<Integer, Double> generationTimes = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Hash> generatedHashes = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Double> quorumCommitTimes = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Double> fullCommitTimes = new LinkedHashMap<>();

    private int generatedCandidates = 0;
    private int maxGeneratedHeight = 0;

    public PerformanceScenario(
            String name, long seed, int nodes, double lambda, double warmup,
            double loadDuration, double drainDuration, double monitorInterval) {

        super(name, seed, nodes, warmup + loadDuration + drainDuration);

        if (nodes < 3) {
            throw new IllegalArgumentException("Performance experiment requires at least 3 nodes.");
        }

        if (lambda <= 0.0) {
            throw new IllegalArgumentException("Offered load lambda must be > 0.");
        }

        if (warmup < 0.0 || loadDuration <= 0.0 || drainDuration < 0.0 || monitorInterval <= 0.0) {
            throw new IllegalArgumentException("Invalid timing configuration.");
        }

        if (BECP.SSEP || !BECP.REAP_PLUS) {
            throw new IllegalStateException(
                    "Experiment 7 primary BECP configuration requires REAP+: "
                    + "-Djabs.becp.ssep=false -Djabs.becp.reapPlus=true");
        }

        this.experimentSeed = seed;
        this.experimentNodes = nodes;
        this.nominalLambda = lambda;
        this.warmup = warmup;
        this.loadDuration = loadDuration;
        this.drainDuration = drainDuration;
        this.monitorInterval = monitorInterval;
        this.loadEnd = warmup + loadDuration;
        this.experimentEnd = loadEnd + drainDuration;
        this.quorumSize = (nodes / 2) + 1;

        /* Separate RNG stream so protocol randomness cannot change workload. */
        this.workloadRandom = new Random(seed ^ 0x6A09E667F3BCC909L);

        this.experimentDirectory = new File(
                "output/journal/performance/becp-reap-plus/nodes-" + nodes + "-"+lambdaDirectory(lambda)
                + "/lambda-" + lambdaDirectory(lambda) + "/seed-" + seed);

        if (!experimentDirectory.exists() && !experimentDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create output directory: " + experimentDirectory);
        }
    }

    @Override
    protected boolean shouldGenerateInitialBlock(final BECPNode node) {
        return false;
    }

    @Override
    protected boolean shouldGenerateContinuousBlock(final BECPNode node) {
        return false;
    }

    @Override
    protected void insertInitialEvents() {
        super.insertInitialEvents();

        proposer = (BECPNode) network.getNode(PROPOSER_ID);

        scheduleNextProposal(warmup);
        simulator.putEvent(this::monitorCommitProgress, warmup);
    }

    private void scheduleNextProposal(double fromTime) {
        double u = workloadRandom.nextDouble();

        if (u <= 0.0) {
            u = Double.MIN_VALUE;
        }

        double interArrival = -Math.log(1.0 - u) / nominalLambda;
        double nextTime = fromTime + interArrival;

        if (nextTime >= loadEnd) {
            return;
        }

        double delay = Math.max(0.0, nextTime - simulator.getSimulationTime());

        simulator.putEvent(() -> injectProposal(nextTime), delay);
    }

    private void injectProposal(double scheduledTime) {
        if (simulator.getSimulationTime() >= loadEnd) {
            return;
        }

        int previousHeight = proposer.getCurrentPreferredBlock().getHeight();

        generateNewBlock(proposer);

        BECPBlock generated = proposer.getCurrentPreferredBlock();

        if (generated == null || generated.getHeight() != previousHeight + 1) {
            throw new IllegalStateException("Controlled workload failed to generate the next " + "candidate height at t=" + simulator.getSimulationTime() + ".");
        }

        int height = generated.getHeight();

        if (generationTimes.containsKey(height)) {
            throw new IllegalStateException("Duplicate generated height " + height + ".");
        }

        generationTimes.put(height, simulator.getSimulationTime());
        generatedHashes.put(height, generated.getHash());

        generatedCandidates++;
        maxGeneratedHeight = Math.max(maxGeneratedHeight, height);

        scheduleNextProposal(scheduledTime);
    }

    private void monitorCommitProgress() {
        captureCommitProgress();

        double now = simulator.getSimulationTime();

        if (now + monitorInterval <= experimentEnd) {
            simulator.putEvent(this::monitorCommitProgress, monitorInterval);
        }
    }

    private void captureCommitProgress() {
        if (maxGeneratedHeight <= 0) {
            return;
        }

        List<BECPNode> nodes = getBECPNodes();
        int[] committedHeights = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            BECPBlock last = nodes.get(i).getLastConfirmedBlock();

            committedHeights[i] = last == null ? 0 : last.getHeight();
        }

        Arrays.sort(committedHeights);

        /*
         * At least quorumSize nodes have committed every height up to
         * committedHeights[N - quorumSize].
         */
        int quorumHeight = committedHeights[committedHeights.length - quorumSize];

        int fullHeight = committedHeights[0];

        double now = simulator.getSimulationTime();

        for (int height = 1; height <= Math.min(quorumHeight, maxGeneratedHeight); height++) {

            if (generationTimes.containsKey(height) && !quorumCommitTimes.containsKey(height)) {
                quorumCommitTimes.put(height, now);
            }
        }

        for (int height = 1; height <= Math.min(fullHeight, maxGeneratedHeight); height++) {

            if (generationTimes.containsKey(height) && !fullCommitTimes.containsKey(height)) {
                fullCommitTimes.put(height, now);
            }
        }
    }

    @Override
    public void run() throws IOException {
        try {
            super.run();
            captureCommitProgress();
            writeOutputs(true, "");
        } catch (RuntimeException exception) {
            try {
                captureCommitProgress();
                writeOutputs(false, exception.getMessage());
            } catch (IOException outputException) {
                exception.addSuppressed(outputException);
            }
            throw exception;
        }
    }

    private void writeOutputs(boolean baseSafetyPassed, String failureMessage) throws IOException {

        SafetyResult safety = evaluateSafety();

        int committedByLoadEnd = 0;
        for (double commitTime : quorumCommitTimes.values()) {
            if (commitTime <= loadEnd) {
                committedByLoadEnd++;
            }
        }

        int committedFinal = quorumCommitTimes.size();
        int fullyCommittedFinal = fullCommitTimes.size();

        int backlogAtLoadEnd = generatedCandidates - committedByLoadEnd;
        int backlogFinal = generatedCandidates - committedFinal;

        double realizedLambda = generatedCandidates / loadDuration;

        double throughput = committedByLoadEnd / loadDuration;

        double uncommittedFraction = generatedCandidates == 0 ? 0.0 : ((double) backlogFinal / generatedCandidates);

        List<Double> quorumLatencies = new ArrayList<>();

        List<Double> fullLatencies = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : generationTimes.entrySet()) {

            int height = entry.getKey();
            double generationTime = entry.getValue();

            Double quorumTime = quorumCommitTimes.get(height);
            Double fullTime = fullCommitTimes.get(height);

            if (quorumTime != null) {
                quorumLatencies.add(quorumTime - generationTime);
            }

            if (fullTime != null) {
                fullLatencies.add(fullTime - generationTime);
            }
        }

        boolean safetyPassed = baseSafetyPassed && safety.divergentHeights == 0 && safety.unexpectedCommittedBlocks == 0;

        writeSummary(
                realizedLambda, throughput, committedByLoadEnd, committedFinal,
                fullyCommittedFinal, backlogAtLoadEnd, backlogFinal, uncommittedFraction,
                quorumLatencies, fullLatencies, safety, safetyPassed, failureMessage);

        writeLatencies();
        writeMetadata(safetyPassed, failureMessage);
    }

    private SafetyResult evaluateSafety() {
        Map<Integer, Set<Hash>> hashesByHeight = new HashMap<>();

        int unexpectedCommitted = 0;

        for (BECPNode node : getBECPNodes()) {
            for (BECPBlock block : node.getLocalLedger()) {
                if (block.getHeight() <= 0 || block.getState() != BECPBlock.State.COMMIT) {
                    continue;
                }

                Set<Hash> hashes = hashesByHeight.computeIfAbsent(block.getHeight(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));

                hashes.add(block.getHash());

                Hash expected = generatedHashes.get(block.getHeight());

                if (expected == null || expected != block.getHash()) {
                    unexpectedCommitted++;
                }
            }
        }

        int divergentHeights = 0;

        for (Set<Hash> hashes : hashesByHeight.values()) {
            if (hashes.size() > 1) {
                divergentHeights++;
            }
        }

        return new SafetyResult(divergentHeights, unexpectedCommitted);
    }

    private void writeSummary(
            double realizedLambda, double throughput, int committedByLoadEnd,
            int committedFinal, int fullyCommittedFinal, int backlogAtLoadEnd,
            int backlogFinal, double uncommittedFraction,
            List<Double> quorumLatencies, List<Double> fullLatencies,
            SafetyResult safety, boolean safetyPassed, String failureMessage) throws IOException {

        File file = new File(experimentDirectory, "summary.csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {

            writer.write(
                    "experiment,protocol,seed,nodes,nominal_lambda,realized_lambda,"
                    + "warmup,load_duration,drain_duration,generated_candidates,quorum_size,"
                    + "quorum_committed_by_load_end,quorum_committed_final,fully_committed_final,"
                    + "throughput,backlog_at_load_end,backlog_final,uncommitted_fraction_final,"
                    + "mean_quorum_latency,median_quorum_latency,p95_quorum_latency,p99_quorum_latency,"
                    + "mean_full_latency,avg_local_commit_latency,divergent_heights,"
                    + "unexpected_committed_blocks,safety_pass,messages,message_bytes,"
                    + "git_commit,failure_message");
            writer.newLine();

            writer.write(
                    "performance,BECP-REAP+," + experimentSeed + "," + experimentNodes + ","
                    + nominalLambda + "," + realizedLambda + "," + warmup + "," + loadDuration + ","
                    + drainDuration + "," + generatedCandidates + "," + quorumSize + ","
                    + committedByLoadEnd + "," + committedFinal + "," + fullyCommittedFinal + ","
                    + throughput + "," + backlogAtLoadEnd + "," + backlogFinal + ","
                    + uncommittedFraction + "," + mean(quorumLatencies) + ","
                    + percentile(quorumLatencies, 0.50) + "," + percentile(quorumLatencies, 0.95) + ","
                    + percentile(quorumLatencies, 0.99) + "," + mean(fullLatencies) + ","
                    + BECPScenario.getAverageConsensusTime() + "," + safety.divergentHeights + ","
                    + safety.unexpectedCommittedBlocks + "," + safetyPassed + ","
                    + BECPCSVLogger.numMessage + "," + BECPCSVLogger.messageSize + ","
                    + csv(System.getenv("GIT_COMMIT")) + "," + csv(failureMessage));
            writer.newLine();
        }
    }

    private void writeLatencies() throws IOException {
        File file = new File(experimentDirectory, "latencies.csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {

            writer.write(
                    "seed,nodes,nominal_lambda,height,generation_time,quorum_commit_time,"
                    + "full_commit_time,quorum_latency,full_latency,committed_by_load_end,"
                    + "committed_by_final_horizon");
            writer.newLine();

            for (Map.Entry<Integer, Double> entry : generationTimes.entrySet()) {

                int height = entry.getKey();
                double generated = entry.getValue();

                Double quorum = quorumCommitTimes.get(height);
                Double full = fullCommitTimes.get(height);

                writer.write(
                        experimentSeed + "," + experimentNodes + "," + nominalLambda + ","
                        + height + "," + generated + "," + nullable(quorum) + "," + nullable(full) + ","
                        + nullable(quorum == null ? null : quorum - generated) + ","
                        + nullable(full == null ? null : full - generated) + ","
                        + (quorum != null && quorum <= loadEnd) + "," + (quorum != null));
                writer.newLine();
            }
        }
    }

    private void writeMetadata(boolean safetyPassed, String failureMessage) throws IOException {

        File file = new File(experimentDirectory, "metadata.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {

            writer.write("experiment=performance\n");
            writer.write("protocol=BECP-REAP+\n");
            writer.write("seed=" + experimentSeed + "\n");
            writer.write("nodes=" + experimentNodes + "\n");
            writer.write("nominal_lambda=" + nominalLambda + "\n");
            writer.write("warmup=" + warmup + "\n");
            writer.write("load_duration=" + loadDuration + "\n");
            writer.write("drain_duration=" + drainDuration + "\n");
            writer.write("monitor_interval=" + monitorInterval + "\n");
            writer.write("workload_process=seeded_poisson\n");
            writer.write("workload_ingress_node=" + PROPOSER_ID + "\n");
            writer.write("quorum_size=" + quorumSize + "\n");
            writer.write("generated_candidates=" + generatedCandidates + "\n");
            writer.write("safety_pass=" + safetyPassed + "\n");
            writer.write("git_commit=" + csv(System.getenv("GIT_COMMIT")) + "\n");
            writer.write("failure_message=" + csv(failureMessage) + "\n");
        }
    }

    private List<BECPNode> getBECPNodes() {
        List<BECPNode> nodes = new ArrayList<>();

        for (Object node : network.getAllNodes()) {
            nodes.add((BECPNode) node);
        }

        return nodes;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }

        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private static double percentile(List<Double> values, double p) {

        if (values.isEmpty()) {
            return Double.NaN;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        if (sorted.size() == 1) {
            return sorted.get(0);
        }

        double position = p * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);

        if (lower == upper) {
            return sorted.get(lower);
        }

        double weight = position - lower;

        return sorted.get(lower) + weight * (sorted.get(upper) - sorted.get(lower));
    }

    private static String nullable(Double value) {
        return value == null ? "" : Double.toString(value);
    }

    private static String csv(String value) {
        return value == null ? "" : value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static String lambdaDirectory(double lambda) {
        return String.format(Locale.ROOT, "%.3f", lambda);
    }

    public File getExperimentDirectory() {
        return experimentDirectory;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);

        long seed = Long.parseLong(options.getOrDefault("seed", "1"));

        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NODES)));

        double lambda = Double.parseDouble(options.getOrDefault("lambda", Double.toString(DEFAULT_LAMBDA)));

        double warmup = Double.parseDouble(options.getOrDefault("warmup", Double.toString(DEFAULT_WARMUP)));

        double loadDuration = Double.parseDouble(options.getOrDefault("load-duration", Double.toString(DEFAULT_LOAD_DURATION)));

        double drain = Double.parseDouble(options.getOrDefault("drain", Double.toString(DEFAULT_DRAIN)));

        double monitorInterval = Double.parseDouble(options.getOrDefault("monitor-interval", Double.toString(DEFAULT_MONITOR_INTERVAL)));

        PerformanceScenario scenario = new PerformanceScenario(
                "Journal Experiment 7 - Offered Load", seed, nodes, lambda,
                warmup, loadDuration, drain, monitorInterval);

        scenario.AddNewLogger(
                new BECPCSVLogger(
                        new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));

        scenario.run();

        System.out.println("[Experiment 7] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
    }

    private static Map<String, String> parseArguments(String[] args) {

        Map<String, String> options = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];

            if (!argument.startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Invalid argument list near: " + argument);
            }

            options.put(argument.substring(2), args[++i]);
        }

        return options;
    }

    private static final class SafetyResult {
        private final int divergentHeights;
        private final int unexpectedCommittedBlocks;

        private SafetyResult(int divergentHeights, int unexpectedCommittedBlocks) {
            this.divergentHeights = divergentHeights;
            this.unexpectedCommittedBlocks = unexpectedCommittedBlocks;
        }
    }
}
