package padik.deadreckoning.activity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.Locale;

import padik.deadreckoning.R;

public class SensorsFragment extends Fragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private TextView textAccX, textAccY, textAccZ;
    private TextView textGyroX, textGyroY, textGyroZ;

    // Charts
    private GraphicalView accChartView, gyroChartView;
    private XYSeries accSeriesX, accSeriesY, accSeriesZ;
    private XYSeries gyroSeriesX, gyroSeriesY, gyroSeriesZ;
    private XYMultipleSeriesDataset accDataset, gyroDataset;
    private XYMultipleSeriesRenderer accRenderer, gyroRenderer;

    private int accCount = 0;
    private int gyroCount = 0;
    private static final int MAX_POINTS = 100;

    // Virtual Speedometer
    private TextView textVirtualSpeed;
    private LinearProgressIndicator speedProgress;
    private double currentSpeedMs = 0;
    private long lastSpeedTimestamp = 0;
    private static final double SPEED_DECAY = 0.98; // Friction to bring speed back to 0
    private static final double ACC_THRESHOLD = 0.15; // Ignore noise

    public interface OnCloseSensorsListener {
        void onCloseSensors();
    }
    private OnCloseSensorsListener closeListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnCloseSensorsListener) {
            closeListener = (OnCloseSensorsListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sensors, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textAccX = view.findViewById(R.id.textAccX);
        textAccY = view.findViewById(R.id.textAccY);
        textAccZ = view.findViewById(R.id.textAccZ);

        textGyroX = view.findViewById(R.id.textGyroX);
        textGyroY = view.findViewById(R.id.textGyroY);
        textGyroZ = view.findViewById(R.id.textGyroZ);

        textVirtualSpeed = view.findViewById(R.id.textVirtualSpeed);
        speedProgress = view.findViewById(R.id.speedProgress);

        View buttonClose = view.findViewById(R.id.buttonCloseSensors);
        buttonClose.setOnClickListener(v -> {
            if (closeListener != null) {
                closeListener.onCloseSensors();
            }
        });

        initCharts(view);

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void initCharts(View view) {
        // Init datasets
        accDataset = new XYMultipleSeriesDataset();
        accSeriesX = new XYSeries("X");
        accSeriesY = new XYSeries("Y");
        accSeriesZ = new XYSeries("Z");
        accDataset.addSeries(accSeriesX);
        accDataset.addSeries(accSeriesY);
        accDataset.addSeries(accSeriesZ);

        gyroDataset = new XYMultipleSeriesDataset();
        gyroSeriesX = new XYSeries("X");
        gyroSeriesY = new XYSeries("Y");
        gyroSeriesZ = new XYSeries("Z");
        gyroDataset.addSeries(gyroSeriesX);
        gyroDataset.addSeries(gyroSeriesY);
        gyroDataset.addSeries(gyroSeriesZ);

        // Init renderers
        accRenderer = createRenderer(true); // true for Acc bounds
        gyroRenderer = createRenderer(false); // false for Gyro bounds

        // Create Views
        accChartView = ChartFactory.getLineChartView(requireContext(), accDataset, accRenderer);
        gyroChartView = ChartFactory.getLineChartView(requireContext(), gyroDataset, gyroRenderer);

        FrameLayout layoutAccGraph = view.findViewById(R.id.layoutAccGraph);
        FrameLayout layoutGyroGraph = view.findViewById(R.id.layoutGyroGraph);

        layoutAccGraph.addView(accChartView);
        layoutGyroGraph.addView(gyroChartView);
    }

    private XYMultipleSeriesRenderer createRenderer(boolean isAcc) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(15);
        renderer.setLegendTextSize(15);
        renderer.setPointSize(0f);
        renderer.setMargins(new int[]{20, 30, 15, 20});
        
        // Transparent styling
        renderer.setMarginsColor(Color.argb(0, 255, 255, 255));
        renderer.setBackgroundColor(Color.TRANSPARENT);
        renderer.setApplyBackgroundColor(true);

        renderer.setXAxisMin(0);
        renderer.setXAxisMax(MAX_POINTS);
        if (isAcc) {
            renderer.setYAxisMin(-15);
            renderer.setYAxisMax(15);
        } else {
            renderer.setYAxisMin(-5);
            renderer.setYAxisMax(5);
        }

        renderer.setAxesColor(Color.LTGRAY);
        renderer.setLabelsColor(Color.GRAY);
        renderer.setXLabels(0);
        renderer.setYLabels(5);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.LTGRAY);
        
        renderer.setPanEnabled(false, false);
        renderer.setZoomEnabled(false, false);

        int[] colors = new int[]{Color.parseColor("#D32F2F"), Color.parseColor("#388E3C"), Color.parseColor("#1976D2")};
        for (int color : colors) {
            XYSeriesRenderer r = new XYSeriesRenderer();
            r.setColor(color);
            r.setPointStyle(PointStyle.POINT);
            r.setLineWidth(3f);
            renderer.addSeriesRenderer(r);
        }
        
        return renderer;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        lastSpeedTimestamp = 0;
        currentSpeedMs = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (getActivity() == null) return;
        
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            textAccX.setText(String.format(Locale.US, "X: %.2f", x));
            textAccY.setText(String.format(Locale.US, "Y: %.2f", y));
            textAccZ.setText(String.format(Locale.US, "Z: %.2f", z));

            accSeriesX.add(accCount, x);
            accSeriesY.add(accCount, y);
            accSeriesZ.add(accCount, z);

            if (accCount > MAX_POINTS) {
                accRenderer.setXAxisMin(accCount - MAX_POINTS);
                accRenderer.setXAxisMax(accCount);
            }
            accCount++;
            accChartView.repaint();

            // Update Virtual Speedometer
            if (lastSpeedTimestamp != 0) {
                double dt = (event.timestamp - lastSpeedTimestamp) / 1000000000.0;
                if (dt > 0 && dt < 0.5) { // Sanity check for dt
                    double magnitude = Math.sqrt(x * x + y * y + z * z);
                    
                    // Simple integration of acceleration magnitude
                    if (magnitude > ACC_THRESHOLD) {
                        currentSpeedMs += (magnitude * dt);
                    }
                    
                    currentSpeedMs *= SPEED_DECAY; // Pseudo-friction
                    if (currentSpeedMs < 0.05) currentSpeedMs = 0;
                    
                    double speedKmh = currentSpeedMs * 3.6;
                    if (textVirtualSpeed != null) {
                        textVirtualSpeed.setText(String.format(Locale.US, "%.1f", speedKmh));
                    }
                    if (speedProgress != null) {
                        // Max 20 km/h for the gauge visualization
                        speedProgress.setProgress((int) Math.min(speedKmh * 10, 200));
                    }
                }
            }
            lastSpeedTimestamp = event.timestamp;
            
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            textGyroX.setText(String.format(Locale.US, "X: %.2f", x));
            textGyroY.setText(String.format(Locale.US, "Y: %.2f", y));
            textGyroZ.setText(String.format(Locale.US, "Z: %.2f", z));

            gyroSeriesX.add(gyroCount, x);
            gyroSeriesY.add(gyroCount, y);
            gyroSeriesZ.add(gyroCount, z);

            if (gyroCount > MAX_POINTS) {
                gyroRenderer.setXAxisMin(gyroCount - MAX_POINTS);
                gyroRenderer.setXAxisMax(gyroCount);
            }
            gyroCount++;
            gyroChartView.repaint();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}