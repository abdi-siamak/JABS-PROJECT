package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import jabs.network.networks.becp.BECPLocalLANNetwork;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;

public class FinalConfirmationSafetyTest {

    private BECPNode createNode(int nodeId) {

        Simulator simulator = new Simulator();

        RandomnessEngine randomnessEngine =
                new RandomnessEngine(12345L);

        BECPLocalLANNetwork network =
                new BECPLocalLANNetwork(randomnessEngine);

        return new BECPNode(
                simulator,
                network,
                nodeId,
                1_000_000L,
                1_000_000L,
                1.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private BECPBlock createBlock(
            BECPNode creator,
            int height,
            int cycleNumber) {

        return new BECPBlock(
                100,
                height,
                cycleNumber,
                cycleNumber,
                creator,
                creator.getNodeID(),
                BECPNode.BECP_GENESIS_BLOCK,
                BECPBlock.State.CONFIRMATION,
                1.0,
                1.0,
                1.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    @Test
    public void duplicateConfirmationFromSameNodeCountsOnlyOnce() {

        BECPNode receiver = createNode(0);
        BECPNode confirmer = createNode(1);

        BECPBlock block =
                createBlock(confirmer, 1, 1);

        assertTrue(
                receiver.recordFinalConfirmation(
                        1,
                        block,
                        confirmer.getNodeID()));

        assertFalse(
                receiver.recordFinalConfirmation(
                        1,
                        block,
                        confirmer.getNodeID()));

        assertEquals(
                1,
                receiver.getFinalConfirmationCount(
                        1,
                        block));
    }

    @Test
    public void differentConfirmersCountSeparately() {

        BECPNode receiver = createNode(0);
        BECPNode creator = createNode(1);

        BECPBlock block =
                createBlock(creator, 1, 1);

        assertTrue(
                receiver.recordFinalConfirmation(
                        1,
                        block,
                        1));

        assertTrue(
                receiver.recordFinalConfirmation(
                        1,
                        block,
                        2));

        assertEquals(
                2,
                receiver.getFinalConfirmationCount(
                        1,
                        block));
    }

    @Test
    public void sameConfirmerCannotConfirmTwoDifferentBlocksAtSameHeight() {

        BECPNode receiver = createNode(0);
        BECPNode creator = createNode(1);

        BECPBlock blockA =
                createBlock(creator, 1, 1);

        BECPBlock blockB =
                createBlock(creator, 1, 2);

        receiver.recordFinalConfirmation(
                1,
                blockA,
                7);

        assertThrows(
                IllegalStateException.class,
                () -> receiver.recordFinalConfirmation(
                        1,
                        blockB,
                        7));
    }

    @Test
    public void persistentFinalVoteCanBeRecordedOnlyOnce() {

        BECPNode node = createNode(3);

        BECPBlock block =
                createBlock(node, 1, 1);

        assertTrue(
                node.recordPersistentFinalVote(
                        1,
                        block));

        assertFalse(
                node.recordPersistentFinalVote(
                        1,
                        block));
    }

    @Test
    public void persistentFinalVoteCannotChangeCandidate() {

        BECPNode node = createNode(3);

        BECPBlock blockA =
                createBlock(node, 1, 1);

        BECPBlock blockB =
                createBlock(node, 1, 2);

        node.recordPersistentFinalVote(
                1,
                blockA);

        assertThrows(
                IllegalStateException.class,
                () -> node.recordPersistentFinalVote(
                        1,
                        blockB));
    }

    @Test
    public void unavailablePersistentVoteStatePreventsNewVote() {

        BECPNode node = createNode(3);

        BECPBlock block =
                createBlock(node, 2, 1);

        node.markFinalVoteStateUnavailable(2);

        assertTrue(
                node.isFinalVoteStateUnavailable(2));

        assertFalse(
                node.recordPersistentFinalVote(
                        2,
                        block));
    }

    @Test
        public void persistentFinalVoteSurvivesCrashAndRestore() {

        BECPNode node = createNode(3);

        BECPBlock blockA =
                createBlock(node, 1, 1);

        BECPBlock blockB =
                createBlock(node, 1, 2);

        assertTrue(
                node.recordPersistentFinalVote(
                        1,
                        blockA));

        node.crash();
        node.restore();

        assertTrue(
                node.hasPersistentFinalVote(1));

        assertSame(
                blockA.getHash(),
                node.getPersistentFinalVote(1));

        assertFalse(
                node.recordPersistentFinalVote(
                        1,
                        blockA));

        assertThrows(
                IllegalStateException.class,
                () -> node.recordPersistentFinalVote(
                        1,
                        blockB));
        }

    @Test
        public void recoveryExchangeSequenceSurvivesCrashAndRestore() {

        BECPNode node = createNode(3);

        RecoveryExchangeId beforeCrash =
                node.createRecoveryExchangeId();

        node.crash();
        node.restore();

        RecoveryExchangeId afterRestore =
                node.createRecoveryExchangeId();

        assertNotEquals(
                beforeCrash,
                afterRestore);

        assertEquals(
                beforeCrash.getSenderNodeId(),
                afterRestore.getSenderNodeId());

        assertEquals(
                beforeCrash.getSequenceNumber() + 1,
                afterRestore.getSequenceNumber());
        }
}