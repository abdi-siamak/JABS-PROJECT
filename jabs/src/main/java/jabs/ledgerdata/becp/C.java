package jabs.ledgerdata.becp;

public class C implements Cloneable{
    private int identifier;
    private double value;
    private double weight;

    public C(final int identifier, final double value, final double weight) {
        this.identifier = identifier;
        this.value = value;
        this.weight = weight;
    }

    public int getIdentifier() {
        return identifier;
    }
    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }
    public double getValue() {
        return value;
    }
    public void setValue(double value) {
        this.value = value;
    }
    public double getWeight() {
        return weight;
    }
    public void setWeight(double weight) {
        this.weight = weight;
    }
    @Override
    public C clone() {
        try {
            return (C) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Should never happen
        }
    }
}
