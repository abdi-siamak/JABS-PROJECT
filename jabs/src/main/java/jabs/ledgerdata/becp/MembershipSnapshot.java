package jabs.ledgerdata.becp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
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
    private final BitSet memberIds;
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

        this.height = height;
        this.memberIds = new BitSet();
        for (Integer memberId : memberIds) {
            if (memberId == null || memberId < 0) {
                throw new IllegalArgumentException("Membership IDs must be non-negative.");
            }
            this.memberIds.set(memberId);
        }

        this.quorumSize = (this.memberIds.cardinality() / 2) + 1;
        this.snapshotId = computeSnapshotId(height, this.memberIds);
    }

    private static String computeSnapshotId(int height, BitSet memberIds) {
        StringBuilder input = new StringBuilder();
        input.append(height).append(":");
        for (int memberId = memberIds.nextSetBit(0);
                memberId >= 0;
                memberId = memberIds.nextSetBit(memberId + 1)) {

            input.append(memberId).append(",");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    input.toString().getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    public int getHeight() {
        return height;
    }

    public Set<Integer> getMemberIds() {
        HashSet<Integer> members = new HashSet<>();
        for (int memberId = memberIds.nextSetBit(0);
                memberId >= 0;
                memberId = memberIds.nextSetBit(memberId + 1)) {

            members.add(memberId);
        }

        return members;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public int getQuorumSize() {
        return quorumSize;
    }

    public boolean containsMember(int nodeId) {
        return nodeId >= 0 && memberIds.get(nodeId);
    }
}