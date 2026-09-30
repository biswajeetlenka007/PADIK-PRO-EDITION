package padik.deadreckoning.ai;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level interface for AI-based speed estimation.
 * Manages the IMU buffer and triggers inference in the background.
 */
public class SpeedEstimator {
    
    private final TFLiteSpeedModel model;
    private final ModelInputProcessor inputProcessor;
    private final List<AISample> rollingBuffer = new ArrayList<>();
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isInferenceRunning = false;
    
    private SpeedEstimate lastEstimate = SpeedEstimate.unavailable();
    private long lastInferenceTimeNs = 0;
    private long lastSampleTimeNs = 0;
    private double actualSensorRateHz = 0.0;
    private boolean hasGap = false;

    public SpeedEstimator(Context context) {
        this.model = new TFLiteSpeedModel(context);
        this.inputProcessor = new ModelInputProcessor();
    }

    /**
     * Adds a new AISample to the rolling buffer.
     * Triggers inference if enough samples are available and the interval has passed.
     */
    public void addAISample(AISample sample) {
        synchronized (rollingBuffer) {
            long currentTimeNs = sample.timestampNs;
            
            // Measure actual sensor rate and detect gaps
            if (lastSampleTimeNs > 0) {
                long dtNs = currentTimeNs - lastSampleTimeNs;
                if (dtNs > SpeedModelConfig.MAX_ALLOWED_GAP_NS) {
                    hasGap = true; // Invalidate window if gap too large
                }
                
                // Simple alpha-beta or exponential moving average for rate
                double instantRate = 1.0 / (dtNs / 1_000_000_000.0);
                if (actualSensorRateHz == 0) {
                    actualSensorRateHz = instantRate;
                } else {
                    actualSensorRateHz = 0.95 * actualSensorRateHz + 0.05 * instantRate;
                }
            }
            lastSampleTimeNs = currentTimeNs;

            rollingBuffer.add(sample);
            if (rollingBuffer.size() > SpeedModelConfig.WINDOW_SIZE) {
                rollingBuffer.remove(0);
            }
            
            // Check if we should run inference
            if (!isInferenceRunning && rollingBuffer.size() == SpeedModelConfig.WINDOW_SIZE) {
                long intervalNs = SpeedModelConfig.INFERENCE_INTERVAL_MS * 1_000_000L;
                
                if (currentTimeNs - lastInferenceTimeNs >= intervalNs) {
                    if (!hasGap) {
                        runInferenceAsync(new ArrayList<>(rollingBuffer), currentTimeNs, actualSensorRateHz);
                    } else {
                        hasGap = false; // Reset for next window
                    }
                    lastInferenceTimeNs = currentTimeNs;
                }
            }
        }
    }

    private void runInferenceAsync(final List<AISample> window, final long timestampNs, final double sensorRateHz) {
        if (!model.isLoaded()) {
            lastEstimate = SpeedEstimate.unavailable();
            return;
        }

        isInferenceRunning = true;
        executor.execute(() -> {
            long startTime = SystemClock.elapsedRealtime();
            
            float[] input = inputProcessor.prepareInput(window);
            float speedMps = model.runInference(input);
            
            long endTime = SystemClock.elapsedRealtime();
            
            if (speedMps >= 0) {
                SpeedEstimate estimate = new SpeedEstimate(speedMps, timestampNs, window.size());
                estimate.inferenceLatencyMs = endTime - startTime;
                estimate.sampleRateHz = sensorRateHz;
                lastEstimate = estimate;
                
                Log.i("IDRSpeedEstimator", String.format("AI Speed: %.2f km/h, rate: %.1f Hz, window: %d, latency: %d ms", 
                        speedMps, sensorRateHz, window.size(), estimate.inferenceLatencyMs));
            } else {
                lastEstimate = SpeedEstimate.unavailable();
            }
            
            isInferenceRunning = false;
        });
    }

    public SpeedEstimate getLastEstimate() {
        return lastEstimate;
    }

    public TFLiteSpeedModel.ModelState getStatus() {
        return model.getState();
    }

    public void shutdown() {
        executor.shutdown();
        model.close();
    }
}
