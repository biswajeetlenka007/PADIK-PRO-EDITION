package padik.deadreckoning.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import padik.deadreckoning.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.topToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnCalibration).setOnClickListener(v -> {
            startActivity(new Intent(this, CalibrationActivity.class));
        });

        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        findViewById(R.id.btnSensors).setOnClickListener(v -> {
            startActivity(new Intent(this, SensorsActivity.class));
        });

        findViewById(R.id.btnSteps).setOnClickListener(v -> {
            startActivity(new Intent(this, StepsActivity.class));
        });

        findViewById(R.id.btnGuide).setOnClickListener(v -> {
            startActivity(new Intent(this, GuideActivity.class));
        });
    }
}
