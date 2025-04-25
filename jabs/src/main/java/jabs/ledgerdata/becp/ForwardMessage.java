package jabs.ledgerdata.becp;

import java.util.HashMap;

import com.google.common.collect.Multimap;

import jabs.network.node.nodes.becp.BECPNode;

public class ForwardMessage {
	private final BECPNode sender;
	private final BECPNode destination;
	private final double senderValue;
	private final double senderWeight;
	private final int senderCycleNumber;
	private final HashMap<Integer, BECPBlock> senderBlockCache;
	private final Multimap<BECPNode, Integer> senderMainCache;
	private final BECPNode sender_d;
	private final Integer sender_v_d;
	private final Integer sender_h;
	
	public ForwardMessage(final BECPNode sender, final BECPNode destination, final double senderValue, final double senderWeight, final int senderCycleNumber, final HashMap<Integer, BECPBlock> senderBlockCache, final Multimap<BECPNode, Integer> senderMainCache, final BECPNode sender_d, final Integer sender_v_d, final Integer sender_h) {
		this.sender = sender;
		this.destination = destination;
		this.senderValue = senderValue;
		this.senderWeight = senderWeight;
		this.senderCycleNumber = senderCycleNumber;
		this.senderBlockCache = senderBlockCache;
		this.senderMainCache = senderMainCache;
		this.sender_d = sender_d;
		this.sender_v_d = sender_v_d;
		this.sender_h = sender_h;
	}
	
	public BECPNode getSender() {
		return sender;
	}

	public BECPNode getDestination() {
		return destination;
	}

	public double getSenderValue() {
		return senderValue;
	}

	public double getSenderWeight() {
		return senderWeight;
	}

	public int getSenderCycleNumber() {
		return senderCycleNumber;
	}
	
	public HashMap<Integer, BECPBlock> getSenderBlockCache() {
		return senderBlockCache;
	}

	public Multimap<BECPNode, Integer> getSenderMainCache() {
		return senderMainCache;
	}

	public BECPNode getSender_d() {
		return sender_d;
	}

	public Integer getSender_v_d() {
		return sender_v_d;
	}

	public Integer getSender_h() {
		return sender_h;
	}
}
