package jabs.ledgerdata.becp;

public class Key {
    private int nodeID;
    private int cycleNumber;

    public Key(int nodeID, int cycleNumber) {
        this.nodeID = nodeID;
        this.cycleNumber = cycleNumber;
    }

	public int getNodeID() {
		return nodeID;
	}

	public int getCycleNumber() {
		return cycleNumber;
	}
	
	@Override
	public boolean equals(Object o) {
	    if (this == o) return true;
	    if (o == null || getClass() != o.getClass()) return false;
	    Key other = (Key) o;
	    return nodeID == other.nodeID && cycleNumber == other.cycleNumber;
	}

	@Override
	public int hashCode() {
	    int result = 17;
	    result = 31 * result + nodeID;
	    result = 31 * result + cycleNumber;
	    return result;
	}
}