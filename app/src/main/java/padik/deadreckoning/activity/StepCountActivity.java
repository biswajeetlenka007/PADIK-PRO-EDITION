package padik.deadreckoning.activity;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.dialog.StepInfoDialogFragment;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.sensor.EnhancedStepCounter;
import padik.deadreckoning.stepcounting.DynamicStepCounter;
import padik.deadreckoning.stepcounting.StaticStepCounter;

public class StepCountActivity extends AppCompatActivity implements SensorEventListener {

    private static final double DEFAULT_STRIDE_LENGTH = 0.75;

    private StepInfoDialogFragment myDialog;
    private EnhancedStepCounter enhancedStepCounter;

    private Button buttonStartCounter;
    private Button buttonStopCounter;
    private Button buttonClearCounter;
    private Button buttonStepInfo;
    private TextView textStaticCounter;
    private TextView textDynamicCounter;
    private TextView textAndroidCounter;
    private TextView textInstantAcc;
    private TextView textDistance;
    private ProgressBar progressAcc;

    private Sensor sensorAccelerometer;
    private Sensor sensorLinearAcceleration;
    private Sensor sensorStepDetector;
    private SensorManager sensorManager;

    private StaticStepCounter[] staticStepCounters;
    private DynamicStepCounter[] dynamicStepCounters;

    private int androidStepCount;
    private double strideLength = DEFAULT_STRIDE_LENGTH;

    private boolean wasRunning;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_counter);

        myDialog = new StepInfoDialogFragment();
        enhancedStepCounter = new EnhancedStepCounter();

        initViews();
        initSensors();
        initStepcounters();
    }

    private void initViews() {
        buttonStartCounter = findViewById(R.id.buttonStartCounter);
        buttonStopCounter = findViewById(R.id.buttonStopCounter);
        buttonClearCounter = findViewById(R.id.buttonClearCounter);
        buttonStepInfo = findViewById(R.id.buttonStepInfo);

        textStaticCounter = findViewById(R.id.textThreshold);
        textDynamicCounter = findViewById(R.id.textMovingAverage);
        textAndroidCounter = findViewById(R.id.textAndroid);
        textInstantAcc = findViewById(R.id.textInstantAcc);
        textDistance = findViewById(R.id.textDistance);
        progressAcc = findViewById(R.id.progressAcc);

        buttonStartCounter.setOnClickListener(v -> startTracking());
        buttonStopCounter.setOnClickListener(v -> stopTracking());
        buttonClearCounter.setOnClickListener(v -> clearCounters());
        buttonStepInfo.setOnClickListener(v -> showStepInfo());
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorLinearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    private void initStepcounters() {
        staticStepCounters = new StaticStepCounter[5];
        staticStepCounters[0] = new StaticStepCounter(2, 1.9);
        staticStepCounters[1] = new StaticStepCounter(3, 2.9);
        staticStepCounters[2] = new StaticStepCounter(4, 3.9);
        staticStepCounters[3] = new StaticStepCounter(5, 4.9);
        staticStepCounters[4] = new StaticStepCounter(6, 5.9);

        dynamicStepCounters = new DynamicStepCounter[5];
        dynamicStepCounters[0] = new DynamicStepCounter(0.875);
        dynamicStepCounters[1] = new DynamicStepCounter(0.80);
        dynamicStepCounters[2] = new DynamicStepCounter(0.85);
        dynamicStepCounters[3] = new DynamicStepCounter(0.90);
        dynamicStepCounters[4] = new DynamicStepCounter(0.95);

        clearCounters();
    }

    private void startTracking() {
        sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorLinearAcceleration, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorStepDetector, SensorManager.SENSOR_DELAY_FASTEST);

        enhancedStepCounter.setStrideLength(strideLength);

        buttonStartCounter.setEnabled(false);
        buttonStopCounter.setEnabled(true);

        wasRunning = true;
    }

    private void stopTracking() {
        sensorManager.unregisterListener(this, sensorAccelerometer);
        sensorManager.unregisterListener(this, sensorLinearAcceleration);
        sensorManager.unregisterListener(this, sensorStepDetector);

        buttonStartCounter.setEnabled(true);
        buttonStopCounter.setEnabled(false);

        wasRunning = false;
    }

    private void clearCounters() {
        textStaticCounter.setText("0");
        textDynamicCounter.setText("0");
        textAndroidCounter.setText("0");
        textInstantAcc.setText("0.00");
        textDistance.setText("0.00 m");
        progressAcc.setProgress(0);

        androidStepCount = 0;

        enhancedStepCounter.reset();

        for (StaticStepCounter counter : staticStepCounters) {
            counter.clearStepCount();
        }

        for (DynamicStepCounter counter : dynamicStepCounters) {
            counter.clearStepCount();
        }
    }

    private void showStepInfo() {
        String message = getStepInfo();
        myDialog.setDialogMessage(message);
        myDialog.show(getSupportFragmentManager(), "Step Info");
    }

    private String getStepInfo() {
        StringBuilder message = new StringBuilder();

        message.append("Static Counters:\n");
        for (int i = 1; i < staticStepCounters.length; i++) {
            message.append(String.format(Locale.US, "T(%.1f, %.1f) = %d\n",
                    staticStepCounters[i].getUpperThreshold(),
                    staticStepCounters[i].getLowerThreshold(),
                    staticStepCounters[i].getStepCount()));
        }

        message.append("\nDynamic Counters:\n");
        for (int i = 1; i < dynamicStepCounters.length; i++) {
            message.append(String.format(Locale.US, "A(%.1f) = %d\n",
                    dynamicStepCounters[i].getSensitivity(),
                    dynamicStepCounters[i].getStepCount()));
        }

        message.append(String.format(Locale.US, "\nStride: %.2f m", strideLength));
        message.append(String.format(Locale.US, "\nEnhanced: %d", enhancedStepCounter.getStepCount()));

        return message.toString();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                androidStepCount++;
                runOnUiThread(() -> textAndroidCounter.setText(String.valueOf(androidStepCount)));
            }
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            processLinearAcceleration(event.values, event.timestamp);
        }
    }

    private void processLinearAcceleration(float[] values, long timestamp) {
        double norm = ExtraFunctions.calcNorm(values[0], values[1], values[2]);

        runOnUiThread(() -> {
            textInstantAcc.setText(String.format(Locale.US, "%.2f", norm));
            progressAcc.setProgress((int) Math.min(norm * 2, 20));
        });

        boolean stepDetected = enhancedStepCounter.detectStep(values, timestamp);

        if (stepDetected) {
            runOnUiThread(() -> {
                textStaticCounter.setText(String.valueOf(enhancedStepCounter.getStepCount()));
                double distance = enhancedStepCounter.getDistanceTraveled();
                textDistance.setText(String.format(Locale.US, "%.2f m", distance));
            });
        }

        for (StaticStepCounter counter : staticStepCounters) {
            counter.findStep(norm);
        }

        for (DynamicStepCounter counter : dynamicStepCounters) {
            counter.findStep(norm);
        }

        runOnUiThread(() -> {
            textStaticCounter.setText(String.valueOf(staticStepCounters[0].getStepCount()));
            textDynamicCounter.setText(String.valueOf(dynamicStepCounters[0].getStepCount()));
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (wasRunning) {
            sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, sensorStepDetector, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, sensorLinearAcceleration, SensorManager.SENSOR_DELAY_FASTEST);

            buttonStartCounter.setEnabled(false);
            buttonStopCounter.setEnabled(true);
        } else {
            buttonStartCounter.setEnabled(true);
            buttonStopCounter.setEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }
}
