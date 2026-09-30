package padik.deadreckoning.sensor;

import padik.deadreckoning.model.IMUSample;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects whether the vehicle is stationary using accelerometer and gyroscope metrics.
 */
public class ZUPTDetector {

    // Configuration
    private int windowSize = 20; // ~400ms at 50Hz
    private double accelMagThreshold = 0.25; // m/s^2 deviation from g
    private double gyroMagThreshold = 0.05;  // rad/s
    private double accelVarThreshold = 0.01; // (m/s^2)^2
    private double gyroVarThreshold = 0.001; // (rad/s)^2
    
    private List<IMUSample> window = new ArrayList<>();
    private boolean isStationary = false;
    
    private static final double G = 9.80665;

    public void addSample(IMUSample imu) {
        window.add(imu);
        if (window.size() > windowSize) {
            window.remove(0);
        }
        
        if (window.size() == windowSize) {
            detect();
        }
    }

    private void detect() {
        double sumAccMag = 0;
        double sumGyroMag = 0;
        
        for (IMUSample s : window) {
            double amag = Math.sqrt(s.getAx()*s.getAx() + s.getAy()*s.getAy() + s.getAz()*s.getAz());
            double gmag = Math.sqrt(s.getGx()*s.getGx() + s.getGy()*s.getGy() + s.getGz()*s.getGz());
            sumAccMag += amag;
            sumGyroMag += gmag;
        }
        
        double meanAccMag = sumAccMag / windowSize;
        double meanGyroMag = sumGyroMag / windowSize;
        
        double varAcc = 0;
        double varGyro = 0;
        for (IMUSample s : window) {
            double amag = Math.sqrt(s.getAx()*s.getAx() + s.getAy()*s.getAy() + s.getAz()*s.getAz());
            double gmag = Math.sqrt(s.getGx()*s.getGx() + s.getGy()*s.getGy() + s.getGz()*s.getGz());
            varAcc += Math.pow(amag - meanAccMag, 2);
            varGyro += Math.pow(gmag - meanGyroMag, 2);
        }
        varAcc /= windowSize;
        varGyro /= windowSize;

        // Stationary logic:
        // 1. Mean acceleration is close to gravity
        // 2. Mean rotation is close to zero
        // 3. Low variance in both (no vibrations or shocks)
        boolean c1 = Math.abs(meanAccMag - G) < accelMagThreshold;
        boolean c2 = meanGyroMag < gyroMagThreshold;
        boolean c3 = varAcc < accelVarThreshold;
        boolean c4 = varGyro < gyroVarThreshold;
        
        isStationary = c1 && c2 && c3 && c4;
    }

    public boolean isStationary() {
        return isStationary;
    }
    
    public double getAccelVariance() {
        if (window.isEmpty()) return 0.0;
        double sumAccMag = 0;
        for (IMUSample s : window) {
            sumAccMag += Math.sqrt(s.getAx() * s.getAx() + s.getAy() * s.getAy() + s.getAz() * s.getAz());
        }
        double meanAccMag = sumAccMag / window.size();
        
        double varAcc = 0;
        for (IMUSample s : window) {
            double amag = Math.sqrt(s.getAx() * s.getAx() + s.getAy() * s.getAy() + s.getAz() * s.getAz());
            varAcc += Math.pow(amag - meanAccMag, 2);
        }
        return varAcc / window.size();
    }

    public void reset() {
        window.clear();
        isStationary = false;
    }
}
