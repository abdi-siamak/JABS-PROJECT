package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.Test;

public class REAPPlusBlockMassConservationTest {

    @Test
    public void lostPullRestorationMustConservePTPBlockMass() {

        /*
         * Initial block-specific masses.
         */
        double senderVp = 8.0;
        double senderWp = 6.0;
        double senderVa = 4.0;
        double senderWa = 2.0;

        double receiverVp = 4.0;
        double receiverWp = 2.0;
        double receiverVa = 6.0;
        double receiverWa = 8.0;

        double initialTotalVp = senderVp + receiverVp;
        double initialTotalWp = senderWp + receiverWp;
        double initialTotalVa = senderVa + receiverVa;
        double initialTotalWa = senderWa + receiverWa;

        /*
         * Sender splits its block mass.
         */
        double senderRetainedVp = senderVp / 2.0;
        double senderRetainedWp = senderWp / 2.0;
        double senderRetainedVa = senderVa / 2.0;
        double senderRetainedWa = senderWa / 2.0;

        double senderPushVp = senderVp / 2.0;
        double senderPushWp = senderWp / 2.0;
        double senderPushVa = senderVa / 2.0;
        double senderPushWa = senderWa / 2.0;

        /*
         * Receiver splits its own block mass for the Pull.
         */
        double receiverPullVp = receiverVp / 2.0;
        double receiverPullWp = receiverWp / 2.0;
        double receiverPullVa = receiverVa / 2.0;
        double receiverPullWa = receiverWa / 2.0;

        double receiverRetainedVp = receiverVp / 2.0;
        double receiverRetainedWp = receiverWp / 2.0;
        double receiverRetainedVa = receiverVa / 2.0;
        double receiverRetainedWa = receiverWa / 2.0;

        /*
         * Receiver merges sender's Push.
         */
        double receiverAfterPushVp =
                receiverRetainedVp + senderPushVp;

        double receiverAfterPushWp =
                receiverRetainedWp + senderPushWp;

        double receiverAfterPushVa =
                receiverRetainedVa + senderPushVa;

        double receiverAfterPushWa =
                receiverRetainedWa + senderPushWa;

        ReplicaBlock receiverPullBlock = new ReplicaBlock();
        receiverPullBlock.setVPropagation(receiverPullVp);
        receiverPullBlock.setWPropagation(receiverPullWp);
        receiverPullBlock.setVAgreement(receiverPullVa);
        receiverPullBlock.setWAgreement(receiverPullWa);

        ReplicaBlock incomingPushBlock = new ReplicaBlock();
        incomingPushBlock.setVPropagation(senderPushVp);
        incomingPushBlock.setWPropagation(senderPushWp);
        incomingPushBlock.setVAgreement(senderPushVa);
        incomingPushBlock.setWAgreement(senderPushWa);

        HashMap<Integer, ReplicaBlock> receiverPullCache =
                new HashMap<>();

        HashMap<Integer, ReplicaBlock> incomingPushCache =
                new HashMap<>();

        receiverPullCache.put(1, receiverPullBlock);
        incomingPushCache.put(1, incomingPushBlock);

        RecoveryEntry recoveryEntry =
                new RecoveryEntry(
                        null,
                        1,
                        2,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        receiverPullCache,
                        incomingPushCache);

        /*
         * Pull is lost.
         *
         * Sender restores its outgoing block mass.
         */
        double senderRestoredVp =
                senderRetainedVp + senderPushVp;

        double senderRestoredWp =
                senderRetainedWp + senderPushWp;

        double senderRestoredVa =
                senderRetainedVa + senderPushVa;

        double senderRestoredWa =
                senderRetainedWa + senderPushWa;

        ReplicaBlock storedPull =
                recoveryEntry.getReplicaBlockCache().get(1);

        ReplicaBlock storedIncoming =
                recoveryEntry.getIncomingPushBlockCache().get(1);

        /*
         * Receiver rollback:
         *
         * current
         * + receiver Pull mass
         * - incoming sender Push mass
         */
        double receiverRestoredVp =
                receiverAfterPushVp
                + storedPull.getVPropagation()
                - storedIncoming.getVPropagation();

        double receiverRestoredWp =
                receiverAfterPushWp
                + storedPull.getWPropagation()
                - storedIncoming.getWPropagation();

        double receiverRestoredVa =
                receiverAfterPushVa
                + storedPull.getVAgreement()
                - storedIncoming.getVAgreement();

        double receiverRestoredWa =
                receiverAfterPushWa
                + storedPull.getWAgreement()
                - storedIncoming.getWAgreement();

        assertEquals(
                initialTotalVp,
                senderRestoredVp + receiverRestoredVp,
                0.0000001);

        assertEquals(
                initialTotalWp,
                senderRestoredWp + receiverRestoredWp,
                0.0000001);

        assertEquals(
                initialTotalVa,
                senderRestoredVa + receiverRestoredVa,
                0.0000001);

        assertEquals(
                initialTotalWa,
                senderRestoredWa + receiverRestoredWa,
                0.0000001);
    }
}