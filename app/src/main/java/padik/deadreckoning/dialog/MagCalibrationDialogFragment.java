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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import padik.deadreckoning.R;
import padik.deadreckoning.bias.MagneticFieldBias;
import padik.deadreckoning.extra.ExtraFunctions;
import padik.deadreckoning.filewriting.DataFileWriter;

public class MagCalibrationDialogFragment
        extends DialogFragment
        implements SensorEventListener {

    private static final String FOLDER_NAME =
            "Dead_Reckoning/Calibration_Fragment";

    private static final String DATA_FILE_NAME =
            "Magnetic_Field_Uncalibrated";

    private static final String DATA_FILE_HEADING =
            "Magnetic_Field_Uncalibrated\n" +
                    "t;uMx;uMy;uMz;" +
                    "xBiasHardIron;yBiasHardIron;zBiasHardIron";

    /*
     * Recommended minimum number of samples.
     *
     * Magnetometer calibration needs many orientations,
     * not just many samples.
     */
    private static final int MIN_SAMPLES = 300;

    /*
     * Maximum number of samples we will collect.
     *
     * Calibration can be stopped earlier by the user.
     */
    private static final int MAX_SAMPLES = 2000;

    private SensorManager sensorManager;

    private Sensor sensorMagneticField;

    private MagneticFieldBias magneticFieldBias;

    private DataFileWriter dataFileWriter;

    private Handler handler;

    private long startTime;

    private boolean firstRun;

    private boolean isRunning;

    private boolean calibrationComplete;


    /**
     * Set Handler used to notify the parent Activity.
     */
    public void setHandler(Handler handler) {
        this.handler = handler;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable Bundle savedInstanceState
    ) {

        startTime = 0;

        firstRun = true;

        isRunning = false;

        calibrationComplete = false;

        /*
         * Create fresh calibration calculator.
         */
        magneticFieldBias =
                new MagneticFieldBias();

        /*
         * Create data writer.
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
         * Get SensorManager.
         */
        Context context = requireContext();

        sensorManager =
                (SensorManager)
                        context.getSystemService(
                                Context.SENSOR_SERVICE
                        );

        /*
         * Prefer uncalibrated magnetometer.
         */
        sensorMagneticField =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
                );

        /*
         * If uncalibrated magnetometer isn't available,
         * fall back to the calibrated magnetometer.
         */
        if (sensorMagneticField == null) {

            sensorMagneticField =
                    sensorManager.getDefaultSensor(
                            Sensor.TYPE_MAGNETIC_FIELD
                    );
        }

        /*
         * Sensor not available.
         */
        if (sensorMagneticField == null) {

            return new AlertDialog.Builder(context)
                    .setTitle(R.string.calibrating)
                    .setMessage(
                            "This device does not have " +
                                    "a magnetometer."
                    )
                    .setPositiveButton(
                            android.R.string.ok,
                            null
                    )
                    .create();
        }

        /*
         * Main calibration dialog.
         */
        return new AlertDialog.Builder(context)
                .setTitle(R.string.calibrating)
                .setMessage(
                        R.string.mag_calibration_msg
                )
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
     * Called when dialog becomes visible.
     */
    @Override
    public void onStart() {

        super.onStart();

        AlertDialog dialog =
                (AlertDialog) getDialog();

        if (dialog == null) {
            return;
        }

        Button calibrationButton =
                dialog.getButton(
                        AlertDialog.BUTTON_NEUTRAL
                );

        if (calibrationButton == null) {
            return;
        }

        /*
         * Override default neutral button behavior.
         */
        calibrationButton.setOnClickListener(
                v -> {

                    if (!isRunning) {

                        startCalibration(
                                calibrationButton
                        );

                    } else {

                        finishCalibration();
                    }
                }
        );
    }


    /**
     * Starts magnetometer calibration.
     */
    private void startCalibration(
            Button calibrationButton
    ) {

        if (sensorManager == null ||
                sensorMagneticField == null) {

            return;
        }

        /*
         * Reset state.
         */
        magneticFieldBias =
                new MagneticFieldBias();

        startTime = 0;

        firstRun = true;

        calibrationComplete = false;

        isRunning = true;

        /*
         * Start collecting sensor data.
         */
        sensorManager.registerListener(
                this,
                sensorMagneticField,
                SensorManager.SENSOR_DELAY_FASTEST
        );

        /*
         * Change button.
         */
        calibrationButton.setText(
                R.string.stop_calibration
        );

        /*
         * Update instructions.
         */
        AlertDialog dialog =
                (AlertDialog) getDialog();

        if (dialog != null) {

            dialog.setMessage(
                    "Rotate the phone slowly through " +
                            "many different orientations.\n\n" +
                            "Try to cover all directions."
            );
        }
    }


    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {
        /*
         * We don't directly use accuracy here.
         */
    }


    /**
     * Receives magnetometer data.
     */
    @Override
    public void onSensorChanged(
            SensorEvent event
    ) {

        if (!isRunning) {
            return;
        }

        if (event.sensor.getType() !=
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED &&
                event.sensor.getType() !=
                        Sensor.TYPE_MAGNETIC_FIELD) {

            return;
        }

        if (event.values == null ||
                event.values.length < 3) {

            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        /*
         * Reject invalid sensor values.
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
         * Set starting timestamp.
         */
        if (firstRun) {

            startTime = event.timestamp;

            firstRun = false;
        }

        /*
         * Add sample to calibration.
         */
        magneticFieldBias.calcBias(
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
         * Automatically stop if maximum sample count
         * has been reached.
         */
        if (magneticFieldBias.getSampleCount()
                >= MAX_SAMPLES) {

            finishCalibration();
        }
    }


    /**
     * Stops sensor collection and calculates
     * the final hard-iron bias.
     */
    private void finishCalibration() {

        if (!isRunning) {
            return;
        }

        /*
         * Check whether enough samples exist.
         */
        if (!magneticFieldBias.isReady()) {

            AlertDialog dialog =
                    (AlertDialog) getDialog();

            if (dialog != null) {

                dialog.setMessage(
                        "Not enough calibration data.\n\n" +
                                "Please collect at least " +
                                MIN_SAMPLES +
                                " samples while rotating " +
                                "the phone through different orientations."
                );
            }

            return;
        }

        /*
         * Stop sensor.
         */
        stopSensor();

        try {

            /*
             * Calculate hard-iron bias.
             */
            float[] bias =
                    magneticFieldBias.getBias();

            calibrationComplete = true;

            /*
             * Save calibration result.
             */
            if (dataFileWriter != null) {

                dataFileWriter.writeToFile(
                        DATA_FILE_NAME,
                        "Calculated_bias: " +
                                Arrays.toString(bias)
                );
            }

            /*
             * Notify parent Activity.
             */
            if (handler != null) {

                handler.sendEmptyMessage(0);
            }

            /*
             * Close dialog.
             */
            dismissAllowingStateLoss();

        } catch (IllegalStateException e) {

            /*
             * Matrix could not be solved.
             *
             * Usually happens when the phone was not
             * rotated through sufficiently different
             * orientations.
             */
            AlertDialog dialog =
                    (AlertDialog) getDialog();

            if (dialog != null) {

                dialog.setMessage(
                        "Calibration failed.\n\n" +
                                "Rotate the phone through many " +
                                "different orientations and try again."
                );
            }

            isRunning = false;
        }
    }


    /**
     * Stops the sensor listener.
     */
    private void stopSensor() {

        if (sensorManager != null) {

            sensorManager.unregisterListener(this);
        }

        isRunning = false;
    }


    /**
     * Stops calibration without saving a result.
     */
    private void stopCalibration() {

        stopSensor();
    }


    /**
     * Clean up sensor listener when Fragment is destroyed.
     */
    @Override
    public void onDestroyView() {

        stopSensor();

        super.onDestroyView();
    }


    /**
     * Returns the calculated magnetometer bias.
     *
     * Array:
     * [0] X hard-iron bias
     * [1] Y hard-iron bias
     * [2] Z hard-iron bias
     * [3] magnetic field strength
     */
    public float[] getMagBias() {

        if (magneticFieldBias == null) {

            return new float[]{
                    0f,
                    0f,
                    0f,
                    0f
            };
        }

        return magneticFieldBias.getBias();
    }
}