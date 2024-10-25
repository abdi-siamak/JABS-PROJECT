package jabs.ledgerdata.snow;

import java.util.List;

import jabs.ledgerdata.Tx;
import jabs.network.node.nodes.snow.AvalancheNode;

public class AvalancheTx extends Tx<AvalancheTx>{
	public static final int Snow_BLOCK_HASH_SIZE = 32;
	private int height;
	private double creationTime;
	private AvalancheNode creator;
	private List<AvalancheTx> parents;
	
    public AvalancheTx(int height, int size, double creationTime, AvalancheNode creator, List<AvalancheTx> parents) {
        super(size, Snow_BLOCK_HASH_SIZE);
        this.height = height;
    	this.creationTime = creationTime;
    	this.creator = creator;
    	this.parents = parents;
    }

	public double getCreationTime() {
		return creationTime;
	}

	public void setCreationTime(double creationTime) {
		this.creationTime = creationTime;
	}

	public AvalancheNode getCreator() {
		return creator;
	}

	public void setCreator(AvalancheNode creator) {
		this.creator = creator;
	}

	public List<AvalancheTx> getParents() {
		return parents;
	}

	public void setParents(List<AvalancheTx> parents) {
		this.parents = parents;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}
}
