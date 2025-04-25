package jabs.ledgerdata.snow;

import java.util.Objects;

import jabs.ledgerdata.SingleParentBlock;
import jabs.network.node.nodes.Node;

public class SnowBlock extends SingleParentBlock<SnowBlock> {
    public static final int Snow_BLOCK_HASH_SIZE = 32;

    public SnowBlock(int size, int height, double creationTime, Node creator, SnowBlock parent) {
        super(size, height, creationTime, creator, parent, Snow_BLOCK_HASH_SIZE);
    }
    
    public void setCreationTime(double time) {
        this.creationTime = time;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(
            getSize(),         
            getHeight(),        
            getCreationTime(),  
            getCreator(),      
            getParent(),
            Snow_BLOCK_HASH_SIZE
        );
    }

    public String getBinaryHash() {
        int hash = this.hashCode();

        // Convert hash to binary and ensure fixed 32-bit length with leading zeros
        return String.format("%32s", Integer.toBinaryString(hash)).replace(' ', '0');
    }

}
