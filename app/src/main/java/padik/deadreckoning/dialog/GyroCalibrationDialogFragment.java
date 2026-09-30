package padik.deadreckoning.dialog;

import android.app.Dialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import padik.deadreckoning.R;
import padik.deadreckoning.bias.GyroscopeBias;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.filewriting.DataFileWriter;

public class GyroCalibrationDialogFragment
        extends DialogFragment
        implements SensorEventListener {

    /*
     * Number of samples ignored after pressing
     * "Start Calibration".
     *
     * This gives the user/device a short settling period.
     */
    public static final int WAIT_COUNTER = 100;

    private static final int CALIBRATION_SAMPLES = 600;

    private static final String FOLDER_NAME =
            "Dead_Reckoning/Calibration_Fragment";

    private static final String DATA_FILE_NAME =
            "Gyroscope_Uncalibrated";

    private static final String DATA_FILE_HEADING =
            "Gyroscope_Uncalibrated\n" +
                    "t;uGx;uGy;uGz;xDrift;yDrift;zDrift";

    private SensorManager sensorManager;

    private Sensor sensorGyroscope;

    private GyroscopeBias gyroscopeBias;

    private DataFileWriter dataFileWriter;

    private Handler handler;

    private int waitCounter;

    private long startTime;

    private boolean calibrationRunning;

    private boolean calibrationComplete;

    private float[] finalBias;


    /**
     * Sets the Handler used to notify the parent Activity.
     *
     * Message 1 means calibration completed.
     */
    public void setHandler(Handler handler) {
        this.handler = handler;
    }


    /**
     * Creates the dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable Bundle savedInstanceState
    ) {

        /*
         * Reset calibration state.
         */
        waitCounter = 0;
        startTime = 0;

        calibrationRunning = false;
        calibrationComplete = false;

        finalBias = null;

        /*
         * Create bias calculator.
         */
        gyroscopeBias =
                new GyroscopeBias(CALIBRATION_SAMPLES);

        /*
         * Get SensorManager.
         */
        Context context = requireContext();

        sensorManager =
                (SensorManager)
                        context.getSystemService(
                                Context.SENSOR_SERVICE
                        );

        /*
         * Prefer the uncalibrated gyroscope because
         * we want to calculate our own bias.
         */
        sensorGyroscope =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_GYROSCOPE_UNCALIBRATED
                );

        /*
         * Create calibration data writer.
         */
        try {

            dataFileWriter =
                    new DataFileWriter(
                            FOLDER_NAME,
                            ExtraFunctions.arrayToList(
                                    new String[]{
                                            DATA_FILE_NAME
                                    }
                            ),
                            ExtraFunctions.arrayToList(
                                    new String[]{
                                            DATA_FILE_HEADING
                                    }
                            )
                    );

        } catch (IOException e) {

            e.printStackTrace();

            dataFileWriter = null;
        }

        /*
         * If the device does not have an
         * uncalibrated gyroscope, inform the user.
         */
        if (sensorGyroscope == null) {

            return new AlertDialog.Builder(context)
                    .setTitle(R.string.calibrating)
                    .setMessage(
                            "This device does not provide " +
                                    "an uncalibrated gyroscope."
                    )
                    .setPositiveButton(
                            android.R.string.ok,
                            null
                    )
                    .create();
        }

        /*
         * Main dialog.
         */
        return new AlertDialog.Builder(context)
                .setTitle(R.string.calibrating)
                .setMessage(R.string.gyro_calibration_msg)
                .setNeutralButton(
                        R.string.start_calibration,
                        null
                )
                .setNegativeButton(
                        R.string.cancel,
                        (dialog, which) -> stopCalibration()
                )
                .create();
    }


    /**
     * Called when the dialog becomes visible.
     */
    @Override
    public void onStart() {

        super.onStart();

        if (!isAdded()) {
            return;
        }

        AlertDialog dialog =
                (AlertDialog) getDialog();

        if (dialog == null) {
            return;
        }

        Button startButton =
                dialog.getButton(
                        AlertDialog.BUTTON_NEUTRAL
                );

        /*
         * Override the default button behavior.
         */
        if (startButton != null) {

            startButton.setOnClickListener(
                    v -> startCalibration()
            );
        }
    }


    /**
     * Starts gyroscope calibration.
     */
    private void startCalibration() {

        if (calibrationRunning) {
            return;
        }

        if (sensorManager == null ||
                sensorGyroscope == null) {

            return;
        }

        /*
         * Reset state.
         */
        waitCounter = 0;
        startTime = 0;

        calibrationRunning = true;
        calibrationComplete = false;

        gyroscopeBias =
                new GyroscopeBias(CALIBRATION_SAMPLES);

        /*
         * Disable start button while calibrating.
         */
        AlertDialog dialog =
                (AlertDialog) getDialog();

        if (dialog != null) {

            Button startButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_NEUTRAL
                    );

            if (startButton != null) {
                startButton.setEnabled(false);
            }
        }

        /*
         * Register sensor listener.
         */
        sensorManager.registerListener(
                this,
                sensorGyroscope,
                SensorManager.SENSOR_DELAY_FASTEST
        );

        /*
         * Update dialog message.
         */
        if (dialog != null) {

            dialog.setMessage(
                    "Keep your phone completely still.\n\n" +
                            "Calibrating gyroscope..."
            );
        }
    }


    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {
        // Not required for gyro calibration.
    }


    /**
     * Receives gyroscope data.
     */
    @Override
    public void onSensorChanged(
            SensorEvent event
    ) {

        if (!calibrationRunning) {
            return;
        }

        if (event.sensor.getType() !=
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            return;
        }

        /*
         * Start timestamp.
         */
        if (startTime == 0) {
            startTime = event.timestamp;
        }

        /*
         * Ignore initial samples.
         *
         * This prevents the button press and user's
         * hand movement from affecting calibration.
         */
        if (waitCounter < WAIT_COUNTER) {

            waitCounter++;

            return;
        }

        /*
         * Validate sensor values.
         */
        if (event.values == null ||
                event.values.length < 3) {

            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        /*
         * Reject NaN / Infinity.
         */
        if (Float.isNaN(x) ||
                Float.isNaN(y) ||
                Float.isNaN(z) ||
                Float.isInfinite(x) ||
                Float.isInfinite(y) ||
                Float.isInfinite(z)) {

            return;
        }

        /*
         * Calculate vector magnitude correctly.
         */
        double norm =
                Math.sqrt(
                        x * x +
                                y * y +
                                z * z
                );

        /*
         * During calibration the phone should be still.
         *
         * Reject unusually large movement.
         *
         * NOTE:
         * This threshold can be tuned according to
         * the sensor/device.
         */
        final double MAX_GYRO_RATE = 0.15;

        if (norm > MAX_GYRO_RATE) {
            return;
        }

        /*
         * Add sample to bias calculator.
         */
        boolean complete =
                gyroscopeBias.calcBias(
                        event.values
                );

        /*
         * Save raw sensor data.
         */
        if (dataFileWriter != null) {

            ArrayList<Float> dataValues =
                    ExtraFunctions.arrayToList(
                            event.values
                    );

            dataValues.add(
                    0,
                    (float)
                            (event.timestamp - startTime)
            );

            dataFileWriter.writeToFile(
                    DATA_FILE_NAME,
                    dataValues
            );
        }

        /*
         * Calibration completed.
         */
        if (complete) {

            finishCalibration();
        }
    }


    /**
     * Finishes calibration.
     */
    private void finishCalibration() {

        if (!calibrationRunning) {
            return;
        }

        calibrationRunning = false;
        calibrationComplete = true;

        /*
         * Get final bias.
         */
        finalBias =
                gyroscopeBias.getBias();

        /*
         * Save result.
         */
        if (dataFileWriter != null) {

            dataFileWriter.writeToFile(
                    DATA_FILE_NAME,
                    "Calculated_bias: " +
                            Arrays.toString(finalBias)
            );
        }

        /*
         * Stop sensor listener.
         */
        if (sensorManager != null) {

            sensorManager.unregisterListener(this);
        }

        /*
         * Notify parent Activity.
         */
        if (handler != null) {

            handler.sendEmptyMessage(1);
        }

        /*
         * Close dialog.
         */
        dismissAllowingStateLoss();
    }


    /**
     * Stops calibration without completing it.
     */
    private void stopCalibration() {

        calibrationRunning = false;

        if (sensorManager != null) {

            sensorManager.unregisterListener(this);
        }
    }


    /**
     * Called when Fragment is destroyed.
     *
     * Important for preventing sensor leaks.
     */
    @Override
    public void onDestroyView() {

        stopCalibration();

        super.onDestroyView();
    }


    /**
     * Returns the calculated gyro bias.
     */
    public float[] getGyroBias() {

        if (finalBias != null) {
            return finalBias.clone();
        }

        return gyroscopeBias != null
                ? gyroscopeBias.getBias()
                : new float[]{0f, 0f, 0f};
    }
}