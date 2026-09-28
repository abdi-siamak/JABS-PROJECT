package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
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
 * Experiment 2:
 *
 * Delayed preferred candidate.
 *
 * Two conflicting candidates are created simultaneously at height 1.
 * Candidate A is created by node 0 and candidate B by node 1.
 *
 * Both therefore have the same creation cycle, making A the normal
 * deterministic preference because creator 0 < creator 1.
 *
 * Candidate A's proposer is temporarily isolated and its normal BECP
 * cycles are paused. Candidate B therefore receives a real propagation
 * head start.
 *
 * Candidate A is released later without recreating the block, changing
 * its cycle, or modifying its PTP state.
 */
public class DelayedCandidateScenario extends BECPScenario {
    public static final int DEFAULT_NUM_NODES = 1000;
    public static final double DEFAULT_SIMULATION_TIME = 90.0;
    public static final double CONFLICT_INJECTION_TIME = 10.0;
    public static final double PREFERRED_RELEASE_TIME = 20.0;
    private static final int CONFLICT_HEIGHT = 1;
    private static final int PROPOSER_A_ID = 0;
    private static final int PROPOSER_B_ID = 1;

    private final long experimentSeed;
    private final int experimentNumNodes;
    private final double experimentDuration;
    private final File experimentDirectory;
    private BECPBlock candidateA;
    private BECPBlock candidateB;
    private BECPNode proposerA;
    private boolean conflictInjected = false;
    private boolean preferredCandidateReleased = false;
    private double actualInjectionTime = Double.NaN;
    private double actualReleaseTime = Double.NaN;
    /*
     * Snapshot of commitment state immediately before A is released.
     */
    private int committedABeforeRelease = 0;
    private int committedBBeforeRelease = 0;
    private int committedOtherBeforeRelease = 0;
    private int uncommittedBeforeRelease = 0;

    public DelayedCandidateScenario(final String name, final long seed, final int numOfNodes, final double simulationStopTime) {
        super(name, seed, numOfNodes, simulationStopTime);
        if (numOfNodes < 2) {
            throw new IllegalArgumentException("Delayed-candidate experiment requires at least two nodes.");
        }

        if (PREFERRED_RELEASE_TIME <= CONFLICT_INJECTION_TIME) {
            throw new IllegalArgumentException( "Preferred-candidate release must occur after injection.");
        }

        if (simulationStopTime <= PREFERRED_RELEASE_TIME) {
            throw new IllegalArgumentException( "Simulation must continue after preferred-candidate release.");
        }

        this.experimentSeed = seed;
        this.experimentNumNodes = numOfNodes;
        this.experimentDuration = simulationStopTime;
        this.experimentDirectory = new File("output/journal/delayed-candidate/" + "nodes-" + numOfNodes + "/seed-" + seed);

        if (!experimentDirectory.exists() && !experimentDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create experiment output directory: " + experimentDirectory);
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

    /**
     * Node 0 retains candidate A but does not execute BECP cycles while
     * A is deliberately withheld.
     */
    @Override
    protected boolean shouldProcessNodeCycle(final BECPNode node) {
        if (conflictInjected&& !preferredCandidateReleased && node.getNodeID() == PROPOSER_A_ID) {
            return false;
        }

        return true;
    }

    @Override
    protected void insertInitialEvents() {
        super.insertInitialEvents();
        double delay = CONFLICT_INJECTION_TIME - simulator.getSimulationTime();
        if (delay < 0.0) {throw new IllegalStateException("Initialisation passed conflict injection time.");
        }

        simulator.putEvent(this::injectConflictingCandidates, delay);
    }

    /**
     * Creates A and B at the same simulator time.
     *
     * Node 0's network interface is disabled before A is generated.
     * A therefore remains a real locally generated candidate while B
     * starts normal dissemination.
     */
    private void injectConflictingCandidates() {
        List<BECPNode> nodes = network.getAllNodes();
        proposerA = findNode(nodes, PROPOSER_A_ID);
        BECPNode proposerB = findNode(nodes, PROPOSER_B_ID);

        if (proposerA == null || proposerB == null) {
            throw new IllegalStateException("Could not find both experiment proposers.");
        }

        if (proposerA.getCurrentPreferredBlock().getHeight() != 0 || proposerB.getCurrentPreferredBlock().getHeight() != 0) {
            throw new IllegalStateException("Both proposers must still prefer Genesis.");
        }

        /*
         * Prevent A from being sent or B from being received by node 0
         * during the deliberate delay interval.
         */
        proposerA.getNodeNetworkInterface().takeDown();
        generateNewBlock(proposerA);
        generateNewBlock(proposerB);

        candidateA = proposerA.getBlockLocalCache().get(CONFLICT_HEIGHT);

        candidateB = proposerB.getBlockLocalCache().get(CONFLICT_HEIGHT);

        if (candidateA == null || candidateB == null) {
            throw new IllegalStateException("Failed to generate both conflicting candidates.");
        }

        if (candidateA.getHash() == candidateB.getHash()) {
            throw new IllegalStateException("Candidates A and B must be different.");
        }

        if (candidateA.getHeight() != candidateB.getHeight()) {
            throw new IllegalStateException("Candidates must have the same height.");
        }

        if (candidateA.getParent().getHash() != candidateB.getParent().getHash()) {
            throw new IllegalStateException("Candidates must share the same parent.");
        }

        /*
         * The experiment requires equal creation cycles.
         * This ensures A is preferred because creator 0 < creator 1,
         * not because it has an earlier cycle.
         */
        if (candidateA.getCycleNumber() != candidateB.getCycleNumber()) {
            throw new IllegalStateException("Candidates were not generated in the same cycle.");
        }

        actualInjectionTime = simulator.getSimulationTime();
        conflictInjected = true;
        double releaseDelay = PREFERRED_RELEASE_TIME - actualInjectionTime;
        simulator.putEvent(this::releasePreferredCandidate, releaseDelay);
        System.out.println("[Experiment 2] Candidates A and B created at t=" + actualInjectionTime + " s.");

        System.out.println(
                "[Experiment 2] Candidate A: creator="
                + candidateA.getCreator().getNodeID()
                + ", cycle="
                + candidateA.getCycleNumber()
                + " [WITHHELD]");

        System.out.println(
                "[Experiment 2] Candidate B: creator="
                + candidateB.getCreator().getNodeID()
                + ", cycle="
                + candidateB.getCycleNumber()
                + " [PROPAGATING]");

        System.out.println(
                "[Experiment 2] Preferred candidate A will be released at t="
                + PREFERRED_RELEASE_TIME
                + " s.");
    }

    /**
     * Records whether B has already committed anywhere, then permits A
     * to resume ordinary BECP dissemination.
     */
    private void releasePreferredCandidate() {
        captureCommitStateBeforeRelease();
        actualReleaseTime = simulator.getSimulationTime();
        preferredCandidateReleased = true;
        proposerA.getNodeNetworkInterface().bringUp();
        System.out.println(
                "[Experiment 2] Released preferred candidate A at t="
                + actualReleaseTime
                + " s.");

        System.out.println(
                "[Experiment 2] Before release: A committed at "
                + committedABeforeRelease
                + " nodes; B committed at "
                + committedBBeforeRelease
                + " nodes; uncommitted="
                + uncommittedBeforeRelease
                + ".");
    }

    private void captureCommitStateBeforeRelease() {
        committedABeforeRelease = 0;
        committedBBeforeRelease = 0;
        committedOtherBeforeRelease = 0;
        uncommittedBeforeRelease = 0;

        List<BECPNode> nodes = network.getAllNodes();
        for (BECPNode node : nodes) {
            BECPBlock committed =findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
            if (committed == null) {
                uncommittedBeforeRelease++;
            } else {
                String label = labelForBlock(committed);
                if ("A".equals(label)) {
                    committedABeforeRelease++;
                } else if ("B".equals(label)) {
                    committedBBeforeRelease++;
                } else {
                    committedOtherBeforeRelease++;
                }
            }
        }
    }

    private BECPNode findNode(final List<BECPNode> nodes, final int nodeId) {
        for (BECPNode node : nodes) {
            if (node.getNodeID() == nodeId) {
                return node;
            }
        }

        return null;
    }

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
        int finalVotesA = 0;
        int finalVotesB = 0;
        int finalVotesOther = 0;
        int noFinalVote = 0;
        int maximumConfirmationsA = 0;
        int maximumConfirmationsB = 0;
        int quorumSize = 0;
        Set<Hash> distinctCommittedHashes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BECPNode node : nodes) {
            BECPBlock committed = findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
            if (committed == null) {
                uncommitted++;
            } else {
                distinctCommittedHashes.add(committed.getHash());
                String label = labelForBlock(committed);
                if ("A".equals(label)) {
                    committedA++;
                } else if ("B".equals(label)) {
                    committedB++;
                } else {
                    committedOther++;
                }
            }

            Hash finalVote = node.getPersistentFinalVote(CONFLICT_HEIGHT);
            String voteLabel = labelForHash(finalVote);

            if ("A".equals(voteLabel)) {
                finalVotesA++;
            } else if ("B".equals(voteLabel)) {
                finalVotesB++;
            } else if ("OTHER".equals(voteLabel)) {
                finalVotesOther++;
            } else {
                noFinalVote++;
            }

            if (candidateA != null) {
                maximumConfirmationsA = Math.max(maximumConfirmationsA, node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateA));
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
        boolean preReleaseCommitsPreserved = committedA >= committedABeforeRelease && committedB >= committedBBeforeRelease;

        boolean safetyPassed = baseSafetyOraclePassed
                && conflictInjected
                && preferredCandidateReleased
                && distinctCommittedHashes.size() <= 1
                && committedOther == 0
                && finalVotesOther == 0
                && preReleaseCommitsPreserved;

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
                preReleaseCommitsPreserved,
                finalVotesA,
                finalVotesB,
                finalVotesOther,
                noFinalVote,
                quorumSize,
                maximumConfirmationsA,
                maximumConfirmationsB,
                averageCommitLatency,
                minimumCommitLatency,
                maximumCommitLatency,
                failureMessage);

        writeNodeCommitments(nodes);
        writeMetadata(
                safetyPassed,
                progressObserved,
                preReleaseCommitsPreserved,
                winner,
                failureMessage);
    }

    private BECPBlock findCommittedBlockAtHeight(
            final BECPNode node,
            final int height) {
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
            final boolean preReleaseCommitsPreserved,
            final int finalVotesA,
            final int finalVotesB,
            final int finalVotesOther,
            final int noFinalVote,
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
                    + "preferred_release_time,"
                    + "preferred_delay,"
                    + "candidate_height,"
                    + "candidate_a_creator,"
                    + "candidate_b_creator,"
                    + "candidate_a_cycle,"
                    + "candidate_b_cycle,"
                    + "quorum_size,"
                    + "pre_release_committed_a,"
                    + "pre_release_committed_b,"
                    + "pre_release_committed_other,"
                    + "pre_release_uncommitted,"
                    + "committed_a_nodes,"
                    + "committed_b_nodes,"
                    + "committed_other_nodes,"
                    + "uncommitted_nodes,"
                    + "distinct_committed_candidates,"
                    + "winner,"
                    + "safety_pass,"
                    + "progress_observed,"
                    + "pre_release_commits_preserved,"
                    + "final_votes_a,"
                    + "final_votes_b,"
                    + "final_votes_other,"
                    + "no_final_vote,"
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
                    "delayed-candidate,"
                    + experimentSeed + ","
                    + experimentNumNodes + ","
                    + experimentDuration + ","
                    + simulator.getSimulationTime() + ","
                    + actualInjectionTime + ","
                    + actualReleaseTime + ","
                    + (actualReleaseTime - actualInjectionTime) + ","
                    + CONFLICT_HEIGHT + ","
                    + creatorId(candidateA) + ","
                    + creatorId(candidateB) + ","
                    + cycleNumber(candidateA) + ","
                    + cycleNumber(candidateB) + ","
                    + quorumSize + ","
                    + committedABeforeRelease + ","
                    + committedBBeforeRelease + ","
                    + committedOtherBeforeRelease + ","
                    + uncommittedBeforeRelease + ","
                    + committedA + ","
                    + committedB + ","
                    + committedOther + ","
                    + uncommitted + ","
                    + distinctCommittedCandidates + ","
                    + winner + ","
                    + safetyPassed + ","
                    + progressObserved + ","
                    + preReleaseCommitsPreserved + ","
                    + finalVotesA + ","
                    + finalVotesB + ","
                    + finalVotesOther + ","
                    + noFinalVote + ","
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
                        + labelForHash(finalVote) + ","
                        + confirmationsA + ","
                        + confirmationsB + ","
                        + node.getLastConfirmedBlock().getHeight() + ","
                        + labelForBlock(node.getCurrentPreferredBlock()));

                writer.newLine();
            }
        }
    }

    private void writeMetadata(
            final boolean safetyPassed,
            final boolean progressObserved,
            final boolean preReleaseCommitsPreserved,
            final String winner,
            final String failureMessage)
            throws IOException {

        File metadataFile = new File(experimentDirectory, "metadata.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile, false))) {
            writer.write("experiment=delayed-candidate");
            writer.newLine();
            writer.write("seed=" + experimentSeed);
            writer.newLine();
            writer.write("nodes=" + experimentNumNodes);
            writer.newLine();
            writer.write( "simulation_duration=" + experimentDuration);
            writer.newLine();
            writer.write("conflict_injection_time=" + actualInjectionTime);
            writer.newLine();
            writer.write("preferred_release_time=" + actualReleaseTime);
            writer.newLine();
            writer.write("preferred_delay=" + (actualReleaseTime - actualInjectionTime));
            writer.newLine();
            writer.write("preferred_candidate=A");
            writer.newLine();
            writer.write("candidate_a_creator=" + creatorId(candidateA));
            writer.newLine();
            writer.write("candidate_b_creator=" + creatorId(candidateB));
            writer.newLine();
            writer.write("candidate_a_cycle=" + cycleNumber(candidateA));
            writer.newLine();
            writer.write("candidate_b_cycle=" + cycleNumber(candidateB));
            writer.newLine();
            writer.write("pre_release_committed_a=" + committedABeforeRelease);
            writer.newLine();
            writer.write("pre_release_committed_b=" + committedBBeforeRelease);
            writer.newLine();
            writer.write("safety_pass=" + safetyPassed);
            writer.newLine();
            writer.write("progress_observed=" + progressObserved);
            writer.newLine();
            writer.write("pre_release_commits_preserved=" + preReleaseCommitsPreserved);
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
        String gitCommit = System.getenv("GIT_COMMIT");
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

    public File getExperimentDirectory() {return experimentDirectory;}

    public static void main(final String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        long seed =Long.parseLong(options.getOrDefault("seed", "1"));
        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NUM_NODES)));
        double duration = Double.parseDouble(options.getOrDefault("duration", Double.toString(DEFAULT_SIMULATION_TIME)));
        DelayedCandidateScenario scenario = new DelayedCandidateScenario(
                        "Journal Experiment 2 - "
                        + "Delayed Preferred Candidate",
                        seed,
                        nodes,
                        duration);

        scenario.AddNewLogger(new BECPCSVLogger(new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));
        scenario.run();

        System.out.println("[Experiment 2] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
    }

    private static Map<String, String> parseArguments(final String[] args) {
        Map<String, String> options = new java.util.HashMap<>();
        for (int i = 0; i < args.length;i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: "+ argument);
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + argument);
            }

            options.put(argument.substring(2), args[++i]);
        }

        return options;
    }
}