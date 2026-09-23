package jabs.ledgerdata.becp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.HexFormat;

/**
 * Immutable membership snapshot used for one unresolved block height.
 *
 * M_h     = fixed set of authenticated member IDs
 * sigma_h = deterministic identifier of the snapshot
 * q_h     = strict-majority threshold: floor(|M_h| / 2) + 1
 */
public final class MembershipSnapshot {

    private final int height;
    private final Set<Integer> memberIds;
    private final String snapshotId;
    private final int quorumSize;

    public MembershipSnapshot(int height, Set<Integer> memberIds) {

        if (height < 1) {
            throw new IllegalArgumentException(
                    "Membership snapshot height must be greater than 0.");
        }

        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Membership snapshot cannot be empty.");
        }

        // TreeSet gives us a deterministic ordering of member IDs.
        TreeSet<Integer> sortedMembers = new TreeSet<>(memberIds);

        this.height = height;
        this.memberIds = Collections.unmodifiableSet(sortedMembers);

        // q_h = floor(|M_h| / 2) + 1
        this.quorumSize = (sortedMembers.size() / 2) + 1;

        // sigma_h uniquely identifies this height and membership snapshot.
        this.snapshotId = computeSnapshotId(height, sortedMembers);
    }

    private static String computeSnapshotId(
            int height,
            Set<Integer> memberIds) {

        StringBuilder input = new StringBuilder();

        input.append(height).append(":");

        for (Integer memberId : memberIds) {
            input.append(memberId).append(",");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    input.toString().getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available.", e);
        }
    }

    public int getHeight() {
        return height;
    }

    public Set<Integer> getMemberIds() {
        return memberIds;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public int getQuorumSize() {
        return quorumSize;
    }

    public boolean containsMember(int nodeId) {
        return memberIds.contains(nodeId);
    }
}