package padik.deadreckoning.sensor;

import java.util.ArrayList;
import java.util.List;

public class EnhancedStepCounter {
    
    private static final int WINDOW_SIZE = 15;
    private static final double STEP_THRESHOLD_HIGH = 1.2;
    private static final double STEP_THRESHOLD_LOW = 0.3;
    private static final double STEP_MIN_TIME_MS = 300;
    private static final double MIN_PEAK_VALLEY_DIFF = 1.0;
    
    private List<Double> accelerationWindow = new ArrayList<>();
    private List<Long> timestampWindow = new ArrayList<>();
    
    private int stepCount = 0;
    private long lastStepTime = 0;
    private boolean isPeakFound = false;
    private double peakValue = 0;
    private double valleyValue = 999.0;
    private double currentStrideLength = 0.75;
    
    private KalmanFilter kalmanFilter;
    private int rejectedStepCount = 0;
    
    public EnhancedStepCounter() {
        kalmanFilter = new KalmanFilter(0.01, 0.1, 0, 1);
    }
    
    public boolean detectStep(float[] linearAcceleration, long timestamp) {
        double magnitude = Math.sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
            linearAcceleration[1] * linearAcceleration[1] +
            linearAcceleration[2] * linearAcceleration[2]
        );
        
        // Motion gating: ignore small noise / vibrations / taps (< 0.35 m/s^2)
        if (magnitude < 0.35) {
            return false;
        }
        
        double filteredAcc = kalmanFilter.update(magnitude);
        
        accelerationWindow.add(filteredAcc);
        timestampWindow.add(timestamp);
        
        if (accelerationWindow.size() > WINDOW_SIZE) {
            accelerationWindow.remove(0);
            timestampWindow.remove(0);
        }
        
        return analyzeForStep(filteredAcc, timestamp);
    }
    
    public void updateOnly(float[] linearAcceleration, long timestamp) {
        double magnitude = Math.sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
            linearAcceleration[1] * linearAcceleration[1] +
            linearAcceleration[2] * linearAcceleration[2]
        );
        double filteredAcc = kalmanFilter.update(magnitude);
        accelerationWindow.add(filteredAcc);
        timestampWindow.add(timestamp);
        if (accelerationWindow.size() > WINDOW_SIZE) {
            accelerationWindow.remove(0);
            timestampWindow.remove(0);
        }
    }
    
    private boolean analyzeForStep(double acceleration, long timestamp) {
        if (!isPeakFound) {
            if (acceleration > STEP_THRESHOLD_HIGH) {
                isPeakFound = true;
                peakValue = acceleration;
                valleyValue = acceleration;
                return false;
            }
        } else {
            if (acceleration > peakValue) {
                peakValue = acceleration;
            }
            if (acceleration < valleyValue) {
                valleyValue = acceleration;
            }
            
            if (acceleration < STEP_THRESHOLD_LOW) {
                long timeDelta = (timestamp - lastStepTime) / 1000000; // ms
                if (timeDelta > STEP_MIN_TIME_MS) {
                    if ((peakValue - valleyValue) >= MIN_PEAK_VALLEY_DIFF) {
                        lastStepTime = timestamp;
                        stepCount++;
                        isPeakFound = false;
                        valleyValue = 999.0;
                        return true;
                    } else {
                        rejectedStepCount++;
                        isPeakFound = false;
                        valleyValue = 999.0;
                    }
                } else {
                    rejectedStepCount++;
                    isPeakFound = false;
                    valleyValue = 999.0;
                }
            }
        }
        
        if (acceleration < STEP_THRESHOLD_LOW && !isPeakFound) {
            isPeakFound = false;
        }
        
        return false;
    }
    
    public void setStrideLength(double strideLengthMeters) {
        this.currentStrideLength = strideLengthMeters;
    }
    
    public double getStrideLength() {
        return currentStrideLength;
    }
    
    public int getStepCount() {
        return stepCount;
    }
    
    public int getRejectedStepCount() {
        return rejectedStepCount;
    }
    
    public void reset() {
        stepCount = 0;
        lastStepTime = 0;
        isPeakFound = false;
        peakValue = 0;
        valleyValue = 999.0;
        rejectedStepCount = 0;
        accelerationWindow.clear();
        timestampWindow.clear();
        kalmanFilter.reset(0, 1);
    }
    
    public double getDistanceTraveled() {
        return stepCount * currentStrideLength;
    }
    
    public double getCurrentAcceleration() {
        if (accelerationWindow.isEmpty()) return 0;
        return accelerationWindow.get(accelerationWindow.size() - 1);
    }
    
    public double getFilteredAcceleration() {
        return kalmanFilter.getEstimate();
    }
    
    public List<Double> getAccelerationHistory() {
        return new ArrayList<>(accelerationWindow);
    }
}
