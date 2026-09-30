package padik.deadreckoning.sensor;

import org.junit.Before;
import org.junit.Test;
import padik.deadreckoning.model.GNSSSample;
import padik.deadreckoning.model.IMUSample;
import padik.deadreckoning.model.INSState;
import org.osmdroid.util.GeoPoint;
import static org.junit.Assert.*;

public class ExtendedKalmanFilterTest {

    private ExtendedKalmanFilter ekf;

    @Before
    public void setUp() {
        ekf = new ExtendedKalmanFilter();
        
        INSState initialState = new INSState();
        initialState.isInitialized = true;
        initialState.origin = new GeoPoint(0.0, 0.0);
        initialState.geoPoint = initialState.origin;
        initialState.quaternion = new double[]{1, 0, 0, 0}; // Identity (level)
        initialState.velNorth = 0.0;
        initialState.velEast = 0.0;
        initialState.velDown = 0.0;
        initialState.posNorth = 0.0;
        initialState.posEast = 0.0;
        initialState.posDown = 0.0;
        
        ekf.initialize(initialState);
    }

    @Test
    public void testPredictStationary() {
        // Stationary IMU: accel = gravity in positive Z (NED frame assumes Down is positive).
        IMUSample imu = new IMUSample(1000000000L, 0.0f, 0.0f, 9.80665f, 0.0f, 0.0f, 0.0f);
        
        ekf.predict(imu, 0.1); // dt = 0.1s
        
        INSState state = ekf.getNominalState();
        assertEquals(0.0, state.velNorth, 0.01);
        assertEquals(0.0, state.velEast, 0.01);
        assertEquals(0.0, state.velDown, 0.01);
        
        assertEquals(0.0, state.posNorth, 0.01);
        assertEquals(0.0, state.posEast, 0.01);
        assertEquals(0.0, state.posDown, 0.01);
    }

    @Test
    public void testPredictConstantAcceleration() {
        // Accelerate North at 2.0 m/s^2, gravity Down at 9.80665 m/s^2
        IMUSample imu = new IMUSample(1000000000L, 2.0f, 0.0f, 9.80665f, 0.0f, 0.0f, 0.0f);
        
        ekf.predict(imu, 0.5); // dt = 0.5s
        
        INSState state = ekf.getNominalState();
        // v = a * t = 2.0 * 0.5 = 1.0 m/s
        assertEquals(1.0, state.velNorth, 0.01);
        assertEquals(0.0, state.velEast, 0.01);
        assertEquals(0.0, state.velDown, 0.01);
        
        // p = v_prev * t (since naive Euler integration in code: pos += vel * dt)
        // vel updated first, so pos += 1.0 * 0.5 = 0.5m
        assertEquals(0.5, state.posNorth, 0.01);
    }
    
    @Test
    public void testZUPTUpdate() {
        // Make it think it's moving
        IMUSample imu = new IMUSample(1000000000L, 2.0f, 0.0f, 9.80665f, 0.0f, 0.0f, 0.0f);
        ekf.predict(imu, 0.5);
        
        INSState state = ekf.getNominalState();
        assertTrue(state.velNorth > 0.5); // It should have velocity
        
        // Apply ZUPT
        ekf.updateWithZUPT();
        
        // Velocity should be corrected back towards 0
        state = ekf.getNominalState();
        assertEquals(0.0, state.velNorth, 0.1);
        assertEquals(0.0, state.velEast, 0.1);
        assertEquals(0.0, state.velDown, 0.1);
    }
}
