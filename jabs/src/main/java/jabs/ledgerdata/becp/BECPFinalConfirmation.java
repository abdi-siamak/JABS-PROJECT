package jabs.ledgerdata.becp;

import jabs.network.node.nodes.Node;

/**
 * Final confirmation message for the PTP protocol.
 *
 * Identifies:
 * - height h
 * - candidate block
 * - membership snapshot sigma_h
 * - confirming node
 */
public class BECPFinalConfirmation extends BECPBlockGossip<BECPBlock> {
    /*
     * Payload:
     * - block identifier/hash: 32 bytes
     * - membership snapshot identifier: 32 bytes
     * - height: 4 bytes
     * - confirmer node ID: 4 bytes
     */
    private static final int FINAL_CONFIRMATION_PAYLOAD_SIZE = (2 * BECPBlock.BECP_BLOCK_HASH_SIZE) + (2 * Integer.BYTES);
    private final int height;
    private final BECPBlock block;
    private final String snapshotId;
    private final int confirmerNodeId;

    public BECPFinalConfirmation(final Node sender, final int height, final BECPBlock block, final String snapshotId) {
        super(BECP_GOSSIP_SIZE_OVERHEAD + FINAL_CONFIRMATION_PAYLOAD_SIZE, sender, GossipType.FINAL_CONFIRMATION);

        if (sender == null) {
            throw new IllegalArgumentException("Final confirmation sender cannot be null.");
        }

        if (height < 1) {
            throw new IllegalArgumentException("Final confirmation height must be greater than 0.");
        }

        if (block == null) {
            throw new IllegalArgumentException("Final confirmation block cannot be null.");
        }

        if (block.getHeight() != height) {
            throw new IllegalArgumentException("Final confirmation block height does not match message height.");
        }

        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("Final confirmation snapshot ID cannot be empty.");
        }

        this.height = height;
        this.block = block;
        this.snapshotId = snapshotId;

        // The confirmer is bound directly to the authenticated JABS gossip sender.
        this.confirmerNodeId = sender.getNodeID();
    }

    public int getHeight() {
        return height;
    }

    public BECPBlock getBlock() {
        return block;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public int getConfirmerNodeId() {
        return confirmerNodeId;
    }
}