package padik.deadreckoning.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import padik.deadreckoning.R;
import padik.deadreckoning.permission.PermissionHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        startAnimations();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkPermissions, 2500);
    }

    private void startAnimations() {
        ImageView icon = findViewById(R.id.icon);
        TextView title = findViewById(R.id.title);
        TextView developer = findViewById(R.id.textDeveloper);
        
        icon.setAlpha(0f);
        title.setAlpha(0f);
        title.setTranslationY(50f);
        developer.setAlpha(0f);
        
        icon.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(200)
            .start();
        
        title.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(500)
            .start();
        
        developer.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(1000)
            .start();
    }

    private void checkPermissions() {
        if (PermissionHelper.hasAllPermissions(this)) {
            startMainActivity();
        } else if (PermissionHelper.shouldShowRationale(this)) {
            showPermissionRationale();
        } else {
            requestPermissions();
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_required)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(R.string.grant, (dialog, which) -> requestPermissions())
                .setNegativeButton(R.string.later, (dialog, which) -> {
                    Toast.makeText(this, R.string.partial_functionality, Toast.LENGTH_SHORT).show();
                    startMainActivity();
                })
                .setCancelable(false)
                .show();
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                PermissionHelper.getRequiredPermissions(this),
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            boolean locationGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int result = grantResults[i];

                if (result == PackageManager.PERMISSION_GRANTED) {
                    if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) ||
                            permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        locationGranted = true;
                    }
                } else {
                    allGranted = false;
                }
            }

            if (locationGranted) {
                Toast.makeText(this, R.string.location_granted, Toast.LENGTH_SHORT).show();
                startMainActivity();
            } else if (!allGranted) {
                showPermissionDeniedDialog();
            } else {
                startMainActivity();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_denied)
                .setMessage(R.string.permission_denied_message)
                .setPositiveButton(R.string.settings, (dialog, which) -> PermissionHelper.openAppSettings(this))
                .setNegativeButton(R.string.continue_anyway, (dialog, which) -> startMainActivity())
                .setCancelable(false)
                .show();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainContainerActivity.class);
        startActivity(intent);
        finish();
    }
}
