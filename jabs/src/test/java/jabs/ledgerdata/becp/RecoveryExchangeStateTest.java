package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.HashMap;

import org.junit.Test;

public class RecoveryExchangeStateTest {

    @Test
    public void recoveryEntryCanBeRestoredOnlyOnce() {

        RecoveryEntry entry =
                new RecoveryEntry(
                        null,
                        1,
                        2,
                        0.5,
                        0.5,
                        new HashMap<>());

        assertEquals(
                RecoveryExchangeState.PENDING,
                entry.getRecoveryExchangeState());

        assertTrue(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));

        assertEquals(
                RecoveryExchangeState.RESTORED,
                entry.getRecoveryExchangeState());

        assertFalse(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));

        assertThrows(
                IllegalStateException.class,
                () -> entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));
    }

    @Test
    public void recoveryEntryCanBeMergedOnlyOnce() {

        RecoveryEntry entry =
                new RecoveryEntry(
                        null,
                        1,
                        2,
                        0.5,
                        0.5,
                        new HashMap<>());

        assertTrue(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));

        assertFalse(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));

        assertThrows(
                IllegalStateException.class,
                () -> entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));
    }

    @Test
    public void pushEntryCanBeRestoredOnlyOnce() {

        PushEntry entry =
                new PushEntry(
                        null,
                        1,
                        1,
                        0.5,
                        0.5,
                        new HashMap<>());

        assertEquals(
                RecoveryExchangeState.PENDING,
                entry.getRecoveryExchangeState());

        assertTrue(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));

        assertFalse(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));

        assertThrows(
                IllegalStateException.class,
                () -> entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));
    }

    @Test
    public void pushEntryCanBeMergedOnlyOnce() {

        PushEntry entry =
                new PushEntry(
                        null,
                        1,
                        1,
                        0.5,
                        0.5,
                        new HashMap<>());

        assertTrue(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));

        assertFalse(
                entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.MERGED));

        assertThrows(
                IllegalStateException.class,
                () -> entry.transitionRecoveryExchangeState(
                        RecoveryExchangeState.RESTORED));
    }
}