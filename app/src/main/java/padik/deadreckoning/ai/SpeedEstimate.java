package padik.deadreckoning.ai;

/**
 * Represents a speed estimate produced by the AI model.
 */
public class SpeedEstimate {
    public float speedMps = 0.0f;
    public float speedKmh = 0.0f;
    public long timestampNs = 0;
    public boolean isValid = false;
    public boolean isModelAvailable = false;
    
    // Performance metrics
    public long inferenceLatencyMs = 0;
    public int windowSize = 0;
    public double sampleRateHz = 0.0;
    
    public SpeedEstimate() {}
    
    public SpeedEstimate(float speedMps, long timestampNs, int windowSize) {
        this.speedMps = speedMps;
        this.speedKmh = speedMps * 3.6f;
        this.timestampNs = timestampNs;
        this.windowSize = windowSize;
        this.isValid = true;
        this.isModelAvailable = true;
    }
    
    public static SpeedEstimate unavailable() {
        SpeedEstimate estimate = new SpeedEstimate();
        estimate.isValid = false;
        estimate.isModelAvailable = false;
        return estimate;
    }
}
