package jabs.ledgerdata.becp;

public class Process implements Cloneable{
	private int identifier;
	private double value;
	private double weight;
	
	public Process(final int identifier, final double value, final double weight){
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
    public Process clone() {
        try {
            return (Process) super.clone();
        } catch (CloneNotSupportedException e) {
            // This should never happen, as we are Cloneable
            throw new AssertionError();
        }
    }

}
