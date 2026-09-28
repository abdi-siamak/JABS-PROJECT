package jabs.scenario.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import jabs.log.BECPCSVLogger;
import jabs.network.message.GossipMessage;
import jabs.network.message.GossipMessageBuilder;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.scenario.BECPScenario;

import static jabs.network.node.nodes.becp.BECPNode.BECP_GENESIS_BLOCK;

/**
 * Experiment 6:
 * Crash/recovery with stale volatile state but a persistent final vote.
 * Candidate A is injected normally. Once victim node 0 reaches the real PTP
 * CONFIRMATION path and persists its vote for A, node 0 crashes.
 * Its volatile candidate/preference state is cleared. After recovery,
 * a conflicting height-1 candidate B is installed as stale volatile state.
 * The durable vote must still be A; replaying A must not emit a second vote,
 * and voting B must be rejected.
 */
public class CrashRecoveryPersistentVoteScenario extends BECPScenario {
    public static final int DEFAULT_NUM_NODES = 1000;
    public static final double DEFAULT_SIMULATION_TIME = 60.0;
    private static final double INJECTION_TIME = 10.0;
    private static final double POLL_INTERVAL = 0.02;
    private static final double CRASH_DURATION = 1.0;
    private static final double VOTE_DEADLINE = 35.0;
    private static final int VICTIM_ID = 0;
    private static final int PROPOSER_A_ID = 1;
    private static final int PROPOSER_B_ID = 2;
    private static final int HEIGHT = 1;
    private final long experimentSeed;
    private final int experimentNumNodes;
    private final double experimentDuration;
    private final File experimentDirectory;
    private BECPNode victim;
    private BECPNode proposerA;
    private BECPNode proposerB;
    private BECPBlock candidateA;
    private BECPBlock candidateB;
    private Hash voteBeforeCrash;
    private Hash voteAfterRecovery;
    private Hash voteAfterConflict;
    private boolean voteAObserved;
    private boolean victimCommittedBeforeCrash;
    private boolean crashPerformed;
    private boolean volatileStateCleared;
    private boolean recoveryPerformed;
    private boolean recoveryRequestSent;
    private boolean recoveryLedgerMerged;
    private int recoveryPeerId = -1;
    private boolean conflictingStateInstalled;
    private boolean durableVotePreserved;
    private boolean sameVoteReplayRejected;
    private boolean conflictingRevoteRejected;
    private boolean durableVoteUnchanged;
    private boolean victimEventuallyCommittedA;
    private double voteObservedTime = Double.NaN;
    private double crashTime = Double.NaN;
    private double recoveryTime = Double.NaN;
    private double conflictAttemptTime = Double.NaN;
    private String conflictingVoteException = "";

    public CrashRecoveryPersistentVoteScenario(String name, long seed, int numOfNodes, double simulationStopTime) {
        super(name, seed, numOfNodes, simulationStopTime);
        if (numOfNodes < 3) {
            throw new IllegalArgumentException("Experiment 6 requires at least 3 nodes.");
        }

        if (BECP.SSEP || !BECP.REAP_PLUS) {
            throw new IllegalStateException("Experiment 6 requires REAP+ recovery mode:" + " -Djabs.becp.ssep=false" + " -Djabs.becp.reapPlus=true.");
        }

        if (simulationStopTime < 45.0) {
            throw new IllegalArgumentException("Experiment 6 requires at least 45 simulated seconds.");
        }

        experimentSeed = seed;
        experimentNumNodes = numOfNodes;
        experimentDuration = simulationStopTime;
        experimentDirectory = new File("output/journal/crash-recovery/" + "nodes-" + numOfNodes + "/seed-" + seed);

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
    protected boolean shouldProcessNodeCycle(final BECPNode node) {
        return !(node.getNodeID() == VICTIM_ID && node.isCrashed);
    }

    @Override
    protected void insertInitialEvents() {
        super.insertInitialEvents();
        victim = (BECPNode) network.getNode(VICTIM_ID);
        proposerA = (BECPNode) network.getNode(PROPOSER_A_ID);
        proposerB = (BECPNode) network.getNode(PROPOSER_B_ID);

        simulator.putEvent(this::injectCandidateA, INJECTION_TIME - simulator.getSimulationTime());
    }

    private void injectCandidateA() {
        generateNewBlock(proposerA);
        candidateA = proposerA.getBlockLocalCache().get(HEIGHT);
        if (candidateA == null) {
            throw new IllegalStateException("Failed to create candidate A.");
        }

        System.out.println("[Experiment 6] Candidate A injected at t=" + simulator.getSimulationTime() + " s.");
        simulator.putEvent(this::pollForVote, POLL_INTERVAL);
    }

    private void pollForVote() {
        if (voteAObserved) {
            return;
        }

        if (simulator.getSimulationTime() > VOTE_DEADLINE) {
            throw new IllegalStateException("Victim did not persist vote A before the deadline.");
        }

        if (!victim.hasPersistentFinalVote(HEIGHT)) {
            simulator.putEvent(this::pollForVote, POLL_INTERVAL);
            return;
        }

        voteBeforeCrash = victim.getPersistentFinalVote(HEIGHT);
        if (voteBeforeCrash != candidateA.getHash()) {
            throw new IllegalStateException("Victim persisted an unexpected hash before crash.");
        }

        voteAObserved = true;
        voteObservedTime = simulator.getSimulationTime();
        victimCommittedBeforeCrash = findCommittedAtHeight(victim, HEIGHT) != null;

        System.out.println("[Experiment 6] Victim persisted vote A at t=" + voteObservedTime + " s; committed_before_crash=" + victimCommittedBeforeCrash + ".");

        crashVictim();
    }

    private void crashVictim() {
        victim.terminalizePendingRecoveryExchangesForRestart();
        victim.clearVolatileFinalConfirmations();
        victim.crash();
        victim.isCrashed = true;
        crashPerformed = true;
        crashTime = simulator.getSimulationTime();

        /*
         * Deliberately stale volatile consensus state.
         * Durable vote state and committed ledger state are untouched.
         */
        victim.getBlockLocalCache().clear();
        victim.setCurrentPreferredBlock(BECP_GENESIS_BLOCK);
        volatileStateCleared = victim.getBlockLocalCache().isEmpty() && victim.getCurrentPreferredBlock() == BECP_GENESIS_BLOCK;
        if (!volatileStateCleared) {
            throw new IllegalStateException("Failed to clear volatile state.");
        }

        if (victim.getPersistentFinalVote(HEIGHT) != voteBeforeCrash) {
            throw new IllegalStateException("Durable vote changed during crash reset.");
        }

        System.out.println("[Experiment 6] Victim crashed at t=" + crashTime + " s; volatile state cleared.");
        simulator.putEvent(this::recoverVictim, CRASH_DURATION);
    }

    private void recoverVictim() {
        /*
         * Re-enable the network interface, but deliberately keep
         * isCrashed=true until the REAP+ Pull response performs the
         * protocol's recovery merge.
         */
        victim.restore();
        recoveryPerformed = true;
        recoveryTime = simulator.getSimulationTime();
        voteAfterRecovery = victim.getPersistentFinalVote(HEIGHT);
        durableVotePreserved = voteAfterRecovery == voteBeforeCrash && voteAfterRecovery == candidateA.getHash();

        if (!durableVotePreserved) {
            throw new IllegalStateException("Durable vote A was not preserved across recovery.");
        }

        /*
         * Install the conflicting stale volatile view BEFORE requesting
         * recovery. The durable vote remains A.
         */
        candidateB = new BECPBlock(
                candidateA.getSize(),
                HEIGHT,
                simulator.getSimulationTime(),
                victim.getCycleNumber(),
                proposerB,
                proposerB.getNodeID(),
                BECP_GENESIS_BLOCK,
                BECPBlock.State.CONFIRMATION,
                1.0, 1.0, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 0.0);

        if (candidateB.getHash() == candidateA.getHash()) {
            throw new IllegalStateException("Candidate B did not get a distinct hash identity.");
        }

        victim.getBlockLocalCache().put(HEIGHT, candidateB);
        victim.setCurrentPreferredBlock(candidateB);
        conflictingStateInstalled = victim.getBlockLocalCache().get(HEIGHT) == candidateB && victim.getCurrentPreferredBlock() == candidateB;

        simulator.putEvent(this::sendRecoveryRequestWhenPeerCommitted, POLL_INTERVAL);
    }

    private void sendRecoveryRequestWhenPeerCommitted() {
        if (recoveryRequestSent) {
            return;
        }

        BECPNode destination = findRecoveryPeer();
        if (destination == null) {
            if (simulator.getSimulationTime() > VOTE_DEADLINE + 5.0) {
                throw new IllegalStateException("No healthy committed peer became available" + " for the REAP+ recovery request.");
            }

            simulator.putEvent(this::sendRecoveryRequestWhenPeerCommitted, POLL_INTERVAL);

            return;
        }

        recoveryPeerId = destination.getNodeID();

        /*
         * Same update-request semantics as BECPScenario.join():
         * clear volatile REAP+ buffers/aggregation state and ask one
         * healthy peer for its current ledger/cache state.
         */
        victim.getRecoveryCache().clear();
        victim.getPushEntriesBuffer().clear();
        victim.getCrashedNodes().clear();
        victim.getJoinedNodes().clear();
        victim.setValue(0.0);
        victim.setWeight(0.0);

        ArrayList<BECPNode> copyNeighborCache = new ArrayList<>(victim.getNeighborsLocalCache());
        copyNeighborCache.remove(destination);
        HashMap<Integer, BECPBlock> emptyBlockCache = new HashMap<>();

        victim.gossipMessage(new GossipMessage( new GossipMessageBuilder()
                                .setCycleNumber(victim.getCycleNumber())
                                .setValue(victim.getValue())
                                .setWeight(victim.getWeight())
                                .setNeighborsLocalCache(copyNeighborCache)
                                .setBlockLocalCache(emptyBlockCache)
                                .setCriticalPushFlag(false)
                                .setIsReceivedPull(false)
                                .setCrashedNodes(victim.getCrashedNodes())
                                .setJoinedNodes(victim.getJoinedNodes())
                                .setIsNewJoined(true)
                                .buildPushGossip(victim, 0)), destination);

        recoveryRequestSent = true;

        System.out.println(
                "[Experiment 6] REAP+ recovery request sent from victim "
                + VICTIM_ID
                + " to node "
                + recoveryPeerId
                + " at t="
                + simulator.getSimulationTime()
                + " s.");

        simulator.putEvent(this::pollForRecoveryMerge, POLL_INTERVAL);
    }

    private BECPNode findRecoveryPeer() {
        for (BECPNode neighbor : victim.getNeighborsLocalCache()) {
            if (neighbor.getNodeID() == VICTIM_ID || neighbor.isCrashed) {
                continue;
            }

            BECPBlock committed = findCommittedAtHeight(neighbor, HEIGHT);
            if (committed != null && committed.getHash() == candidateA.getHash()) {
                return neighbor;
            }
        }

        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            if (node.getNodeID() == VICTIM_ID || node.isCrashed) {
                continue;
            }

            BECPBlock committed = findCommittedAtHeight(node, HEIGHT);
            if (committed != null && committed.getHash() == candidateA.getHash()) {
                return node;
            }
        }

        return null;
    }

    private void pollForRecoveryMerge() {
        BECPBlock recoveredCommit = findCommittedAtHeight(victim, HEIGHT);
        recoveryLedgerMerged = !victim.isCrashed && recoveredCommit != null && recoveredCommit.getHash() == candidateA.getHash();
        if (!recoveryLedgerMerged) {
            if (simulator.getSimulationTime() > VOTE_DEADLINE + 10.0) {
                throw new IllegalStateException("REAP+ recovery response did not merge committed A" + " into the victim ledger.");
            }

            simulator.putEvent(this::pollForRecoveryMerge, POLL_INTERVAL);

            return;
        }

        System.out.println("[Experiment 6] REAP+ recovery merged committed A at t=" + simulator.getSimulationTime() + " s.");

        attemptConflictingVoteAfterRecovery();
    }

    private void attemptConflictingVoteAfterRecovery() {
        conflictAttemptTime = simulator.getSimulationTime();
        boolean recordedAgain = victim.recordPersistentFinalVote(HEIGHT, candidateA);
        sameVoteReplayRejected = !recordedAgain;
        try {
            victim.recordPersistentFinalVote(HEIGHT, candidateB);
            conflictingRevoteRejected = false;
        } catch (IllegalStateException expected) {
            conflictingRevoteRejected = true;
            conflictingVoteException =
                    expected.getMessage() == null ? "" : expected.getMessage();
        }
        voteAfterConflict = victim.getPersistentFinalVote(HEIGHT);
        durableVoteUnchanged = voteAfterConflict == voteBeforeCrash && voteAfterConflict == candidateA.getHash();
        if (!sameVoteReplayRejected || !conflictingRevoteRejected || !durableVoteUnchanged) {
            throw new IllegalStateException("Persistent-vote recovery oracle failed.");
        }

        System.out.println("[Experiment 6] Recovered node rejected conflicting vote B" + " at t=" + conflictAttemptTime + " s; durable vote remains A.");

        /*
         * Recovery has already merged committed A into the durable ledger.
         * Remove only the synthetic stale volatile B view.
         */
        BECPBlock recoveredA = findCommittedAtHeight(victim, HEIGHT);
        if (recoveredA == null || recoveredA.getHash() != candidateA.getHash()) {
            throw new IllegalStateException("Recovered committed candidate A is missing.");
        }

        /*
        * Remove the synthetic stale candidate B, but keep the recovered
        * committed candidate A in the volatile cache so the normal REAP+
        * rejoin bookkeeping can complete safely.
        */
        victim.getBlockLocalCache().put(HEIGHT, recoveredA);
        victim.setCurrentPreferredBlock(recoveredA);}

    @Override
    public void run() throws IOException {
        try {
            super.run();
            writeOutputs(true, "");
        } catch (RuntimeException exception) {
            try {
                writeOutputs(false, exception.getMessage());
            } catch (IOException outputException) {
                exception.addSuppressed(outputException);
            }
            throw exception;
        }
    }

    private void writeOutputs(boolean baseSafetyPassed, String failureMessage) throws IOException {
        int committedA = 0;
        int committedOther = 0;
        int uncommitted = 0;
        Set<Hash> committedHashes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            BECPBlock committed = findCommittedAtHeight(node, HEIGHT);
            if (committed == null) {
                uncommitted++;
                continue;
            }
            committedHashes.add(committed.getHash());
            if (candidateA != null && committed.getHash() == candidateA.getHash()) {
                committedA++;
            } else {
                committedOther++;
            }
        }

        BECPBlock victimCommitted = findCommittedAtHeight(victim, HEIGHT);
        victimEventuallyCommittedA = victimCommitted != null && candidateA != null && victimCommitted.getHash() == candidateA.getHash();
        int finalCrashedNodes = 0;
        for (BECPNode node : (List<BECPNode>) network.getAllNodes()) {
            if (node.isCrashed) {
                finalCrashedNodes++;
            }
        }

        boolean durableOraclePassed =
                voteAObserved
                && crashPerformed
                && volatileStateCleared
                && recoveryPerformed
                && recoveryRequestSent
                && recoveryLedgerMerged
                && conflictingStateInstalled
                && durableVotePreserved
                && sameVoteReplayRejected
                && conflictingRevoteRejected
                && durableVoteUnchanged;

        boolean progressObserved = committedA > 0;
        boolean fullyConverged = committedA == experimentNumNodes;
        boolean noDivergence = committedHashes.size() <= 1 && committedOther == 0;
        boolean safetyPassed = baseSafetyPassed && durableOraclePassed && noDivergence && finalCrashedNodes == 0;
        double avgLatency = BECPScenario.consensusTimes.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        writeSummary(
                committedA,
                committedOther,
                uncommitted,
                committedHashes.size(),
                finalCrashedNodes,
                durableOraclePassed,
                progressObserved,
                fullyConverged,
                noDivergence,
                safetyPassed,
                avgLatency,
                failureMessage);
        writeVoteAudit();
        writeMetadata(safetyPassed, durableOraclePassed, failureMessage);
    }

    private BECPBlock findCommittedAtHeight(BECPNode node, int height) {
        for (BECPBlock block : node.getLocalLedger()) {
            if (block.getHeight() == height && block.getState() == BECPBlock.State.COMMIT) {
                return block;
            }
        }
        return null;
    }

    private void writeSummary(
            int committedA,
            int committedOther,
            int uncommitted,
            int distinctCommitted,
            int finalCrashedNodes,
            boolean durableOraclePassed,
            boolean progressObserved,
            boolean fullyConverged,
            boolean noDivergence,
            boolean safetyPassed,
            double avgLatency,
            String failureMessage) throws IOException {

        File file = new File(experimentDirectory, "summary.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            w.write(
                    "experiment,seed,nodes,configured_duration,actual_end_time,"
                    + "vote_observed_time,crash_time,recovery_time,conflict_attempt_time,"
                    + "victim_committed_before_crash,vote_a_observed,crash_performed,"
                    + "volatile_state_cleared,recovery_performed,recovery_request_sent,"
                    + "recovery_ledger_merged,recovery_peer_id,conflicting_state_installed,"
                    + "durable_vote_preserved,same_vote_replay_rejected,"
                    + "conflicting_revote_rejected,durable_vote_unchanged_after_conflict,"
                    + "victim_eventually_committed_a,durable_oracle_pass,"
                    + "actual_crashed_nodes_final,committed_a_nodes,committed_other_nodes,"
                    + "uncommitted_nodes,distinct_committed_candidates,progress_observed,"
                    + "fully_converged,no_divergent_commit,safety_pass,avg_commit_latency,"
                    + "messages,message_bytes,git_commit,failure_message");
            w.newLine();
            w.write(
                    "crash-recovery,"
                    + experimentSeed + ","
                    + experimentNumNodes + ","
                    + experimentDuration + ","
                    + simulator.getSimulationTime() + ","
                    + voteObservedTime + ","
                    + crashTime + ","
                    + recoveryTime + ","
                    + conflictAttemptTime + ","
                    + victimCommittedBeforeCrash + ","
                    + voteAObserved + ","
                    + crashPerformed + ","
                    + volatileStateCleared + ","
                    + recoveryPerformed + ","
                    + recoveryRequestSent + ","
                    + recoveryLedgerMerged + ","
                    + recoveryPeerId + ","
                    + conflictingStateInstalled + ","
                    + durableVotePreserved + ","
                    + sameVoteReplayRejected + ","
                    + conflictingRevoteRejected + ","
                    + durableVoteUnchanged + ","
                    + victimEventuallyCommittedA + ","
                    + durableOraclePassed + ","
                    + finalCrashedNodes + ","
                    + committedA + ","
                    + committedOther + ","
                    + uncommitted + ","
                    + distinctCommitted + ","
                    + progressObserved + ","
                    + fullyConverged + ","
                    + noDivergence + ","
                    + safetyPassed + ","
                    + avgLatency + ","
                    + BECPCSVLogger.numMessage + ","
                    + BECPCSVLogger.messageSize + ","
                    + csv(System.getenv("GIT_COMMIT")) + ","
                    + csv(failureMessage));
            w.newLine();
        }
    }

    private void writeVoteAudit() throws IOException {
        File file = new File(experimentDirectory, "vote-audit.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            w.write("stage,simulation_time,a_hash,b_hash,durable_hash,result");
            w.newLine();
            w.write("before_crash," + voteObservedTime + "," + identity(candidateA == null ? null : candidateA.getHash()) + ",," + identity(voteBeforeCrash) + "," + voteAObserved);
            w.newLine();
            w.write("after_recovery," + recoveryTime + "," + identity(candidateA == null ? null : candidateA.getHash())  + ",," + identity(voteAfterRecovery) + "," + durableVotePreserved);
            w.newLine();
            w.write(
                    "after_conflict_attempt,"
                    + conflictAttemptTime + ","
                    + identity(candidateA == null ? null : candidateA.getHash())
                    + ","
                    + identity(candidateB == null ? null : candidateB.getHash())
                    + ","
                    + identity(voteAfterConflict) + ","
                    + conflictingRevoteRejected);
            w.newLine();
        }
    }

    private void writeMetadata(boolean safetyPassed, boolean durableOraclePassed, String failureMessage) throws IOException {
        File file = new File(experimentDirectory, "metadata.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            w.write("experiment=crash-recovery\n");
            w.write("seed=" + experimentSeed + "\n");
            w.write("nodes=" + experimentNumNodes + "\n");
            w.write("vote_observed_time=" + voteObservedTime + "\n");
            w.write("crash_time=" + crashTime + "\n");
            w.write("recovery_time=" + recoveryTime + "\n");
            w.write("victim_committed_before_crash=" + victimCommittedBeforeCrash + "\n");
            w.write("recovery_request_sent=" + recoveryRequestSent + "\n");
            w.write("recovery_ledger_merged=" + recoveryLedgerMerged + "\n");
            w.write("recovery_peer_id=" + recoveryPeerId + "\n");
            w.write("durable_vote_preserved=" + durableVotePreserved + "\n");
            w.write("same_vote_replay_rejected=" + sameVoteReplayRejected + "\n");
            w.write("conflicting_revote_rejected=" + conflictingRevoteRejected + "\n");
            w.write("durable_vote_unchanged_after_conflict=" + durableVoteUnchanged + "\n");
            w.write("conflicting_vote_exception=" + csv(conflictingVoteException) + "\n");
            w.write("victim_eventually_committed_a=" + victimEventuallyCommittedA + "\n");
            w.write("durable_oracle_pass=" + durableOraclePassed + "\n");
            w.write("safety_pass=" + safetyPassed + "\n");
            w.write("git_commit=" + csv(System.getenv("GIT_COMMIT")) + "\n");
            w.write("failure_message=" + csv(failureMessage) + "\n");
        }
    }

    private String identity(Object object) {
        return object == null ? "" : Integer.toHexString(System.identityHashCode(object));
    }

    private String csv(String value) {
        return value == null ? "" : value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    public File getExperimentDirectory() {
        return experimentDirectory;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        long seed = Long.parseLong(options.getOrDefault("seed", "1"));
        int nodes = Integer.parseInt(options.getOrDefault("nodes", Integer.toString(DEFAULT_NUM_NODES)));
        double duration = Double.parseDouble(options.getOrDefault("duration", Double.toString(DEFAULT_SIMULATION_TIME)));
        CrashRecoveryPersistentVoteScenario scenario = new CrashRecoveryPersistentVoteScenario("Journal Experiment 6 - Crash/Recovery Persistent Vote", seed, nodes, duration);
        scenario.AddNewLogger(new BECPCSVLogger(new File(scenario.getExperimentDirectory(), "messages.csv").toPath()));
        scenario.run();
        System.out.println("[Experiment 6] Results written to: " + scenario.getExperimentDirectory().getAbsolutePath());
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
}
