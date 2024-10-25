package jabs.ledgerdata;

import jabs.network.node.nodes.Node;

import java.util.List;

public abstract class Block<B extends Block<B>> extends Data implements Comparable<Block<B>> {
    protected int height;
    protected double creationTime;
    private List<B> parents;
    protected Node creator;

    protected Block(int size, int height, double creationTime, Node creator, List<B> parents, int hashSize) {
        super(size, hashSize);
        this.height = height;
        this.creationTime = creationTime;
        this.creator = creator;
        this.parents = parents;
    }

    public int getHeight() {
        return this.height;
    }

    public double getCreationTime() {
        return this.creationTime;
    }

    public Node getCreator() {
        return this.creator;
    }

    public List<B> getParents() {
        return this.parents;
    }

    public void setParent(B parent) { // for single parent blocks
        this.parents.clear();
        this.parents.add(0, parent);
    }

    public int compareTo(Block<B> b) {
        return Integer.compare(this.height, b.getHeight());
    }
}
