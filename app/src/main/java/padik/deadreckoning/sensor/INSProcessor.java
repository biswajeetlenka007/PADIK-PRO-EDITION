package padik.deadreckoning.sensor;

import android.content.Context;

import padik.deadreckoning.ai.SpeedEstimate;
import padik.deadreckoning.ai.SpeedEstimator;
import padik.deadreckoning.model.IMUSample;
import padik.deadreckoning.model.INSState;
import padik.deadreckoning.model.GNSSSample;
import padik.deadreckoning.model.MotionMode;

import org.osmdroid.util.GeoPoint;
import org.ejml.data.DMatrixRMaj;

/**
 * 6-DOF Inertial Navigation System Processor.
 * Implements IMU mechanization: integrating raw accel and gyro into 3D navigation state.
 * Frame Convention: NED (North, East, Down).
 */
public class INSProcessor {

    private ExtendedKalmanFilter ekf = new ExtendedKalmanFilter();
    private ZUPTDetector zuptDetector = new ZUPTDetector();
    private boolean isInitialized = false;
    private long lastTimestampNs = 0;
    
    // UI state persistence for cloned states
    private String lastAiStatus = "UNAVAILABLE";
    private float lastAiSpeedMps = -1f;
    
    // AI Speed Estimator
    private SpeedEstimator speedEstimator;
    private SpeedSourceManager speedSourceManager;

    // R_vd: Device to Vehicle rotation from Phase 2
    private DMatrixRMaj R_vd = null;

    public INSProcessor(Context context) {
    }

    public void setSpeedEstimator(SpeedEstimator estimator) {
        this.speedEstimator = estimator;
    }
    
    public void setSpeedSourceManager(SpeedSourceManager manager) {
        this.speedSourceManager = manager;
    }

    public void setMountingRotation(DMatrixRMaj rotation) {
        this.R_vd = rotation;
        ekf.setMountingRotation(rotation);
    }

    /**
     * Initialize the INS with a GNSS position.
     */
    public void initialize(GNSSSample gnss) {
        INSState initialState = new INSState();
        initialState.origin = new GeoPoint(gnss.getLatitude(), gnss.getLongitude());
        initialState.geoPoint = initialState.origin;
        initialState.posNorth = 0;
        initialState.posEast = 0;
        initialState.posDown = 0;
        initialState.velNorth = 0; 
        initialState.velEast = 0;
        initialState.velDown = 0;
        
        if (R_vd != null) {
            initialState.quaternion = matrixToQuaternion(R_vd);
        } else {
            initialState.quaternion = new double[]{1, 0, 0, 0};
        }
        
        initialState.isInitialized = true;
        ekf.initialize(initialState);
        isInitialized = true;
    }

    public void update(IMUSample imu, MotionMode motionMode, double calibratedHeading) {
        if (!isInitialized) return;
        
        if (lastTimestampNs == 0) {
            lastTimestampNs = imu.getTimestamp();
            return;
        }
        double dt = (imu.getTimestamp() - lastTimestampNs) / 1_000_000_000.0;
        lastTimestampNs = imu.getTimestamp();

        if (dt > 0 && dt < 0.5) {
            boolean isStationary = zuptDetector.isStationary();
            double aiSpeed = 0.0;
            boolean hasAiSpeed = false;
            
            lastAiStatus = "UNAVAILABLE";
            lastAiSpeedMps = -1f;

            if (speedEstimator != null) {
                SpeedEstimate estimate = speedEstimator.getLastEstimate();
                if (estimate != null && estimate.isValid && estimate.isModelAvailable) {
                    aiSpeed = estimate.speedMps;
                    hasAiSpeed = true;
                    lastAiStatus = "ACTIVE";
                    lastAiSpeedMps = (float) aiSpeed;
                }
            }

            if (motionMode == MotionMode.VEHICLE) {
                // Kinematic Velocity Integration (AI-DRIVE / GPS-DRIVE)
                double v = 0.0;
                if (!isStationary) {
                    if (speedSourceManager != null) {
                        v = speedSourceManager.getCurrentSpeedKmh() / 3.6; // convert km/h to m/s
                    } else if (hasAiSpeed && aiSpeed >= 0.3) {
                        v = aiSpeed;
                    }
                }
                
                // Extra guard if speed is explicitly very low
                if (v < 0.3) {
                    v = 0.0;
                }
                
                ekf.predictKinematic(imu, dt, v, calibratedHeading);
            } else {
                // Standard IMU prediction
                ekf.predict(imu, dt);
                
                if (hasAiSpeed) {
                    ekf.updateWithAISpeed(aiSpeed);
                }
            }
            
            // Constraints (ZUPT/NHC)
            zuptDetector.addSample(imu);
            if (zuptDetector.isStationary()) {
                ekf.updateWithZUPT();
            } else if (motionMode != MotionMode.VEHICLE) {
                // Apply NHC when moving (e.g., speed > 0.5 m/s)
                INSState current = ekf.getNominalState();
                double speed = Math.sqrt(current.velNorth*current.velNorth + current.velEast*current.velEast + current.velDown*current.velDown);
                if (speed > 0.5) {
                    ekf.updateWithNHC();
                }
            }
        }
    }

    public void updateGNSS(GNSSSample gnss) {
        if (!isInitialized) {
            initialize(gnss);
            return;
        }

        // Convert lat/lon to NED meters relative to origin
        INSState state = ekf.getNominalState();
        if (state.origin == null) return;

        double earthRadius = 6378137.0;
        double latRad = Math.toRadians(state.origin.getLatitude());
        double dLat = Math.toRadians(gnss.getLatitude() - state.origin.getLatitude());
        double dLon = Math.toRadians(gnss.getLongitude() - state.origin.getLongitude());

        double posN = dLat * earthRadius;
        double posE = dLon * earthRadius * Math.cos(latRad);
        double posD = 0; // Altitude error handled by R_gnss if needed

        ekf.updateGNSS(gnss, posN, posE, posD);
    }

    private double[] matrixToQuaternion(DMatrixRMaj R) {
        double t = R.get(0, 0) + R.get(1, 1) + R.get(2, 2);
        double w, x, y, z;
        if (t > 0) {
            double s = Math.sqrt(t + 1.0) * 2;
            w = 0.25 * s;
            x = (R.get(2, 1) - R.get(1, 2)) / s;
            y = (R.get(0, 2) - R.get(2, 0)) / s;
            z = (R.get(1, 0) - R.get(0, 1)) / s;
        } else if ((R.get(0, 0) > R.get(1, 1)) && (R.get(0, 0) > R.get(2, 2))) {
            double s = Math.sqrt(1.0 + R.get(0, 0) - R.get(1, 1) - R.get(2, 2)) * 2;
            w = (R.get(2, 1) - R.get(1, 2)) / s;
            x = 0.25 * s;
            y = (R.get(0, 1) + R.get(1, 0)) / s;
            z = (R.get(0, 2) + R.get(2, 0)) / s;
        } else if (R.get(1, 1) > R.get(2, 2)) {
            double s = Math.sqrt(1.0 + R.get(1, 1) - R.get(0, 0) - R.get(2, 2)) * 2;
            w = (R.get(0, 2) - R.get(2, 0)) / s;
            x = (R.get(0, 1) + R.get(1, 0)) / s;
            y = 0.25 * s;
            z = (R.get(1, 2) + R.get(2, 1)) / s;
        } else {
            double s = Math.sqrt(1.0 + R.get(2, 2) - R.get(0, 0) - R.get(1, 1)) * 2;
            w = (R.get(1, 0) - R.get(0, 1)) / s;
            x = (R.get(0, 2) + R.get(2, 0)) / s;
            y = (R.get(1, 2) + R.get(2, 1)) / s;
            z = 0.25 * s;
        }
        return new double[]{w, x, y, z};
    }

    public INSState getState() {
        INSState internal = ekf.getNominalState();
        // Update GeoPoint before returning
        double earthRadius = 6378137.0;
        if (internal.origin != null) {
            double latRad = Math.toRadians(internal.origin.getLatitude());
            double dLat = internal.posNorth / earthRadius;
            double dLon = internal.posEast / (earthRadius * Math.cos(latRad));
            internal.geoPoint = new GeoPoint(
                internal.origin.getLatitude() + Math.toDegrees(dLat),
                internal.origin.getLongitude() + Math.toDegrees(dLon)
            );
        }
        
        internal.stationary = zuptDetector.isStationary();
        internal.zuptActive = internal.stationary;
        double speed = Math.sqrt(internal.velNorth*internal.velNorth + internal.velEast*internal.velEast + internal.velDown*internal.velDown);
        internal.nhcActive = !internal.stationary && speed > 0.5;
        
        internal.aiStatus = lastAiStatus;
        internal.aiSpeedMps = lastAiSpeedMps;

        return internal;
    }
    
    public void reset() {
        ekf = new ExtendedKalmanFilter();
        zuptDetector.reset();
        isInitialized = false;
        lastTimestampNs = 0;
    }
}
