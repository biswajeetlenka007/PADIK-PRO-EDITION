package padik.deadreckoning.sensor;

import android.os.SystemClock;

public class SpeedSourceManager {
    public enum SpeedSource {
        GPS,
        AI_DR
    }

    private SpeedSource currentSource = SpeedSource.AI_DR;
    
    private double gpsSpeedKmh = 0.0;
    private double aiSpeedKmh = 0.0;
    
    private long lastGpsValidTimeMs = 0;
    private long gpsTimeoutMs = 2000; // 2 seconds
    private float maximumGpsAccuracyMeters = 15.0f;
    private double minimumGpsSpeedMps = 0.5;

    public void updateGpsSpeed(double speedMps, float accuracy) {
        if (accuracy <= maximumGpsAccuracyMeters) {
            this.gpsSpeedKmh = speedMps * 3.6;
            this.lastGpsValidTimeMs = SystemClock.elapsedRealtime();
        }
    }

    public void updateAiSpeed(double speedMps) {
        if (speedMps >= 0) {
            this.aiSpeedKmh = speedMps * 3.6;
        }
    }

    public boolean isGpsAvailable() {
        return (SystemClock.elapsedRealtime() - lastGpsValidTimeMs) < gpsTimeoutMs;
    }

    public SpeedSource getCurrentSpeedSource() {
        if (isGpsAvailable() && gpsSpeedKmh > (minimumGpsSpeedMps * 3.6)) {
            currentSource = SpeedSource.GPS;
        } else {
            // Hysteresis: only switch to AI if GPS has been invalid/too slow or timed out
            currentSource = SpeedSource.AI_DR;
        }
        return currentSource;
    }

    public double getCurrentSpeedKmh() {
        if (getCurrentSpeedSource() == SpeedSource.GPS) {
            return gpsSpeedKmh;
        } else {
            return aiSpeedKmh;
        }
    }

    public double getGpsSpeedKmh() {
        return gpsSpeedKmh;
    }

    public double getAiSpeedKmh() {
        return aiSpeedKmh;
    }
}
