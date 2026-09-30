package padik.deadreckoning.bias;

import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;

import padik.deadreckoning.extra.ExtraFunctions;

/**
 * Estimates magnetometer hard-iron bias using ellipsoid/sphere fitting.
 *
 * The device should be rotated through many different orientations
 * during calibration.
 *
 * Output:
 * [0] X-axis hard-iron bias
 * [1] Y-axis hard-iron bias
 * [2] Z-axis hard-iron bias
 * [3] Estimated magnetic field strength
 */
public class MagneticFieldBias {

    // X^T * X
    private final double[][] XTX;

    // X^T * Y
    private final double[][] XTY;

    private int sampleCount;

    private boolean firstRun;

    private float reserveX;
    private float reserveY;
    private float reserveZ;

    private float[] bias;

    /**
     * Minimum number of samples recommended before calibration.
     *
     * This is not a strict mathematical requirement, but using
     * several hundred samples gives much better results.
     */
    private static final int MIN_SAMPLES = 100;

    public MagneticFieldBias() {

        XTX = new double[4][4];
        XTY = new double[4][1];

        sampleCount = 0;
        firstRun = true;

        reserveX = 0.0f;
        reserveY = 0.0f;
        reserveZ = 0.0f;

        bias = null;
    }

    /**
     * Adds one magnetometer sample to the calibration.
     *
     * @param rawMagneticValues magnetometer values [X, Y, Z]
     */
    public void calcBias(float[] rawMagneticValues) {

        validateInput(rawMagneticValues);

        float x;
        float y;
        float z;

        /*
         * Keep the first sample in reserve.
         *
         * This preserves the behavior of the original implementation,
         * where the most recent sample is intentionally not included
         * until the next reading arrives.
         */
        if (firstRun) {

            reserveX = rawMagneticValues[0];
            reserveY = rawMagneticValues[1];
            reserveZ = rawMagneticValues[2];

            firstRun = false;
            return;
        }

        x = reserveX;
        y = reserveY;
        z = reserveZ;

        reserveX = rawMagneticValues[0];
        reserveY = rawMagneticValues[1];
        reserveZ = rawMagneticValues[2];

        double x2 = x * x;
        double y2 = y * y;
        double z2 = z * z;

        double magnitudeSquared = x2 + y2 + z2;

        /*
         * X =
         *
         * [ x²? No ]
         *
         * For the equation:
         *
         * x² + y² + z² = Bx*x + By*y + Bz*z + C
         *
         * the design vector is:
         *
         * [x y z 1]
         */

        XTX[0][0] += x2;
        XTX[0][1] += x * y;
        XTX[0][2] += x * z;
        XTX[0][3] += x;

        XTX[1][0] += x * y;
        XTX[1][1] += y2;
        XTX[1][2] += y * z;
        XTX[1][3] += y;

        XTX[2][0] += x * z;
        XTX[2][1] += y * z;
        XTX[2][2] += z2;
        XTX[2][3] += z;

        XTX[3][0] += x;
        XTX[3][1] += y;
        XTX[3][2] += z;
        XTX[3][3] += 1.0;

        XTY[0][0] += x * magnitudeSquared;
        XTY[1][0] += y * magnitudeSquared;
        XTY[2][0] += z * magnitudeSquared;
        XTY[3][0] += magnitudeSquared;

        sampleCount++;

        // Previous result is no longer valid.
        bias = null;
    }

    /**
     * Calculates and returns the magnetometer hard-iron bias.
     *
     * @return [xBias, yBias, zBias, magneticFieldStrength]
     */
    public float[] getBias() {

        if (sampleCount < MIN_SAMPLES) {

            throw new IllegalStateException(
                    "Not enough magnetometer samples. " +
                            "Collect at least " + MIN_SAMPLES +
                            " samples before calculating bias."
            );
        }

        SimpleMatrix M_XTX = new SimpleMatrix(XTX);
        SimpleMatrix M_XTY = new SimpleMatrix(XTY);

        /*
         * Instead of explicitly calculating:
         *
         * XTX^-1
         *
         * solve:
         *
         * XTX * B = XTY
         *
         * This is generally more numerically stable.
         */

        SimpleMatrix M_B = new SimpleMatrix(4, 1);

        LinearSolverDense<org.ejml.data.DMatrixRMaj> solver =
                LinearSolverFactory_DDRM.linear(4);

        if (!solver.setA(M_XTX.getDDRM())) {

            throw new IllegalStateException(
                    "Magnetometer calibration matrix is singular. " +
                            "Rotate the device through more different orientations."
            );
        }

        solver.solve(
                M_XTY.getDDRM(),
                M_B.getDDRM()
        );

        float[][] B =
                ExtraFunctions.denseMatrixToArray(M_B.getMatrix());

        /*
         * The fitted equation is:
         *
         * x² + y² + z² =
         * B0*x + B1*y + B2*z + B3
         *
         * Therefore:
         *
         * xBias = B0 / 2
         * yBias = B1 / 2
         * zBias = B2 / 2
         */

        float xBias = B[0][0] / 2.0f;
        float yBias = B[1][0] / 2.0f;
        float zBias = B[2][0] / 2.0f;

        double fieldStrengthSquared =
                B[3][0]
                        + xBias * xBias
                        + yBias * yBias
                        + zBias * zBias;

        if (fieldStrengthSquared <= 0.0) {

            throw new IllegalStateException(
                    "Invalid magnetic field strength calculated."
            );
        }

        float magneticFieldStrength =
                (float) Math.sqrt(fieldStrengthSquared);

        bias = new float[]{
                xBias,
                yBias,
                zBias,
                magneticFieldStrength
        };

        return bias.clone();
    }

    /**
     * Returns whether enough samples have been collected.
     */
    public boolean isReady() {
        return sampleCount >= MIN_SAMPLES;
    }

    /**
     * Returns number of collected samples.
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * Returns calibration progress from 0.0 to 1.0.
     */
    public float getProgress() {

        return Math.min(
                1.0f,
                (float) sampleCount / MIN_SAMPLES
        );
    }

    /**
     * Returns previously calculated bias.
     *
     * @return bias array or null if calibration has not been calculated
     */
    public float[] getCachedBias() {

        return bias == null ? null : bias.clone();
    }

    /**
     * Resets the calibration.
     */
    public void reset() {

        for (int i = 0; i < 4; i++) {

            for (int j = 0; j < 4; j++) {
                XTX[i][j] = 0.0;
            }

            XTY[i][0] = 0.0;
        }

        sampleCount = 0;

        firstRun = true;

        reserveX = 0.0f;
        reserveY = 0.0f;
        reserveZ = 0.0f;

        bias = null;
    }

    /**
     * Validates magnetometer input.
     */
    private void validateInput(float[] values) {

        if (values == null || values.length < 3) {

            throw new IllegalArgumentException(
                    "Magnetometer data must contain at least 3 values."
            );
        }

        for (int i = 0; i < 3; i++) {

            if (Float.isNaN(values[i]) ||
                    Float.isInfinite(values[i])) {

                throw new IllegalArgumentException(
                        "Magnetometer data contains invalid values."
                );
            }
        }
    }
}