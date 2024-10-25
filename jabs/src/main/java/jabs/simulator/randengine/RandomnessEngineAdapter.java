package jabs.simulator.randengine;

import java.util.Random;

public class RandomnessEngineAdapter extends Random {
    private RandomnessEngine engine;

    public RandomnessEngineAdapter(RandomnessEngine engine) {
        this.engine = engine;
    }

    @Override
    protected int next(int bits) {
        return engine.nextInt(bits);
    }
}
