package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Hash;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.PushEntry;
import jabs.ledgerdata.becp.RecoveryEntry;
import jabs.ledgerdata.becp.RecoveryExchangeId;
import jabs.ledgerdata.becp.RecoveryExchangeState;
import jabs.log.BECPCSVLogger;
import jabs.network.networks.becp.BECPWANNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;
import jabs.simulator.randengine.RandomnessEngine;

/**
 * Experiment 5:
 * False crash suspicion caused by a deliberately delayed REAP+ Pull.
 * The experiment does not crash any node.
 * 1. Quiesce normal node cycles so pre-fault traffic can drain.
 * 2. Force one critical Push from node 0 to node 1.
 * 3. Delay exactly the first node-1 -> node-0 packet after that Push.
 *    Because node 1 is cycle-paused, this packet is the Pull response.
 * 4. Node 0 reaches PULL_TIMEOUT, restores its sender-side mass,
 *    marks node 1 as suspected, and sends a notification RePush.
 * 5. Node 1 observes the notification RePush and subsequently performs
 *    receiver-side recovery while node 0 is in fact alive.
 * 6. The original delayed Pull is eventually delivered. The frozen
 *    exact-once recovery state must prevent duplicate mass application.
 * 7. Resume the system and inject one ordinary block to check continued
 *    consensus progress without any real crash.
 */
public class FalseCrashSuspicionScenario extends BECPScenario {
    public static final int DEFAULT_NUM_NODES = 1000;
    public static final double DEFAULT_SIMULATION_TIME = 75.0;
    private static final double QUIESCE_TIME = 8.0;
    private static final double FAULT_ARM_TIME = 10.0;
    private static final double BLOCK_INJECTION_TIME = 15.0;

    /*
     * One normal BECP cycle is ~0.351 s in the current WAN model.
     * Four cycles is deliberately longer than PULL_TIMEOUT=1 cycle.
     */
    private static final double DELAYED_PULL_LATENCY = 4.0 * CYCLE_TIME;
    private static final double CACHE_RESTORE_DELAY = 0.05;
    private static final double PAUSE_SENDER_AFTER = CYCLE_TIME + 0.01;
    private static final double ALLOW_TARGET_AFTER = CYCLE_TIME + MAX_LATENCY + 0.08;
    private static final double OBSERVE_AFTER = DELAYED_PULL_LATENCY + (2.0 * MAX_LATENCY) + 0.25;
    private static final int SENDER_ID = 0;
    private static final int TARGET_ID = 1;
    private static final int BLOCK_PROPOSER_ID = 2;
    private static final int BLOCK_HEIGHT = 1;
    private final long experimentSeed;
    private final int experimentNumNodes;
    private final double experimentDuration;
    private final File experimentDirectory;
    private DelayedPullBECPWANNetwork delayedPullNetwork;
    private BECPNode sender;
    private BECPNode target;
    private BECPNode blockProposer;
    private ArrayList<BECPNode> senderOriginalNeighbors = new ArrayList<>();
    private boolean quiescing = false;
    private boolean faultArmed = false;
    private boolean faultTriggered = false;
    private boolean senderPaused = false;
    private boolean targetAllowed = false;
    private boolean observationComplete = false;
    private double actualFaultTime = Double.NaN;
    private int faultCycle = -1;
    private int targetedExchangeCycle = -1;
    private RecoveryExchangeId targetedExchangeId = null;
    private boolean senderSuspectedTarget = false;
    private boolean targetSuspectedSender = false;
    private boolean targetExchangeObserved = false;
    private String targetExchangeStateAtObservation = "MISSING";
    private int targetExchangeTimeoutAtObservation = -1;
    private int actualCrashedNodesAtObservation = -1;
    private int senderCrashedSetSizeAtObservation = -1;
    private int targetCrashedSetSizeAtObservation = -1;
    private BECPBlock candidate = null;

    public FalseCrashSuspicionScenario(final String name, final long seed, final int numOfNodes, final double simulationStopTime) {
        super(name, seed, numOfNodes, simulationStopTime);

        if (numOfNodes <= BLOCK_PROPOSER_ID) {
            throw new IllegalArgumentException("Experiment 5 requires at least 3 nodes.");
        }

        if (!BECP.REAP_PLUS) {
            throw new IllegalStateException("Experiment 5 requires REAP+." + " Run with -Djabs.becp.reapPlus=true.");
        }

        if (BECP.SSEP) {
            throw new IllegalStateException("Experiment 5 requires SSEP disabled when REAP+ is active." + " Run with -Djabs.becp.ssep=false.");
        }

        if (simulationStopTime <= BLOCK_INJECTION_TIME + 10.0) {
            throw new IllegalArgumentException("Simulation horizon is too short for Experiment 5.");
        }

        this.experimentSeed = seed;
        this.experimentNumNodes = numOfNodes;
        this.experimentDuration = simulationStopTime;
        this.experimentDirectory = new File("output/journal/false-crash/" + "nodes-" + numOfNodes + "/seed-" + seed);

        if (!experimentDirectory.exists() && !experimentDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create experiment output directory: " + experimentDirectory);
        }
    }

    @Override
    protected BECPWANNetwork createBECPNetwork() {
        delayedPullNetwork = new DelayedPullBECPWANNetwork(this.randomnessEngine);
        return delayedPullNetwork;
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
        sender = (BECPNode) network.getNode(SENDER_ID);
        target = (BECPNode) network.getNode(TARGET_ID);
        blockProposer = (BECPNode) network.getNode(BLOCK_PROPOSER_ID);
        scheduleAtAbsoluteTime(QUIESCE_TIME, this::beginQuiescence);
        scheduleAtAbsoluteTime(FAULT_ARM_TIME, this::armFalseSuspicionFault);
        scheduleAtAbsoluteTime(BLOCK_INJECTION_TIME, this::injectPostFaultBlock);
    }

    private void scheduleAtAbsoluteTime(final double absoluteTime, final Runnable action) {
        double delay = absoluteTime - simulator.getSimulationTime();
        if (delay < 0.0) {
            throw new IllegalStateException("Cannot schedule event in the past: " + absoluteTime);
        }

        simulator.putEvent(action::run, delay);
    }

    private void beginQuiescence() {
        quiescing = true;
        System.out.println("[Experiment 5] Quiescing node cycles at t=" + simulator.getSimulationTime() + " s so existing traffic can drain.");
    }

    private void armFalseSuspicionFault() {
        faultArmed = true;
        System.out.println("[Experiment 5] False-suspicion fault armed at t=" + simulator.getSimulationTime() + " s.");
    }

    @Override
    protected boolean shouldProcessNodeCycle(final BECPNode node) {
        /*
         * The first sender cycle after t=10 creates the controlled
         * recovery exchange.
         */
        if (faultArmed && !faultTriggered && node.getNodeID() == SENDER_ID) {
            triggerControlledExchange(node);
            return true;
        }

        if (!quiescing) {
            return true;
        }

        if (node.getNodeID() == SENDER_ID) {
            return faultTriggered && !senderPaused;
        }

        if (node.getNodeID() == TARGET_ID) {
            return targetAllowed;
        }

        return false;
    }

    private void triggerControlledExchange(final BECPNode node) {
        faultTriggered = true;
        actualFaultTime = simulator.getSimulationTime();
        faultCycle = node.getCycleNumber();

        /*
         * BECP.newCycle() increments the node cycle before creating the
         * PushEntry / RecoveryExchangeId for this controlled Push.
         */
        targetedExchangeCycle = faultCycle + 1;
        senderOriginalNeighbors = new ArrayList<>(sender.getNeighborsLocalCache());

        /*
         * Force exactly this cycle's ordinary NCP destination to node 1.
         * The original cache is restored shortly afterward, before the
         * sender's timeout cycle.
         */
        sender.getNeighborsLocalCache().clear();
        sender.getNeighborsLocalCache().add(target);
        delayedPullNetwork.armOneDelayedReversePacket(TARGET_ID, SENDER_ID, DELAYED_PULL_LATENCY);
        simulator.putEvent(this::captureTargetedExchange, 0.001);
        simulator.putEvent(this::restoreSenderNeighborCache, CACHE_RESTORE_DELAY);
        simulator.putEvent(() -> senderPaused = true, PAUSE_SENDER_AFTER);
        simulator.putEvent(() -> targetAllowed = true, ALLOW_TARGET_AFTER);
        simulator.putEvent(this::observeFaultAndRelease, OBSERVE_AFTER);

        System.out.println(
                "[Experiment 5] Controlled critical Push will originate"
                + " from node "
                + SENDER_ID
                + " to live node "
                + TARGET_ID
                + " at t="
                + actualFaultTime
                + " s (cycle "
                + faultCycle
                + ").");

        System.out.println(
                "[Experiment 5] The first node "
                + TARGET_ID
                + " -> node "
                + SENDER_ID
                + " packet will be delayed by "
                + DELAYED_PULL_LATENCY
                + " s.");
    }

    private void captureTargetedExchange() {
        PushEntry newest = null;
        for (PushEntry entry : sender.getPushEntriesBuffer()) {
            if (entry.getDestination().getNodeID() != TARGET_ID) {
                continue;
            }

            if (entry.getCycleNumber()!= targetedExchangeCycle) {
                continue;
            }

            newest = entry;
        }

        if (newest == null) {
            throw new IllegalStateException("Could not find the controlled sender-side PushEntry.");
        }

        targetedExchangeId = newest.getRecoveryExchangeId();
        if (targetedExchangeId == null) {
            throw new IllegalStateException("Controlled REAP+ PushEntry has no recovery exchange ID.");
        }

        System.out.println("[Experiment 5] Targeted recovery exchange: " + targetedExchangeId);
    }

    private void restoreSenderNeighborCache() {
        sender.getNeighborsLocalCache().clear();
        sender.getNeighborsLocalCache().addAll(senderOriginalNeighbors);
    }

    private void observeFaultAndRelease() {
        senderSuspectedTarget = sender.getCrashedNodes().contains(target);
        targetSuspectedSender = target.getCrashedNodes().contains(sender);
        actualCrashedNodesAtObservation = countActualCrashes();
        senderCrashedSetSizeAtObservation = sender.getCrashedNodes().size();
        targetCrashedSetSizeAtObservation = target.getCrashedNodes().size();

        if (targetedExchangeId != null) {
            RecoveryEntry receiverEntry = target.getRecoveryExchangeCache().get(targetedExchangeId);
            if (receiverEntry != null) {
                targetExchangeObserved = true;
                RecoveryExchangeState state = receiverEntry.getRecoveryExchangeState();
                targetExchangeStateAtObservation = state == null ? "NULL" : state.name();
                targetExchangeTimeoutAtObservation = receiverEntry.getTimeout();
            }
        }

        observationComplete = true;

        /*
         * Resume the complete system. The post-fault block injected at
         * t=15 then checks that ordinary consensus can still progress.
         */
        quiescing = false;
        senderPaused = false;
        targetAllowed = true;

        System.out.println(
                "[Experiment 5] Observation complete at t="
                + simulator.getSimulationTime()
                + " s.");

        System.out.println(
                "[Experiment 5] sender_suspected_target="
                + senderSuspectedTarget
                + ", target_suspected_sender="
                + targetSuspectedSender
                + ", actual_crashes="
                + actualCrashedNodesAtObservation
                + ".");

        System.out.println(
                "[Experiment 5] delayed_reverse_packets="
                + delayedPullNetwork.getDelayedPacketCount()
                + ", receiver_exchange_state="
                + targetExchangeStateAtObservation
                + ", receiver_exchange_timeout="
                + targetExchangeTimeoutAtObservation
                + ".");
    }

    private int countActualCrashes() {
        int crashes = 0;
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            if (node.isCrashed) {
                crashes++;
            }
        }

        return crashes;
    }

    private void injectPostFaultBlock() {
        if (!observationComplete) {
            /*
             * Under the current constants this should never happen, but
             * scheduling the block only after the fault observation keeps
             * the experiment semantically unambiguous.
             */
            simulator.putEvent(this::injectPostFaultBlock, CYCLE_TIME);

            return;
        }

        if (blockProposer.getCurrentPreferredBlock().getHeight() != 0) {
            throw new IllegalStateException("Post-fault proposer must still prefer Genesis.");
        }

        generateNewBlock(blockProposer);
        candidate = blockProposer.getBlockLocalCache().get(BLOCK_HEIGHT);
        if (candidate == null) {
            throw new IllegalStateException("Failed to create post-fault candidate.");
        }

        System.out.println(
                "[Experiment 5] Post-fault block injected at t="
                + simulator.getSimulationTime()
                + " s by node "
                + BLOCK_PROPOSER_ID
                + ".");
    }

    @Override
    public void run()
            throws IOException {
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
        int actualCrashesFinal = countActualCrashes();
        int committedCandidate = 0;
        int committedOther = 0;
        int uncommitted = 0;
        Set<Hash> distinctCommittedHashes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BECPNode node : nodes) {
            BECPBlock committed = findCommittedBlockAtHeight(node, BLOCK_HEIGHT);
            if (committed == null) {
                uncommitted++;
                continue;
            }
            distinctCommittedHashes.add(committed.getHash());
            if (candidate != null && committed.getHash() == candidate.getHash()) {
                committedCandidate++;
            } else {
                committedOther++;
            }
        }

        boolean falseSuspicionObserved = senderSuspectedTarget;
        boolean noRealCrash = actualCrashedNodesAtObservation == 0 && actualCrashesFinal == 0;
        boolean delayedPullInjected = delayedPullNetwork != null && delayedPullNetwork.getDelayedPacketCount() == 1;
        boolean recoveryExchangeTracked = targetedExchangeId != null && targetExchangeObserved;
        boolean exactOnceTerminalObserved = "RESTORED".equals(targetExchangeStateAtObservation) || "MERGED".equals(targetExchangeStateAtObservation);
        boolean progressObserved = committedCandidate > 0;
        boolean fullyConverged = committedCandidate == experimentNumNodes;
        boolean safetyPassed =
                baseSafetyOraclePassed
                && observationComplete
                && falseSuspicionObserved
                && noRealCrash
                && delayedPullInjected
                && recoveryExchangeTracked
                && exactOnceTerminalObserved
                && committedOther == 0
                && distinctCommittedHashes.size() <= 1;

        double averageCommitLatency = BECPScenario.consensusTimes.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double minimumCommitLatency = BECPScenario.consensusTimes.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double maximumCommitLatency = BECPScenario.consensusTimes.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);

        writeSummary(
                actualCrashesFinal,
                committedCandidate,
                committedOther,
                uncommitted,
                distinctCommittedHashes.size(),
                falseSuspicionObserved,
                noRealCrash,
                delayedPullInjected,
                recoveryExchangeTracked,
                exactOnceTerminalObserved,
                progressObserved,
                fullyConverged,
                safetyPassed,
                averageCommitLatency,
                minimumCommitLatency,
                maximumCommitLatency,
                failureMessage);

        writeMetadata(
                actualCrashesFinal,
                falseSuspicionObserved,
                noRealCrash,
                delayedPullInjected,
                recoveryExchangeTracked,
                exactOnceTerminalObserved,
                progressObserved,
                fullyConverged,
                safetyPassed,
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

    private void writeSummary(
            final int actualCrashesFinal,
            final int committedCandidate,
            final int committedOther,
            final int uncommitted,
            final int distinctCommittedCandidates,
            final boolean falseSuspicionObserved,
            final boolean noRealCrash,
            final boolean delayedPullInjected,
            final boolean recoveryExchangeTracked,
            final boolean exactOnceTerminalObserved,
            final boolean progressObserved,
            final boolean fullyConverged,
            final boolean safetyPassed,
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
                    + "quiesce_time,"
                    + "fault_arm_time,"
                    + "actual_fault_time,"
                    + "fault_cycle_before_new_cycle,"
                    + "targeted_exchange_cycle,"
                    + "sender_id,"
                    + "target_id,"
                    + "delayed_pull_latency,"
                    + "delayed_reverse_packets,"
                    + "targeted_exchange_id,"
                    + "sender_suspected_target,"
                    + "target_suspected_sender,"
                    + "sender_crashed_set_size_observation,"
                    + "target_crashed_set_size_observation,"
                    + "actual_crashes_observation,"
                    + "actual_crashes_final,"
                    + "receiver_exchange_observed,"
                    + "receiver_exchange_state_observation,"
                    + "receiver_exchange_timeout_observation,"
                    + "false_suspicion_observed,"
                    + "no_real_crash,"
                    + "delayed_pull_injected,"
                    + "recovery_exchange_tracked,"
                    + "exact_once_terminal_observed,"
                    + "candidate_creator,"
                    + "committed_candidate_nodes,"
                    + "committed_other_nodes,"
                    + "uncommitted_nodes,"
                    + "distinct_committed_candidates,"
                    + "progress_observed,"
                    + "fully_converged,"
                    + "safety_pass,"
                    + "avg_commit_latency,"
                    + "min_commit_latency,"
                    + "max_commit_latency,"
                    + "messages,"
                    + "message_bytes,"
                    + "git_commit,"
                    + "failure_message");

            writer.newLine();
            writer.write(
                    "false-crash,"
                    + experimentSeed + ","
                    + experimentNumNodes + ","
                    + experimentDuration + ","
                    + simulator.getSimulationTime() + ","
                    + QUIESCE_TIME + ","
                    + FAULT_ARM_TIME + ","
                    + actualFaultTime + ","
                    + faultCycle + ","
                    + targetedExchangeCycle + ","
                    + SENDER_ID + ","
                    + TARGET_ID + ","
                    + DELAYED_PULL_LATENCY + ","
                    + delayedPullNetwork.getDelayedPacketCount()
                    + ","
                    + sanitiseCsv(String.valueOf(targetedExchangeId))
                    + ","
                    + senderSuspectedTarget + ","
                    + targetSuspectedSender + ","
                    + senderCrashedSetSizeAtObservation + ","
                    + targetCrashedSetSizeAtObservation + ","
                    + actualCrashedNodesAtObservation + ","
                    + actualCrashesFinal + ","
                    + targetExchangeObserved + ","
                    + targetExchangeStateAtObservation + ","
                    + targetExchangeTimeoutAtObservation + ","
                    + falseSuspicionObserved + ","
                    + noRealCrash + ","
                    + delayedPullInjected + ","
                    + recoveryExchangeTracked + ","
                    + exactOnceTerminalObserved + ","
                    + BLOCK_PROPOSER_ID + ","
                    + committedCandidate + ","
                    + committedOther + ","
                    + uncommitted + ","
                    + distinctCommittedCandidates + ","
                    + progressObserved + ","
                    + fullyConverged + ","
                    + safetyPassed + ","
                    + averageCommitLatency + ","
                    + minimumCommitLatency + ","
                    + maximumCommitLatency + ","
                    + BECPCSVLogger.numMessage + ","
                    + BECPCSVLogger.messageSize + ","
                    + sanitiseCsv(getGitCommit())
                    + ","
                    + sanitiseCsv(failureMessage));

            writer.newLine();
        }
    }

    private void writeMetadata(
            final int actualCrashesFinal,
            final boolean falseSuspicionObserved,
            final boolean noRealCrash,
            final boolean delayedPullInjected,
            final boolean recoveryExchangeTracked,
            final boolean exactOnceTerminalObserved,
            final boolean progressObserved,
            final boolean fullyConverged,
            final boolean safetyPassed,
            final String failureMessage)
            throws IOException {

        File metadataFile = new File(experimentDirectory, "metadata.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile, false))) {
            writer.write("experiment=false-crash");
            writer.newLine();
            writer.write("seed=" + experimentSeed);
            writer.newLine();
            writer.write("nodes=" + experimentNumNodes);
            writer.newLine();
            writer.write("simulation_duration=" + experimentDuration);
            writer.newLine();
            writer.write("reap_plus=" + BECP.REAP_PLUS);
            writer.newLine();
            writer.write("ssep=" + BECP.SSEP);
            writer.newLine();
            writer.write("actual_fault_time=" + actualFaultTime);
            writer.newLine();
            writer.write("targeted_exchange_id=" + targetedExchangeId);
            writer.newLine();
            writer.write("sender_suspected_target=" + senderSuspectedTarget);
            writer.newLine();
            writer.write("target_suspected_sender=" + targetSuspectedSender);
            writer.newLine();
            writer.write("actual_crashes_final=" + actualCrashesFinal);
            writer.newLine();
            writer.write("receiver_exchange_state_observation=" + targetExchangeStateAtObservation);
            writer.newLine();
            writer.write("false_suspicion_observed="+ falseSuspicionObserved);
            writer.newLine();
            writer.write("no_real_crash=" + noRealCrash);
            writer.newLine();
            writer.write("delayed_pull_injected=" + delayedPullInjected);
            writer.newLine();
            writer.write("recovery_exchange_tracked=" + recoveryExchangeTracked);
            writer.newLine();
            writer.write("exact_once_terminal_observed=" + exactOnceTerminalObserved);
            writer.newLine();
            writer.write("progress_observed=" + progressObserved);
            writer.newLine();
            writer.write("fully_converged=" + fullyConverged);
            writer.newLine();
            writer.write("safety_pass=" + safetyPassed);
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

    public File getExperimentDirectory() {
        return experimentDirectory;
    }

    public static void main(final String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        long seed = Long.parseLong(options.getOrDefault("seed", "1"));
        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NUM_NODES)));
        double duration = Double.parseDouble(options.getOrDefault("duration", Double.toString(DEFAULT_SIMULATION_TIME)));
        FalseCrashSuspicionScenario scenario = new FalseCrashSuspicionScenario("Journal Experiment 5 - " + "False Crash Suspicion from Delayed Pull/RePush", seed, nodes, duration);
        scenario.AddNewLogger(new BECPCSVLogger(new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));
        scenario.run();

        System.out.println("[Experiment 5] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
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

    /**
     * Delays exactly one directional packet after the experiment arms it.
     *
     * Because all node cycles are quiesced and the target remains paused,
     * the first target -> sender packet created by the controlled Push is
     * its Pull response.
     */
    private static final class DelayedPullBECPWANNetwork extends BECPWANNetwork {
        private boolean armed = false;
        private int fromId = -1;
        private int toId = -1;
        private double artificialLatency = 0.0;
        private int delayedPacketCount = 0;
        private DelayedPullBECPWANNetwork(final RandomnessEngine randomnessEngine) {
            super(randomnessEngine);
        }

        private void armOneDelayedReversePacket(final int fromNodeId, final int toNodeId, final double latency) {
            fromId = fromNodeId;
            toId = toNodeId;
            artificialLatency = latency;
            armed = true;
        }

        private int getDelayedPacketCount() {
            return delayedPacketCount;
        }

        @Override
        public double getLatency(final Node from, final Node to) {
            if (armed && from.getNodeID() == fromId && to.getNodeID() == toId) {
                armed = false;
                delayedPacketCount++;
                return artificialLatency;
            }

            return super.getLatency(from, to);
        }
    }
}
