package jabs.network.node.nodes.becp;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Test;

import jabs.ledgerdata.becp.PushEntry;
import jabs.ledgerdata.becp.RecoveryEntry;
import jabs.ledgerdata.becp.RecoveryExchangeId;
import jabs.ledgerdata.becp.RecoveryExchangeState;
import jabs.ledgerdata.becp.ReplicaBlock;
import jabs.network.networks.becp.BECPLocalLANNetwork;
import jabs.simulator.Simulator;
import jabs.simulator.randengine.RandomnessEngine;

public class RecoveryRestartSafetyTest {

    @Test
    public void restartMustTerminalizePendingRecoveryExchanges()
            throws Exception {

        Simulator simulator = new Simulator();

        BECPLocalLANNetwork network =
                new BECPLocalLANNetwork(
                        new RandomnessEngine(1L));

        BECPNode node =
                new BECPNode(
                        simulator,
                        network,
                        1,
                        100_000_000L,
                        100_000_000L,
                        1.0,
                        1.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0);

        /*
         * REAP+ is disabled in the normal unit-test configuration,
         * therefore initialise these two protocol structures explicitly.
         */
        ArrayList<PushEntry> pushEntries =
                new ArrayList<>();

        HashMap<RecoveryExchangeId, RecoveryEntry>
                recoveryExchangeCache =
                new HashMap<>();

        Field pushEntriesField =
                BECPNode.class.getDeclaredField(
                        "pushEntriesBuffer");

        pushEntriesField.setAccessible(true);
        pushEntriesField.set(node, pushEntries);

        Field recoveryExchangeCacheField =
                BECPNode.class.getDeclaredField(
                        "recoveryExchangeCache");

        recoveryExchangeCacheField.setAccessible(true);
        recoveryExchangeCacheField.set(
                node,
                recoveryExchangeCache);

        /*
         * Sender-side PENDING exchange.
         */
        PushEntry pendingPush =
                new PushEntry(
                        null,
                        10,
                        1,
                        1.0,
                        1.0,
                        new HashMap<Integer, ReplicaBlock>());

        pendingPush.setRecoveryExchangeId(
                new RecoveryExchangeId(
                        node.getNodeID(),
                        10,
                        0));

        pushEntries.add(pendingPush);

        /*
         * Sender-side exchange already terminal.
         * Restart cleanup must not change it.
         */
        PushEntry mergedPush =
                new PushEntry(
                        null,
                        11,
                        1,
                        1.0,
                        1.0,
                        new HashMap<Integer, ReplicaBlock>());

        mergedPush.setRecoveryExchangeId(
                new RecoveryExchangeId(
                        node.getNodeID(),
                        11,
                        1));

        mergedPush.transitionRecoveryExchangeState(
                RecoveryExchangeState.MERGED);

        pushEntries.add(mergedPush);

        /*
         * Receiver-side PENDING exchange.
         */
        RecoveryEntry pendingRecovery =
                new RecoveryEntry(
                        null,
                        10,
                        2,
                        1.0,
                        1.0,
                        new HashMap<Integer, ReplicaBlock>());

        RecoveryExchangeId pendingRecoveryId =
                new RecoveryExchangeId(
                        2,
                        10,
                        0);

        recoveryExchangeCache.put(
                pendingRecoveryId,
                pendingRecovery);

        /*
         * Receiver-side exchange already terminal.
         */
        RecoveryEntry mergedRecovery =
                new RecoveryEntry(
                        null,
                        11,
                        2,
                        1.0,
                        1.0,
                        new HashMap<Integer, ReplicaBlock>());

        mergedRecovery.transitionRecoveryExchangeState(
                RecoveryExchangeState.MERGED);

        RecoveryExchangeId mergedRecoveryId =
                new RecoveryExchangeId(
                        2,
                        11,
                        1);

        recoveryExchangeCache.put(
                mergedRecoveryId,
                mergedRecovery);

        /*
         * Simulate the restart cleanup performed before volatile
         * recovery state is discarded/reconstructed.
         */
        node.terminalizePendingRecoveryExchangesForRestart();

        assertEquals(
                RecoveryExchangeState.RESTORED,
                pendingPush.getRecoveryExchangeState());

        assertEquals(
                RecoveryExchangeState.MERGED,
                mergedPush.getRecoveryExchangeState());

        assertEquals(
                RecoveryExchangeState.RESTORED,
                pendingRecovery.getRecoveryExchangeState());

        assertEquals(
                RecoveryExchangeState.MERGED,
                mergedRecovery.getRecoveryExchangeState());
    }
}