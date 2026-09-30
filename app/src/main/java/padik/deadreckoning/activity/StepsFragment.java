package padik.deadreckoning.activity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.preferences.StepCounterPreferences;
import padik.deadreckoning.sensor.EnhancedStepCounter;
import padik.deadreckoning.stepcounting.DynamicStepCounter;
import padik.deadreckoning.stepcounting.StaticStepCounter;

public class StepsFragment extends Fragment implements SensorEventListener {

    private static final double DEFAULT_STRIDE_LENGTH = 0.75;

    private EnhancedStepCounter enhancedStepCounter;
    private StepCounterPreferences stepPrefs;

    private TextView textSteps;
    private TextView textDistance;
    private TextView textModeUsed;
    private TextView textDynamicCounter;
    private TextView textAndroidCounter;
    private TextView textStaticCounter;
    private TextView textInstantAcc;
    private ProgressBar progressAcc;

    private SensorManager sensorManager;
    private Sensor sensorLinearAcceleration;
    private Sensor sensorStepDetector;

    private StaticStepCounter[] staticStepCounters;
    private DynamicStepCounter[] dynamicStepCounters;

    private int androidStepCount = 0;
    private int currentModeSteps = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_steps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        stepPrefs = new StepCounterPreferences(requireContext());

        initViews(view);
        initSensors();
        initStepcounters();
        updateModeDisplay();
    }

    private void initViews(View view) {
        textSteps = view.findViewById(R.id.textSteps);
        textDistance = view.findViewById(R.id.textDistance);
        textModeUsed = view.findViewById(R.id.textModeUsed);
        textDynamicCounter = view.findViewById(R.id.textDynamicCounter);
        textAndroidCounter = view.findViewById(R.id.textAndroidCounter);
        textStaticCounter = view.findViewById(R.id.textStaticCounter);
        textInstantAcc = view.findViewById(R.id.textInstantAcc);
        progressAcc = view.findViewById(R.id.progressAcc);
    }

    private void updateModeDisplay() {
        StepCounterPreferences.StepMode mode = stepPrefs.getStepMode();
        String modeText;
        
        switch (mode) {
            case DYNAMIC:
                modeText = getString(R.string.dynamic_mode);
                break;
            case ANDROID:
                modeText = getString(R.string.android_mode);
                break;
            case STATIC:
                modeText = getString(R.string.static_mode);
                break;
            default:
                modeText = getString(R.string.dynamic_mode);
        }
        
        if (textModeUsed != null) {
            textModeUsed.setText("Mode: " + modeText);
        }
    }

    private void initSensors() {
        sensorManager = (SensorManager) requireContext().getSystemService(requireContext().SENSOR_SERVICE);
        sensorLinearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    private void initStepcounters() {
        enhancedStepCounter = new EnhancedStepCounter();
        enhancedStepCounter.setStrideLength(DEFAULT_STRIDE_LENGTH);

        staticStepCounters = new StaticStepCounter[5];
        staticStepCounters[0] = new StaticStepCounter(1.5, 0.5);
        staticStepCounters[1] = new StaticStepCounter(2.0, 1.0);
        staticStepCounters[2] = new StaticStepCounter(2.5, 1.5);
        staticStepCounters[3] = new StaticStepCounter(3.0, 2.0);
        staticStepCounters[4] = new StaticStepCounter(3.5, 2.5);

        dynamicStepCounters = new DynamicStepCounter[5];
        dynamicStepCounters[0] = new DynamicStepCounter(0.875);
        dynamicStepCounters[1] = new DynamicStepCounter(0.80);
        dynamicStepCounters[2] = new DynamicStepCounter(0.85);
        dynamicStepCounters[3] = new DynamicStepCounter(0.90);
        dynamicStepCounters[4] = new DynamicStepCounter(0.95);
    }

    @Override
    public void onResume() {
        super.onResume();
        stepPrefs = new StepCounterPreferences(requireContext());
        updateModeDisplay();
        
        if (sensorLinearAcceleration != null) {
            sensorManager.registerListener(this, sensorLinearAcceleration, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (sensorStepDetector != null) {
            sensorManager.registerListener(this, sensorStepDetector, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                androidStepCount++;
                StepCounterPreferences.StepMode mode = stepPrefs.getStepMode();
                if (mode == StepCounterPreferences.StepMode.ANDROID) {
                    currentModeSteps = androidStepCount;
                }
                requireActivity().runOnUiThread(() -> textAndroidCounter.setText(String.valueOf(androidStepCount)));
            }
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            processAccelerometer(event.values, event.timestamp);
        }
    }

    private void processAccelerometer(float[] values, long timestamp) {
        double norm = ExtraFunctions.calcNorm(values[0], values[1], values[2]);

        requireActivity().runOnUiThread(() -> {
            textInstantAcc.setText(String.format(Locale.US, "%.2f", norm));
            progressAcc.setProgress((int) Math.min(norm * 2, 20));
        });

        boolean stepDetected = enhancedStepCounter.detectStep(values, timestamp);

        for (StaticStepCounter counter : staticStepCounters) {
            counter.findStep(norm);
        }

        for (DynamicStepCounter counter : dynamicStepCounters) {
            counter.findStep(norm);
        }

        StepCounterPreferences.StepMode mode = stepPrefs.getStepMode();
        
        switch (mode) {
            case DYNAMIC:
                currentModeSteps = dynamicStepCounters[0].getStepCount();
                break;
            case ANDROID:
                currentModeSteps = androidStepCount;
                break;
            case STATIC:
                currentModeSteps = staticStepCounters[0].getStepCount();
                break;
        }

        final int displaySteps = currentModeSteps;

        requireActivity().runOnUiThread(() -> {
            textSteps.setText(String.valueOf(displaySteps));
            textDistance.setText(String.format(Locale.US, "%.2f m", displaySteps * DEFAULT_STRIDE_LENGTH));
            textDynamicCounter.setText(String.valueOf(dynamicStepCounters[0].getStepCount()));
            textStaticCounter.setText(String.valueOf(staticStepCounters[0].getStepCount()));
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
