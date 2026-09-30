package padik.deadreckoning.ai;

/**
 * Centralized configuration for the AI Speed Estimation model.
 */
public class SpeedModelConfig {
    public static final String MODEL_FILE_NAME = "models/idr_speed_estimator_float32.tflite";
    
    // Model architecture parameters
    public static final int WINDOW_SIZE = 20;      // N samples
    public static final int FEATURE_COUNT = 15;    // 15 features
    public static final double MODEL_EXPECTED_RATE_HZ = 10.0; // Target frequency for the model
    
    // Safety thresholds
    public static final long MAX_ALLOWED_GAP_NS = 500_000_000L; // 500ms gap allowed before invalidating window
    
    // Inference parameters
    public static final int INFERENCE_INTERVAL_MS = 100; // Run every 100ms (10 Hz)
    
    // Feature order in the input tensor (as per normalization.json)
    public static final int INDEX_ACCEL_X = 0;
    public static final int INDEX_ACCEL_Y = 1;
    public static final int INDEX_ACCEL_Z = 2;
    public static final int INDEX_GRAVITY_X = 3;
    public static final int INDEX_GRAVITY_Y = 4;
    public static final int INDEX_GRAVITY_Z = 5;
    public static final int INDEX_GYRO_YAW = 6;
    public static final int INDEX_GYRO_PITCH = 7;
    public static final int INDEX_GYRO_ROLL = 8;
    public static final int INDEX_MAG_X = 9;
    public static final int INDEX_MAG_Y = 10;
    public static final int INDEX_MAG_Z = 11;
    public static final int INDEX_ORIENTATION_YAW = 12;
    public static final int INDEX_ORIENTATION_PITCH = 13;
    public static final int INDEX_ORIENTATION_ROLL = 14;
    
    public static float[] FEATURE_MEAN = new float[15];
    public static float[] FEATURE_STD = new float[15];
    
    public static final boolean USE_NORMALIZATION = true; 
}
