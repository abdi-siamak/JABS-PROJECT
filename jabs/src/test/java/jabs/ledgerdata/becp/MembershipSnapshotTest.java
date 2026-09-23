package jabs.ledgerdata.becp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class MembershipSnapshotTest {

    @Test
    public void snapshotIdIsDeterministicRegardlessOfInputOrder() {

        Set<Integer> membersA =
                new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));

        Set<Integer> membersB =
                new HashSet<>(Arrays.asList(5, 3, 1, 4, 2));

        MembershipSnapshot snapshotA =
                new MembershipSnapshot(7, membersA);

        MembershipSnapshot snapshotB =
                new MembershipSnapshot(7, membersB);

        assertEquals(
                snapshotA.getSnapshotId(),
                snapshotB.getSnapshotId());
    }

    @Test
    public void differentMembershipProducesDifferentSnapshotId() {

        MembershipSnapshot snapshotA =
                new MembershipSnapshot(
                        7,
                        new HashSet<>(
                                Arrays.asList(1, 2, 3, 4)));

        MembershipSnapshot snapshotB =
                new MembershipSnapshot(
                        7,
                        new HashSet<>(
                                Arrays.asList(1, 2, 3, 5)));

        assertNotEquals(
                snapshotA.getSnapshotId(),
                snapshotB.getSnapshotId());
    }

    @Test
    public void differentHeightProducesDifferentSnapshotId() {

        Set<Integer> members =
                new HashSet<>(
                        Arrays.asList(1, 2, 3, 4));

        MembershipSnapshot snapshotAtHeight7 =
                new MembershipSnapshot(7, members);

        MembershipSnapshot snapshotAtHeight8 =
                new MembershipSnapshot(8, members);

        assertNotEquals(
                snapshotAtHeight7.getSnapshotId(),
                snapshotAtHeight8.getSnapshotId());
    }

    @Test
    public void quorumIsStrictMajorityForOddMembership() {

        MembershipSnapshot snapshot =
                new MembershipSnapshot(
                        1,
                        new HashSet<>(
                                Arrays.asList(1, 2, 3, 4, 5)));

        assertEquals(3, snapshot.getQuorumSize());
    }

    @Test
    public void quorumIsStrictMajorityForEvenMembership() {

        MembershipSnapshot snapshot =
                new MembershipSnapshot(
                        1,
                        new HashSet<>(
                                Arrays.asList(1, 2, 3, 4)));

        assertEquals(3, snapshot.getQuorumSize());
    }

    @Test
    public void snapshotContainsOnlyItsMembers() {

        MembershipSnapshot snapshot =
                new MembershipSnapshot(
                        1,
                        new HashSet<>(
                                Arrays.asList(10, 20, 30)));

        assertTrue(snapshot.containsMember(10));
        assertTrue(snapshot.containsMember(20));
        assertTrue(snapshot.containsMember(30));

        assertFalse(snapshot.containsMember(40));
    }
}