package jabs.ledgerdata.becp;

import jabs.network.node.nodes.Node;

public class ReplicaBlock {
    private double vPropagation;
    private double wPropagation;
    private double vAgreement;
    private double wAgreement;
    private Node blockCreator;
    
	public double getVPropagation() {
		return vPropagation;
	}
	public void setVPropagation(double vPropagation) {
		this.vPropagation = vPropagation;
	}
	public double getWPropagation() {
		return wPropagation;
	}
	public void setWPropagation(double wPropagation) {
		this.wPropagation = wPropagation;
	}
	public double getVAgreement() {
		return vAgreement;
	}
	public void setVAgreement(double vAgreement) {
		this.vAgreement = vAgreement;
	}
	public double getWAgreement() {
		return wAgreement;
	}
	public void setWAgreement(double wAgreement) {
		this.wAgreement = wAgreement;
	}
	public Node getBlockCreator() {
		return blockCreator;
	}
	public void setBlockCreator(Node blockCreator) {
		this.blockCreator = blockCreator;
	}
}
