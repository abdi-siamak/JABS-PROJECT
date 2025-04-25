package jabs.network.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import com.google.common.collect.Multimap;

import jabs.ledgerdata.Gossip;
import jabs.ledgerdata.becp.A;
import jabs.ledgerdata.becp.BECPBlock;
import jabs.ledgerdata.becp.BECPPull;
import jabs.ledgerdata.becp.BECPPush;
import jabs.ledgerdata.becp.C;
import jabs.ledgerdata.becp.Process;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.becp.BECPNode;

public class GossipMessageBuilder {
    private double value; // SSEP & REAP & REAP+
    private double weight; // SSEP & REAP & REAP+
    private int cycleNumber; 
    private LinkedHashSet<BECPBlock> localLedger; // REAP+
    private ArrayList<BECPNode> neighborsLocalCache; // NCP
    private HashMap<Integer, BECPBlock> blockLocalCache; // PTP
    private boolean criticalPushFlag; // REAP
    private HashMap<Integer, Process> P; // ARP 
    private A A; // the process Ai-ARP 
    private C C; // the process Ci-ARP 
    private int l; // incremental global epoch identifier-ARP
    private HashSet<BECPNode> crashedNodes;// REAP+
    private HashSet<BECPNode> joinedNodes;// REAP+
    private boolean isNewJoined; // REAP+
    private boolean isReceivedPull; // REAP+
    private Multimap<BECPNode, Integer> mainCache_S; // EMP+
    private Multimap<BECPNode, Integer> mainCache_d; // EMP+
    private Multimap<BECPNode, Integer> donatedCache; // Q: set of donated cache entries (EMP+)
    private Integer v_d; // current main overlap (EMP+)
    private Integer h; // hop count (EMP+)
    private BECPNode d; // current best destination node (EMP+)
	public GossipMessageBuilder setValue(double value) {
		this.value = value;
		return this;
	}
	public GossipMessageBuilder setWeight(double weight) {
		this.weight = weight;
		return this;
	}
	public GossipMessageBuilder setCycleNumber(int cycleNumber) {
		this.cycleNumber = cycleNumber;
		return this;
	}
	public GossipMessageBuilder setLocalLedger(LinkedHashSet<BECPBlock> localLedger) {
		this.localLedger = localLedger;
		return this;
	}
	public GossipMessageBuilder setNeighborsLocalCache(ArrayList<BECPNode> neighborsLocalCache) {
		this.neighborsLocalCache = neighborsLocalCache;
		return this;
	}
	public GossipMessageBuilder setBlockLocalCache(HashMap<Integer, BECPBlock> blockLocalCache) {
		this.blockLocalCache = blockLocalCache;
		return this;
	}
	public GossipMessageBuilder setCriticalPushFlag(boolean criticalPushFlag) {
		this.criticalPushFlag = criticalPushFlag;
		return this;
	}
	public GossipMessageBuilder setP(HashMap<Integer, Process> p) {
		P = p;
		return this;
	}
	public GossipMessageBuilder setA(A a) {
		A = a;
		return this;
	}
	public GossipMessageBuilder setC(C c) {
		C = c;
		return this;
	}
	public GossipMessageBuilder setL(int l) {
		this.l = l;
		return this;
	}
	public GossipMessageBuilder setCrashedNodes(HashSet<BECPNode> crashedNodes) {
		this.crashedNodes = crashedNodes;
		return this;
	}
	public GossipMessageBuilder setJoinedNodes(HashSet<BECPNode> joinedNodes) {
		this.joinedNodes = joinedNodes;
		return this;
	}
	public GossipMessageBuilder setIsReceivedPull(boolean isReceivedPull) {
		this.isReceivedPull = isReceivedPull;
		return this;
	}
	public GossipMessageBuilder setIsNewJoined(boolean isNewJoined) {
		this.isNewJoined = isNewJoined;
		return this;
	}
    
    public Gossip buildPullGossip(Node sender, int size) {
    	return new BECPPull<BECPBlock>(sender, cycleNumber, size, value, weight, neighborsLocalCache, blockLocalCache, criticalPushFlag, l, P, A, C, crashedNodes, joinedNodes, localLedger, mainCache_d, donatedCache);
    }
    
    public Gossip buildPushGossip(Node sender, int size) {
    	return new BECPPush<BECPBlock>(sender, cycleNumber, size, value, weight, neighborsLocalCache, blockLocalCache, criticalPushFlag, isReceivedPull, l, P, A, C, crashedNodes, joinedNodes, isNewJoined, mainCache_S, v_d, h, d);
    }

	public GossipMessageBuilder setV_d(Integer v_d) {
		this.v_d = v_d;
		return this;
	}

	public GossipMessageBuilder setH(Integer h) {
		this.h = h;
		return this;
	}
	
	public GossipMessageBuilder setD(BECPNode d) {
		this.d = d;
		return this;
	}

	public GossipMessageBuilder setMainCache_d(Multimap<BECPNode, Integer> mainCache_d) {
		this.mainCache_d = mainCache_d;
		return this;
	}
	
	public GossipMessageBuilder setMainCache_S(Multimap<BECPNode, Integer> mainCache_S) {
		this.mainCache_S = mainCache_S;
		return this;
	}
	
	public GossipMessageBuilder setDonatedCache(Multimap<BECPNode, Integer> donatedCache) {
		this.donatedCache = donatedCache;
		return this;
	}
}
