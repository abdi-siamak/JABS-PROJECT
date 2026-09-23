package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.Test;

public class REAPPlusMassConservationTest {

        @Test
        public void lostPullRestorationMustConserveSystemMass() {

        double initialSenderMass = 8.0;
        double initialReceiverMass = 4.0;
        double initialTotalMass =
                initialSenderMass + initialReceiverMass;

        // Sender splits its mass.
        double senderRetainedMass =
                initialSenderMass / 2.0;

        double senderPushMass =
                initialSenderMass / 2.0;

        // Receiver splits its own mass for the Pull.
        double receiverPullMass =
                initialReceiverMass / 2.0;

        double receiverRetainedMass =
                initialReceiverMass / 2.0;

        // Receiver has already merged the sender's Push.
        double receiverMassAfterPush =
                receiverRetainedMass + senderPushMass;

        RecoveryEntry receiverRecoveryEntry =
                new RecoveryEntry(
                        null,
                        1,
                        2,
                        receiverPullMass,
                        receiverPullMass,
                        senderPushMass,
                        senderPushMass,
                        new HashMap<>());

        /*
        * Pull is lost.
        *
        * Sender restores its outgoing Push mass.
        */
        double senderMassAfterRestoration =
                senderRetainedMass + senderPushMass;

        /*
        * Receiver must restore its own Pull mass,
        * but must also remove the sender Push mass
        * because the sender has restored that mass.
        */
        double receiverMassAfterRestoration =
                receiverMassAfterPush
                + receiverRecoveryEntry.getReplicaValue()
                - receiverRecoveryEntry.getIncomingPushValue();

        double recoveredTotalMass =
                senderMassAfterRestoration
                + receiverMassAfterRestoration;

        assertEquals(
                initialTotalMass,
                recoveredTotalMass,
                0.0000001);
        }
}