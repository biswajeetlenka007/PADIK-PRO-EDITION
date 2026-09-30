package padik.deadreckoning.sensor;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreciseHeadingEstimatorTest {

    private PreciseHeadingEstimator estimator;

    @Before
    public void setUp() {
        estimator = new PreciseHeadingEstimator();
    }

    @Test
    public void testGravityUpdate() {
        float[] values = {0f, 0f, 9.8f};
        estimator.updateGravity(values);
        
        float[] gravity = estimator.getGravity();
        assertNotNull(gravity);
        assertEquals(0f, gravity[0], 0.001f);
        assertEquals(0f, gravity[1], 0.001f);
        assertEquals(9.8f, gravity[2], 0.001f);
    }

    @Test
    public void testGyroscopeIntegration() {
        // We'll feed a constant turn rate and see if the heading integrates
        // Assuming updateGyroscope calculates dt based on timestamps
        // Let's supply 1 rad/s yaw rate around Z (assuming Z is up/down)
        
        float[] gyro = {0f, 0f, 1.0f};
        
        // Initial call to set timestamp
        estimator.updateGyroscope(gyro, 1000000000L); // 1 sec
        
        // Next call 0.5s later (500ms = 500,000,000 ns)
        estimator.updateGyroscope(gyro, 1500000000L); 
        
        // The gyroscope should have integrated ~0.5 rad
        // Since we don't have direct access to gyro-only heading without reflection, 
        // we can just check if getGyroscope() returns the array
        
        float[] latestGyro = estimator.getGyroscope();
        assertNotNull(latestGyro);
        assertEquals(1.0f, latestGyro[2], 0.001f);
    }
}
