package jabs.ledgerdata.becp;

import java.util.ArrayList;

public class Metadata {
    private int times;  
    private ArrayList<Integer> heights = new ArrayList<>();
    
    public int getTimes() {
    	return times;
    }
    
    public ArrayList<Integer> getHeights(){
    	return heights;
    }
    
    public void addTimes() {
    	times = times + 1;
    }
    
    public void addHeight(int h) {
    	heights.add(h);
    }
    
    public double getAverageHeight() {
    	return heights.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}