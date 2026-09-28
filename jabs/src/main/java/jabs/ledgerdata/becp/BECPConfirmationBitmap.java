package jabs.ledgerdata.becp;

import jabs.ledgerdata.Hash;
import java.util.BitSet;

public final class BECPConfirmationBitmap {
    private static final int METADATA_SIZE = BECPBlock.BECP_BLOCK_HASH_SIZE + BECPBlock.BECP_BLOCK_HASH_SIZE + Integer.BYTES;
    private final int height;
    private final Hash blockHash;
    private final String snapshotId;
    private final BitSet confirmations;

    public BECPConfirmationBitmap(int height, Hash blockHash, String snapshotId, BitSet confirmations) {
        if (height < 1) {
            throw new IllegalArgumentException("Confirmation bitmap height must be greater than 0.");
        }
        if (blockHash == null) {
            throw new IllegalArgumentException("Confirmation bitmap block hash cannot be null.");
        }
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("Confirmation bitmap snapshot ID cannot be empty.");
        }

        this.height = height;
        this.blockHash = blockHash;
        this.snapshotId = snapshotId;
        this.confirmations = confirmations == null ? new BitSet() : (BitSet) confirmations.clone();
    }

    public int getHeight() {
        return height;
    }

    public Hash getBlockHash() {
        return blockHash;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public BitSet getConfirmations() {
        return (BitSet) confirmations.clone();
    }

    public int getSize() {
        return METADATA_SIZE + confirmations.toByteArray().length;
    }
}