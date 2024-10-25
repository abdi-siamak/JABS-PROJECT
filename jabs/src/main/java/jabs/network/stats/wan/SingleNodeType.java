package jabs.network.stats.wan;

public enum SingleNodeType {
    WAN_NODE(0);

    public static final SingleNodeType[] sixRegionsValues = SingleNodeType.values();

    private final int value;
    SingleNodeType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
