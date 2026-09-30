package padik.deadreckoning.sensor;

import android.location.Location;
import java.util.ArrayList;
import java.util.List;

public class GPSCalibrator {
    
    private List<Location> calibrationPoints = new ArrayList<>();
    private List<Double> estimatedXs = new ArrayList<>();
    private List<Double> estimatedYs = new ArrayList<>();
    
    private boolean isCalibrated = false;
    private int calibrationCount = 0;
    
    private double scaleFactorX = 1.0;
    private double scaleFactorY = 1.0;
    private double headingBias = 0.0;
    
    public static final int MIN_CALIBRATION_POINTS = 3;
    public static final int MAX_CALIBRATION_POINTS = 20;
    
    public void addCalibrationPoint(Location gpsLocation, double estimatedX, double estimatedY) {
        if (gpsLocation == null) return;
        
        calibrationPoints.add(gpsLocation);
        estimatedXs.add(estimatedX);
        estimatedYs.add(estimatedY);
        
        if (calibrationPoints.size() > MAX_CALIBRATION_POINTS) {
            calibrationPoints.remove(0);
            estimatedXs.remove(0);
            estimatedYs.remove(0);
        }
        
        if (calibrationPoints.size() >= MIN_CALIBRATION_POINTS) {
            recalculateCalibration();
        }
    }
    
    private void recalculateCalibration() {
        int size = calibrationPoints.size();
        if (size < 2) return;
        
        Location lastGps = calibrationPoints.get(size - 1);
        Location prevGps = calibrationPoints.get(size - 2);
        
        double lastX = estimatedXs.get(size - 1);
        double lastY = estimatedYs.get(size - 1);
        double prevX = estimatedXs.get(size - 2);
        double prevY = estimatedYs.get(size - 2);
        
        double gpsDistance = lastGps.distanceTo(prevGps);
        double estimatedDistance = Math.sqrt(
            Math.pow(lastX - prevX, 2) + 
            Math.pow(lastY - prevY, 2)
        );
        
        if (estimatedDistance > 0.5) {
            double currentScale = gpsDistance / estimatedDistance;
            // Simple moving average to smooth scale factor
            scaleFactorX = (scaleFactorX * 0.8) + (currentScale * 0.2);
            scaleFactorY = scaleFactorX;
        }
        
        headingBias = calculateHeadingBias();
        
        calibrationCount++;
        isCalibrated = calibrationCount >= MIN_CALIBRATION_POINTS;
    }
    
    private double calculateHeadingBias() {
        int size = calibrationPoints.size();
        if (size < 2) return headingBias;
        
        Location last = calibrationPoints.get(size - 1);
        Location prev = calibrationPoints.get(size - 2);
        
        double lastX = estimatedXs.get(size - 1);
        double lastY = estimatedYs.get(size - 1);
        double prevX = estimatedXs.get(size - 2);
        double prevY = estimatedYs.get(size - 2);
        
        double gpsBearing = prev.bearingTo(last); // bearing from prev to last
        double estimatedBearing = Math.toDegrees(Math.atan2(lastX - prevX, lastY - prevY));
        
        double diff = gpsBearing - estimatedBearing;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        // Blend with existing bias
        return (headingBias * 0.9) + (diff * 0.1);
    }
    
    public double[] correctPosition(double estimatedX, double estimatedY, double heading) {
        if (!isCalibrated) {
            return new double[]{estimatedX, estimatedY};
        }
        
        double correctedX = estimatedX * scaleFactorX;
        double correctedY = estimatedY * scaleFactorY;
        
        return new double[]{correctedX, correctedY};
    }
    
    public double correctHeading(double heading) {
        if (!isCalibrated) return heading;
        
        double corrected = heading + headingBias;
        while (corrected > 180) corrected -= 360;
        while (corrected < -180) corrected += 360;
        
        return corrected;
    }
    
    public void resetCalibration() {
        calibrationPoints.clear();
        estimatedXs.clear();
        estimatedYs.clear();
        isCalibrated = false;
        calibrationCount = 0;
        scaleFactorX = 1.0;
        scaleFactorY = 1.0;
        headingBias = 0.0;
    }
    
    public boolean isCalibrated() {
        return isCalibrated;
    }
    
    public int getCalibrationPointCount() {
        return calibrationPoints.size();
    }
    
    public Location getLastKnownLocation() {
        if (calibrationPoints.isEmpty()) return null;
        return calibrationPoints.get(calibrationPoints.size() - 1);
    }
    
    public double getScaleFactor() {
        return (scaleFactorX + scaleFactorY) / 2.0;
    }
}
