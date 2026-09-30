package padik.deadreckoning.sensor;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import padik.deadreckoning.model.IMUSample;
import padik.deadreckoning.model.GNSSSample;
import padik.deadreckoning.model.INSState;

/**
 * 15-state Error-State Extended Kalman Filter (ES-EKF).
 * 
 * State vector (error state delta_x):
 * 0-2:   Position Error (NED) [m]
 * 3-5:   Velocity Error (NED) [m/s]
 * 6-8:   Attitude Error (NED-frame small angles) [rad]
 * 9-11:  Accelerometer Bias Error (Body) [m/s^2]
 * 12-14: Gyroscope Bias Error (Body) [rad/s]
 * 
 * Nominal State:
 * Position (p_n), Velocity (v_n), Attitude (q_nb), Accel Bias (b_a), Gyro Bias (b_g)
 */
public class ExtendedKalmanFilter {

    // Matrices
    private DMatrixRMaj P; // Covariance matrix (15x15)
    private DMatrixRMaj Q; // Process noise covariance (15x15)
    private DMatrixRMaj F; // State transition matrix (15x15)
    
    // Nominal State
    private INSState nominalState;
    private double[] accelBias = {0, 0, 0};
    private double[] gyroBias = {0, 0, 0};
    
    // Constants
    private static final double G_VAL = 9.80665;
    
    // Noise Parameters (Configurable)
    private double sigma_accel = 0.1;      // Accel noise [m/s^2]
    private double sigma_gyro = 0.01;       // Gyro noise [rad/s]
    private double sigma_accel_bias = 0.001; // Accel bias random walk [m/s^3]
    private double sigma_gyro_bias = 0.0001; // Gyro bias random walk [rad/s^2]
    
    // Measurement Variances
    private double var_ai_speed = 0.25;      // [m/s]^2 - placeholder to be tuned
    private double var_zupt = 0.01;          // [m/s]^2 - Stationary velocity uncertainty
    private double var_nhc = 0.1;           // [m/s]^2 - Lateral/Vertical constraint uncertainty
    
    // R_vb: Vehicle to Body (Device) mounting matrix
    private DMatrixRMaj R_vb = null;

    public ExtendedKalmanFilter() {
        P = new DMatrixRMaj(15, 15);
        Q = new DMatrixRMaj(15, 15);
        F = new DMatrixRMaj(15, 15);
        nominalState = new INSState();
        
        initializeCovariance();
    }

    private void initializeCovariance() {
        // Initial uncertainties
        for (int i = 0; i < 3; i++) P.set(i, i, 10.0 * 10.0);       // Pos: 10m
        for (int i = 3; i < 6; i++) P.set(i, i, 1.0 * 1.0);         // Vel: 1m/s
        for (int i = 6; i < 9; i++) P.set(i, i, Math.toRadians(5) * Math.toRadians(5)); // Att: 5 deg
        for (int i = 9; i < 12; i++) P.set(i, i, 0.1 * 0.1);        // Accel Bias: 0.1 m/s^2
        for (int i = 12; i < 15; i++) P.set(i, i, 0.01 * 0.01);     // Gyro Bias: 0.01 rad/s
    }

    /**
     * Prediction step using IMU measurement.
     */
    public void predict(IMUSample imu, double dt) {
        if (!nominalState.isInitialized || dt <= 0 || dt > 0.5) return;

        // 1. Nominal State Integration (IMU Mechanization)
        // 1.1 Correct IMU with biases
        double ax = imu.getAx() - accelBias[0];
        double ay = imu.getAy() - accelBias[1];
        double az = imu.getAz() - accelBias[2];
        
        double wx = imu.getGx() - gyroBias[0];
        double wy = imu.getGy() - gyroBias[1];
        double wz = imu.getGz() - gyroBias[2];

        // 1.2 Rotation Matrix R_nb from nominal quaternion
        double[][] R_nb_arr = quaternionToMatrix(nominalState.quaternion);
        DMatrixRMaj R_nb = new DMatrixRMaj(R_nb_arr);

        // 1.3 Velocity update: v_k+1 = v_k + (R_nb * f_b + g_n) * dt
        double[] f_n = rotateVector(nominalState.quaternion, new double[]{ax, ay, az});
        nominalState.velNorth += f_n[0] * dt;
        nominalState.velEast  += f_n[1] * dt;
        nominalState.velDown  += (f_n[2] - G_VAL) * dt;

        // 1.4 Position update: p_k+1 = p_k + v_k * dt
        nominalState.posNorth += nominalState.velNorth * dt;
        nominalState.posEast  += nominalState.velEast  * dt;
        nominalState.posDown  += nominalState.velDown  * dt;

        // 1.5 Attitude update (Quaternions)
        updateQuaternion(nominalState.quaternion, wx, wy, wz, dt);

        // 2. Covariance Propagation
        // 2.1 Construct Transition Matrix F (15x15)
        // F = I + A*dt
        CommonOps_DDRM.setIdentity(F);
        
        // dPos = dVel * dt
        for (int i = 0; i < 3; i++) F.set(i, i + 3, dt);
        
        // dVel = -[R*f_b]x * dTheta * dt + R * dAccBias * dt
        DMatrixRMaj fx = skewSymmetric(f_n);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                F.set(3 + r, 6 + c, -fx.get(r, c) * dt);
                F.set(3 + r, 9 + c, R_nb.get(r, c) * dt);
            }
        }
        
        // dTheta = -R * dGyroBias * dt
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                F.set(6 + r, 12 + c, -R_nb.get(r, c) * dt);
            }
        }

        // 2.2 Construct Process Noise Q
        updateProcessNoise(dt);

        // 2.3 Propagate Covariance: P = F*P*F' + Q
        DMatrixRMaj P_next = new DMatrixRMaj(15, 15);
        DMatrixRMaj FP = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.mult(F, P, FP);
        CommonOps_DDRM.multTransB(FP, F, P_next);
        CommonOps_DDRM.addEquals(P_next, Q);
        this.P = P_next;
    }

    public void predictKinematic(IMUSample imu, double dt, double v, double headingDeg) {
        if (!nominalState.isInitialized || dt <= 0 || dt > 0.5) return;

        // Kinematic Velocity Integration (AI-DRIVE)
        double headingRad = Math.toRadians(headingDeg);
        nominalState.velNorth = v * Math.cos(headingRad);
        nominalState.velEast  = v * Math.sin(headingRad);
        nominalState.velDown  = 0.0; // Vehicles stay on ground

        // Single integration for position
        nominalState.posNorth += nominalState.velNorth * dt;
        nominalState.posEast  += nominalState.velEast  * dt;
        
        // Update Attitude (Quaternions) with Gyro for the state consistency
        double wx = imu.getGx() - gyroBias[0];
        double wy = imu.getGy() - gyroBias[1];
        double wz = imu.getGz() - gyroBias[2];
        updateQuaternion(nominalState.quaternion, wx, wy, wz, dt);

        // Covariance Propagation
        CommonOps_DDRM.setIdentity(F);
        
        // dPos = dVel * dt
        for (int i = 0; i < 3; i++) F.set(i, i + 3, dt);
        
        // Update Q
        updateProcessNoise(dt);
        
        // P = F*P*F^T + Q
        DMatrixRMaj FP = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.mult(F, P, FP);
        DMatrixRMaj FPFt = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.multTransB(FP, F, FPFt);
        CommonOps_DDRM.add(FPFt, Q, P);
    }

    /**
     * Correction step using GNSS position and velocity.
     */
    public void updateGNSS(GNSSSample gnss, double pos_m_n, double pos_m_e, double pos_m_d) {
        if (!nominalState.isInitialized) return;

        boolean hasVelocity = gnss.hasSpeed() && gnss.hasBearing();
        int measDim = hasVelocity ? 6 : 3;

        // Measurement z = [p_n, p_e, p_d] or [p_n, p_e, p_d, v_n, v_e, v_d]'
        // Observation model: z = H * delta_x + n
        DMatrixRMaj H = new DMatrixRMaj(measDim, 15);
        for (int i = 0; i < 3; i++) {
            H.set(i, i, 1.0);       // Position
        }
        if (hasVelocity) {
            for (int i = 0; i < 3; i++) {
                H.set(i + 3, i + 3, 1.0); // Velocity
            }
        }

        // Innovation y = z - h(x)
        DMatrixRMaj y = new DMatrixRMaj(measDim, 1);
        y.set(0, 0, pos_m_n - nominalState.posNorth);
        y.set(1, 0, pos_m_e - nominalState.posEast);
        y.set(2, 0, pos_m_d - nominalState.posDown);
        
        if (hasVelocity) {
            double v_n = gnss.getSpeed() * Math.cos(Math.toRadians(gnss.getBearing()));
            double v_e = gnss.getSpeed() * Math.sin(Math.toRadians(gnss.getBearing()));
            y.set(3, 0, v_n - nominalState.velNorth);
            y.set(4, 0, v_e - nominalState.velEast);
            y.set(5, 0, 0 - nominalState.velDown); 
        }

        // Measurement noise covariance R
        DMatrixRMaj R = new DMatrixRMaj(measDim, measDim);
        double var_pos = Math.pow(Math.max(gnss.getAccuracy(), 2.0), 2);
        for (int i = 0; i < 3; i++) R.set(i, i, var_pos);
        if (hasVelocity) {
            double var_vel = Math.pow(1.0, 2); 
            for (int i = 3; i < 6; i++) R.set(i, i, var_vel);
        }

        // Kalman Gain: K = P*H' * inv(H*P*H' + R)
        DMatrixRMaj K = calculateKalmanGain(H, R);
        if (K == null) return;

        // Correct Error State: delta_x = K * y
        DMatrixRMaj dx = new DMatrixRMaj(15, 1);
        CommonOps_DDRM.mult(K, y, dx);

        // Inject Error into Nominal State
        injectError(dx);

        // Update Covariance (Joseph Form): P = (I - KH)P(I - KH)' + KRK'
        updateCovarianceJoseph(K, H, R);
    }

    /**
     * Correction step using AI speed estimate.
     * Speed is a scalar magnitude: s = sqrt(vN^2 + vE^2 + vD^2)
     */
    public void updateWithAISpeed(double speedMps) {
        if (!nominalState.isInitialized) return;

        // Jacobian H = [0, 0, 0, dvN, dvE, dvD, 0, ..., 0] (1x15)
        double vn = nominalState.velNorth;
        double ve = nominalState.velEast;
        double vd = nominalState.velDown;
        double speed = Math.sqrt(vn*vn + ve*ve + vd*vd);
        
        DMatrixRMaj H = new DMatrixRMaj(1, 15);
        if (speed > 0.01) {
            H.set(0, 3, vn / speed);
            H.set(0, 4, ve / speed);
            H.set(0, 5, vd / speed);
        } else {
            // Speed too low for meaningful gradient, but we can still push velocity to zero
            H.set(0, 3, 1.0);
            H.set(0, 4, 1.0);
            H.set(0, 5, 1.0);
            speed = 0;
        }

        // Innovation y = z - h(x)
        DMatrixRMaj y = new DMatrixRMaj(1, 1);
        y.set(0, 0, speedMps - speed);

        // Measurement noise R
        DMatrixRMaj R = new DMatrixRMaj(1, 1);
        R.set(0, 0, var_ai_speed);

        // Kalman Gain
        DMatrixRMaj K = calculateKalmanGain(H, R);
        if (K == null) return;

        // Correct State
        DMatrixRMaj dx = new DMatrixRMaj(15, 1);
        CommonOps_DDRM.mult(K, y, dx);
        injectError(dx);

        // Update Covariance
        updateCovarianceJoseph(K, H, R);
    }

    /**
     * Correction step using Zero Velocity Update (ZUPT).
     * Constrains velocity in NED frame to zero.
     */
    public void updateWithZUPT() {
        if (!nominalState.isInitialized) return;

        // Jacobian H = [0_3, I_3, 0_3, 0_3, 0_3] (3x15)
        DMatrixRMaj H = new DMatrixRMaj(3, 15);
        for (int i = 0; i < 3; i++) H.set(i, i + 3, 1.0);

        // Innovation y = z - h(x) = [0,0,0] - [vN,vE,vD]
        DMatrixRMaj y = new DMatrixRMaj(3, 1);
        y.set(0, 0, 0 - nominalState.velNorth);
        y.set(1, 0, 0 - nominalState.velEast);
        y.set(2, 0, 0 - nominalState.velDown);

        // Measurement noise R
        DMatrixRMaj R = new DMatrixRMaj(3, 3);
        CommonOps_DDRM.setIdentity(R);
        CommonOps_DDRM.scale(var_zupt, R);

        // Kalman Gain
        DMatrixRMaj K = calculateKalmanGain(H, R);
        if (K == null) return;

        // Correct State
        DMatrixRMaj dx = new DMatrixRMaj(15, 1);
        CommonOps_DDRM.mult(K, y, dx);
        injectError(dx);

        // Update Covariance
        updateCovarianceJoseph(K, H, R);
    }

    /**
     * Correction step using Non-Holonomic Constraints (NHC).
     * Constrains lateral and vertical velocity in vehicle frame to zero.
     */
    public void updateWithNHC() {
        if (!nominalState.isInitialized || R_vb == null) return;

        double[] v_n = {nominalState.velNorth, nominalState.velEast, nominalState.velDown};
        DMatrixRMaj R_nb = new DMatrixRMaj(quaternionToMatrix(nominalState.quaternion));
        
        NonHolonomicConstraint.NHCResult res = NonHolonomicConstraint.calculate(R_vb, R_nb, v_n);

        // Measurement noise R
        DMatrixRMaj R = new DMatrixRMaj(2, 2);
        CommonOps_DDRM.setIdentity(R);
        CommonOps_DDRM.scale(var_nhc, R);

        // Kalman Gain
        DMatrixRMaj K = calculateKalmanGain(res.H, R);
        if (K == null) return;

        // Correct State
        DMatrixRMaj dx = new DMatrixRMaj(15, 1);
        CommonOps_DDRM.mult(K, res.y, dx);
        injectError(dx);

        // Update Covariance
        updateCovarianceJoseph(K, res.H, R);
    }

    public void setMountingRotation(DMatrixRMaj R_vb) {
        this.R_vb = R_vb;
    }

    private void injectError(DMatrixRMaj dx) {
        nominalState.posNorth += dx.get(0, 0);
        nominalState.posEast  += dx.get(1, 0);
        nominalState.posDown  += dx.get(2, 0);
        
        nominalState.velNorth += dx.get(3, 0);
        nominalState.velEast  += dx.get(4, 0);
        nominalState.velDown  += dx.get(5, 0);
        
        double dPhi = dx.get(6, 0);
        double dTheta = dx.get(7, 0);
        double dPsi = dx.get(8, 0);
        
        double[] q_err = {1.0, dPhi/2.0, dTheta/2.0, dPsi/2.0};
        double norm_err = Math.sqrt(q_err[0]*q_err[0] + q_err[1]*q_err[1] + q_err[2]*q_err[2] + q_err[3]*q_err[3]);
        for (int i = 0; i < 4; i++) q_err[i] /= norm_err;
        
        nominalState.quaternion = multiplyQuaternions(q_err, nominalState.quaternion);
        
        accelBias[0] += dx.get(9, 0);
        accelBias[1] += dx.get(10, 0);
        accelBias[2] += dx.get(11, 0);
        
        gyroBias[0] += dx.get(12, 0);
        gyroBias[1] += dx.get(13, 0);
        gyroBias[2] += dx.get(14, 0);
    }

    private DMatrixRMaj calculateKalmanGain(DMatrixRMaj H, DMatrixRMaj R) {
        int m = H.numRows;
        int n = H.numCols;
        DMatrixRMaj PHt = new DMatrixRMaj(n, m);
        CommonOps_DDRM.multTransB(P, H, PHt);
        DMatrixRMaj HPHt = new DMatrixRMaj(m, m);
        CommonOps_DDRM.mult(H, PHt, HPHt);
        CommonOps_DDRM.addEquals(HPHt, R);
        DMatrixRMaj invS = new DMatrixRMaj(m, m);
        if (!CommonOps_DDRM.invert(HPHt, invS)) return null;
        DMatrixRMaj K = new DMatrixRMaj(n, m);
        CommonOps_DDRM.mult(PHt, invS, K);
        return K;
    }

    private void updateCovarianceJoseph(DMatrixRMaj K, DMatrixRMaj H, DMatrixRMaj R) {
        DMatrixRMaj I = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.setIdentity(I);
        DMatrixRMaj KH = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.mult(K, H, KH);
        DMatrixRMaj IKH = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.subtract(I, KH, IKH);
        DMatrixRMaj IKHP = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.mult(IKH, P, IKHP);
        DMatrixRMaj P_new = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.multTransB(IKHP, IKH, P_new);
        DMatrixRMaj KR = new DMatrixRMaj(K.numRows, R.numCols);
        CommonOps_DDRM.mult(K, R, KR);
        DMatrixRMaj KRKt = new DMatrixRMaj(15, 15);
        CommonOps_DDRM.multTransB(KR, K, KRKt);
        CommonOps_DDRM.addEquals(P_new, KRKt);
        this.P = P_new;
    }

    private void updateProcessNoise(double dt) {
        CommonOps_DDRM.fill(Q, 0);
        for (int i = 3; i < 6; i++) Q.set(i, i, Math.pow(sigma_accel, 2) * dt);
        for (int i = 6; i < 9; i++) Q.set(i, i, Math.pow(sigma_gyro, 2) * dt);
        for (int i = 9; i < 12; i++) Q.set(i, i, Math.pow(sigma_accel_bias, 2) * dt);
        for (int i = 12; i < 15; i++) Q.set(i, i, Math.pow(sigma_gyro_bias, 2) * dt);
    }

    private DMatrixRMaj skewSymmetric(double[] v) {
        DMatrixRMaj m = new DMatrixRMaj(3, 3);
        m.set(0, 1, -v[2]); m.set(0, 2, v[1]);
        m.set(1, 0, v[2]);  m.set(1, 2, -v[0]);
        m.set(2, 0, -v[1]); m.set(2, 1, v[0]);
        return m;
    }

    private void updateQuaternion(double[] q, double wx, double wy, double wz, double dt) {
        double dw = 0.5 * (-q[1] * wx - q[2] * wy - q[3] * wz);
        double dx = 0.5 * ( q[0] * wx + q[2] * wz - q[3] * wy);
        double dy = 0.5 * ( q[0] * wy - q[1] * wz + q[3] * wx);
        double dz = 0.5 * ( q[0] * wz + q[1] * wy - q[2] * wx);
        q[0] += dw * dt; q[1] += dx * dt; q[2] += dy * dt; q[3] += dz * dt;
        double norm = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
        for (int i = 0; i < 4; i++) q[i] /= norm;
    }

    private double[] multiplyQuaternions(double[] q1, double[] q2) {
        return new double[] {
            q1[0]*q2[0] - q1[1]*q2[1] - q1[2]*q2[2] - q1[3]*q2[3],
            q1[0]*q2[1] + q1[1]*q2[0] + q1[2]*q2[3] - q1[3]*q2[2],
            q1[0]*q2[2] - q1[1]*q2[3] + q1[2]*q2[0] + q1[3]*q2[1],
            q1[0]*q2[3] + q1[1]*q2[2] - q1[2]*q2[1] + q1[3]*q2[0]
        };
    }

    private double[] rotateVector(double[] q, double[] v) {
        double vx = v[0], vy = v[1], vz = v[2];
        double qw = q[0], qx = q[1], qy = q[2], qz = q[3];
        double resX = vx*(qw*qw+qx*qx-qy*qy-qz*qz) + vy*2*(qx*qy-qw*qz) + vz*2*(qx*qz+qw*qy);
        double resY = vx*2*(qx*qy+qw*qz) + vy*(qw*qw-qx*qx+qy*qy-qz*qz) + vz*2*(qy*qz-qw*qx);
        double resZ = vx*2*(qx*qz-qw*qy) + vy*2*(qy*qz+qw*qx) + vz*(qw*qw-qx*qx-qy*qy+qz*qz);
        return new double[]{resX, resY, resZ};
    }

    private double[][] quaternionToMatrix(double[] q) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        return new double[][] {
            {1 - 2*y*y - 2*z*z, 2*x*y - 2*w*z, 2*x*z + 2*w*y},
            {2*x*y + 2*w*z, 1 - 2*x*x - 2*z*z, 2*y*z - 2*w*x},
            {2*x*z - 2*w*y, 2*y*z + 2*w*x, 1 - 2*x*x - 2*y*y}
        };
    }

    public void initialize(INSState initialState) {
        this.nominalState = new INSState(initialState);
        this.accelBias = new double[]{0,0,0};
        this.gyroBias = new double[]{0,0,0};
        initializeCovariance();
    }

    public INSState getNominalState() {
        return new INSState(nominalState);
    }
}
