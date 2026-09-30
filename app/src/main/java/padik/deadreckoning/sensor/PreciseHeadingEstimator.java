package padik.deadreckoning.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import padik.deadreckoning.model.MotionMode;

public class PreciseHeadingEstimator {
    
    private static final float MAG_WEIGHT = 0.02f;
    private static final float ALPHA = 0.96f;
    
    private float[] gravityValues = null;
    private float[] magValues = null;
    private float[] gyroValues = null;
    
    private double gyroHeading = 0;
    private double magHeading = 0;
    private double filteredHeading = 0;
    
    private long lastTimestamp = 0;
    
    private KalmanFilter headingKalman;
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    
    private MotionMode motionMode = MotionMode.WALKING;
    
    public PreciseHeadingEstimator() {
        headingKalman = new KalmanFilter(0.001, 0.05, 0, 1);
    }
    
    public void setMotionMode(MotionMode mode) {
        this.motionMode = mode;
    }

    public void updateGravity(float[] values) {
        this.gravityValues = values;
    }
    
    public void updateMagneticField(float[] values) {
        this.magValues = values;
    }
    
    private boolean isInitialized = false;
    
    public void updateGyroscope(float[] values, long timestamp) {
        if (lastTimestamp == 0) {
            lastTimestamp = timestamp;
            return;
        }
        
        float dt = (timestamp - lastTimestamp) / 1_000_000_000.0f;
        lastTimestamp = timestamp;
        
        gyroValues = values.clone();
        
        // Initialize gyroHeading with magHeading on first run if available
        if (!isInitialized && magValues != null && gravityValues != null) {
            calculateMagnetometerHeading();
            gyroHeading = magHeading;
            isInitialized = true;
        }
        
        double gyroHeadingDelta = -gyroValues[2] * dt;
        gyroHeading += gyroHeadingDelta;
        
        while (gyroHeading > Math.PI) gyroHeading -= 2 * Math.PI;
        while (gyroHeading < -Math.PI) gyroHeading += 2 * Math.PI;
    }
    
    public void updateGyroscope(SensorEvent event) {
        updateGyroscope(event.values, event.timestamp);
    }
    
    public double getHeading() {
        if (gravityValues != null && magValues != null) {
            calculateMagnetometerHeading();
        }
        
        if (gyroValues != null) {
            if (motionMode == MotionMode.VEHICLE) {
                // Do not fuse magnetometer in vehicle mode, rely strictly on gyroscope integration
            } else {
                double deltaComp = magHeading - gyroHeading;
                
                // Phase unwrapping for complementary filter
                if (deltaComp > Math.PI) deltaComp -= 2 * Math.PI;
                if (deltaComp < -Math.PI) deltaComp += 2 * Math.PI;
                
                gyroHeading = gyroHeading + (1 - ALPHA) * deltaComp;
                
                // Wrap gyroHeading to [-PI, PI]
                if (gyroHeading > Math.PI) gyroHeading -= 2 * Math.PI;
                if (gyroHeading < -Math.PI) gyroHeading += 2 * Math.PI;
            }
            
            filteredHeading = gyroHeading;
        } else {
            filteredHeading = magHeading;
        }
        
        // Phase unwrapping for Kalman Filter
        double currentEstimate = headingKalman.getEstimate();
        double deltaKalman = filteredHeading - currentEstimate;
        if (deltaKalman > Math.PI) filteredHeading -= 2 * Math.PI;
        if (deltaKalman < -Math.PI) filteredHeading += 2 * Math.PI;

        headingKalman.update(filteredHeading);
        headingKalman.wrapX(-Math.PI, Math.PI);
        
        return Math.toDegrees(headingKalman.getEstimate());
    }
    
    // Allows injecting external GPS calibration for gyro bias tracking
    public void setGyroHeading(double headingRadians) {
        this.gyroHeading = headingRadians;
    }
    
    public double getGyroHeadingRadians() {
        return this.gyroHeading;
    }
    
    private void calculateMagnetometerHeading() {
        boolean success = SensorManager.getRotationMatrix(
            rotationMatrix, null, gravityValues, magValues
        );
        
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            magHeading = orientationAngles[0];
        }
    }
    
    public double getHeadingDegrees() {
        return getHeading();
    }
    
    public void reset() {
        gyroHeading = 0;
        magHeading = 0;
        filteredHeading = 0;
        lastTimestamp = 0;
        isInitialized = false;
        headingKalman.reset(0, 1);
    }
    
    public float getAccuracy() {
        return gravityValues != null && magValues != null ? 1.0f : 0.0f;
    }
    
    public boolean hasValidData() {
        return gravityValues != null && magValues != null;
    }

    public float[] getGravity() {
        return gravityValues;
    }

    public float[] getMagneticField() {
        return magValues;
    }

    public float[] getGyroscope() {
        return gyroValues;
    }
}
