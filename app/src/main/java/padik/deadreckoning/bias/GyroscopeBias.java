package padik.deadreckoning.bias;

import java.util.Arrays;

/**
 * Calculates gyroscope bias by averaging stationary sensor readings.
 *
 * IMPORTANT:
 * During calibration, the device should remain completely still.
 */
public class GyroscopeBias {

    private final int trials;
    private int runCount;

    private final float[] gyroBias;

    /**
     * Creates a gyroscope bias calculator.
     *
     * @param trials Number of samples used for calibration.
     */
    public GyroscopeBias(int trials) {

        if (trials <= 0) {
            throw new IllegalArgumentException(
                    "Number of calibration trials must be greater than 0"
            );
        }

        this.trials = trials;
        this.runCount = 0;
        this.gyroBias = new float[3];
    }

    /**
     * Adds a new gyroscope reading to the calibration.
     *
     * @param rawGyroValues Gyroscope values [X, Y, Z] in rad/s.
     * @return true when calibration is complete.
     */
    public boolean calcBias(float[] rawGyroValues) {

        if (rawGyroValues == null || rawGyroValues.length < 3) {
            throw new IllegalArgumentException(
                    "Gyroscope data must contain at least 3 values"
            );
        }

        // Do not process more samples after calibration.
        if (isCalibrated()) {
            return true;
        }

        runCount++;

        /*
         * First sample initializes the average.
         */
        if (runCount == 1) {

            gyroBias[0] = rawGyroValues[0];
            gyroBias[1] = rawGyroValues[1];
            gyroBias[2] = rawGyroValues[2];

        } else {

            /*
             * Incremental moving average:
             *
             * average = oldAverage
             *         + (newValue - oldAverage) / n
             */
            float n = runCount;

            gyroBias[0] += (rawGyroValues[0] - gyroBias[0]) / n;
            gyroBias[1] += (rawGyroValues[1] - gyroBias[1]) / n;
            gyroBias[2] += (rawGyroValues[2] - gyroBias[2]) / n;
        }

        return isCalibrated();
    }

    /**
     * Returns the calculated gyroscope bias.
     *
     * @return Bias values [X, Y, Z].
     */
    public float[] getBias() {
        return gyroBias.clone();
    }

    /**
     * Returns the number of samples collected so far.
     */
    public int getRunCount() {
        return runCount;
    }

    /**
     * Returns the total number of samples required.
     */
    public int getTrials() {
        return trials;
    }

    /**
     * Returns calibration progress from 0.0 to 1.0.
     */
    public float getProgress() {
        return Math.min(1.0f, (float) runCount / trials);
    }

    /**
     * Returns true when enough samples have been collected.
     */
    public boolean isCalibrated() {
        return runCount >= trials;
    }

    /**
     * Resets the calibration process.
     */
    public void reset() {
        runCount = 0;
        Arrays.fill(gyroBias, 0f);
    }
}