package padik.deadreckoning.ai;

import java.util.List;

/**
 * Prepares raw IMU data for the TFLite model.
 */
public class ModelInputProcessor {

    /**
     * Converts a window of AISamples into a flat float array for inference.
     * Shape: [WindowSize * FeatureCount]
     */
    public float[] prepareInput(List<AISample> window) {
        int windowSize = SpeedModelConfig.WINDOW_SIZE;
        int featureCount = SpeedModelConfig.FEATURE_COUNT;
        
        float[] input = new float[windowSize * featureCount];
        
        for (int i = 0; i < Math.min(window.size(), windowSize); i++) {
            AISample sample = window.get(i);
            
            for (int j = 0; j < featureCount; j++) {
                float value = sample.features[j];
                
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    value = 0f; // Safe fallback for invalid sensor values
                }
                
                if (SpeedModelConfig.USE_NORMALIZATION) {
                    value = (value - SpeedModelConfig.FEATURE_MEAN[j]) / SpeedModelConfig.FEATURE_STD[j];
                }
                
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    value = 0f; // Safe fallback if normalization causes NaN/Inf
                }
                
                input[i * featureCount + j] = value;
            }
        }
        
        return input;
    }
}
