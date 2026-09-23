package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class RecoveryExchangeIdTest {

    @Test
    public void identicalExchangeFieldsProduceEqualIds() {

        RecoveryExchangeId idA =
                new RecoveryExchangeId(3, 10, 7);

        RecoveryExchangeId idB =
                new RecoveryExchangeId(3, 10, 7);

        assertEquals(idA, idB);
        assertEquals(idA.hashCode(), idB.hashCode());
    }

    @Test
    public void sequenceNumberMakesExchangeUnique() {

        RecoveryExchangeId idA =
                new RecoveryExchangeId(3, 10, 7);

        RecoveryExchangeId idB =
                new RecoveryExchangeId(3, 10, 8);

        assertNotEquals(idA, idB);
    }

    @Test
    public void senderAndCycleArePartOfExchangeIdentity() {

        RecoveryExchangeId original =
                new RecoveryExchangeId(3, 10, 7);

        RecoveryExchangeId differentSender =
                new RecoveryExchangeId(4, 10, 7);

        RecoveryExchangeId differentCycle =
                new RecoveryExchangeId(3, 11, 7);

        assertNotEquals(original, differentSender);
        assertNotEquals(original, differentCycle);
    }

    @Test
    public void distinctExchangeIdsRemainDistinctInHashSet() {

        Set<RecoveryExchangeId> exchanges =
                new HashSet<>();

        exchanges.add(
                new RecoveryExchangeId(3, 10, 7));

        exchanges.add(
                new RecoveryExchangeId(3, 10, 8));

        exchanges.add(
                new RecoveryExchangeId(3, 11, 7));

        exchanges.add(
                new RecoveryExchangeId(4, 10, 7));

        assertEquals(4, exchanges.size());
    }

    @Test
    public void negativeExchangeFieldsAreRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new RecoveryExchangeId(-1, 10, 7));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RecoveryExchangeId(3, -1, 7));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RecoveryExchangeId(3, 10, -1));
    }

    @Test
        public void pushEntryRecoveryExchangeIdCannotBeReplaced() {
        PushEntry entry =
                new PushEntry(
                        null,
                        10,
                        2,
                        0.5,
                        0.5,
                        new HashMap<>());

        RecoveryExchangeId originalId =
                new RecoveryExchangeId(3, 10, 7);

        RecoveryExchangeId differentId =
                new RecoveryExchangeId(3, 10, 8);

        entry.setRecoveryExchangeId(originalId);

        assertSame(
                originalId,
                entry.getRecoveryExchangeId());

        // Reassigning the same logical ID is harmless.
        entry.setRecoveryExchangeId(
                new RecoveryExchangeId(3, 10, 7));

        assertEquals(
                originalId,
                entry.getRecoveryExchangeId());

        assertThrows(
                IllegalStateException.class,
                () -> entry.setRecoveryExchangeId(
                        differentId));
        }
    @Test
        public void pushEntryRejectsNullRecoveryExchangeId() {
        PushEntry entry =
                new PushEntry(
                        null,
                        10,
                        2,
                        0.5,
                        0.5,
                        new HashMap<>());

        assertThrows(
                IllegalArgumentException.class,
                () -> entry.setRecoveryExchangeId(null));
        }
}