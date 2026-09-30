package padik.deadreckoning.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.ejml.data.DMatrixRMaj;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

import padik.deadreckoning.R;
import padik.deadreckoning.activity.MainContainerActivity;
import padik.deadreckoning.ai.AISample;
import padik.deadreckoning.ai.SpeedEstimate;
import padik.deadreckoning.ai.SpeedEstimator;
import padik.deadreckoning.ai.SpeedModelConfig;
import padik.deadreckoning.model.IMUSample;
import padik.deadreckoning.model.GNSSSample;
import padik.deadreckoning.model.INSState;
import padik.deadreckoning.model.Trip;
import padik.deadreckoning.model.TurnEvent;
import padik.deadreckoning.model.MotionMode;
import padik.deadreckoning.preferences.TurnModePreferences;
import padik.deadreckoning.sensor.DeadReckoningEngine;
import padik.deadreckoning.sensor.GNSSQualityManager;
import padik.deadreckoning.sensor.INSProcessor;
import padik.deadreckoning.sensor.GNSSQualityManager;
import padik.deadreckoning.sensor.PreciseHeadingEstimator;
import padik.deadreckoning.sensor.SpeedSourceManager;
import padik.deadreckoning.storage.TripStorage;

public class TrackingService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "tracking_channel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_START = "padik.deadreckoning.action.START";
    public static final String ACTION_STOP = "padik.deadreckoning.action.STOP";

    private final IBinder binder = new LocalBinder();

    private SensorManager sensorManager;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private PowerManager.WakeLock wakeLock;

    private Sensor sensorGravity;
    private Sensor sensorMagnetic;
    private Sensor sensorGyroscope;
    private Sensor sensorLinearAcceleration;
    private Sensor sensorAccelerometer;

    private DeadReckoningEngine deadReckoningEngine;
    private PreciseHeadingEstimator headingEstimator;
    private INSProcessor insProcessor;
    private GNSSQualityManager gnssQualityManager;
    private TripStorage tripStorage;
    private SpeedEstimator speedEstimator;
    private SpeedSourceManager speedSourceManager;
    private TurnModePreferences turnModePrefs;

    private MotionMode motionMode = MotionMode.WALKING;
    private boolean isTracking = false;
    private boolean isManualMode = false;
    private boolean isScreenOn = true;

    private List<GeoPoint> pathPoints = new ArrayList<>();
    private Trip currentTrip = null;

    private float[] latestAccelerometer;
    private float[] latestGyroscope;

    private int gpsCalibrationPoints = 0;
    
    private long lastAiSampleTimeNs = 0;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
            }
        }
    };

    public class LocalBinder extends Binder {
        public TrackingService getService() {
            return TrackingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initSensors();
        initLocation();
        initEngine();
        initWakeLock();
        registerScreenReceiver();
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                "DeadReckoning:TrackingWakeLock");
        }
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startTracking();
            } else if (ACTION_STOP.equals(action)) {
                stopTracking();
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_in_progress),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.tracking_notification_desc));
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Sensor sensorStepDetector;

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorLinearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleGPSUpdate(location);
                }
            }
        };
    }

    private void initEngine() {
        deadReckoningEngine = new DeadReckoningEngine();
        headingEstimator = new PreciseHeadingEstimator();
        insProcessor = new INSProcessor(this);
        speedEstimator = new SpeedEstimator(this);
        speedSourceManager = new SpeedSourceManager();
        insProcessor.setSpeedEstimator(speedEstimator);
        insProcessor.setSpeedSourceManager(speedSourceManager);
        gnssQualityManager = new GNSSQualityManager();
        loadMountingCalibration();
        tripStorage = new TripStorage(this);
        turnModePrefs = new TurnModePreferences(this);
        isManualMode = turnModePrefs.getTurnMode() == TurnModePreferences.TurnMode.MANUAL;
    }

    private void loadMountingCalibration() {
        SharedPreferences prefs = getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("mount_calibrated", false)) {
            DMatrixRMaj R = new DMatrixRMaj(3, 3);
            for (int i = 0; i < 9; i++) {
                R.data[i] = prefs.getFloat("mount_r" + i, 0);
            }
            insProcessor.setMountingRotation(R);
        }
    }

    public void startTracking() {
        if (isTracking) return;

        isTracking = true;
        deadReckoningEngine.start();
        insProcessor.reset();
        loadMountingCalibration();
        latestAccelerometer = null;
        latestGyroscope = null;
        pathPoints.clear();
        gpsCalibrationPoints = 0;

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 60 * 1000L);
        }

        registerSensors();
        startLocationUpdates();
        startForeground();

        updateNotification(getString(R.string.tracking_in_progress), getString(R.string.tracking_status_format, 0, 0.00));
    }

    public void stopTracking() {
        if (!isTracking) return;

        isTracking = false;
        deadReckoningEngine.stop();

        sensorManager.unregisterListener(this);
        fusedLocationClient.removeLocationUpdates(locationCallback);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (deadReckoningEngine.getCurrentTrip() != null) {
            tripStorage.saveTrip(deadReckoningEngine.getCurrentTrip());
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    public void calibrateGPS() {
        gpsCalibrationPoints = 0;
    }

    private void registerSensors() {
        if (sensorGravity != null) {
            sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorMagnetic != null) {
            sensorManager.registerListener(this, sensorMagnetic, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorGyroscope != null) {
            sensorManager.registerListener(this, sensorGyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorLinearAcceleration != null) {
            sensorManager.registerListener(this, sensorLinearAcceleration, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorAccelerometer != null) {
            sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorStepDetector != null) {
            sensorManager.registerListener(this, sensorStepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void startForeground() {
        Intent notificationIntent = new Intent(this, MainContainerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.tracking_in_progress) + "...")
                .setSmallIcon(R.drawable.ic_directions_walk)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, MainContainerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_directions_walk)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void handleGPSUpdate(Location location) {
        if (!isTracking) return;

        GNSSQualityManager.GNSSQuality quality = gnssQualityManager.assess(location);
        
        if (speedSourceManager != null && location.hasSpeed()) {
            speedSourceManager.updateGpsSpeed(location.getSpeed(), location.getAccuracy());
        }
        
        if (quality == GNSSQualityManager.GNSSQuality.GOOD || quality == GNSSQualityManager.GNSSQuality.DEGRADED) {
            deadReckoningEngine.calibrateWithGPS(location);
            gpsCalibrationPoints++;
            
            // GNSS COURSE-OVER-GROUND (COG) GYRO BIAS CALIBRATION
            if (motionMode == MotionMode.VEHICLE && location.hasBearing() && location.hasSpeed() && location.getSpeed() > 2.0) {
                double currentGyroHeading = headingEstimator.getGyroHeadingRadians();
                double gpsBearingRadians = Math.toRadians(location.getBearing());
                
                double angleDiff = gpsBearingRadians - currentGyroHeading;
                while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                
                headingEstimator.setGyroHeading(currentGyroHeading + 0.05 * angleDiff);
            }
            
            // INS Update (Phase 4 EKF)
            GNSSSample gnss = new GNSSSample(
                location.getTime(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                location.getSpeed(),
                location.getBearing(),
                location.getAccuracy(),
                location.hasSpeed(),
                location.hasBearing()
            );
            
            if (!insProcessor.getState().isInitialized) {
                insProcessor.initialize(gnss);
            }
            insProcessor.updateGNSS(gnss);
        }

        int steps = deadReckoningEngine.getStepCount();
        double distance = deadReckoningEngine.getDistance();
        updateNotification(getString(R.string.app_name), getString(R.string.tracking_status_format, steps, distance));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking) return;

        float[] values = event.values.clone();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            latestAccelerometer = values;
        }

        // Feed AI sampling pipeline (~10 Hz)
        long currentTimeNs = event.timestamp;
        if (currentTimeNs - lastAiSampleTimeNs >= 100_000_000L && latestAccelerometer != null) {
            lastAiSampleTimeNs = currentTimeNs;
            
            float[] features = new float[15];
            float[] gravity = headingEstimator.getGravity();
            float[] mag = headingEstimator.getMagneticField();
            float[] gyro = headingEstimator.getGyroscope();
            
            features[SpeedModelConfig.INDEX_ACCEL_X] = latestAccelerometer[0];
            features[SpeedModelConfig.INDEX_ACCEL_Y] = latestAccelerometer[1];
            features[SpeedModelConfig.INDEX_ACCEL_Z] = latestAccelerometer[2];
            
            if (gravity != null) {
                features[SpeedModelConfig.INDEX_GRAVITY_X] = gravity[0];
                features[SpeedModelConfig.INDEX_GRAVITY_Y] = gravity[1];
                features[SpeedModelConfig.INDEX_GRAVITY_Z] = gravity[2];
            }
            if (mag != null) {
                features[SpeedModelConfig.INDEX_MAG_X] = mag[0];
                features[SpeedModelConfig.INDEX_MAG_Y] = mag[1];
                features[SpeedModelConfig.INDEX_MAG_Z] = mag[2];
            }
            if (gyro != null) {
                features[SpeedModelConfig.INDEX_GYRO_YAW] = gyro[0];
                features[SpeedModelConfig.INDEX_GYRO_PITCH] = gyro[1];
                features[SpeedModelConfig.INDEX_GYRO_ROLL] = gyro[2];
            }
            if (gravity != null && mag != null) {
                float[] R = new float[9];
                float[] I = new float[9];
                if (SensorManager.getRotationMatrix(R, I, gravity, mag)) {
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    
                    float yawDeg = (float) Math.toDegrees(orientation[0]);
                    if (yawDeg < 0) yawDeg += 360f;
                    float pitchDeg = (float) Math.toDegrees(orientation[1]);
                    float rollDeg = (float) Math.toDegrees(orientation[2]);
                    
                    features[SpeedModelConfig.INDEX_ORIENTATION_YAW] = yawDeg;
                    features[SpeedModelConfig.INDEX_ORIENTATION_PITCH] = pitchDeg;
                    features[SpeedModelConfig.INDEX_ORIENTATION_ROLL] = rollDeg;
                }
            }
            
            AISample aiSample = new AISample(currentTimeNs, features);
            if (speedEstimator != null) {
                speedEstimator.addAISample(aiSample);
                SpeedEstimate estimate = speedEstimator.getLastEstimate();
                if (estimate != null && estimate.isValid) {
                    if (speedSourceManager != null) {
                        speedSourceManager.updateAiSpeed(estimate.speedMps);
                    }
                }
            }
        }

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                if (latestGyroscope != null) {
                    IMUSample imuSample = new IMUSample(
                            event.timestamp,
                            latestAccelerometer[0], latestAccelerometer[1], latestAccelerometer[2],
                            latestGyroscope[0], latestGyroscope[1], latestGyroscope[2]
                    );
                    if (motionMode == MotionMode.VEHICLE) {
                        insProcessor.update(imuSample, motionMode, headingEstimator.getHeading());
                    }
                }
                break;
            case Sensor.TYPE_GRAVITY:
                headingEstimator.updateGravity(values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                headingEstimator.updateMagneticField(values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                latestGyroscope = values;
                headingEstimator.updateGyroscope(values, event.timestamp);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                deadReckoningEngine.updateSensors(
                        headingEstimator.getGravity(),
                        headingEstimator.getMagneticField(),
                        headingEstimator.getGyroscope(),
                        values,
                        event.timestamp
                );
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                if (motionMode == MotionMode.WALKING) {
                    if (event.values.length > 0 && event.values[0] == 1.0f) {
                        deadReckoningEngine.addAndroidStep(event.timestamp);
                    }
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public boolean isTracking() {
        return isTracking;
    }

    public int getStepCount() {
        return deadReckoningEngine.getStepCount();
    }

    public double getDistance() {
        return deadReckoningEngine.getDistance();
    }

    public double getUncertaintyRadius() {
        return deadReckoningEngine.getUncertaintyRadius();
    }

    public double getSpeed() {
        if (speedSourceManager != null) {
            return speedSourceManager.getCurrentSpeedKmh() / 3.6; // returns m/s
        }
        return 0.0;
    }

    public String getSpeedSource() {
        if (speedSourceManager != null) {
            return speedSourceManager.getCurrentSpeedSource().name();
        }
        return "UNKNOWN";
    }

    public SpeedSourceManager getSpeedSourceManager() {
        return speedSourceManager;
    }

    public double getHeading() {
        return deadReckoningEngine.getHeading();
    }

    public boolean isTurning() {
        return deadReckoningEngine.isTurning();
    }

    public double getAccumulatedHeadingChange() {
        return deadReckoningEngine.getAccumulatedHeadingChange();
    }

    public int getCalibrationPoints() {
        return gpsCalibrationPoints;
    }

    public void setMotionMode(MotionMode mode) {
        this.motionMode = mode;
        if (deadReckoningEngine != null) {
            deadReckoningEngine.setMotionMode(mode);
        }
    }

    public void setFixedHeading(double heading) {
        if (deadReckoningEngine != null) {
            deadReckoningEngine.setFixedHeading(heading);
        }
    }

    public MotionMode getMotionMode() {
        return motionMode;
    }

    public GeoPoint getVehiclePosition() {
        return insProcessor.getState().geoPoint;
    }

    public void turnLeft() {
        if (isTracking) {
            deadReckoningEngine.turnLeft();
        }
    }

    public void turnRight() {
        if (isTracking) {
            deadReckoningEngine.turnRight();
        }
    }

    public void turnAround() {
        if (isTracking) {
            deadReckoningEngine.turnAround();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {}
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        stopTracking();
    }
}
