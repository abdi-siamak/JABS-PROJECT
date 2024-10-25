package jabs.simulator.event;

import jabs.network.node.nodes.Node;

public class HeartbeatEvent <N extends Node> implements Event{
	private final N node;
	
	public HeartbeatEvent(N node) {
		this.node = node;
	}
	
	public N getNode() {
		return node;
	}

	@Override
	public void execute() {
		// TODO Auto-generated method stub
		
	}
}
