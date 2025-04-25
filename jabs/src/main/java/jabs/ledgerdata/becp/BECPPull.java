package jabs.ledgerdata.becp;

import jabs.consensus.algorithm.BECP;
import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.becp.BECPNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import com.google.common.collect.Multimap;

public class BECPPull<B extends Block<B>> extends BECPBlockGossip<B> {
    private final double value; // SSEP & REAP
    private final double weight; // SSEP & REAP
    private final int cycleNumber;
    private final LinkedHashSet<BECPBlock> localLedger; // REAP+
    private final ArrayList<BECPNode> neighborsLocalCache; // NCP
    private final HashMap<Integer, BECPBlock> blockLocalCache; // PTP
    private boolean criticalPushFlag; // REAP 
    private final HashMap<Integer, Process> P; // ARP 
    private final A A; // the process Ai-ARP .
    private final C C; // the process Ci-ARP .
    private final int l; // incremental global epoch identifier-ARP
    private final HashSet<BECPNode> crashedNodes;// REAP+
    private final HashSet<BECPNode> joinedNodes;// REAP+
    private Multimap<BECPNode, Integer> mainCache_d; // Q_d: main cache of d (EMP+)
    private Multimap<BECPNode, Integer> donatedCache; // Q: set of donated cache entries (EMP+)
    
    public BECPPull(final Node sender, final int cycleNumber, final int size, final double value, final double weight, final ArrayList<BECPNode> neighborsLocalCache, final HashMap<Integer, BECPBlock> blockLocalCache, final boolean criticalPushFlag, final int l, final HashMap<Integer, Process> P, final A A, final C C, final HashSet<BECPNode> crashedNodes, final HashSet<BECPNode> joinedNodes, final LinkedHashSet<BECPBlock> localLedger, Multimap<BECPNode, Integer> mainCache_d, Multimap<BECPNode, Integer> donatedCache) {
        super(size + BECP_GOSSIP_SIZE_OVERHEAD, sender, GossipType.PULL);
        this.value = value;
        this.weight = weight;
        this.cycleNumber = cycleNumber;
        this.neighborsLocalCache = neighborsLocalCache;
        this.blockLocalCache = blockLocalCache;
        this.criticalPushFlag = criticalPushFlag;
        this.l = l;
        this.P = P;
        this.A = A;
        this.C = C;
        this.crashedNodes = crashedNodes;
        this.joinedNodes = joinedNodes;
        this.localLedger = localLedger;
        this.setMainCache_d(mainCache_d);
        this.setDonatedCache(donatedCache);
    }
    public double getValue(){
        return value;
    }
    public double getWeight(){
        return weight;
    }
    public double getSystemSize() { // Estimated System Size
    	if(BECP.ARP) {
    		ArrayList<Double> estimateValues = new ArrayList<>();
			for(Integer processID : P.keySet()) {
				Process process = P.get(processID);
				double estimateValue = process.getValue()/process.getWeight();
				if(!Double.isNaN(estimateValue)&&Double.isFinite(estimateValue)) {
					estimateValues.add(estimateValue);
				}
			}
			double average = getAverage(estimateValues);
			return average;
    	}else {
    		return value/weight;
    	}
    } 
    public ArrayList<BECPNode> getNeighborsLocalCache() { return neighborsLocalCache; }
    public HashMap<Integer, BECPBlock> getBlockLocalCache() { return blockLocalCache; }
    public boolean getCriticalPushFlag(){ return criticalPushFlag; }
	public HashMap<Integer, Process> getP() {
		return P;
	}
	public int getL() {
		return l;
	}
	public A getA() {
		return A;
	}
	public C getC() {
		return C;
	}
	private double getAverage(final ArrayList<Double> list) {
		double average = 0;
		for(double e:list) {
			average +=e;
		}
		return average/list.size();
	}
	public int getCycleNumber() {
		return cycleNumber;
	}
	public LinkedHashSet<BECPBlock> getLocalLedger() {
		return localLedger;
	}
	public HashSet<BECPNode> getCrashedNodes() {
		return crashedNodes;
	}
	public HashSet<BECPNode> getJoinedNodes() {
		return joinedNodes;
	}
	public Multimap<BECPNode, Integer> getMainCache_d() {
		return mainCache_d;
	}
	public void setMainCache_d(Multimap<BECPNode, Integer> mainCache_d) {
		this.mainCache_d = mainCache_d;
	}
	public Multimap<BECPNode, Integer> getDonatedCache() {
		return donatedCache;
	}
	public void setDonatedCache(Multimap<BECPNode, Integer> donatedCache) {
		this.donatedCache = donatedCache;
	}
}
