package padik.deadreckoning.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Wrapper for the TFLite Speed Estimation model.
 * Handles model loading and inference.
 */
public class TFLiteSpeedModel {
    private static final String TAG = "TFLiteSpeedModel";
    
    private Interpreter interpreter;
    private boolean isModelLoaded = false;
    
    public enum ModelState {
        MODEL_NOT_FOUND,
        MODEL_LOADING,
        MODEL_READY,
        MODEL_ERROR,
        INFERENCE_ERROR
    }
    
    private ModelState state = ModelState.MODEL_NOT_FOUND;

    public TFLiteSpeedModel(Context context) {
        loadNormalization(context);
        loadModel(context);
    }

    private void loadNormalization(Context context) {
        try {
            InputStream is = context.getAssets().open("models/normalization.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            
            JSONObject obj = new JSONObject(json);
            JSONArray meanArray = obj.getJSONArray("mean");
            JSONArray stdArray = obj.getJSONArray("std");
            
            for (int i = 0; i < SpeedModelConfig.FEATURE_COUNT; i++) {
                SpeedModelConfig.FEATURE_MEAN[i] = (float) meanArray.getDouble(i);
                SpeedModelConfig.FEATURE_STD[i] = (float) stdArray.getDouble(i);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading normalization.json: " + e.getMessage());
        }
    }

    public void runTestInference() {
        if (!isLoaded()) return;
        try {
            float[][][] input3D = new float[1][SpeedModelConfig.WINDOW_SIZE][SpeedModelConfig.FEATURE_COUNT];
            // Fill with synthetic data (0.5 for all features)
            for (int i = 0; i < SpeedModelConfig.WINDOW_SIZE; i++) {
                for (int j = 0; j < SpeedModelConfig.FEATURE_COUNT; j++) {
                    input3D[0][i][j] = 0.5f;
                }
            }
            float[][] output = new float[1][1];
            long startTime = System.currentTimeMillis();
            interpreter.run(input3D, output);
            long latency = System.currentTimeMillis() - startTime;
            
            Log.d(TAG, "=== TEST MODE ===");
            Log.d(TAG, "Model: " + SpeedModelConfig.MODEL_FILE_NAME);
            Log.d(TAG, "Input Shape: [1, 20, 15], Type: FLOAT32");
            Log.d(TAG, "Output Shape: [1, 1], Type: FLOAT32");
            Log.d(TAG, "Inference Latency: " + latency + " ms");
            Log.d(TAG, "Synthetic Test Output: " + output[0][0] + " km/h");
            Log.d(TAG, "=================");
        } catch (Exception e) {
            Log.e(TAG, "Test inference failed: " + e.getMessage());
        }
    }

    private void loadModel(Context context) {
        state = ModelState.MODEL_LOADING;
        try {
            MappedByteBuffer tfliteModel = loadModelFile(context, SpeedModelConfig.MODEL_FILE_NAME);
            Interpreter.Options options = new Interpreter.Options();
            interpreter = new Interpreter(tfliteModel, options);
            
            // Validate input tensor shape [1, WindowSize, FeatureCount] or [1, WindowSize*FeatureCount]
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "Model loaded. Input shape: " + Arrays.toString(inputShape) + ", Output shape: " + Arrays.toString(outputShape));
            
            isModelLoaded = true;
            state = ModelState.MODEL_READY;
            
            runTestInference();
        } catch (IOException e) {
            Log.e(TAG, "Could not load TFLite model: " + e.getMessage());
            state = ModelState.MODEL_NOT_FOUND;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TFLite interpreter: " + e.getMessage());
            state = ModelState.MODEL_ERROR;
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float runInference(float[] flatInput) {
        if (!isModelLoaded || interpreter == null) {
            return -1f;
        }

        try {
            float[][][] input3D = new float[1][SpeedModelConfig.WINDOW_SIZE][SpeedModelConfig.FEATURE_COUNT];
            int index = 0;
            for (int i = 0; i < SpeedModelConfig.WINDOW_SIZE; i++) {
                for (int j = 0; j < SpeedModelConfig.FEATURE_COUNT; j++) {
                    if (index < flatInput.length) {
                        input3D[0][i][j] = flatInput[index++];
                    }
                }
            }

            float[][] output = new float[1][1];
            
            interpreter.run(input3D, output);
            
            return output[0][0];
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            state = ModelState.INFERENCE_ERROR;
            return -1f;
        }
    }

    public boolean isLoaded() {
        return isModelLoaded;
    }

    public ModelState getState() {
        return state;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
    }
}
