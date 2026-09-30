package padik.deadreckoning.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.filewriting.DataFileWriter;
import padik.deadreckoning.model.IMUGNSSSample;
import padik.deadreckoning.navigation.MotionModeDetector;
import padik.deadreckoning.sensor.DeadReckoningEngine;

public class DataCollectActivity extends AppCompatActivity implements SensorEventListener {

    private static final String FOLDER_NAME = "Dead_Reckoning/Unified_Datasets";
    
    private static final String[] DATA_FILE_NAMES = {
            "Unified_Dataset"
    };
    private static final String[] DATA_FILE_HEADINGS = {
            IMUGNSSSample.getCsvHeader()
    };

    private TextView textRecordingStatus;
    private TextView textImuCount;
    private TextView textGnssCount;
    private TextView textGnssAccuracy;
    private TextView textGnssSpeed;
    private TextView textCurrentHeading;
    private TextView textMotionMode;
    private TextView textDebugInfo;

    private Button buttonStart;
    private Button buttonPause;
    private Button buttonStop;

    private SensorManager sensorManager;
    private Sensor[] sensors;
    
    private float[] latestAccel, latestGyro, latestMag, latestGravity, latestLinearAccel;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;

    private DataFileWriter dataFileWriter;
    private DeadReckoningEngine drEngine;
    private MotionModeDetector motionModeDetector;

    private long imuSampleCount = 0;
    private long gnssSampleCount = 0;
    private boolean isRecording = false;
    private boolean wasRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collect);

        MaterialToolbar toolbar = findViewById(R.id.topToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        initSensors();
        initLocation();
        initEngine();
    }

    private void initViews() {
        textRecordingStatus = findViewById(R.id.textRecordingStatus);
        textImuCount = findViewById(R.id.textImuCount);
        textGnssCount = findViewById(R.id.textGnssCount);
        textGnssAccuracy = findViewById(R.id.textGnssAccuracy);
        textGnssSpeed = findViewById(R.id.textGnssSpeed);
        textCurrentHeading = findViewById(R.id.textCurrentHeading);
        textMotionMode = findViewById(R.id.textMotionMode);
        textDebugInfo = findViewById(R.id.textDataCollect);

        buttonStart = findViewById(R.id.buttonDataStart);
        buttonPause = findViewById(R.id.buttonDataPause);
        buttonStop = findViewById(R.id.buttonDataStop);

        buttonStart.setOnClickListener(v -> startRecording());
        buttonPause.setOnClickListener(v -> pauseRecording());
        buttonStop.setOnClickListener(v -> stopRecording());

        enableStartButton();
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensors = new Sensor[5];
        sensors[0] = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensors[1] = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensors[2] = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensors[3] = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensors[4] = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    lastLocation = location;
                    gnssSampleCount++;
                    updateGnssUI();
                }
            }
        };
    }

    private void initEngine() {
        drEngine = new DeadReckoningEngine();
        motionModeDetector = new MotionModeDetector();
    }

    private void startRecording() {
        if (isRecording) return;

        imuSampleCount = 0;
        gnssSampleCount = 0;
        drEngine.start();
        
        try {
            dataFileWriter = new DataFileWriter(FOLDER_NAME, DATA_FILE_NAMES, DATA_FILE_HEADINGS);
            isRecording = true;
            wasRunning = true;
            textRecordingStatus.setText("Recording: ON");
            
            for (Sensor sensor : sensors) {
                if (sensor != null) {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
            startLocationUpdates();
            enableStopButton();
        } catch (IOException e) {
            Toast.makeText(this, "Error creating data files: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void pauseRecording() {
        if (!isRecording) return;
        sensorManager.unregisterListener(this);
        stopLocationUpdates();
        isRecording = false;
        textRecordingStatus.setText("Recording: PAUSED");
        buttonStart.setEnabled(true);
        buttonPause.setEnabled(false);
    }

    private void stopRecording() {
        sensorManager.unregisterListener(this);
        stopLocationUpdates();
        drEngine.stop();
        isRecording = false;
        wasRunning = false;
        textRecordingStatus.setText("Recording: OFF");
        dataFileWriter = null;
        enableStartButton();
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMinUpdateIntervalMillis(500)
                    .build();
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void updateGnssUI() {
        if (lastLocation != null) {
            textGnssCount.setText("GNSS Samples: " + gnssSampleCount);
            textGnssAccuracy.setText(String.format(Locale.US, "GNSS Accuracy: %.2f m", lastLocation.getAccuracy()));
            textGnssSpeed.setText(String.format(Locale.US, "GNSS Speed: %.2f m/s", lastLocation.getSpeed()));
        }
    }

    private void enableStopButton() {
        buttonStart.setEnabled(false);
        buttonPause.setEnabled(true);
        buttonStop.setEnabled(true);
    }

    private void enableStartButton() {
        buttonStart.setEnabled(true);
        buttonPause.setEnabled(false);
        buttonStop.setEnabled(false);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording) return;

        float[] values = event.values.clone();
        
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                latestAccel = values;
                processUnifiedSample(event.timestamp);
                break;
            case Sensor.TYPE_GYROSCOPE:
                latestGyro = values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                latestMag = values;
                break;
            case Sensor.TYPE_GRAVITY:
                latestGravity = values;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                latestLinearAccel = values;
                break;
        }

        // Keep engine and motion detector updated
        if (latestGravity != null && latestMag != null && latestGyro != null && latestLinearAccel != null) {
            drEngine.updateSensors(latestGravity, latestMag, latestGyro, latestLinearAccel, event.timestamp);
        }
    }

    private void processUnifiedSample(long timestampNs) {
        imuSampleCount++;
        
        IMUGNSSSample sample = new IMUGNSSSample();
        sample.timestampNs = timestampNs;
        sample.timestampMs = System.currentTimeMillis(); // Wall clock reference

        if (latestAccel != null) {
            sample.accelX = latestAccel[0];
            sample.accelY = latestAccel[1];
            sample.accelZ = latestAccel[2];
        }
        if (latestGyro != null) {
            sample.gyroX = latestGyro[0];
            sample.gyroY = latestGyro[1];
            sample.gyroZ = latestGyro[2];
        }
        if (latestMag != null) {
            sample.magX = latestMag[0];
            sample.magY = latestMag[1];
            sample.magZ = latestMag[2];
        }
        if (latestGravity != null) {
            sample.gravityX = latestGravity[0];
            sample.gravityY = latestGravity[1];
            sample.gravityZ = latestGravity[2];
        }
        if (latestLinearAccel != null) {
            sample.linearAccelX = latestLinearAccel[0];
            sample.linearAccelY = latestLinearAccel[1];
            sample.linearAccelZ = latestLinearAccel[2];
        }

        if (lastLocation != null) {
            sample.latitude = lastLocation.getLatitude();
            sample.longitude = lastLocation.getLongitude();
            sample.altitude = lastLocation.getAltitude();
            sample.gnssSpeed = lastLocation.getSpeed();
            sample.gnssBearing = lastLocation.getBearing();
            sample.horizontalAccuracy = lastLocation.getAccuracy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sample.verticalAccuracy = lastLocation.getVerticalAccuracyMeters();
            }
            sample.locationTimestampMs = lastLocation.getTime();
            sample.gnssAgeMs = System.currentTimeMillis() - lastLocation.getTime();
        }

        sample.stepCount = drEngine.getStepCount();
        sample.heading = (float) drEngine.getHeading();
        sample.motionMode = motionModeDetector.getCurrentMode().name();

        if (dataFileWriter != null) {
            dataFileWriter.writeToFile("Unified_Dataset", sample.toCsvRow());
        }

        // Update UI every 10 samples to save CPU
        if (imuSampleCount % 10 == 0) {
            runOnUiThread(() -> {
                textImuCount.setText("IMU Samples: " + imuSampleCount);
                textCurrentHeading.setText(String.format(Locale.US, "Heading: %.1f°", sample.heading));
                textMotionMode.setText("Motion: " + sample.motionMode);
                textDebugInfo.setText("Last Sample:\n" + sample.toCsvRow().replace(",", "\n"));
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (wasRunning && !isRecording) {
            startRecording();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            pauseRecording();
            wasRunning = true;
        }
    }
}
