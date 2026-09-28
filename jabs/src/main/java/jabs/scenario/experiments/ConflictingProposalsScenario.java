package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jabs.ledgerdata.Hash;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.MembershipSnapshot;
import jabs.log.BECPCSVLogger;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;

/**
 * Experiment 1:
 *
 * Simultaneous conflicting proposals at the same ledger height.
 *
 * Two nodes create different height-1 candidates at exactly the same
 * simulation time. Normal initial and continuous block generation are
 * disabled so that these are the only candidates introduced by the
 * workload.
 *
 * The underlying BECP implementation is not modified by this experiment.
 */
public class ConflictingProposalsScenario extends BECPScenario {
    public static final double DEFAULT_SIMULATION_TIME = 75.0;
    public static final double CONFLICT_INJECTION_TIME = 10.0;
    public static final int DEFAULT_NUM_NODES = 1000;

    private static final int CONFLICT_HEIGHT = 1;
    private static final int PROPOSER_A_ID = 0;
    private static final int PROPOSER_B_ID = 1;

    private final long experimentSeed;
    private final int experimentNumNodes;
    private final double experimentDuration;

    private final File experimentDirectory;

    private BECPBlock candidateA;
    private BECPBlock candidateB;

    private boolean conflictInjected = false;
    private double actualInjectionTime = Double.NaN;

    public ConflictingProposalsScenario(final String name, final long seed, final int numOfNodes, final double simulationStopTime) {
        super(name, seed, numOfNodes, simulationStopTime);
        if (numOfNodes < 2) {
            throw new IllegalArgumentException("Conflicting-proposals experiment requires at least two nodes.");
        }

        if (simulationStopTime <= CONFLICT_INJECTION_TIME) {
            throw new IllegalArgumentException("Simulation duration must be greater than conflict injection time.");
        }

        this.experimentSeed = seed;
        this.experimentNumNodes = numOfNodes;
        this.experimentDuration = simulationStopTime;

        this.experimentDirectory = new File(
                        "output/journal/conflicting-proposals/"
                        + "nodes-" + numOfNodes
                        + "/seed-" + seed);

        if (!experimentDirectory.exists() && !experimentDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create experiment output directory: " + experimentDirectory);
        }
    }

    /**
     * Normal initial candidate generation is disabled.
     *
     * Genesis initialization still occurs in BECPScenario.
     */
    @Override
    protected boolean shouldGenerateInitialBlock(final BECPNode node) {
        return false;
    }

    /**
     * Background block generation is disabled.
     *
     * Experiment 1 contains exactly the two candidates injected at t=10 s.
     */
    @Override
    protected boolean shouldGenerateContinuousBlock(final BECPNode node) {
        return false;
    }

    /**
     * Let BECPScenario initialize the normal network and protocol state,
     * then schedule one deterministic conflict-injection event.
     */
    @Override
    protected void insertInitialEvents() {
        super.insertInitialEvents();
        double delay = CONFLICT_INJECTION_TIME - simulator.getSimulationTime();
        if (delay < 0.0) {
            throw new IllegalStateException("Initialisation already passed the requested " + "conflict injection time.");
        }

        simulator.putEvent(this::injectConflictingProposals, delay);
    }

    /**
     * Creates two different blocks with the same parent and same height.
     *
     * Because both calls execute inside the same simulator event, the two
     * blocks have the same simulation creation time.
     */
    private void injectConflictingProposals() {
        List<BECPNode> nodes = network.getAllNodes();
        BECPNode proposerA = findNode(nodes, PROPOSER_A_ID);
        BECPNode proposerB = findNode(nodes, PROPOSER_B_ID);
        if (proposerA == null || proposerB == null) {
            throw new IllegalStateException("Could not find the two experiment proposers.");
        }

        if (proposerA.getCurrentPreferredBlock().getHeight() != 0 || proposerB.getCurrentPreferredBlock().getHeight() != 0) {
            throw new IllegalStateException("Conflict injection expected both proposers " + "to still prefer Genesis.");
        }

        if (proposerA.getBlockLocalCache().containsKey(CONFLICT_HEIGHT) || proposerB.getBlockLocalCache().containsKey(CONFLICT_HEIGHT)) {
            throw new IllegalStateException("Height 1 was already populated before " + "the conflict injection.");
        }

        /*
         * Use exactly the same block-generation method as the normal
         * BECP scenario.
         */
        generateNewBlock(proposerA);
        generateNewBlock(proposerB);

        candidateA = proposerA.getBlockLocalCache().get(CONFLICT_HEIGHT);
        candidateB = proposerB.getBlockLocalCache().get(CONFLICT_HEIGHT);

        if (candidateA == null || candidateB == null) {
            throw new IllegalStateException("Failed to create both conflicting candidates.");
        }

        if (candidateA == candidateB || candidateA.getHash() == candidateB.getHash()) {
            throw new IllegalStateException("Injected candidates are not distinct.");
        }

        if (candidateA.getHeight() != candidateB.getHeight()) {
            throw new IllegalStateException( "Injected candidates are not at the same height.");
        }

        if (candidateA.getParent().getHash() != candidateB.getParent().getHash()) {
            throw new IllegalStateException( "Injected candidates do not share the same parent.");
        }

        actualInjectionTime = simulator.getSimulationTime();
        conflictInjected = true;

        System.out.println(
                "[Experiment 1] Injected conflicting candidates "
                + "A and B at height "
                + candidateA.getHeight()
                + " at simulation time "
                + actualInjectionTime
                + " s.");

        System.out.println(
                "[Experiment 1] Candidate A creator: "
                + candidateA.getCreator().getNodeID()
                + ", cycle: "
                + candidateA.getCycleNumber());

        System.out.println(
                "[Experiment 1] Candidate B creator: "
                + candidateB.getCreator().getNodeID()
                + ", cycle: "
                + candidateB.getCycleNumber());
    }

    private BECPNode findNode(final List<BECPNode> nodes, final int nodeId) {
        for (BECPNode node : nodes) {
            if (node.getNodeID() == nodeId) {
                return node;
            }
        }

        return null;
    }

    /**
     * Run the normal BECP simulator.
     *
     * BECPScenario's global blockchain oracle remains active.
     * Any divergent commit, committed-block replacement, or invalid
     * parent chain therefore still terminates the experiment.
     */
    @Override
    public void run() throws IOException {
        try {
            super.run();
            writeExperimentOutputs(true, "");
        } catch (RuntimeException exception) {
            try {
                writeExperimentOutputs(false, exception.getMessage());
            } catch (IOException outputException) {
                exception.addSuppressed(outputException);
            }

            throw exception;
        }
    }

    private void writeExperimentOutputs(final boolean baseSafetyOraclePassed, final String failureMessage) throws IOException {
        List<BECPNode> nodes = network.getAllNodes();
        int committedA = 0;
        int committedB = 0;
        int committedOther = 0;
        int uncommitted = 0;
        Set<Hash> distinctCommittedHashes = Collections.newSetFromMap(new IdentityHashMap<>());
        int maximumConfirmationsA = 0;
        int maximumConfirmationsB = 0;
        int quorumSize = 0;

        for (BECPNode node : nodes) {
            BECPBlock committedBlock = findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
            if (committedBlock == null) {
                uncommitted++;
            } else {
                distinctCommittedHashes.add(committedBlock.getHash());
                String label = labelForBlock(committedBlock);
                if ("A".equals(label)) {
                    committedA++;
                } else if ("B".equals(label)) {
                    committedB++;
                } else {
                    committedOther++;
                }
            }

            if (candidateA != null) {
                maximumConfirmationsA = Math.max(maximumConfirmationsA, node.getFinalConfirmationCount(CONFLICT_HEIGHT,candidateA));
            }

            if (candidateB != null) {
                maximumConfirmationsB = Math.max(maximumConfirmationsB, node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateB));
            }

            MembershipSnapshot snapshot = node.getMembershipSnapshot(CONFLICT_HEIGHT);

            if (snapshot != null) {
                quorumSize = snapshot.getQuorumSize();
            }
        }

        int totalCommitted = committedA + committedB + committedOther;
        boolean progressObserved = totalCommitted > 0;
        boolean safetyPassed = baseSafetyOraclePassed && conflictInjected && distinctCommittedHashes.size() <= 1 && committedOther == 0;

        String winner = determineWinner(committedA, committedB, committedOther);
        double averageCommitLatency = BECPScenario.consensusTimes
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(Double.NaN);

        double minimumCommitLatency = BECPScenario.consensusTimes
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .min()
                        .orElse(Double.NaN);

        double maximumCommitLatency = BECPScenario.consensusTimes
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(Double.NaN);

        writeSummary(
                committedA,
                committedB,
                committedOther,
                uncommitted,
                distinctCommittedHashes.size(),
                winner,
                safetyPassed,
                progressObserved,
                quorumSize,
                maximumConfirmationsA,
                maximumConfirmationsB,
                averageCommitLatency,
                minimumCommitLatency,
                maximumCommitLatency,
                failureMessage);

        writeNodeCommitments(nodes);
        writeMetadata(safetyPassed, progressObserved, winner, failureMessage);
    }

    private BECPBlock findCommittedBlockAtHeight(final BECPNode node, final int height) {
        for (BECPBlock block : node.getLocalLedger()) {
            if (block.getHeight() == height && block.getState() == BECPBlock.State.COMMIT) {
                return block;
            }
        }

        return null;
    }

    private String labelForBlock(final BECPBlock block) {
        if (block == null) {
            return "NONE";
        }

        return labelForHash(block.getHash());
    }

    private String labelForHash(final Hash hash) {
        if (hash == null) {
            return "NONE";
        }

        if (candidateA != null && hash == candidateA.getHash()) {
            return "A";
        }

        if (candidateB != null && hash == candidateB.getHash()) {
            return "B";
        }

        return "OTHER";
    }

    private String determineWinner(final int committedA, final int committedB, final int committedOther) {
        if (committedOther > 0) {
            return "OTHER";
        }

        if (committedA > 0 && committedB > 0) {
            return "DIVERGENT";
        }

        if (committedA > 0) {
            return "A";
        }

        if (committedB > 0) {
            return "B";
        }

        return "NONE";
    }

    private void writeSummary(
            final int committedA,
            final int committedB,
            final int committedOther,
            final int uncommitted,
            final int distinctCommittedCandidates,
            final String winner,
            final boolean safetyPassed,
            final boolean progressObserved,
            final int quorumSize,
            final int maximumConfirmationsA,
            final int maximumConfirmationsB,
            final double averageCommitLatency,
            final double minimumCommitLatency,
            final double maximumCommitLatency,
            final String failureMessage)
            throws IOException {

        File summaryFile = new File(experimentDirectory, "summary.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, false))) {
            writer.write(
                    "experiment,"
                    + "seed,"
                    + "nodes,"
                    + "configured_duration,"
                    + "actual_end_time,"
                    + "injection_time,"
                    + "candidate_height,"
                    + "candidate_a_creator,"
                    + "candidate_b_creator,"
                    + "candidate_a_cycle,"
                    + "candidate_b_cycle,"
                    + "quorum_size,"
                    + "committed_a_nodes,"
                    + "committed_b_nodes,"
                    + "committed_other_nodes,"
                    + "uncommitted_nodes,"
                    + "distinct_committed_candidates,"
                    + "winner,"
                    + "safety_pass,"
                    + "progress_observed,"
                    + "max_confirmations_a,"
                    + "max_confirmations_b,"
                    + "avg_commit_latency,"
                    + "min_commit_latency,"
                    + "max_commit_latency,"
                    + "messages,"
                    + "message_bytes,"
                    + "git_commit,"
                    + "failure_message");

            writer.newLine();
            writer.write(
                    "conflicting-proposals,"
                    + experimentSeed + ","
                    + experimentNumNodes + ","
                    + experimentDuration + ","
                    + simulator.getSimulationTime() + ","
                    + actualInjectionTime + ","
                    + CONFLICT_HEIGHT + ","
                    + creatorId(candidateA) + ","
                    + creatorId(candidateB) + ","
                    + cycleNumber(candidateA) + ","
                    + cycleNumber(candidateB) + ","
                    + quorumSize + ","
                    + committedA + ","
                    + committedB + ","
                    + committedOther + ","
                    + uncommitted + ","
                    + distinctCommittedCandidates + ","
                    + winner + ","
                    + safetyPassed + ","
                    + progressObserved + ","
                    + maximumConfirmationsA + ","
                    + maximumConfirmationsB + ","
                    + averageCommitLatency + ","
                    + minimumCommitLatency + ","
                    + maximumCommitLatency + ","
                    + BECPCSVLogger.numMessage + ","
                    + BECPCSVLogger.messageSize + ","
                    + sanitiseCsv(getGitCommit()) + ","
                    + sanitiseCsv(failureMessage));

            writer.newLine();
        }
    }

    private void writeNodeCommitments(final List<BECPNode> nodes) throws IOException {
        File commitmentsFile = new File(experimentDirectory, "commitments.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(commitmentsFile, false))) {
            writer.write(
                    "node_id,"
                    + "committed_candidate,"
                    + "committed_creator,"
                    + "persistent_final_vote,"
                    + "confirmations_a,"
                    + "confirmations_b,"
                    + "last_confirmed_height,"
                    + "current_preferred_candidate");

            writer.newLine();
            for (BECPNode node : nodes) {
                BECPBlock committed = findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
                Hash finalVote = node.getPersistentFinalVote(CONFLICT_HEIGHT);
                int confirmationsA = candidateA == null ? 0 : node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateA);
                int confirmationsB = candidateB == null ? 0 : node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateB);

                writer.write(
                        node.getNodeID() + ","
                        + labelForBlock(committed) + ","
                        + creatorId(committed) + ","
                        + labelForHash(finalVote) + ","
                        + confirmationsA + ","
                        + confirmationsB + ","
                        + node.getLastConfirmedBlock().getHeight() + ","
                        + labelForBlock(node.getCurrentPreferredBlock()));

                writer.newLine();
            }
        }
    }

    private void writeMetadata(final boolean safetyPassed, final boolean progressObserved, final String winner, final String failureMessage) throws IOException {
        File metadataFile = new File(experimentDirectory, "metadata.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile, false))) {
            writer.write("experiment=conflicting-proposals");
            writer.newLine();
            writer.write("seed=" + experimentSeed);
            writer.newLine();
            writer.write("nodes=" + experimentNumNodes);
            writer.newLine();
            writer.write("simulation_duration=" + experimentDuration);
            writer.newLine();
            writer.write("conflict_injection_time=" + CONFLICT_INJECTION_TIME);
            writer.newLine();
            writer.write("actual_injection_time=" + actualInjectionTime);
            writer.newLine();
            writer.write("candidate_height=" + CONFLICT_HEIGHT);
            writer.newLine();
            writer.write("candidate_a_creator=" + creatorId(candidateA));
            writer.newLine();
            writer.write("candidate_b_creator=" + creatorId(candidateB));
            writer.newLine();
            writer.write("safety_pass=" + safetyPassed);
            writer.newLine();
            writer.write("progress_observed=" + progressObserved);
            writer.newLine();
            writer.write("winner=" + winner);
            writer.newLine();
            writer.write("git_commit=" + getGitCommit());
            writer.newLine();
            writer.write("java_version=" + System.getProperty("java.version"));
            writer.newLine();
            writer.write("generated_at=" + Instant.now());
            writer.newLine();
            writer.write("failure_message=" + sanitiseCsv(failureMessage));
            writer.newLine();
        }
    }

    private int creatorId(final BECPBlock block) {
        if (block == null || block.getCreator() == null) {
            return -1;
        }

        return block.getCreator().getNodeID();
    }

    private int cycleNumber(final BECPBlock block) {
        if (block == null) {
            return -1;
        }

        return block.getCycleNumber();
    }

    private String getGitCommit() {
        String gitCommit =System.getenv("GIT_COMMIT");
        if (gitCommit == null || gitCommit.isBlank()) {
            return "unknown";
        }

        return gitCommit;
    }

    private String sanitiseCsv(final String value) {
        if (value == null) {
            return "";
        }

        return value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    public File getExperimentDirectory() {
        return experimentDirectory;
    }

    /**
     * Usage:
     *
     * --seed 1 --nodes 1000 --duration 75
     */
    public static void main(final String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        long seed = Long.parseLong(options.getOrDefault("seed", "1"));
        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NUM_NODES)));
        double duration = Double.parseDouble(options.getOrDefault("duration", Double.toString(DEFAULT_SIMULATION_TIME)));
        ConflictingProposalsScenario scenario = new ConflictingProposalsScenario(
                        "Journal Experiment 1 - "
                        + "Simultaneous Conflicting Proposals",
                        seed,
                        nodes,
                        duration);

        scenario.AddNewLogger(new BECPCSVLogger(new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));
        scenario.run();

        System.out.println("[Experiment 1] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
    }

    private static Map<String, String> parseArguments(final String[] args) {
        Map<String, String> options = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException( "Unexpected argument: " + argument);
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + argument);
            }

            String key = argument.substring(2);
            String value = args[++i];

            options.put(key, value);
        }

        return options;
    }
}