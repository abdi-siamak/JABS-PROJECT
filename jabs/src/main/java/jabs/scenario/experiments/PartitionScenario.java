package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jabs.ledgerdata.Hash;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.MembershipSnapshot;
import jabs.log.BECPCSVLogger;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;

/**
 * Experiment 3:
 * Temporary 50/50 network partition followed by healing.
 * - 1000 nodes by default.
 * - Two equal partitions of 500 nodes.
 * - One conflicting height-1 proposal is injected in each partition.
 * - The membership snapshot still contains all 1000 nodes, therefore
 *   the strict-majority quorum is 501.
 * - Cross-partition packets are dropped while the partition is active.
 * - The original NCP neighbor caches are restored at healing time.
 *
 * Safety expectation:
 *   neither 500-node partition may commit before healing and no divergent
 *   committed blocks may appear after healing.
 *
 * Liveness observation:
 *   after healing, record whether the network eventually commits one
 *   candidate and whether all nodes converge to it.
 */
public class PartitionScenario extends BECPScenario {
    public static final int DEFAULT_NUM_NODES = 1000;
    public static final double DEFAULT_SIMULATION_TIME = 90.0;
    public static final double PARTITION_START_TIME = 10.0;

    /*
     * Allow all Push/Pull traffic already in flight when the cut is
     * introduced to drain before conflicting candidates are created.
     * CYCLE_TIME already includes a complete Push/Pull latency budget,
     * so two cycles provide a conservative stabilization interval.
     */
    public static final double PARTITION_STABILIZATION_TIME = 2.0 * CYCLE_TIME;
    public static final double CONFLICT_INJECTION_TIME = PARTITION_START_TIME + PARTITION_STABILIZATION_TIME;

    /*
     * Keep the two candidates isolated for 20 simulated seconds.
     */
    public static final double PARTITION_HEAL_TIME = CONFLICT_INJECTION_TIME + 20.0;
    private static final int CONFLICT_HEIGHT = 1;
    private static final int EXPECTED_QUORUM_DIVISOR = 2;
    private final long experimentSeed;
    private final int experimentNumNodes;
    private final double experimentDuration;
    private final File experimentDirectory;
    private final Set<Integer> groupAIds = new HashSet<>();
    private final Set<Integer> groupBIds = new HashSet<>();
    private final Map<Integer, ArrayList<BECPNode>>originalNeighborCaches = new HashMap<>();
    private BECPNode proposerA;
    private BECPNode proposerB;
    private BECPBlock candidateA;
    private BECPBlock candidateB;
    private boolean partitionStarted = false;
    private boolean partitionHealed = false;
    private double actualPartitionStartTime = Double.NaN;
    private double actualInjectionTime = Double.NaN;
    private double actualPartitionHealTime = Double.NaN;

    /*
     * State captured immediately before healing.
     */
    private int preHealCommittedA = 0;
    private int preHealCommittedB = 0;
    private int preHealCommittedOther = 0;
    private int preHealUncommitted = 0;
    private int preHealFinalVotesA = 0;
    private int preHealFinalVotesB = 0;
    private int preHealFinalVotesOther = 0;
    private int preHealNoFinalVote = 0;
    private int preHealMaxConfirmationsA = 0;
    private int preHealMaxConfirmationsB = 0;
    private int preHealQuorumSize = 0;
    private int preHealAVisibleInGroupA = 0;
    private int preHealAVisibleInGroupB = 0;
    private int preHealBVisibleInGroupA = 0;
    private int preHealBVisibleInGroupB = 0;

    public PartitionScenario(final String name, final long seed, final int numOfNodes, final double simulationStopTime) {
        super(name, seed, numOfNodes, simulationStopTime);
        if (numOfNodes < 4 || numOfNodes % 2 != 0) {
            throw new IllegalArgumentException("50/50 partition experiment requires an even node count >= 4.");
        }

        if (PARTITION_HEAL_TIME <= PARTITION_START_TIME) {
            throw new IllegalArgumentException("Partition healing must occur after partition start.");
        }

        if (simulationStopTime <= PARTITION_HEAL_TIME) {
            throw new IllegalArgumentException("Simulation must continue after partition healing.");
        }

        this.experimentSeed = seed;
        this.experimentNumNodes = numOfNodes;
        this.experimentDuration = simulationStopTime;
        this.experimentDirectory = new File("output/journal/partition-50-50/" + "nodes-" + numOfNodes + "/seed-" + seed);

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

    @Override
    protected void insertInitialEvents() {
        super.insertInitialEvents();
        configureGroupsAndSaveOverlay();
        double delay = PARTITION_START_TIME - simulator.getSimulationTime();

        if (delay < 0.0) {
            throw new IllegalStateException("Initialisation passed partition start time.");
        }

        simulator.putEvent(this::beginPartitionIsolation, delay);
    }

    /**
     * Randomly assigns exactly half of the nodes to each side using the
     * experiment seed. This avoids a fixed node-ID split while remaining
     * perfectly reproducible.
     */
    private void configureGroupsAndSaveOverlay() {
        List<BECPNode> nodes = network.getAllNodes();
        List<BECPNode> shuffled = new ArrayList<>(nodes);
        Collections.shuffle(shuffled, new Random(experimentSeed));
        int groupASize = experimentNumNodes / 2;
        for (int i = 0; i < shuffled.size(); i++) {
            BECPNode node = shuffled.get(i);
            if (i < groupASize) {groupAIds.add(node.getNodeID());
            } else {
                groupBIds.add(node.getNodeID());
            }
            originalNeighborCaches.put(node.getNodeID(), new ArrayList<>(node.getNeighborsLocalCache()));
        }

        proposerA = shuffled.get(0);
        proposerB = shuffled.get(groupASize);
    }

    /**
     * Introduces the topology cut first, without any height-1 candidate.
     *
     * Existing Push/Pull traffic from before the cut may still arrive for
     * a short time. During the stabilization interval every node cache is
     * sanitized before each normal cycle. No experiment candidate exists
     * yet, so stale pre-cut traffic cannot leak A or B across the cut.
     */
    private void beginPartitionIsolation() {
        actualPartitionStartTime = simulator.getSimulationTime();
        partitionStarted = true;
        applyPartitionedNeighborCaches();
        validatePartitionedNeighborCaches();
        simulator.putEvent(this::injectConflictingCandidates, PARTITION_STABILIZATION_TIME);
        System.out.println("[Experiment 3] 50/50 partition cut introduced at t=" + actualPartitionStartTime + " s.");
        System.out.println("[Experiment 3] Stabilizing partition for " + PARTITION_STABILIZATION_TIME + " s before candidate injection.");
    }

    /**
     * After stale pre-cut traffic has drained, clean all caches once more,
     * validate the cut, and inject one candidate into each half.
     */
    private void injectConflictingCandidates() {
        applyPartitionedNeighborCaches();
        validatePartitionedNeighborCaches();
        actualInjectionTime = simulator.getSimulationTime();
        if (proposerA == null || proposerB == null) {
            throw new IllegalStateException("Partition proposers are not configured.");
        }

        if (!groupAIds.contains(proposerA.getNodeID()) || !groupBIds.contains(proposerB.getNodeID())) {
            throw new IllegalStateException("Conflict proposers are not in opposite partitions.");
        }

        if (proposerA.getCurrentPreferredBlock().getHeight() != 0 || proposerB.getCurrentPreferredBlock().getHeight() != 0) {
            throw new IllegalStateException("Both proposers must still prefer Genesis.");
        }

        /*
         * The cut is active before either candidate is generated.
         */
        generateNewBlock(proposerA);
        generateNewBlock(proposerB);
        candidateA = proposerA.getBlockLocalCache().get(CONFLICT_HEIGHT);
        candidateB = proposerB.getBlockLocalCache().get(CONFLICT_HEIGHT);
        validateInjectedConflict();
        double healDelay = PARTITION_HEAL_TIME - actualInjectionTime;
        simulator.putEvent(this::healPartition, healDelay);
        System.out.println("[Experiment 3] Conflicting candidates injected at t=" + actualInjectionTime + " s.");

        System.out.println(
                "[Experiment 3] Group A size="
                + groupAIds.size()
                + ", proposer="
                + proposerA.getNodeID()
                + ", candidate cycle="
                + candidateA.getCycleNumber()
                + ".");

        System.out.println(
                "[Experiment 3] Group B size="
                + groupBIds.size()
                + ", proposer="
                + proposerB.getNodeID()
                + ", candidate cycle="
                + candidateB.getCycleNumber()
                + ".");

        System.out.println( "[Experiment 3] Partition will heal at t=" + PARTITION_HEAL_TIME + " s.");
    }

    private void validateInjectedConflict() {
        if (candidateA == null || candidateB == null) {
            throw new IllegalStateException("Failed to generate both partition candidates.");
        }

        if (candidateA.getHash() == candidateB.getHash()) {
            throw new IllegalStateException("Partition candidates must be distinct.");
        }

        if (candidateA.getHeight() != candidateB.getHeight()) {
            throw new IllegalStateException("Partition candidates must have the same height.");
        }

        if (candidateA.getParent().getHash() != candidateB.getParent().getHash()) {
            throw new IllegalStateException("Partition candidates must share Genesis as parent.");
        }
    }

    /**
     * Capture the isolated state before reconnecting the two halves.
     * Then restore the original overlay and enable ordinary WAN latency.
     */
    private void healPartition() {
        capturePreHealState();
        actualPartitionHealTime = simulator.getSimulationTime();
        restoreOriginalNeighborCaches();
        partitionHealed = true;
        System.out.println("[Experiment 3] Partition healed at t=" + actualPartitionHealTime + " s.");

        System.out.println(
                "[Experiment 3] Before healing: committed A="
                + preHealCommittedA
                + ", committed B="
                + preHealCommittedB
                + ", other="
                + preHealCommittedOther
                + ", uncommitted="
                + preHealUncommitted
                + ".");

        System.out.println(
                "[Experiment 3] Before healing: quorum="
                + preHealQuorumSize
                + ", max confirmations A="
                + preHealMaxConfirmationsA
                + ", max confirmations B="
                + preHealMaxConfirmationsB
                + ".");

        System.out.println(
                "[Experiment 3] Candidate visibility before healing: "
                + "A in group A="
                + preHealAVisibleInGroupA
                + ", A in group B="
                + preHealAVisibleInGroupB
                + ", B in group A="
                + preHealBVisibleInGroupA
                + ", B in group B="
                + preHealBVisibleInGroupB
                + ".");
    }

    /**
     * Keep the NCP cut stable throughout the partition.
     *
     * An old pre-cut Push/Pull can temporarily reintroduce a cross-side
     * neighbor. Sanitizing immediately before every normal node cycle
     * prevents that entry from becoming a new gossip destination.
     */
    @Override
    protected boolean shouldProcessNodeCycle(final BECPNode node) {
        if (partitionStarted && !partitionHealed) {
            sanitizeNodeNeighborCache(node);
        }

        return true;
    }

    private void sanitizeNodeNeighborCache(final BECPNode node) {
        boolean nodeInA = groupAIds.contains(node.getNodeID());
        node.getNeighborsLocalCache().removeIf(neighbor -> {
                    boolean neighborInA = groupAIds.contains(neighbor.getNodeID());
                    return nodeInA != neighborInA;
                });

        /*
         * A stale message could theoretically leave a very small cache.
         * Refill only from the node's original same-side neighbors.
         */
        if (node.getNeighborsLocalCache().isEmpty()) {
            ArrayList<BECPNode> original = originalNeighborCaches.get(node.getNodeID());
            if (original != null) {
                for (BECPNode neighbor : original) {
                    boolean neighborInA = groupAIds.contains(neighbor.getNodeID());
                    if (nodeInA == neighborInA) {
                        node.getNeighborsLocalCache().add(neighbor);
                    }
                }
            }
        }

        if (node.getNeighborsLocalCache().isEmpty()) {
            throw new IllegalStateException("Partition left node " + node.getNodeID() + " without an intra-partition neighbor.");
        }
    }

    /**
     * Implements a real 50/50 NCP partition without dropping protocol mass.
     *
     * Each node keeps only neighbors in its own half. Because NCP chooses
     * destinations from this cache and exchanges copies of that cache,
     * all gossip remains inside the corresponding half until healing.
     */
    private void applyPartitionedNeighborCaches() {
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            ArrayList<BECPNode> original = originalNeighborCaches.get(node.getNodeID());
            if (original == null) {
                throw new IllegalStateException("Missing original neighbor cache for node " + node.getNodeID());
            }

            node.getNeighborsLocalCache().clear();
            node.getNeighborsLocalCache().addAll(original);
            sanitizeNodeNeighborCache(node);
        }
    }

    private void validatePartitionedNeighborCaches() {
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            boolean nodeInA = groupAIds.contains(node.getNodeID());
            for (BECPNode neighbor : node.getNeighborsLocalCache()) {
                boolean neighborInA = groupAIds.contains(neighbor.getNodeID());
                if (nodeInA != neighborInA) {
                    throw new IllegalStateException(
                            "Partition isolation failure: node "
                            + node.getNodeID()
                            + " contains cross-partition neighbor "
                            + neighbor.getNodeID()
                            + ".");
                }
            }
        }
    }

    private void restoreOriginalNeighborCaches() {
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            ArrayList<BECPNode> original = originalNeighborCaches.get(node.getNodeID());
            if (original == null) {
                throw new IllegalStateException("Missing original neighbor cache for node " + node.getNodeID());
            }

            node.getNeighborsLocalCache().clear();
            node.getNeighborsLocalCache().addAll(original);
        }
    }

    private void capturePreHealState() {
        validatePartitionedNeighborCaches();
        preHealCommittedA = 0;
        preHealCommittedB = 0;
        preHealCommittedOther = 0;
        preHealUncommitted = 0;
        preHealFinalVotesA = 0;
        preHealFinalVotesB = 0;
        preHealFinalVotesOther = 0;
        preHealNoFinalVote = 0;
        preHealMaxConfirmationsA = 0;
        preHealMaxConfirmationsB = 0;

        /*
         * M_h is the full network membership. Even if a node has not yet
         * entered CONFIRMATION and therefore has no stored snapshot, the
         * experiment's required strict-majority threshold is known.
         */
        preHealQuorumSize = (experimentNumNodes / EXPECTED_QUORUM_DIVISOR) + 1;
        preHealAVisibleInGroupA = 0;
        preHealAVisibleInGroupB = 0;
        preHealBVisibleInGroupA = 0;
        preHealBVisibleInGroupB = 0;

        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            BECPBlock committed = findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
            String committedLabel = labelForBlock(committed);
            if ("A".equals(committedLabel)) {
                preHealCommittedA++;
            } else if ("B".equals(committedLabel)) {
                preHealCommittedB++;
            } else if ("OTHER".equals(committedLabel)) {
                preHealCommittedOther++;
            } else {
                preHealUncommitted++;
            }

            String voteLabel = labelForHash(node.getPersistentFinalVote(CONFLICT_HEIGHT));
            if ("A".equals(voteLabel)) {
                preHealFinalVotesA++;
            } else if ("B".equals(voteLabel)) {
                preHealFinalVotesB++;
            } else if ("OTHER".equals(voteLabel)) {
                preHealFinalVotesOther++;
            } else {
                preHealNoFinalVote++;
            }

            if (candidateA != null) {preHealMaxConfirmationsA = Math.max(preHealMaxConfirmationsA, node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateA));
            }

            if (candidateB != null) {
                preHealMaxConfirmationsB = Math.max(preHealMaxConfirmationsB, node.getFinalConfirmationCount(CONFLICT_HEIGHT, candidateB));
            }

            BECPBlock cached = node.getBlockLocalCache().get(CONFLICT_HEIGHT);
            String cachedLabel = labelForBlock(cached);

            if (groupAIds.contains(node.getNodeID())) {
                if ("A".equals(cachedLabel)) {
                    preHealAVisibleInGroupA++;
                } else if ("B".equals(cachedLabel)) {
                    preHealBVisibleInGroupA++;
                }
            } else {
                if ("A".equals(cachedLabel)) {
                    preHealAVisibleInGroupB++;
                } else if ("B".equals(cachedLabel)) {
                    preHealBVisibleInGroupB++;
                }
            }

            MembershipSnapshot snapshot = node.getMembershipSnapshot(CONFLICT_HEIGHT);
            if (snapshot != null && snapshot.getQuorumSize() != preHealQuorumSize) {
                throw new IllegalStateException(
                        "Unexpected quorum size "
                        + snapshot.getQuorumSize()
                        + " at height "
                        + CONFLICT_HEIGHT
                        + "; expected "
                        + preHealQuorumSize
                        + ".");
            }
        }
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

    private void writeExperimentOutputs(
            final boolean baseSafetyOraclePassed,
            final String failureMessage)
            throws IOException {List<BECPNode> nodes = network.getAllNodes();
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
        int quorumSize = (experimentNumNodes / EXPECTED_QUORUM_DIVISOR) + 1;

        Set<Hash> distinctCommittedHashes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BECPNode node : nodes) {
            BECPBlock committed = findCommittedBlockAtHeight(node, CONFLICT_HEIGHT);
            if (committed == null) {
                uncommitted++;
            } else {
                distinctCommittedHashes.add(committed.getHash());
                String label =labelForBlock(committed);
                if ("A".equals(label)) {
                    committedA++;
                } else if ("B".equals(label)) {
                    committedB++;
                } else {
                    committedOther++;
                }
            }
            String voteLabel = labelForHash(node.getPersistentFinalVote(CONFLICT_HEIGHT));
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
            if (snapshot != null && snapshot.getQuorumSize()!= quorumSize) {
                throw new IllegalStateException(
                        "Unexpected final quorum size "
                        + snapshot.getQuorumSize()
                        + "; expected "
                        + quorumSize
                        + ".");
            }
        }

        int totalCommitted = committedA + committedB + committedOther;
        boolean progressObserved = totalCommitted > 0;
        boolean fullyConverged = (committedA == experimentNumNodes) || (committedB == experimentNumNodes);
        boolean partitionIsolationRespected = preHealAVisibleInGroupB == 0 && preHealBVisibleInGroupA == 0;
        boolean preHealQuorumRespected =
                preHealCommittedA == 0
                && preHealCommittedB == 0
                && preHealCommittedOther == 0
                && preHealMaxConfirmationsA < preHealQuorumSize
                && preHealMaxConfirmationsB < preHealQuorumSize;

        boolean safetyPassed =
                baseSafetyOraclePassed
                && partitionStarted
                && partitionHealed
                && partitionIsolationRespected
                && preHealQuorumRespected
                && distinctCommittedHashes.size() <= 1
                && committedOther == 0
                && finalVotesOther == 0;

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
                fullyConverged,
                partitionIsolationRespected,
                preHealQuorumRespected,
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
                fullyConverged,
                preHealQuorumRespected,
                winner,
                failureMessage);
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
            final boolean fullyConverged,
            final boolean partitionIsolationRespected,
            final boolean preHealQuorumRespected,
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
                    + "partition_start_time,"
                    + "partition_stabilization_time,"
                    + "conflict_injection_time,"
                    + "partition_heal_time,"
                    + "partition_duration,"
                    + "candidate_isolation_duration,"
                    + "group_a_size,"
                    + "group_b_size,"
                    + "candidate_height,"
                    + "candidate_a_creator,"
                    + "candidate_b_creator,"
                    + "candidate_a_cycle,"
                    + "candidate_b_cycle,"
                    + "pre_heal_quorum_size,"
                    + "pre_heal_committed_a,"
                    + "pre_heal_committed_b,"
                    + "pre_heal_committed_other,"
                    + "pre_heal_uncommitted,"
                    + "pre_heal_final_votes_a,"
                    + "pre_heal_final_votes_b,"
                    + "pre_heal_final_votes_other,"
                    + "pre_heal_no_final_vote,"
                    + "pre_heal_max_confirmations_a,"
                    + "pre_heal_max_confirmations_b,"
                    + "pre_heal_a_visible_group_a,"
                    + "pre_heal_a_visible_group_b,"
                    + "pre_heal_b_visible_group_a,"
                    + "pre_heal_b_visible_group_b,"
                    + "partition_isolation_respected,"
                    + "pre_heal_quorum_respected,"
                    + "quorum_size,"
                    + "committed_a_nodes,"
                    + "committed_b_nodes,"
                    + "committed_other_nodes,"
                    + "uncommitted_nodes,"
                    + "distinct_committed_candidates,"
                    + "winner,"
                    + "safety_pass,"
                    + "progress_observed,"
                    + "fully_converged,"
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
                    "partition-50-50,"
                    + experimentSeed + ","
                    + experimentNumNodes + ","
                    + experimentDuration + ","
                    + simulator.getSimulationTime() + ","
                    + actualPartitionStartTime + ","
                    + PARTITION_STABILIZATION_TIME + ","
                    + actualInjectionTime + ","
                    + actualPartitionHealTime + ","
                    + (actualPartitionHealTime
                    - actualPartitionStartTime) + ","
                    + (actualPartitionHealTime
                    - actualInjectionTime) + ","
                    + groupAIds.size() + ","
                    + groupBIds.size() + ","
                    + CONFLICT_HEIGHT + ","
                    + creatorId(candidateA) + ","
                    + creatorId(candidateB) + ","
                    + cycleNumber(candidateA) + ","
                    + cycleNumber(candidateB) + ","
                    + preHealQuorumSize + ","
                    + preHealCommittedA + ","
                    + preHealCommittedB + ","
                    + preHealCommittedOther + ","
                    + preHealUncommitted + ","
                    + preHealFinalVotesA + ","
                    + preHealFinalVotesB + ","
                    + preHealFinalVotesOther + ","
                    + preHealNoFinalVote + ","
                    + preHealMaxConfirmationsA + ","
                    + preHealMaxConfirmationsB + ","
                    + preHealAVisibleInGroupA + ","
                    + preHealAVisibleInGroupB + ","
                    + preHealBVisibleInGroupA + ","
                    + preHealBVisibleInGroupB + ","
                    + partitionIsolationRespected + ","
                    + preHealQuorumRespected + ","
                    + quorumSize + ","
                    + committedA + ","
                    + committedB + ","
                    + committedOther + ","
                    + uncommitted + ","
                    + distinctCommittedCandidates + ","
                    + winner + ","
                    + safetyPassed + ","
                    + progressObserved + ","
                    + fullyConverged + ","
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
                    + "partition,"
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
                        + partitionLabel(node.getNodeID()) + ","
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

    private void writeMetadata(final boolean safetyPassed, final boolean progressObserved, final boolean fullyConverged, final boolean preHealQuorumRespected, final String winner, final String failureMessage) throws IOException {
        File metadataFile = new File(experimentDirectory, "metadata.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile, false))) {
            writer.write("experiment=partition-50-50");
            writer.newLine();
            writer.write("seed=" + experimentSeed);
            writer.newLine();
            writer.write("nodes=" + experimentNumNodes);
            writer.newLine();
            writer.write("simulation_duration=" + experimentDuration);
            writer.newLine();
            writer.write("partition_start_time=" + actualPartitionStartTime);
            writer.newLine();
            writer.write("partition_stabilization_time="+ PARTITION_STABILIZATION_TIME);
            writer.newLine();
            writer.write("conflict_injection_time=" + actualInjectionTime);
            writer.newLine();
            writer.write("partition_heal_time=" + actualPartitionHealTime);
            writer.newLine();
            writer.write( "partition_duration=" + (actualPartitionHealTime - actualPartitionStartTime));
            writer.newLine();
            writer.write("group_a_size=" + groupAIds.size());
            writer.newLine();
            writer.write("group_b_size=" + groupBIds.size());
            writer.newLine();
            writer.write("candidate_a_creator=" + creatorId(candidateA));
            writer.newLine();
            writer.write("candidate_b_creator=" + creatorId(candidateB));
            writer.newLine();
            writer.write("pre_heal_quorum_size=" + preHealQuorumSize);
            writer.newLine();
            writer.write("pre_heal_max_confirmations_a=" + preHealMaxConfirmationsA);
            writer.newLine();
            writer.write("pre_heal_max_confirmations_b=" + preHealMaxConfirmationsB);
            writer.newLine();
            writer.write("pre_heal_a_visible_group_a=" + preHealAVisibleInGroupA);
            writer.newLine();
            writer.write("pre_heal_a_visible_group_b=" + preHealAVisibleInGroupB);
            writer.newLine();
            writer.write("pre_heal_b_visible_group_a=" + preHealBVisibleInGroupA);
            writer.newLine();
            writer.write( "pre_heal_b_visible_group_b=" + preHealBVisibleInGroupB);
            writer.newLine();
            writer.write("pre_heal_quorum_respected=" + preHealQuorumRespected);
            writer.newLine();
            writer.write("safety_pass=" + safetyPassed);
            writer.newLine();
            writer.write("progress_observed=" + progressObserved);
            writer.newLine();
            writer.write("fully_converged=" + fullyConverged);
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

    private String partitionLabel(
            final int nodeId) {
        if (groupAIds.contains(nodeId)) {
            return "A";
        }
        if (groupBIds.contains(nodeId)) {
            return "B";
        }

        return "UNKNOWN";
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
        if (gitCommit == null|| gitCommit.isBlank()) {
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

    public static void main(final String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        long seed =Long.parseLong(options.getOrDefault("seed", "1"));
        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NUM_NODES)));
        double duration = Double.parseDouble(options.getOrDefault("duration", Double.toString(DEFAULT_SIMULATION_TIME)));
        PartitionScenario scenario = new PartitionScenario(
                        "Journal Experiment 3 - "
                        + "Temporary 50/50 Partition and Healing",
                        seed,
                        nodes,
                        duration);

        scenario.AddNewLogger(new BECPCSVLogger(new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));
        scenario.run();
        System.out.println("[Experiment 3] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
    }

    private static Map<String, String> parseArguments(final String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + argument);
            }

            options.put(argument.substring(2), args[++i]);
        }

        return options;
    }
}
