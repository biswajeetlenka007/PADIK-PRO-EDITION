package padik.deadreckoning.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.calibration.AutoFrameCalibration;

public class AutoFrameCalibrationDialogFragment extends DialogFragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private AutoFrameCalibration calibration;
    
    private TextView textStatus;
    private ProgressBar progressBar;
    private Button buttonAction;
    
    private boolean isRecording = false;
    private static final int SAMPLE_COUNT = 300; // ~6 seconds at GAME rate

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_auto_frame_calib, null);
        
        textStatus = view.findViewById(R.id.textCalibStatus);
        progressBar = view.findViewById(R.id.progressCalib);
        buttonAction = view.findViewById(R.id.buttonCalibAction);
        
        progressBar.setMax(SAMPLE_COUNT);
        
        buttonAction.setOnClickListener(v -> {
            if (!isRecording) {
                startCalibration();
            } else {
                dismiss();
            }
        });

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        return new AlertDialog.Builder(requireContext())
                .setTitle("Auto-Frame Calibration")
                .setView(view)
                .setNegativeButton("Cancel", (dialog, which) -> stopRecording())
                .create();
    }

    private void startCalibration() {
        calibration = new AutoFrameCalibration(SAMPLE_COUNT);
        isRecording = true;
        buttonAction.setEnabled(false);
        textStatus.setText("Keep phone stationary...");
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopRecording() {
        isRecording = false;
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording) return;

        if (calibration.addSample(event.values)) {
            finishCalibration();
        }
        
        progressBar.setProgress(calibration.getProgress());
    }

    private void finishCalibration() {
        stopRecording();
        AutoFrameCalibration.CalibrationResult result = calibration.calculate();
        
        if (result.success) {
            saveResult(result);
            textStatus.setText(String.format(Locale.US, "Success!\nGravity: %.2f m/s²\nVariance: %.4f", result.magnitude, result.variance));
            buttonAction.setText("Close");
            buttonAction.setEnabled(true);
            Toast.makeText(getContext(), "Mounting calibration saved", Toast.LENGTH_SHORT).show();
        } else {
            textStatus.setText("Failed: " + result.errorMessage);
            buttonAction.setText("Retry");
            buttonAction.setEnabled(true);
            isRecording = false;
        }
    }

    private void saveResult(AutoFrameCalibration.CalibrationResult result) {
        SharedPreferences prefs = requireContext().getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putFloat("mount_gx", (float) result.gX);
        editor.putFloat("mount_gy", (float) result.gY);
        editor.putFloat("mount_gz", (float) result.gZ);
        editor.putFloat("mount_mag", (float) result.magnitude);
        editor.putBoolean("mount_calibrated", true);
        
        // Save rotation matrix flattened
        if (result.rotationMatrix != null) {
            for (int i = 0; i < 9; i++) {
                editor.putFloat("mount_r" + i, (float) result.rotationMatrix.data[i]);
            }
        }
        
        editor.apply();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onPause() {
        super.onPause();
        stopRecording();
    }
}
