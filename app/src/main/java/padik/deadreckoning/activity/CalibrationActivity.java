package padik.deadreckoning.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import padik.deadreckoning.R;
import padik.deadreckoning.dialog.AutoFrameCalibrationDialogFragment;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.preferences.StepCounterPreferences;
import padik.deadreckoning.sensor.EnhancedStepCounter;

public class CalibrationActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "CalibrationPrefs";
    private static final String PREF_STRIDE_LENGTH = "stride_length";
    private static final String PREF_CALIBRATED = "is_calibrated";

    private SharedPreferences prefs;
    private StepCounterPreferences stepPrefs;
    private SensorManager sensorManager;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private RadioGroup radioGroupStepMode;
    private MaterialRadioButton radioDynamic, radioAndroid, radioStatic;
    private Button buttonSaveStepMode;

    private Sensor sensorAccelerometer;
    private Sensor sensorMagneticField;
    private Sensor sensorGravity;

    private TextView textCurrentSteps;
    private TextView textMeasuredDistance;
    private TextView textCalculatedStride;
    private TextView textGPSStatus;
    private TextView textHeadingValue;
    private TextView textAccuracyValue;
    private TextView textCalibrationStatus;
    private LinearProgressIndicator progressIndicator;

    private TextInputEditText inputKnownDistance;
    private TextInputEditText inputKnownSteps;

    private Button buttonStartCalibration;
    private Button buttonSaveCalibration;
    private Button buttonResetCalibration;

    private int calibrationStepCount = 0;
    private double measuredDistance = 0;
    private double calculatedStrideLength = 0.75;
    private double startLatitude = 0;
    private double startLongitude = 0;
    private double lastLatitude = 0;
    private double lastLongitude = 0;
    private boolean isCalibrating = false;
    private final AtomicBoolean isStepDetected = new AtomicBoolean(false);
    private double lastAccMagnitude = 0;
    
    private EnhancedStepCounter stepCounter;
    private boolean useAndroidSteps = false;
    private int androidStepCount = 0;

    private float[] gravityValues = null;
    private float[] magValues = null;
    private double currentHeading = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepPrefs = new StepCounterPreferences(this);

        initViews();
        setupToolbar();
        initSensors();
        loadSavedCalibration();
        loadStepModeSelection();
    }

    private void initViews() {
        textCurrentSteps = findViewById(R.id.textCurrentSteps);
        textMeasuredDistance = findViewById(R.id.textMeasuredDistance);
        textCalculatedStride = findViewById(R.id.textCalculatedStride);
        textGPSStatus = findViewById(R.id.textGPSStatus);
        textHeadingValue = findViewById(R.id.textHeadingValue);
        textAccuracyValue = findViewById(R.id.textAccuracyValue);
        textCalibrationStatus = findViewById(R.id.textCalibrationStatus);
        progressIndicator = findViewById(R.id.progressIndicator);

        inputKnownDistance = findViewById(R.id.inputKnownDistance);
        inputKnownSteps = findViewById(R.id.inputKnownSteps);

        buttonStartCalibration = findViewById(R.id.buttonStartCalibration);
        buttonSaveCalibration = findViewById(R.id.buttonSaveCalibration);
        buttonResetCalibration = findViewById(R.id.buttonResetCalibration);

        radioGroupStepMode = findViewById(R.id.radioGroupStepMode);
        radioDynamic = findViewById(R.id.radioDynamic);
        radioAndroid = findViewById(R.id.radioAndroid);
        radioStatic = findViewById(R.id.radioStatic);
        buttonSaveStepMode = findViewById(R.id.buttonSaveStepMode);

        Button buttonMountCalibration = findViewById(R.id.buttonMountCalibration);
        buttonMountCalibration.setOnClickListener(v -> showMountingCalibration());

        buttonStartCalibration.setOnClickListener(v -> toggleCalibration());
        buttonSaveCalibration.setOnClickListener(v -> saveCalibration());
        buttonResetCalibration.setOnClickListener(v -> resetCalibration());
        buttonSaveStepMode.setOnClickListener(v -> saveStepMode());
        
        radioGroupStepMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioDynamic) {
                showStepModeInfo("Dynamic: Uses acceleration + moving average. Best for walking.");
            } else if (checkedId == R.id.radioAndroid) {
                showStepModeInfo("Android: Uses built-in step detector. Most accurate but needs hardware support.");
            } else if (checkedId == R.id.radioStatic) {
                showStepModeInfo("Static: Uses simple thresholds. Fast but less accurate.");
            }
        });
    }
    
    private void showStepModeInfo(String message) {
        textCalibrationStatus.setText(message);
        textCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
    }

    private void showMountingCalibration() {
        AutoFrameCalibrationDialogFragment dialog = new AutoFrameCalibrationDialogFragment();
        dialog.show(getSupportFragmentManager(), "AutoFrameCalib");
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        stepCounter = new EnhancedStepCounter();
    }

    private void loadSavedCalibration() {
        double savedStride = prefs.getFloat(PREF_STRIDE_LENGTH, 0.75f);
        boolean wasCalibrated = prefs.getBoolean(PREF_CALIBRATED, false);

        calculatedStrideLength = savedStride;
        if (textCalculatedStride != null) {
            textCalculatedStride.setText(String.format(Locale.US, "%.2f m", savedStride));
        }

        if (wasCalibrated && textCalibrationStatus != null) {
            textCalibrationStatus.setText("Calibrated");
            textCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
            if (buttonSaveCalibration != null) buttonSaveCalibration.setEnabled(false);
        }
    }

    private void loadStepModeSelection() {
        StepCounterPreferences.StepMode mode = stepPrefs.getStepMode();
        switch (mode) {
            case DYNAMIC: radioDynamic.setChecked(true); break;
            case ANDROID: radioAndroid.setChecked(true); break;
            case STATIC: radioStatic.setChecked(true); break;
        }
    }

    private void saveStepMode() {
        StepCounterPreferences.StepMode selectedMode;
        String modeName;
        if (radioDynamic.isChecked()) {
            selectedMode = StepCounterPreferences.StepMode.DYNAMIC;
            modeName = "Dynamic";
        } else if (radioAndroid.isChecked()) {
            selectedMode = StepCounterPreferences.StepMode.ANDROID;
            modeName = "Android";
        } else {
            selectedMode = StepCounterPreferences.StepMode.STATIC;
            modeName = "Static";
        }
        
        stepPrefs.setStepMode(selectedMode);
        Toast.makeText(this, "Step mode: " + modeName + " saved!", Toast.LENGTH_SHORT).show();
        
        new AlertDialog.Builder(this)
            .setTitle("Step Mode Changed")
            .setMessage("The step counting mode has been changed to " + modeName + ".\n\n" +
                (selectedMode == StepCounterPreferences.StepMode.DYNAMIC ? 
                    "Uses acceleration + moving average. Best for walking." :
                    selectedMode == StepCounterPreferences.StepMode.ANDROID ?
                    "Uses built-in step detector. Most accurate." :
                    "Uses simple thresholds. Fast but less accurate."))
            .setPositiveButton("OK", null)
            .show();
    }

    private void toggleCalibration() {
        if (isCalibrating) {
            stopCalibration();
        } else {
            startCalibration();
        }
    }

    @SuppressLint("MissingPermission")
    private void startCalibration() {
        calibrationStepCount = 0;
        measuredDistance = 0;
        startLatitude = 0;
        startLongitude = 0;
        lastLatitude = 0;
        lastLongitude = 0;
        isCalibrating = true;

        buttonStartCalibration.setText("Stop");
        buttonStartCalibration.setBackgroundColor(ContextCompat.getColor(this, R.color.colorError));
        progressIndicator.setVisibility(LinearProgressIndicator.VISIBLE);

        textCurrentSteps.setText("0");
        textMeasuredDistance.setText("0.00 m");
        textCalculatedStride.setText("0.75 m");
        textGPSStatus.setText("Getting GPS...");
        
        if (stepCounter != null) {
            stepCounter.reset();
        }
        
        sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorMagneticField, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_FASTEST);
        
        Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST);
            useAndroidSteps = true;
        } else {
            useAndroidSteps = false;
        }
        
        stepCounter.reset();

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleGPSUpdate(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && isCalibrating) {
                handleGPSUpdate(location);
            }
        });
        
        Toast.makeText(this, "Walk to calibrate - GPS acquiring...", Toast.LENGTH_SHORT).show();
    }

    private void stopCalibration() {
        isCalibrating = false;
        sensorManager.unregisterListener(this);
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        buttonStartCalibration.setText("Start");
        buttonStartCalibration.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
        progressIndicator.setVisibility(LinearProgressIndicator.GONE);

        if (calibrationStepCount > 0 && measuredDistance > 0) {
            calculatedStrideLength = measuredDistance / calibrationStepCount;
            textCalculatedStride.setText(String.format(Locale.US, "%.2f m", calculatedStrideLength));
            buttonSaveCalibration.setEnabled(true);
        }
    }

    private void handleGPSUpdate(Location location) {
        if (!isCalibrating) return;

        double accuracy = location.getAccuracy();
        if (textAccuracyValue != null) {
            textAccuracyValue.setText(String.format(Locale.US, "%.1f m", accuracy));
        }
        
        if (textGPSStatus != null) {
            if (accuracy < 10) {
                textGPSStatus.setText("GPS: Excellent");
                textGPSStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
            } else if (accuracy < 20) {
                textGPSStatus.setText("GPS: Good");
                textGPSStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
            } else if (accuracy < 30) {
                textGPSStatus.setText("GPS: Fair");
                textGPSStatus.setTextColor(ContextCompat.getColor(this, R.color.colorWarning));
            } else {
                textGPSStatus.setText("GPS: Poor - Move to open area");
                textGPSStatus.setTextColor(ContextCompat.getColor(this, R.color.colorError));
            }
        }

        if (accuracy < 30) {
            if (startLatitude == 0 && startLongitude == 0) {
                startLatitude = location.getLatitude();
                startLongitude = location.getLongitude();
            }

            if (lastLatitude != 0 && lastLongitude != 0) {
                float[] results = new float[1];
                Location.distanceBetween(lastLatitude, lastLongitude, location.getLatitude(), location.getLongitude(), results);
                double segmentDistance = results[0];
                
                if (segmentDistance > 0.3 && segmentDistance < 15) {
                    measuredDistance += segmentDistance;
                    textMeasuredDistance.setText(String.format(Locale.US, "%.2f m", measuredDistance));
                    
                    if (calibrationStepCount > 0) {
                        double currentStride = measuredDistance / calibrationStepCount;
                        textCalculatedStride.setText(String.format(Locale.US, "%.2f m", currentStride));
                    }
                }
            }

            lastLatitude = location.getLatitude();
            lastLongitude = location.getLongitude();
        }
    }

    private void saveCalibration() {
        String knownDistanceStr = inputKnownDistance.getText().toString();
        String knownStepsStr = inputKnownSteps.getText().toString();

        if (!knownDistanceStr.isEmpty() && !knownStepsStr.isEmpty()) {
            try {
                double knownDistance = Double.parseDouble(knownDistanceStr);
                int knownSteps = Integer.parseInt(knownStepsStr);
                if (knownSteps > 0) {
                    calculatedStrideLength = knownDistance / knownSteps;
                }
            } catch (NumberFormatException ignored) {}
        }

        prefs.edit()
                .putFloat(PREF_STRIDE_LENGTH, (float) calculatedStrideLength)
                .putBoolean(PREF_CALIBRATED, true)
                .apply();

        textCalibrationStatus.setText("Calibrated");
        textCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
        textCalculatedStride.setText(String.format(Locale.US, "%.2f m", calculatedStrideLength));
        buttonSaveCalibration.setEnabled(false);

        Toast.makeText(this, "Calibration saved!", Toast.LENGTH_SHORT).show();
    }

    private void resetCalibration() {
        new AlertDialog.Builder(this)
                .setTitle("Reset")
                .setMessage("Do you want to reset the calibration?")
                .setPositiveButton("Yes", (d, w) -> {
                    prefs.edit()
                            .putFloat(PREF_STRIDE_LENGTH, 0.75f)
                            .putBoolean(PREF_CALIBRATED, false)
                            .apply();

                    calculatedStrideLength = 0.75;
                    textCalculatedStride.setText("0.75 m");
                    textCalibrationStatus.setText("Not calibrated");
                    textCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
                    buttonSaveCalibration.setEnabled(false);
                    Toast.makeText(this, "Calibration reset", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                if (!useAndroidSteps) {
                    processStepDetection(event.values, event.timestamp);
                }
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                calibrationStepCount++;
                textCurrentSteps.setText(String.valueOf(calibrationStepCount));
                if (measuredDistance > 0 && calibrationStepCount > 0) {
                    double currentStride = measuredDistance / calibrationStepCount;
                    textCalculatedStride.setText(String.format(Locale.US, "%.2f m", currentStride));
                }
                break;
            case Sensor.TYPE_GRAVITY:
                gravityValues = event.values.clone();
                updateHeading();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magValues = event.values.clone();
                updateHeading();
                break;
        }
    }

    private void processStepDetection(float[] values, long timestamp) {
        if (stepCounter.detectStep(values, timestamp)) {
            calibrationStepCount++;
            textCurrentSteps.setText(String.valueOf(calibrationStepCount));
            
            if (measuredDistance > 0 && calibrationStepCount > 0) {
                double currentStride = measuredDistance / calibrationStepCount;
                textCalculatedStride.setText(String.format(Locale.US, "%.2f m", currentStride));
            }
        }
    }

    private void updateHeading() {
        if (gravityValues != null && magValues != null) {
            float[] rotationMatrix = new float[9];
            float[] orientationAngles = new float[3];
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magValues)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                currentHeading = Math.toDegrees(orientationAngles[0]);
                if (currentHeading < 0) currentHeading += 360;
                textHeadingValue.setText(String.format(Locale.US, "%.1f°", currentHeading));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        if (isCalibrating) {
            stopCalibration();
        }
        sensorManager.unregisterListener(this);
    }
}
