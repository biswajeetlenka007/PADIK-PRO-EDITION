package padik.deadreckoning.calibration;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AutoFrameCalibration determines the orientation of the device relative to the vehicle.
 * 
 * Coordinate Frames:
 * - Device Frame (d): Standard Android (X-right, Y-up, Z-out)
 * - Navigation Frame (n): NED (North, East, Down)
 * - Vehicle Frame (v): X-forward, Y-right, Z-down
 * 
 * Implementation uses Gravity estimation and PCA while stationary to find the 'Down' axis.
 */
public class AutoFrameCalibration {

    private final List<float[]> accelSamples = new ArrayList<>();
    private final int maxSamples;
    private boolean isFinished = false;

    // Threshold for stationary detection (variance of acceleration magnitude)
    private static final double STATIONARY_VAR_THRESHOLD = 0.05; 
    private static final double G_NOMINAL = 9.81;

    public AutoFrameCalibration(int sampleCount) {
        this.maxSamples = sampleCount;
    }

    public boolean addSample(float[] accel) {
        if (isFinished || accelSamples.size() >= maxSamples) return true;
        accelSamples.add(accel.clone());
        return accelSamples.size() >= maxSamples;
    }

    public int getProgress() {
        return accelSamples.size();
    }

    public CalibrationResult calculate() {
        if (accelSamples.size() < maxSamples / 2) {
            return new CalibrationResult(false, "Not enough samples");
        }

        // 1. Calculate Mean Gravity Vector (g_device)
        double sumX = 0, sumY = 0, sumZ = 0;
        for (float[] s : accelSamples) {
            sumX += s[0];
            sumY += s[1];
            sumZ += s[2];
        }
        double meanX = sumX / accelSamples.size();
        double meanY = sumY / accelSamples.size();
        double meanZ = sumZ / accelSamples.size();
        
        double magnitude = Math.sqrt(meanX * meanX + meanY * meanY + meanZ * meanZ);
        
        // Check if magnitude is reasonable for gravity
        if (Math.abs(magnitude - G_NOMINAL) > 1.5) {
            return new CalibrationResult(false, "Invalid gravity magnitude: " + String.format(Locale.US, "%.2f", magnitude));
        }

        // 2. Variance check to ensure stationary
        double varSum = 0;
        for (float[] s : accelSamples) {
            double mag = Math.sqrt(s[0]*s[0] + s[1]*s[1] + s[2]*s[2]);
            varSum += Math.pow(mag - magnitude, 2);
        }
        double variance = varSum / accelSamples.size();
        if (variance > STATIONARY_VAR_THRESHOLD) {
            return new CalibrationResult(false, "Device was not stationary (variance too high)");
        }

        // 3. Eigen Decomposition on the second moment matrix (uncentered)
        // This identifies the dominant direction which, in a stationary 
        // setup, is the gravity vector.
        DMatrixRMaj secondMoment = new DMatrixRMaj(3, 3);
        for (float[] s : accelSamples) {
            secondMoment.add(0, 0, s[0] * s[0]);
            secondMoment.add(0, 1, s[0] * s[1]);
            secondMoment.add(0, 2, s[0] * s[2]);
            secondMoment.add(1, 1, s[1] * s[1]);
            secondMoment.add(1, 2, s[1] * s[2]);
            secondMoment.add(2, 2, s[2] * s[2]);
        }
        secondMoment.set(1, 0, secondMoment.get(0, 1));
        secondMoment.set(2, 0, secondMoment.get(0, 2));
        secondMoment.set(2, 1, secondMoment.get(1, 2));
        for (int i = 0; i < 9; i++) secondMoment.data[i] /= accelSamples.size();

        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(3, true);
        if (!eig.decompose(secondMoment)) {
            return new CalibrationResult(false, "Eigen decomposition failed");
        }

        // The eigenvector corresponding to the largest eigenvalue is the gravity axis
        int largestIndex = 0;
        double maxVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            if (eig.getEigenvalue(i).getReal() > maxVal) {
                maxVal = eig.getEigenvalue(i).getReal();
                largestIndex = i;
            }
        }
        DMatrixRMaj vG = eig.getEigenVector(largestIndex);
        
        // Resolve sign using the mean vector
        double dot = vG.get(0) * meanX + vG.get(1) * meanY + vG.get(2) * meanZ;
        double sign = dot >= 0 ? 1.0 : -1.0;
        
        double downX = (vG.get(0) * sign);
        double downY = (vG.get(1) * sign);
        double downZ = (vG.get(2) * sign);

        // Represent device-to-vehicle rotation (R_vd)
        // Since we only have gravity, we align Z_v with gravity (Down).
        // Yaw remains ambiguous. We'll set a default "forward" as the projection
        // of device Y on the horizontal plane, or similar, until external yaw is provided.
        
        isFinished = true;
        return new CalibrationResult(true, downX, downY, downZ, magnitude, variance);
    }

    public static class CalibrationResult {
        public final boolean success;
        public final String errorMessage;
        public final double gX, gY, gZ; // Unit gravity vector in device frame
        public final double magnitude;
        public final double variance;
        public DMatrixRMaj rotationMatrix; // Device to Vehicle rotation

        public CalibrationResult(boolean success, String error) {
            this.success = success;
            this.errorMessage = error;
            this.gX = gY = gZ = 0;
            this.magnitude = 0;
            this.variance = 0;
            this.rotationMatrix = null;
        }

        public CalibrationResult(boolean success, double gx, double gy, double gz, double mag, double var) {
            this.success = success;
            this.errorMessage = "";
            this.gX = gx;
            this.gY = gy;
            this.gZ = gz;
            this.magnitude = mag;
            this.variance = var;
            
            // Create a preliminary rotation matrix aligning Z with gravity
            this.rotationMatrix = calculateRotationMatrix(gx, gy, gz);
        }

        public void setYawReference(double yawDegrees) {
            if (rotationMatrix == null) return;
            
            // Apply a rotation around the vehicle Z-axis (which is now aligned with gravity)
            double yawRad = Math.toRadians(yawDegrees);
            DMatrixRMaj R_yaw = new DMatrixRMaj(3, 3);
            R_yaw.set(0, 0, Math.cos(yawRad));
            R_yaw.set(0, 1, -Math.sin(yawRad));
            R_yaw.set(1, 0, Math.sin(yawRad));
            R_yaw.set(1, 1, Math.cos(yawRad));
            R_yaw.set(2, 2, 1.0);
            
            DMatrixRMaj result = new DMatrixRMaj(3, 3);
            CommonOps_DDRM.mult(rotationMatrix, R_yaw, result);
            this.rotationMatrix = result;
        }

        private DMatrixRMaj calculateRotationMatrix(double gx, double gy, double gz) {
            // Z_v = [gx, gy, gz] (Down in device frame)
            // Need to find X_v and Y_v to complete the basis.
            // Since Yaw is ambiguous, we pick an arbitrary X_v perpendicular to Z_v.
            
            DMatrixRMaj zv = new DMatrixRMaj(3, 1, true, gx, gy, gz);
            DMatrixRMaj temp = new DMatrixRMaj(3, 1, true, 0, 1, 0);
            if (Math.abs(gx) < 0.1 && Math.abs(gz) < 0.1) {
                temp.set(0, 0, 1); // Avoid singularity if gravity is aligned with Y
            }
            
            // Y_v = Z_v x temp (normalized)
            double yvx = zv.get(1) * temp.get(2) - zv.get(2) * temp.get(1);
            double yvy = zv.get(2) * temp.get(0) - zv.get(0) * temp.get(2);
            double yvz = zv.get(0) * temp.get(1) - zv.get(1) * temp.get(0);
            double ymag = Math.sqrt(yvx*yvx + yvy*yvy + yvz*yvz);
            yvx /= ymag; yvy /= ymag; yvz /= ymag;

            // X_v = Y_v x Z_v
            double xvx = yvy * zv.get(2) - yvz * zv.get(1);
            double xvy = yvz * zv.get(0) - yvx * zv.get(2);
            double xvz = yvx * zv.get(1) - yvy * zv.get(0);

            // R_vd (Device to Vehicle) columns are [X_v, Y_v, Z_v]
            DMatrixRMaj R = new DMatrixRMaj(3, 3);
            R.set(0, 0, xvx); R.set(0, 1, yvx); R.set(0, 2, gx);
            R.set(1, 0, xvy); R.set(1, 1, yvy); R.set(1, 2, gy);
            R.set(2, 0, xvz); R.set(2, 1, yvz); R.set(2, 2, gz);
            
            return R;
        }
    }
}
