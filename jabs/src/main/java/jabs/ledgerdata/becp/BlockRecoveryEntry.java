package jabs.ledgerdata.becp;

public class BlockRecoveryEntry {
    private final double replicaVPropagation;
    private final double replicaWPropagation;
    private final double replicaVAgreement;
    private final double replicaWAgreement;

    public BlockRecoveryEntry(double replicaVPropagation, double replicaWPropagation, double replicaVAgreement, double replicaWAgreement){
        this.replicaVPropagation = replicaVPropagation;
        this.replicaWPropagation = replicaWPropagation;
        this.replicaVAgreement = replicaVAgreement;
        this.replicaWAgreement = replicaWAgreement;
    }

	public double getReplicaVPropagation() {
		return replicaVPropagation;
	}

	public double getReplicaWPropagation() {
		return replicaWPropagation;
	}

	public double getReplicaVAgreement() {
		return replicaVAgreement;
	}

	public double getReplicaWAgreement() {
		return replicaWAgreement;
	}
}
