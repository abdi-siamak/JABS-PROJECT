package jabs.ledgerdata.raft;

import jabs.network.node.nodes.Node;

public class HeartbeatMessage extends RAFTBlockVote {
	private static final int RAFT_HEARTBEAT_SIZE_OVERHEAD = 60;
    public HeartbeatMessage(Node leader) {
        super(RAFT_HEARTBEAT_SIZE_OVERHEAD, leader, VoteType.HEARTBEAT);
    }
}
