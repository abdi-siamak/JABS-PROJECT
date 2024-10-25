package jabs.ledgerdata.becp;

import java.util.HashSet;

import jabs.ledgerdata.SingleParentBlock;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.becp.BECPNode;

public class BECPBlock extends SingleParentBlock<BECPBlock> implements Cloneable {
    public static final int BECP_BLOCK_HASH_SIZE = 32; //(ENCRYPTED)
    private final BECPBlock parent; //(ENCRYPTED)
    private HashSet<BECPBlock> children;
    private double vPropagation;//(PTP protocol)
    private double wPropagation;//(PTP protocol)
    private double vAgreement;//(PTP protocol)
    private double wAgreement;//(PTP protocol)
    private double vDataAggregation;//(ECP protocol)
    private double wDataAggregation;//(ECP protocol)
    private double vDataConvergence;//(ECP protocol)
    private double vDataAgreement;//(ECP protocol)
    private double weightValue;//(ECP protocol)
    private BECPBlock.State state;//(ECP & PTP)
    private int cycleNumber; //(PTP protocol)
    private int leader; //(ECP protocol)
    private int numCommits = 0; //(PTP protocol)
    private int numAgreements = 0; //(PTP protocol)
    private int blockLifeTime = 0; 
    
    public BECPBlock(int size, int height, double creationTime, int cycleNumber, Node creator, int leader, BECPBlock parent, BECPBlock.State state, double vPropagation, double wPropagation, double vAgreement, double wAgreement, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
        super(size, height, creationTime, creator, parent, BECP_BLOCK_HASH_SIZE);
        this.cycleNumber=cycleNumber;
        this.vPropagation=vPropagation;
        this.wPropagation=wPropagation;
        this.vAgreement=vAgreement;
        this.wAgreement=wAgreement;
        this.vDataAggregation = vDataAggregation;
        this.wDataAggregation = wDataAggregation;
        this.vDataConvergence = vDataConvergence;
        this.vDataAgreement = vDataAgreement;
        this.weightValue = weightValue;
        this.state=state;
        this.leader = leader;
        this.parent = parent;
        this.children = new HashSet<>();
    }

    public enum State {
        PROPAGATION,
        AGREEMENT,
        COMMIT;
       
    }
    
    @Override
    public BECPBlock clone() {
        try {
            return (BECPBlock) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Should never happen
        }
    }
    

    public double getVPropagation(){
        return vPropagation;
    }
    public void setVPropagation(double vPropagation) {this.vPropagation = vPropagation; }
    public double getWPropagation(){
        return wPropagation;
    }
    public void setWPropagation(double wPropagation) { this.wPropagation = wPropagation; }
    public double getVAgreement(){
        return vAgreement;
    }
    public void setVAgreement(double vAgreement) {this.vAgreement = vAgreement; }
    public double getWAgreement(){
        return wAgreement;
    }
    public void setWAgreement(double wAgreement) {this.wAgreement = wAgreement; }
    public BECPBlock.State getState(){
        return state;
    }
    public void setState(BECPBlock.State state){this.state = state; }
    public void setNumCommits(int num) {this.numCommits = num; }
    public int getNumCommits() { return numCommits; }
    public void setNumAgreements(int num) {this.numAgreements = num; }
    public int getNumAgreements() { return numAgreements; }
    public void setCreator(BECPNode creator) {this.creator = creator; }
    public void setSize(int size) {this.size=size; }
    public int getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(int cycle) {this.cycleNumber=cycle;}

    public void setVDataAggregation(double vDataAggregation) {this.vDataAggregation = vDataAggregation; }
    public double getVDataAggregation() {return vDataAggregation; }
    public void setWDataAggregation(double wDataAggregation) {this.wDataAggregation = wDataAggregation; }
    public double getWDataAggregation() {return wDataAggregation; }
    public void setVDataConvergence(double vDataConvergence) {this.vDataConvergence = vDataConvergence; }
    public double getVDataConvergence() {return vDataConvergence; }
    public void setVDataAgreement(double vDataAgreement) {this.vDataAgreement = vDataAgreement; }
    public double getVDataAgreement() {return vDataAgreement; }
    public void setWeightValue(double weightValue) {this.weightValue = weightValue; }
    public double getWeightValue() {return weightValue; }
	public int getLeader() {
		return leader;
	}

	public void setLeader(int leader) {
		this.leader = leader;
	}
    public void setCreationTime(double time) {
        this.creationTime = time;
    }

	public HashSet<BECPBlock> getChildren() {
		return children;
	}
	public void addTochildren(BECPBlock child) {
		this.children.add(child);
	}


	public void increaceBlockLifeTime() {
		blockLifeTime = blockLifeTime +1;
	}

	public int getBlockLifeTime() {
		return blockLifeTime;
	}
}
