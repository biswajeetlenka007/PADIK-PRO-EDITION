package padik.deadreckoning.sensor;

import org.junit.Test;
import static org.junit.Assert.*;

public class KalmanFilterTest {

    @Test
    public void testInitialization() {
        KalmanFilter kf = new KalmanFilter(0.1, 1.0, 5.0, 10.0);
        assertEquals(5.0, kf.getEstimate(), 0.001);
        assertEquals(10.0, kf.getError(), 0.001);
    }

    @Test
    public void testUpdateStep() {
        // High process noise, low measurement noise -> trusts measurement more
        KalmanFilter kf = new KalmanFilter(100.0, 0.1, 0.0, 1.0);
        
        double result = kf.update(10.0);
        // Estimate should jump close to 10.0
        assertTrue("Estimate should move towards measurement", result > 9.0);
    }

    @Test
    public void testUpdateStepTrustEstimate() {
        // Low process noise, high measurement noise -> trusts estimate more
        KalmanFilter kf = new KalmanFilter(0.001, 100.0, 0.0, 0.1);
        
        double result = kf.update(10.0);
        // Estimate should stay close to 0.0
        assertTrue("Estimate should stay close to initial estimate", result < 1.0);
    }

    @Test
    public void testWrapX() {
        KalmanFilter kf = new KalmanFilter(0.1, 1.0, 370.0, 1.0);
        kf.wrapX(0, 360);
        assertEquals(10.0, kf.getEstimate(), 0.001);
        
        KalmanFilter kf2 = new KalmanFilter(0.1, 1.0, -10.0, 1.0);
        kf2.wrapX(0, 360);
        assertEquals(350.0, kf2.getEstimate(), 0.001);
    }
}
