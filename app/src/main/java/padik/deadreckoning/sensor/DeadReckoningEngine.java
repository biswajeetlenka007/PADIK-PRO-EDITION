package padik.deadreckoning.sensor;

import android.location.Location;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import padik.deadreckoning.model.Trip;
import padik.deadreckoning.model.TurnEvent;
import padik.deadreckoning.model.MotionMode;
import padik.deadreckoning.preferences.StepCounterPreferences;
import padik.deadreckoning.stepcounting.DynamicStepCounter;
import padik.deadreckoning.stepcounting.StaticStepCounter;

public class DeadReckoningEngine {
    
    private final EnhancedStepCounter stepCounter;
    private final PreciseHeadingEstimator headingEstimator;
    private final GPSCalibrator gpsCalibrator;
    
    private double currentX = 0;
    private double currentY = 0;
    private double currentHeading = 0;
    private double previousHeading = 0;
    private double totalDistance = 0;
    
    private boolean isRunning = false;
    private boolean isPaused = false;
    private Location startLocation = null;
    private Location lastGPSLocation = null;
    
    private Trip currentTrip = null;
    private final List<TurnEvent> turnEvents = new ArrayList<>();
    
    private static final double TURN_THRESHOLD = 25.0;
    private static final int MIN_STEPS_BETWEEN_TURNS = 5;
    private int stepsSinceLastTurn = 0;
    private double accumulatedHeadingChange = 0;
    private boolean isTurning = false;
    
    private int androidStepCount = 0;
    private int staticStepCount = 0;
    private StepCounterPreferences.StepMode stepMode = StepCounterPreferences.StepMode.DYNAMIC;
    
    private DynamicStepCounter dynamicStepCounter;
    private StaticStepCounter staticStepCounter;

    // Hardware step integration
    private boolean hardwareStepAvailable = false;
    private long lastHardwareStepTime = 0;
    private long lastStepTimestamp = 0;
    private double timeSincePreviousStep = 0;
    private long lastStepTime = 0;
    private String stepSourceUsed = "FALLBACK";
    private int rejectedStepCount = 0;
    private MotionMode motionMode = MotionMode.WALKING;

    // Mathematical state covariance for position (Pk)
    private double positionVariance = 25.0; // Starts at 5m radius (5^2)
    private long lastSensorTime = 0;
    
    public DeadReckoningEngine() {
        stepCounter = new EnhancedStepCounter();
        headingEstimator = new PreciseHeadingEstimator();
        gpsCalibrator = new GPSCalibrator();
        
        dynamicStepCounter = new DynamicStepCounter(0.875);
        staticStepCounter = new StaticStepCounter(2.0, 1.0);
    }
    
    public void setStepMode(StepCounterPreferences.StepMode mode) {
        this.stepMode = mode;
    }
    
    public void setMotionMode(MotionMode mode) {
        this.motionMode = mode;
        if (headingEstimator != null) {
            headingEstimator.setMotionMode(mode);
        }
    }
    
    public void setHardwareStepAvailable(boolean available) {
        this.hardwareStepAvailable = available;
        this.stepSourceUsed = available ? "HARDWARE" : "FALLBACK";
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public boolean isHardwareSensorAvailable() {
        return hardwareStepAvailable;
    }

    public String getStepSource() {
        return stepSourceUsed;
    }

    public long getLastStepTimestamp() {
        return lastStepTimestamp;
    }

    public double getTimeSincePreviousStep() {
        return timeSincePreviousStep;
    }

    public int getRejectedStepCount() {
        return stepCounter.getRejectedStepCount() + rejectedStepCount;
    }

    public String getStepDebugInfo() {
        return String.format(Locale.getDefault(), 
            "Source: %s | HW Available: %s | Steps: %d | Rejected: %d | Last Step Δ: %.1fms",
            stepSourceUsed, hardwareStepAvailable ? "YES" : "NO", getStepCount(), getRejectedStepCount(), timeSincePreviousStep);
    }

    public void addAndroidStep(long timestamp) {
        if (!isRunning || isPaused || motionMode == MotionMode.VEHICLE) return;

        // Prevent duplicate hardware events
        if (lastHardwareStepTime > 0 && timestamp == lastHardwareStepTime) {
            Log.d("StepDebug", "STEP_REJECTED: reason=DUPLICATE timestamp=" + timestamp);
            rejectedStepCount++;
            return;
        }

        // Minimum interval check (250ms)
        if (lastHardwareStepTime > 0 && (timestamp - lastHardwareStepTime) < 250_000_000L) {
            Log.d("StepDebug", "STEP_REJECTED: reason=TOO_FAST timestamp=" + timestamp);
            rejectedStepCount++;
            return;
        }
        lastHardwareStepTime = timestamp;

        androidStepCount++;

        // Apply ZUPT constraint (Shrink ellipse on foot step)
        positionVariance *= 0.85; 
        if (positionVariance < 1.0) positionVariance = 1.0;
        
        updatePosition();
        stepsSinceLastTurn++;

        lastStepTimestamp = timestamp;
        timeSincePreviousStep = lastStepTime > 0 ? (timestamp - lastStepTime) / 1000000.0 : 0;
        lastStepTime = timestamp;
        stepSourceUsed = "ANDROID HARDWARE";
        
        Log.d("StepDebug", "STEP_EVENT: source=ANDROID timestamp=" + timestamp + " count=" + androidStepCount);
    }
    
    public void start() {
        isRunning = true;
        isPaused = false;
        stepCounter.reset();
        dynamicStepCounter = new DynamicStepCounter(0.875);
        staticStepCounter = new StaticStepCounter(2.0, 1.0);
        headingEstimator.reset();
        currentTrip = new Trip();
        turnEvents.clear();
        useManualHeading = false;
        
        currentX = 0;
        currentY = 0;
        currentHeading = 0;
        previousHeading = 0;
        totalDistance = 0;
        accumulatedHeadingChange = 0;
        stepsSinceLastTurn = 0;
        androidStepCount = 0;
        rejectedStepCount = 0;
        lastHardwareStepTime = 0;
        lastStepTimestamp = 0;
        lastStepTime = 0;
        timeSincePreviousStep = 0;
    }
    
    public void stop() {
        isRunning = false;
        isPaused = false;
        
        if (currentTrip != null) {
            currentTrip.setTotalDistance(totalDistance);
            currentTrip.setTotalSteps(getStepCount());
            currentTrip.setTurnEvents(new ArrayList<>(turnEvents));
            currentTrip.finish();
        }
    }

    public void resetCalibration() {
        gpsCalibrator.resetCalibration();
    }
    
    public void reset() {
        currentX = 0;
        currentY = 0;
        currentHeading = 0;
        previousHeading = 0;
        totalDistance = 0;
        stepCounter.reset();
        dynamicStepCounter = new DynamicStepCounter(0.875);
        staticStepCounter = new StaticStepCounter(2.0, 1.0);
        headingEstimator.reset();
        gpsCalibrator.resetCalibration();
        
        currentTrip = null;
        turnEvents.clear();
        
        accumulatedHeadingChange = 0;
        stepsSinceLastTurn = 0;
        isTurning = false;
        
        startLocation = null;
        lastGPSLocation = null;
        
        androidStepCount = 0;
        rejectedStepCount = 0;
        lastHardwareStepTime = 0;
        lastStepTimestamp = 0;
        lastStepTime = 0;
        timeSincePreviousStep = 0;

        positionVariance = 25.0;
        lastSensorTime = 0;
    }
    
    public void updateSensors(float[] gravity, float[] magnetic, float[] gyro, float[] linearAccel, long timestamp) {
        if (!isRunning || isPaused || motionMode == MotionMode.VEHICLE) return;
        
        // Update covariance (time drift)
        if (lastSensorTime > 0) {
            double dt = (timestamp - lastSensorTime) / 1000000000.0; // ns to seconds
            if (dt > 0 && dt < 1.0) {
                positionVariance += 1.5 * dt; // IMU drift integration
            }
        }
        lastSensorTime = timestamp;
        
        if (gravity != null) {
            headingEstimator.updateGravity(gravity);
        }
        if (magnetic != null) {
            headingEstimator.updateMagneticField(magnetic);
        }
        if (gyro != null) {
            headingEstimator.updateGyroscope(gyro, timestamp);
        }
        
        if (gravity != null && magnetic != null) {
            previousHeading = currentHeading;
            if (!useManualHeading) {
                currentHeading = headingEstimator.getHeadingDegrees();
            }
            detectTurn();
        }
        
        if (linearAccel != null) {
            if (hardwareStepAvailable || stepMode == StepCounterPreferences.StepMode.ANDROID) {
                // Do not detect navigation steps from acceleration.
                return;
            }

            double magnitude = Math.sqrt(
                linearAccel[0] * linearAccel[0] +
                linearAccel[1] * linearAccel[1] +
                linearAccel[2] * linearAccel[2]
            );
            
            
            boolean enhancedDetected = stepCounter.detectStep(linearAccel, timestamp);
            boolean dynamicDetected = dynamicStepCounter.findStep(magnitude);
            boolean staticDetected = staticStepCounter.findStep(magnitude);
            boolean detected = false;
            
            switch (stepMode) {
                case DYNAMIC:
                    detected = dynamicDetected;
                    break;
                case STATIC:
                    detected = staticDetected;
                    break;
                case ANDROID:
                    // Should be caught above, but just in case
                    detected = false;
                    break;
            }
            
            if (detected) {
                // Apply ZUPT constraint (Shrink ellipse on foot step)
                positionVariance *= 0.85; 
                if (positionVariance < 1.0) positionVariance = 1.0;
                
                updatePosition();
                stepsSinceLastTurn++;
                
                lastStepTimestamp = timestamp;
                timeSincePreviousStep = lastStepTime > 0 ? (timestamp - lastStepTime) / 1000000.0 : 0;
                lastStepTime = timestamp;
                stepSourceUsed = "ACCELERATION_FALLBACK";
                
                Log.d("StepDebug", "STEP_EVENT: source=ACCELERATION_FALLBACK timestamp=" + timestamp + " count=" + getStepCount());
            }
        }
    }
    

    
    private void detectTurn() {
        double headingDelta = currentHeading - previousHeading;
        
        if (Math.abs(headingDelta) > 180) {
            if (headingDelta > 0) {
                headingDelta -= 360;
            } else {
                headingDelta += 360;
            }
        }
        
        if (Math.abs(headingDelta) > 5 && !isTurning) {
            isTurning = true;
            accumulatedHeadingChange = 0;
        }
        
        if (isTurning) {
            accumulatedHeadingChange += headingDelta;
            
            if (Math.abs(accumulatedHeadingChange) >= TURN_THRESHOLD && stepsSinceLastTurn >= MIN_STEPS_BETWEEN_TURNS) {
                TurnEvent.TurnType turnType = TurnEvent.determineTurnType(accumulatedHeadingChange);
                
                if (turnType != null && Math.abs(accumulatedHeadingChange) < 270) {
                    addTurnEvent(turnType, accumulatedHeadingChange);
                }
                
                isTurning = false;
                accumulatedHeadingChange = 0;
                stepsSinceLastTurn = 0;
            } else if (Math.abs(accumulatedHeadingChange) < 5) {
                isTurning = false;
                accumulatedHeadingChange = 0;
            }
        }
    }
    
    private void addTurnEvent(TurnEvent.TurnType type, double headingChange) {
        double lat = 0, lon = 0;
        
        if (currentTrip != null && !currentTrip.getPathPoints().isEmpty()) {
            GeoPoint lastPoint = currentTrip.getPathPoints().get(currentTrip.getPathPoints().size() - 1);
            lat = lastPoint.getLatitude();
            lon = lastPoint.getLongitude();
        }
        
        TurnEvent event = new TurnEvent(type, headingChange, getStepCount(), lat, lon);
        turnEvents.add(event);
        
        if (currentTrip != null) {
            currentTrip.addTurnEvent(event);
        }
    }
    
    private void updatePosition() {
        double strideLength = stepCounter.getStrideLength();
        double correctedHeading = gpsCalibrator.correctHeading(currentHeading);
        double headingRadians = Math.toRadians(correctedHeading);
        
        double deltaX = strideLength * Math.sin(headingRadians);
        double deltaY = strideLength * Math.cos(headingRadians);
        
        currentX += deltaX;
        currentY += deltaY;
        totalDistance += strideLength;
        
        if (currentTrip != null) {
            GeoPoint point = calculateGeoPoint(currentX, currentY);
            if (point != null) {
                currentTrip.addPoint(point);
            }
        }
    }
    
    private GeoPoint calculateGeoPoint(double x, double y) {
        if (startLocation == null) return null;
        
        double earthRadius = 6371000; // meters
        double latRad = Math.toRadians(startLocation.getLatitude());
        double lonRad = Math.toRadians(startLocation.getLongitude());
        
        double newLatRad = latRad + y / earthRadius;
        double newLonRad = lonRad + x / (earthRadius * Math.cos(latRad));
        
        return new GeoPoint(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad));
    }
    
    public void calibrateWithGPS(Location location) {
        if (location == null || !location.hasAccuracy() || location.getAccuracy() > 20) {
            return;
        }
        
        if (startLocation == null) {
            startLocation = location;
            lastGPSLocation = location;
            
            if (currentTrip != null) {
                currentTrip.addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
            }
            return;
        }
        
        gpsCalibrator.addCalibrationPoint(
            location,
            currentX,
            currentY
        );
        
        double[] corrected = gpsCalibrator.correctPosition(currentX, currentY, currentHeading);
        currentX = corrected[0];
        currentY = corrected[1];
        
        lastGPSLocation = location;
        
        // Reset covariance under strong GPS
        positionVariance = Math.pow(location.getAccuracy(), 2);
    }
    
    public double getUncertaintyRadius() {
        return Math.sqrt(positionVariance);
    }
    
    public double getX() {
        return currentX;
    }
    
    public double getY() {
        return currentY;
    }
    
    public double getHeading() {
        return gpsCalibrator.correctHeading(currentHeading);
    }
    
    public int getStepCount() {
        if (hardwareStepAvailable) {
            return androidStepCount;
        }
        switch (stepMode) {
            case DYNAMIC:
                return dynamicStepCounter.getStepCount();
            case STATIC:
                return staticStepCounter.getStepCount();
            case ANDROID:
                return androidStepCount;
            default:
                return stepCounter.getStepCount();
        }
    }
    
    public int getDynamicStepCount() {
        return dynamicStepCounter.getStepCount();
    }
    
    public int getStaticStepCount() {
        return staticStepCounter.getStepCount();
    }
    
    public int getAndroidStepCount() {
        return androidStepCount;
    }
    
    public double getDistance() {
        return totalDistance;
    }
    
    public double getDistanceFromGPS(Location gpsLocation) {
        if (startLocation == null || gpsLocation == null) {
            return -1;
        }
        return startLocation.distanceTo(gpsLocation);
    }
    
    public boolean isCalibrated() {
        return gpsCalibrator.isCalibrated();
    }
    
    public int getCalibrationPoints() {
        return gpsCalibrator.getCalibrationPointCount();
    }
    
    public void setStrideLength(double meters) {
        stepCounter.setStrideLength(meters);
    }
    
    public double getStrideLength() {
        return stepCounter.getStrideLength();
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public Location getStartLocation() {
        return startLocation;
    }
    
    public Location getLastGPSLocation() {
        return lastGPSLocation;
    }
    
    public double getScaleFactor() {
        return gpsCalibrator.getScaleFactor();
    }
    
    public Trip getCurrentTrip() {
        return currentTrip;
    }
    
    public List<TurnEvent> getTurnEvents() {
        return new ArrayList<>(turnEvents);
    }
    
    public boolean isTurning() {
        return isTurning;
    }
    
    public double getAccumulatedHeadingChange() {
        return accumulatedHeadingChange;
    }

    private boolean useManualHeading = false;

    public void turnLeft() {
        if (!isRunning) return;
        
        useManualHeading = true;
        currentHeading -= 90.0;
        while (currentHeading < 0) currentHeading += 360;
        addManualTurnEvent(TurnEvent.TurnType.LEFT, -90.0);
    }

    public void turnRight() {
        if (!isRunning) return;
        
        useManualHeading = true;
        currentHeading += 90.0;
        while (currentHeading >= 360) currentHeading -= 360;
        addManualTurnEvent(TurnEvent.TurnType.RIGHT, 90.0);
    }

    public void turnAround() {
        if (!isRunning) return;
        
        useManualHeading = true;
        currentHeading += 180.0;
        while (currentHeading >= 360) currentHeading -= 360;
        addManualTurnEvent(TurnEvent.TurnType.UTURN, 180.0);
    }

    private void addManualTurnEvent(TurnEvent.TurnType type, double angle) {
        double lat = 0, lon = 0;
        
        if (currentTrip != null && !currentTrip.getPathPoints().isEmpty()) {
            GeoPoint lastPoint = currentTrip.getPathPoints().get(currentTrip.getPathPoints().size() - 1);
            lat = lastPoint.getLatitude();
            lon = lastPoint.getLongitude();
        }
        
        TurnEvent event = new TurnEvent(type, angle, getStepCount(), lat, lon);
        turnEvents.add(event);
        
        if (currentTrip != null) {
            currentTrip.addTurnEvent(event);
        }
    }
    
    public void setFixedHeading(double heading) {
        currentHeading = heading;
        useManualHeading = true;
    }
    
    public void resetToCompassHeading() {
        useManualHeading = false;
    }
}
