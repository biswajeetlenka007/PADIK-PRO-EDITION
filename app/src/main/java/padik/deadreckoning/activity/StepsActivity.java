package padik.deadreckoning.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import padik.deadreckoning.R;

public class StepsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new StepsFragment())
                .commit();
    }
}
