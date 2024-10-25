package jabs.simulator.event;

import jabs.network.node.nodes.Node;

public class NodeCycleEvent<N extends Node> implements Event{
	private final N node;
	
	public NodeCycleEvent(N node) {
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
