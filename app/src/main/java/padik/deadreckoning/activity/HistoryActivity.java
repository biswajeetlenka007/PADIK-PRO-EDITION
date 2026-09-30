package padik.deadreckoning.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.model.Trip;
import padik.deadreckoning.storage.TripStorage;
import padik.deadreckoning.adapter.TripAdapter;

public class HistoryActivity extends AppCompatActivity implements TripAdapter.OnTripClickListener {

    private TripStorage tripStorage;
    private RecyclerView recyclerView;
    private TripAdapter adapter;
    private LinearLayout emptyState;
    private TextView textTotalDistance;
    private TextView textTotalTrips;
    private TextView textTotalSteps;
    
    private Trip pendingExportTrip;
    private String pendingExportFormat;
    private Uri pendingExportFolderUri;

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri folderUri = result.getData().getData();
                if (folderUri != null) {
                    pendingExportFolderUri = folderUri;
                    performExportToFolder();
                }
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        tripStorage = new TripStorage(this);

        initViews();
        setupToolbar();
        loadTrips();
        updateStats();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewTrips);
        emptyState = findViewById(R.id.emptyState);
        textTotalDistance = findViewById(R.id.textTotalDistance);
        textTotalTrips = findViewById(R.id.textTotalTrips);
        textTotalSteps = findViewById(R.id.textTotalSteps);

        adapter = new TripAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadTrips() {
        List<Trip> trips = tripStorage.getAllTrips();
        trips.sort((t1, t2) -> Long.compare(t2.getStartTime(), t1.getStartTime()));

        if (trips.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateTrips(trips);
        }
    }

    private void updateStats() {
        int totalTrips = tripStorage.getTotalTrips();
        double totalDistance = tripStorage.getTotalDistance();
        int totalSteps = tripStorage.getTotalSteps();

        textTotalTrips.setText(String.valueOf(totalTrips));
        textTotalDistance.setText(String.format(Locale.US, "%.2f km", totalDistance / 1000));
        textTotalSteps.setText(String.valueOf(totalSteps));
    }

    @Override
    public void onTripClick(Trip trip) {
        showTripDetails(trip);
    }

    @Override
    public void onTripLongClick(Trip trip) {
        showTripOptions(trip);
    }

    private void showTripDetails(Trip trip) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        
        String details = getString(R.string.trip_details_format,
            sdf.format(new Date(trip.getStartTime())),
            trip.getFormattedDuration(),
            trip.getTotalDistance(),
            trip.getTotalSteps(),
            trip.getTurnEvents().size(),
            trip.getStartLatitude(),
            trip.getStartLongitude(),
            trip.getEndLatitude(),
            trip.getEndLongitude()
        );

        new AlertDialog.Builder(this)
            .setTitle(trip.getName() != null ? trip.getName() : getString(R.string.trip))
            .setMessage(details)
            .setPositiveButton(R.string.view_on_map, (d, w) -> viewTripOnMap(trip))
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private void showTripOptions(Trip trip) {
        String[] options = {
            getString(R.string.export_csv_choose_folder),
            getString(R.string.export_gpx_choose_folder),
            getString(R.string.export_csv_downloads),
            getString(R.string.export_gpx_downloads),
            getString(R.string.delete)
        };

        new AlertDialog.Builder(this)
            .setTitle(trip.getName() != null ? trip.getName() : getString(R.string.trip))
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        exportTrip(trip, "csv");
                        break;
                    case 1:
                        exportTrip(trip, "gpx");
                        break;
                    case 2:
                        exportToDownloads(trip, "csv");
                        break;
                    case 3:
                        exportToDownloads(trip, "gpx");
                        break;
                    case 4:
                        confirmDeleteTrip(trip);
                        break;
                }
            })
            .show();
    }
    
    private void exportToDownloads(Trip trip, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "trip_" + sdf.format(new Date(trip.getStartTime())) + "." + format;
            String mimeType = "csv".equals(format) ? "text/csv" : "application/gpx+xml";
            String content = "csv".equals(format) ? trip.toCSV() : trip.toGPX();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                
                android.net.Uri uri = getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                
                if (uri != null) {
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                    
                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    
                    Toast.makeText(this, getString(R.string.exported_to_downloads, fileName), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.error, "Failed to create file"), Toast.LENGTH_SHORT).show();
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);
                
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(content.getBytes());
                }
                
                Toast.makeText(this, getString(R.string.exported_to_downloads, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void viewTripOnMap(Trip trip) {
        Intent intent = new Intent(this, MainNavigationActivity.class);
        intent.putExtra("trip_id", trip.getId());
        intent.putExtra("view_mode", true);
        startActivity(intent);
    }

    private void exportTrip(Trip trip, String format) {
        pendingExportTrip = trip;
        pendingExportFormat = format;
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }
    
    private void performExportToFolder() {
        if (pendingExportTrip == null || pendingExportFolderUri == null) return;
        
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(pendingExportFolderUri, takeFlags);
            
            String content;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName;
            String mimeType;
            
            if ("csv".equals(pendingExportFormat)) {
                content = pendingExportTrip.toCSV();
                fileName = "trip_" + sdf.format(new Date(pendingExportTrip.getStartTime())) + ".csv";
                mimeType = "text/csv";
            } else {
                content = pendingExportTrip.toGPX();
                fileName = "trip_" + sdf.format(new Date(pendingExportTrip.getStartTime())) + ".gpx";
                mimeType = "application/gpx+xml";
            }
            
            Uri fileUri = DocumentsContract.createDocument(
                getContentResolver(), 
                pendingExportFolderUri, 
                mimeType, 
                fileName
            );
            
            if (fileUri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(fileUri)) {
                    if (os != null) {
                        os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                
                Toast.makeText(this, getString(R.string.exported, fileName), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.error, "Failed to create file"), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
        
        pendingExportTrip = null;
        pendingExportFormat = null;
        pendingExportFolderUri = null;
    }

    private void confirmDeleteTrip(Trip trip) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_trip)
            .setMessage(R.string.confirm_delete_trip)
            .setPositiveButton(R.string.delete, (d, w) -> {
                tripStorage.deleteTrip(trip.getId());
                loadTrips();
                updateStats();
                Toast.makeText(this, R.string.trip_deleted, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
        updateStats();
    }
}
