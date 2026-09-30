package padik.deadreckoning.sensor;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnhancedStepCounterTest {

    private EnhancedStepCounter stepCounter;

    @Before
    public void setUp() {
        stepCounter = new EnhancedStepCounter();
    }

    @Test
    public void testPeakValleyDetection() {
        // A single step involves a valley followed by a peak, or vice-versa, above/below thresholds.
        // We'll feed it a synthetic wave.
        long timeNs = 1000000000L;
        long dt = 20000000L; // 20ms (50Hz)
        
        // Steady state (linear accel is 0)
        for (int i = 0; i < 50; i++) {
            float[] accel = {0, 0, 0.0f};
            stepCounter.detectStep(accel, timeNs);
            timeNs += dt;
        }
        
        // Impact (Peak) - linear accel shoots up
        for (int i = 0; i < 50; i++) {
            float[] accel = {0, 0, 5.0f};
            stepCounter.detectStep(accel, timeNs);
            timeNs += dt;
        }
        
        // Free-fall (Valley) - linear accel drops significantly
        for (int i = 0; i < 150; i++) {
            float[] accel = {0, 0, 0.1f};
            stepCounter.detectStep(accel, timeNs);
            timeNs += dt;
        }
        
        // Recovery (steady)
        for (int i = 0; i < 50; i++) {
            float[] accel = {0, 0, 0.0f};
            stepCounter.detectStep(accel, timeNs);
            timeNs += dt;
        }
        
        // Test basic methods
        assertNotNull(stepCounter);
        stepCounter.setStrideLength(0.8);
        assertEquals(0.8, stepCounter.getStrideLength(), 0.001);
        stepCounter.reset();
        assertEquals(0, stepCounter.getStepCount());
    }
}
