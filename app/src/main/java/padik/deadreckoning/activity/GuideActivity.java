package padik.deadreckoning.activity;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import padik.deadreckoning.R;
import padik.deadreckoning.adapter.GuideAdapter;
import padik.deadreckoning.model.GuideItem;

public class GuideActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private GuideAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        initViews();
        setupToolbar();
        loadGuideItems();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewGuide);
        adapter = new GuideAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadGuideItems() {
        List<GuideItem> items = new ArrayList<>();

        items.add(new GuideItem(
            "1. How does Padik work?",
            "Padik uses advanced sensor fusion to calculate your position without constant GPS:\n\n" +
            "• Steps: Detected via Linear Acceleration with AI-filtered noise.\n" +
            "• Heading: Fused Gyroscope and Magnetometer data through a Kalman Filter with phase-wrap correction.\n" +
            "• GPS Fusion: Corrects inertial drift automatically when signal is strong.",
            R.drawable.ic_directions_walk
        ));

        items.add(new GuideItem(
            "2. Confidence Ellipse",
            "The pulsating red circle around your position represents mathematical uncertainty (Covariance):\n\n" +
            "• Tiny Circle: High confidence (Strong GPS).\n" +
            "• Growing Circle: Increasing uncertainty (GPS outage/Drift).\n" +
            "• Snapping Back: Occurs during footsteps (ZUPT constraint) or GPS reconnection.",
            R.drawable.ic_gps
        ));

        items.add(new GuideItem(
            "3. Sensors & Speedometer",
            "The new 'Sensors' tab provides real-time analytics:\n\n" +
            "• Virtual Speedometer: Calculates your speed (km/h) using pure inertial data.\n" +
            "• Live Graphs: Visualize raw Accelerometer and Gyroscope data on X, Y, and Z axes.\n" +
            "• Live Status: Monitor the health of your device's internal sensors.",
            R.drawable.ic_sensor
        ));

        items.add(new GuideItem(
            "4. Finding a Path (Routing)",
            "Navigate to any destination using the 'Find Path' feature:\n\n" +
            "• Tap the Directions icon on the map.\n" +
            "• Enter any address or use 'Current Location'.\n" +
            "• Live Distance: The app shows the remaining distance along the road path in real-time.",
            R.drawable.ic_directions
        ));

        items.add(new GuideItem(
            "5. Turn Detection",
            "The app automatically identifies maneuvers:\n\n" +
            "• Automated detection for Left, Right, and U-Turns.\n" +
            "• Manual Overrides: Use the arrow buttons or the 360° degree dial in 'No GPS' mode to set a fixed heading.",
            R.drawable.ic_turn_left
        ));

        items.add(new GuideItem(
            "6. GPS & Step Calibration",
            "Maintain high precision:\n\n" +
            "• GPS Calib: Automatically scales your step length based on real-world movement.\n" +
            "• Manual Calib: Set your exact stride length in the Calibration tab for higher offline accuracy.",
            R.drawable.ic_settings
        ));

        items.add(new GuideItem(
            "7. Map Controls",
            "Interact with the Vanilla Red interface:\n\n" +
            "• Rotation: Use two fingers to rotate the map 360°.\n" +
            "• Markers: Tap '+' to drop custom emoji markers at any location.\n" +
            "• Clear Data: Use the trash icon to reset tracks or markers.",
            R.drawable.ic_map
        ));

        items.add(new GuideItem(
            "8. Data Export",
            "Save your journeys for analysis:\n\n" +
            "• Export as CSV (Spreadsheets) or GPX (Other map tools).\n" +
            "• View full trip statistics in the History tab.",
            R.drawable.ic_history
        ));

        items.add(new GuideItem(
            "About & Credits",
            "Padik: Inertial Navigation Pro\n\n" +
            "Lead Developer:\n" +
            "Biswajeet Lenka\n\n" +
            "Features:\n" +
            "• AI-enhanced Dead Reckoning\n" +
            "• Kalman Filter Sensor Fusion\n" +
            "• OSRM Road Routing\n" +
            "• Real-time Covariance Visualization",
            R.drawable.ic_help
        ));

        adapter.updateItems(items);
    }
}
