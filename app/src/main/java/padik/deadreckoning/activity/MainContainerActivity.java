package padik.deadreckoning.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import padik.deadreckoning.R;
import padik.deadreckoning.activity.SensorsFragment.OnCloseSensorsListener;

public class MainContainerActivity extends AppCompatActivity implements OnCloseSensorsListener {

    private BottomNavigationView bottomNavigation;
    private MapFragment mapFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_container);

        initViews();
        initFragments();
        setupBottomNavigation();
    }

    private void initViews() {
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void initFragments() {
        mapFragment = new MapFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, mapFragment, "map")
                .commit();

        currentFragment = mapFragment;
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_map) {
                showFragment(mapFragment);
                return true;
            } else if (itemId == R.id.nav_logger) {
                startActivity(new Intent(this, DataCollectActivity.class));
                return false;
            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return false;
            }
            
            return false;
        });
    }

    private void showFragment(Fragment fragment) {
        if (fragment == currentFragment) return;

        getSupportFragmentManager().beginTransaction()
                .hide(currentFragment)
                .show(fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();

        currentFragment = fragment;
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigation.setSelectedItemId(R.id.nav_map);
    }

    @Override
    public void onCloseSensors() {
        bottomNavigation.setSelectedItemId(R.id.nav_map);
    }
}
