package jabs.ledgerdata.becp;
import jabs.network.node.nodes.becp.BECPNode;

public class neighborEntry {
	private final BECPNode node;
	private final int createdTime;
	
	public neighborEntry(final BECPNode node, final int createdTime) {
		this.node = node;
		this.createdTime = createdTime;
	}
	
	public BECPNode getNode() {
		return node;
	}
	
	public int getCreatedTime() {
		return createdTime;
	}
}

