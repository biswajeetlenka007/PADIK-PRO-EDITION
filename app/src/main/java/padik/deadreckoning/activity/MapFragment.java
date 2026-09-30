package padik.deadreckoning.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.ejml.data.DMatrixRMaj;
import org.osmdroid.api.IMapController;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.ai.AISample;
import padik.deadreckoning.ai.SpeedEstimator;
import padik.deadreckoning.ai.SpeedModelConfig;
import padik.deadreckoning.ai.AISample;
import padik.deadreckoning.ai.SpeedModelConfig;
import padik.deadreckoning.ai.SpeedEstimator;
import padik.deadreckoning.model.IMUSample;
import padik.deadreckoning.model.GNSSSample;
import padik.deadreckoning.model.INSState;
import padik.deadreckoning.model.Marker;
import padik.deadreckoning.model.MotionMode;
import padik.deadreckoning.preferences.TurnModePreferences;
import padik.deadreckoning.preferences.StepCounterPreferences;
import padik.deadreckoning.sensor.DeadReckoningEngine;
import padik.deadreckoning.sensor.GNSSQualityManager;
import padik.deadreckoning.sensor.INSProcessor;
import padik.deadreckoning.sensor.GNSSQualityManager;
import padik.deadreckoning.sensor.PreciseHeadingEstimator;
import padik.deadreckoning.sensor.SpeedSourceManager;
import padik.deadreckoning.service.TrackingService;
import padik.deadreckoning.storage.MarkerStorage;
import padik.deadreckoning.storage.TripStorage;
import padik.deadreckoning.view.DegreeDialView;

import android.app.Activity;
import android.location.Address;
import android.location.Geocoder;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;

public class MapFragment extends Fragment implements SensorEventListener {

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private Polyline pathOverlay;
    private Polyline routingOverlay;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private SensorManager sensorManager;
    private Sensor sensorGravity;
    private Sensor sensorMagnetic;
    private Sensor sensorGyroscope;
    private Sensor sensorLinearAcceleration;
    private Sensor sensorAccelerometer;
    private Sensor sensorStepDetector;

    private DeadReckoningEngine deadReckoningEngine;
    private GNSSQualityManager gnssQualityManager;
    private PreciseHeadingEstimator headingEstimator;
    private INSProcessor insProcessor;
    private SpeedEstimator speedEstimator;
    private MarkerStorage markerStorage;
    private TurnModePreferences turnModePrefs;
    private StepCounterPreferences stepPrefs;

    private TrackingService trackingService;
    private boolean isServiceBound = false;
    private boolean isFollowingUser = false;
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable uiUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTracking && isServiceBound && trackingService != null) {
                updateUIFromService();
            }
            
            // Always check GPS availability, even before tracking starts
            if (!isTracking && getContext() != null) {
                LocationManager lm = (LocationManager) requireContext()
                    .getSystemService(Context.LOCATION_SERVICE);
                boolean gpsEnabled = lm != null && 
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                long timeSinceGPS = System.currentTimeMillis() - lastGPSFixTime;
                
                if (!gpsEnabled || timeSinceGPS > 5000) {
                    if (textGPSStatus != null) {
                        textGPSStatus.setText("● DR ONLY (No GNSS)");
                        textGPSStatus.setTextColor(
                            ContextCompat.getColor(getContext(), R.color.navError));
                    }
                }
                updateMyLocationIcon();
            }
            
            uiUpdateHandler.postDelayed(this, 100);
        }
    };
    
    private void updateUIFromService() {
        if (getActivity() == null || !isAdded() || getContext() == null) return;
        
        long timeSinceLastGPS = System.currentTimeMillis() - lastGPSFixTime;
        if (isTracking && !forceNoGPSMode && timeSinceLastGPS > 5000) {
            if (textGPSStatus != null && getContext() != null) {
                textGPSStatus.setText("● DR ONLY (No GNSS)");
                textGPSStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.navError));
            }
        }
        
        if (textSteps != null) textSteps.setText(String.valueOf(trackingService.getStepCount()));
        if (textDistance != null) textDistance.setText(String.format(Locale.getDefault(), "%.2f m", trackingService.getDistance()));
        double heading = trackingService.getHeading();
        if (textHeading != null) textHeading.setText(String.format(Locale.getDefault(), "%.1f°", heading));

        if (imageDirectionArrow != null && cardDirection != null) {
            float rotation = (float) -heading;
            imageDirectionArrow.setRotation(rotation);
            cardDirection.setVisibility(View.VISIBLE);
        }

        if (!isManualMode) {
            boolean isTurning = isServiceBound ? trackingService.isTurning() : deadReckoningEngine.isTurning();
            if (isTurning) {
                double turnAmount = isServiceBound ? trackingService.getAccumulatedHeadingChange() : deadReckoningEngine.getAccumulatedHeadingChange();
                String turnText;
                if (turnAmount > 0) {
                    turnText = "Left " + String.format(Locale.getDefault(), "%.0f°", Math.abs(turnAmount));
                } else {
                    turnText = "Right " + String.format(Locale.getDefault(), "%.0f°", Math.abs(turnAmount));
                }
                showTurnIndicator(turnText);
                
                if (forceNoGPSMode) {
                    noGPSFixedHeading = heading;
                    if (isServiceBound) {
                        trackingService.setFixedHeading(heading);
                    } else if (deadReckoningEngine != null) {
                        deadReckoningEngine.setFixedHeading(heading);
                    }
                }
            }
        }
        if (trackingService.getMotionMode() == MotionMode.VEHICLE) {
            GeoPoint servicePos = trackingService.getVehiclePosition();
            if (servicePos != null) {
                currentPosition = servicePos;
                pathPoints.add(currentPosition);
                updatePathOnMap();
            }
        }
        
        // Update primary Speed display
        if (textSpeedValue != null) {
            if (motionMode == MotionMode.VEHICLE) {
                double speedMps = trackingService.getSpeed();
                textSpeedValue.setText(String.format(Locale.getDefault(), "%.0f", speedMps * 3.6));
                
                String source = trackingService.getSpeedSource();
                // Optionally update a UI label with "Source: " + source if needed
            } else {
                textSpeedValue.setText("--"); 
            }
        }
        
        updateDiagnosticsPanel();

        // Update Elapsed Time
        if (isTracking && textElapsedTime != null) {
            long elapsed = System.currentTimeMillis() - trackingStartTime;
            long seconds = (elapsed / 1000) % 60;
            long minutes = (elapsed / (1000 * 60)) % 60;
            long hours = (elapsed / (1000 * 60 * 60));
            if (hours > 0) {
                textElapsedTime.setText(String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds));
            } else {
                textElapsedTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }
        }

        double totalDistance = trackingService.getDistance();
        if (forceNoGPSMode && lastDeadReckoningPoint != null) {
            double deltaDistance = totalDistance - lastDeadReckoningDistance;
            if (deltaDistance > 1.0) {
                GeoPoint newPoint = calculateEstimatedPosition(
                        lastDeadReckoningPoint.getLatitude(),
                        lastDeadReckoningPoint.getLongitude(),
                        noGPSFixedHeading,
                        deltaDistance
                );
                if (newPoint != null) {
                    noGPSPathPoints.add(newPoint);
                    lastDeadReckoningPoint = newPoint;
                    currentPosition = newPoint;
                    updatePathOnMap();
                }
                lastDeadReckoningDistance = totalDistance;
            }
        }
        
        // Update Uncertainty Ellipse
        if (isTracking && currentPosition != null) {
            double radius = trackingService.getUncertaintyRadius();
            // To create a pulsating effect, we can apply a slight sinusoidal scale based on system time
            double pulse = 1.0 + 0.1 * Math.sin(System.currentTimeMillis() / 200.0);
            uncertaintyPolygon.setPoints(Polygon.pointsAsCircle(currentPosition, radius * pulse));
            
            if (isFollowingUser) {
                mapView.getController().animateTo(currentPosition);
            }
            
            mapView.invalidate();
        }

        updateRemainingDistance();
    }
    
    private void updateDiagnosticsPanel() {
        TextView textDiag = getView() != null ? getView().findViewById(R.id.textDiagnostics) : null;
        if (textDiag != null && textDiag.getVisibility() == View.VISIBLE) {
            if (isServiceBound && trackingService != null) {
                SpeedSourceManager mgr = trackingService.getSpeedSourceManager();
                if (mgr != null) {
                    String info = String.format(Locale.getDefault(),
                        "=== DIAGNOSTICS ===\n" +
                        "AI Speed: %.1f km/h\n" +
                        "GPS Speed: %.1f km/h\n" +
                        "Source: %s\n" +
                        "GPS Available: %b\n",
                        mgr.getAiSpeedKmh(),
                        mgr.getGpsSpeedKmh(),
                        mgr.getCurrentSpeedSource().name(),
                        mgr.isGpsAvailable()
                    );
                    textDiag.setText(info);
                }
            }
        }
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TrackingService.LocalBinder binder = (TrackingService.LocalBinder) service;
            trackingService = binder.getService();
            isServiceBound = true;
            trackingService.setMotionMode(motionMode);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    private TextView textSteps;
    private TextView textDistance;
    private TextView textHeading;
    private TextView textGPSAccuracy;
    private TextView textGPSStatus;
    private TextView textTurnIndicator;
    private MaterialButton buttonModeToggle;
    private FloatingActionButton buttonGPSCalibrate;
    private MaterialCardView cardTurn;
    private TextView textRemainingDistance;
    private TextView textSpeedValue;
    private TextView textElapsedTime;
    private TextView textRoadName;
    private LinearLayout manualTurnControls;

    private FloatingActionButton buttonTurnLeft;
    private FloatingActionButton buttonTurnRight;
    private FloatingActionButton buttonTurnAround;
    private FloatingActionButton buttonOpenDial;
    private FloatingActionButton fabCenterGPS;
    private FloatingActionButton fabRoute;
    private FloatingActionButton fabAddMarker;
    private FloatingActionButton fabClearAll;
    private MaterialButton buttonStartStop;
    private MaterialButton buttonPause;
    private FloatingActionButton buttonNoGPS;
    private MaterialButton buttonConfirmDial;
    private ImageView imageDirectionArrow;
    private MaterialCardView cardDirection;
    private FrameLayout dialOverlayContainer;
    private DegreeDialView degreeDialView;

    private List<GeoPoint> pathPoints = new ArrayList<>();
    private List<GeoPoint> noGPSPathPoints = new ArrayList<>();
    private List<GeoPoint> recentGPSPoints = new ArrayList<>();
    private List<Marker> mapMarkers = new ArrayList<>();
    private List<org.osmdroid.views.overlay.Marker> markerOverlays = new ArrayList<>();
    private GeoPoint currentPosition = null;
    private GeoPoint startPosition = null;
    private GeoPoint lastGPSPoint = null;
    private GeoPoint destinationPoint = null;
    private List<GeoPoint> currentRoutePoints = new ArrayList<>();

    private boolean isTracking = false;
    private boolean isPaused = false;
    private boolean isManualMode = false;
    private Location lastKnownGPS = null;
    private boolean useGPSForTrace = false;
    private boolean forceNoGPSMode = false;
    private long lastGPSMoveTime = 0;
    private long trackingStartTime = 0;
    private long lastGPSFixTime = 0;
    private GeoPoint lastDeadReckoningPoint = null;
    private double lastDeadReckoningDistance = 0;
    private double noGPSFixedHeading = 0;
    private Polyline noGPSPathOverlay;
    private Polygon uncertaintyPolygon;
    
    private MotionMode motionMode = MotionMode.WALKING;
    private boolean isAddingMarker = false;
    private String selectedEmoji = "📍";
    private GeoPoint pendingMarkerPosition = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        turnModePrefs = new TurnModePreferences(requireContext());
        stepPrefs = new StepCounterPreferences(requireContext());
        markerStorage = new MarkerStorage(requireContext());
        isManualMode = turnModePrefs.getTurnMode() == TurnModePreferences.TurnMode.MANUAL;

        initViews(view);
        initEngine();
        initSensors();
        initLocation();
        initMap(view);
        
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE && isFollowingUser) {
                isFollowingUser = false;
                updateMyLocationIcon();
            }
            return false;
        });
        
        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(requireActivity());
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fused.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    mapView.getController().setCenter(startPoint);
                    mapView.getController().setZoom(16.0);
                }
            });
        }
        
        updateTurnModeUI();
        loadMarkers();
    }

    private void initViews(View view) {
        textSteps = view.findViewById(R.id.textSteps);
        textDistance = view.findViewById(R.id.textDistance);
        textHeading = view.findViewById(R.id.textHeading);
        textGPSAccuracy = view.findViewById(R.id.textGPSAccuracy);
        textGPSStatus = view.findViewById(R.id.textGPSStatus);
        textTurnIndicator = view.findViewById(R.id.textTurnIndicator);
        buttonModeToggle = view.findViewById(R.id.buttonModeToggle);
        buttonGPSCalibrate = view.findViewById(R.id.buttonGPSCalibrate);
        cardTurn = view.findViewById(R.id.cardTurn);
        textRemainingDistance = view.findViewById(R.id.textRemainingDistance);
        textSpeedValue = view.findViewById(R.id.textSpeedValue);
        textElapsedTime = view.findViewById(R.id.textElapsedTime);
        textRoadName = view.findViewById(R.id.textRoadName);
        manualTurnControls = view.findViewById(R.id.manualTurnControls);

        buttonTurnLeft = view.findViewById(R.id.buttonTurnLeft);
        buttonTurnRight = view.findViewById(R.id.buttonTurnRight);
        buttonTurnAround = view.findViewById(R.id.buttonTurnAround);
        fabCenterGPS = view.findViewById(R.id.fabCenterGPS);
        fabRoute = view.findViewById(R.id.fabRoute);
        fabAddMarker = view.findViewById(R.id.fabAddMarker);
        fabClearAll = view.findViewById(R.id.fabClearAll);
        buttonStartStop = view.findViewById(R.id.buttonStartStop);
        buttonPause = view.findViewById(R.id.buttonPause);
        buttonNoGPS = view.findViewById(R.id.buttonNoGPS);
        imageDirectionArrow = view.findViewById(R.id.imageDirectionArrow);
        cardDirection = view.findViewById(R.id.cardDirection);

        buttonOpenDial = view.findViewById(R.id.buttonOpenDial);
        dialOverlayContainer = view.findViewById(R.id.dialOverlayContainer);
        degreeDialView = view.findViewById(R.id.degreeDialView);
        buttonConfirmDial = view.findViewById(R.id.buttonConfirmDial);

        buttonModeToggle.setOnClickListener(v -> toggleTurnMode());
        
        if (buttonNoGPS != null) {
            buttonNoGPS.setOnClickListener(v -> toggleNoGPSMode());
        }
        
        buttonGPSCalibrate.setOnClickListener(v -> calibrateGPS());

        buttonOpenDial.setOnClickListener(v -> openDialOverlay());
        
        dialOverlayContainer.setOnClickListener(v -> closeDialOverlay());
        
        degreeDialView.setOnDegreeChangedListener(degree -> {
            textHeading.setText(String.format("%.0f°", degree));
        });
        
        buttonConfirmDial.setOnClickListener(v -> confirmDialSelection());
        
        buttonTurnLeft.setOnClickListener(v -> {
            if (isTracking) {
                if (isServiceBound) {
                    trackingService.turnLeft();
                    noGPSFixedHeading = trackingService.getHeading();
                } else if (deadReckoningEngine != null) {
                    deadReckoningEngine.turnLeft();
                    noGPSFixedHeading = deadReckoningEngine.getHeading();
                }
                showTurnIndicator("Left ↰");
            }
        });
        
        buttonTurnRight.setOnClickListener(v -> {
            if (isTracking) {
                if (isServiceBound) {
                    trackingService.turnRight();
                    noGPSFixedHeading = trackingService.getHeading();
                } else if (deadReckoningEngine != null) {
                    deadReckoningEngine.turnRight();
                    noGPSFixedHeading = deadReckoningEngine.getHeading();
                }
                showTurnIndicator("Right ↱");
            }
        });
        
        buttonTurnAround.setOnClickListener(v -> {
            if (isTracking) {
                if (isServiceBound) {
                    trackingService.turnAround();
                    noGPSFixedHeading = trackingService.getHeading();
                } else if (deadReckoningEngine != null) {
                    deadReckoningEngine.turnAround();
                    noGPSFixedHeading = deadReckoningEngine.getHeading();
                }
                showTurnIndicator("U-Turn 🔄");
            }
        });

        fabCenterGPS.setOnClickListener(v -> onMyLocationClicked());
        
        if (cardTurn != null) {
            cardTurn.setOnLongClickListener(v -> {
                View diag = getView() != null ? getView().findViewById(R.id.cardDiagnostics) : null;
                if (diag != null) {
                    diag.setVisibility(diag.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
                return true;
            });
        }
        
        if (textSpeedValue != null) {
            textSpeedValue.setOnLongClickListener(v -> {
                View diag = getView() != null ? getView().findViewById(R.id.cardDiagnostics) : null;
                if (diag != null) {
                    diag.setVisibility(diag.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
                return true;
            });
        }

        if (fabRoute != null) {
            fabRoute.setOnClickListener(v -> showSearchPathDialog());
        }
        
        fabAddMarker.setOnClickListener(v -> toggleAddMarkerMode());
        
        fabClearAll.setOnClickListener(v -> showClearAllDialog());
        
        if (buttonStartStop != null) {
            buttonStartStop.setOnClickListener(v -> {
                if (isTracking) {
                    stopTracking();
                } else {
                    startTracking();
                }
            });
        }
        
        if (buttonPause != null) {
            buttonPause.setOnClickListener(v -> togglePause());
        }
    }
    
    private void togglePause() {
        if (!isTracking) return;
        
        isPaused = !isPaused;
        deadReckoningEngine.setPaused(isPaused);
        
        if (isPaused) {
            buttonPause.setText(R.string.resume);
            buttonPause.setIconResource(R.drawable.ic_play);
            Toast.makeText(requireContext(), "Tracking paused", Toast.LENGTH_SHORT).show();
        } else {
            buttonPause.setText(R.string.pause);
            buttonPause.setIconResource(R.drawable.ic_pause);
            Toast.makeText(requireContext(), "Tracking resumed", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleNoGPSMode() {
        forceNoGPSMode = !forceNoGPSMode;
        useGPSForTrace = !forceNoGPSMode;
        
        if (forceNoGPSMode) {
            buttonNoGPS.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navError));
            manualTurnControls.setVisibility(View.VISIBLE);
            
            GeoPoint startPoint = calculatePoint5MetersBack();
            
            if (startPoint != null) {
                lastDeadReckoningPoint = startPoint;
            } else if (lastKnownGPS != null && currentPosition != null) {
                lastDeadReckoningPoint = currentPosition;
            }
            
            if (isTracking && isServiceBound) {
                lastDeadReckoningDistance = trackingService.getDistance();
                noGPSFixedHeading = trackingService.getHeading();
                trackingService.setFixedHeading(noGPSFixedHeading);
            } else if (deadReckoningEngine != null) {
                lastDeadReckoningDistance = deadReckoningEngine.getDistance();
                noGPSFixedHeading = deadReckoningEngine.getHeading();
                deadReckoningEngine.setFixedHeading(noGPSFixedHeading);
            }
            
            Toast.makeText(requireContext(), "Mode: No GPS - Straight line, use arrows to turn", Toast.LENGTH_SHORT).show();
        } else {
            buttonNoGPS.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navSurfaceElevated));
            if (!isManualMode) {
                manualTurnControls.setVisibility(View.GONE);
            }
            if (deadReckoningEngine != null) {
                deadReckoningEngine.resetToCompassHeading();
            }
            Toast.makeText(requireContext(), "Mode: GPS (Compass)", Toast.LENGTH_SHORT).show();
        }
    }

    private GeoPoint calculatePoint5MetersBack() {
        if (recentGPSPoints.size() < 2) {
            return null;
        }
        
        GeoPoint lastPoint = recentGPSPoints.get(recentGPSPoints.size() - 1);
        GeoPoint secondLastPoint = recentGPSPoints.get(recentGPSPoints.size() - 2);
        
        double bearing = calculateBearing(secondLastPoint.getLatitude(), secondLastPoint.getLongitude(),
                                          lastPoint.getLatitude(), lastPoint.getLongitude());
        
        double backBearing = (bearing + 180) % 360;
        
        return calculateEstimatedPosition(
            lastPoint.getLatitude(),
            lastPoint.getLongitude(),
            backBearing,
            5.0
        );
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double lon1Rad = Math.toRadians(lon1);
        double lon2Rad = Math.toRadians(lon2);
        
        double dLon = lon2Rad - lon1Rad;
        
        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    private void onMyLocationClicked() {
        LocationManager lm = (LocationManager) requireContext()
            .getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        
        if (!gpsEnabled) {
            // STATE A: Location OFF — show settings
            Toast.makeText(requireContext(), 
                "Enable location services", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        
        if (!isFollowingUser) {
            // STATE B → C: Start following
            isFollowingUser = true;
            updateMyLocationIcon();
            if (currentPosition != null) {
                mapView.getController().animateTo(currentPosition);
                mapView.getController().setZoom(19.0);
            } else if (lastKnownGPS != null) {
                mapView.getController().animateTo(new GeoPoint(
                    lastKnownGPS.getLatitude(), lastKnownGPS.getLongitude()));
                mapView.getController().setZoom(19.0);
            } else {
                Toast.makeText(requireContext(), 
                    R.string.searching_gps, Toast.LENGTH_SHORT).show();
                startLocationUpdates();
            }
        }
    }
    
    private void updateMyLocationIcon() {
        LocationManager lm = (LocationManager) requireContext()
            .getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        
        if (!gpsEnabled) {
            // STATE A: Crossed out / disabled icon, gray tint
            fabCenterGPS.setImageResource(R.drawable.ic_location_disabled);
            fabCenterGPS.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.navTextSecondary));
        } else if (isFollowingUser) {
            // STATE C: Filled icon, blue tint (following)
            fabCenterGPS.setImageResource(R.drawable.ic_blue_dot);
            fabCenterGPS.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.navAccent));
        } else {
            // STATE B: Outline icon, dark gray (not following)
            fabCenterGPS.setImageResource(R.drawable.ic_blue_dot);
            fabCenterGPS.clearColorFilter();
        }
    }

    private void toggleTurnMode() {
        if (motionMode == MotionMode.WALKING) {
            motionMode = MotionMode.VEHICLE;
            deadReckoningEngine.setMotionMode(motionMode);
            if (trackingService != null) {
                trackingService.setMotionMode(motionMode);
            }
            buttonModeToggle.setText("Mode: VEHICLE");
            buttonModeToggle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_directions, 0, 0, 0);
            Toast.makeText(requireContext(), "Mode: VEHICLE (6-DOF INS)", Toast.LENGTH_SHORT).show();
        } else {
            motionMode = MotionMode.WALKING;
            deadReckoningEngine.setMotionMode(motionMode);
            if (trackingService != null) {
                trackingService.setMotionMode(motionMode);
            }
            turnModePrefs.toggleTurnMode();
            isManualMode = turnModePrefs.getTurnMode() == TurnModePreferences.TurnMode.MANUAL;
            updateTurnModeUI();
            
            String mode = isManualMode ? "Manual" : "Auto";
            Toast.makeText(requireContext(), "Mode: WALKING (" + mode + ")", Toast.LENGTH_SHORT).show();
        }
    }

    private void calibrateGPS() {
        if (deadReckoningEngine != null) {
            deadReckoningEngine.resetCalibration();
            Toast.makeText(requireContext(), R.string.gps_reset, Toast.LENGTH_LONG).show();
            textGPSStatus.setText("GPS: Calibrating...");
        }
    }

    private void updateTurnModeUI() {
        if (isManualMode) {
            buttonModeToggle.setText("Mode: Manual");
            if (isTracking) {
                manualTurnControls.setVisibility(View.VISIBLE);
            }
        } else {
            buttonModeToggle.setText("Mode: Auto");
        manualTurnControls.setVisibility(View.GONE);

        if (buttonNoGPS != null) {
            buttonNoGPS.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navSurfaceElevated));
        }
        }
    }

    private void showTurnIndicator(String text) {
        if (textTurnIndicator != null && cardTurn != null) {
            textTurnIndicator.setText(text);
            cardTurn.setVisibility(View.VISIBLE);
            
            cardTurn.removeCallbacks(hideTurnRunnable);
            cardTurn.postDelayed(hideTurnRunnable, 2000);
        }
    }

    private final Runnable hideTurnRunnable = new Runnable() {
        @Override
        public void run() {
            cardTurn.setVisibility(View.GONE);
        }
    };

    private void initSensors() {
        sensorManager = (SensorManager) requireContext().getSystemService(requireContext().SENSOR_SERVICE);

        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorLinearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        if (sensorStepDetector != null) {
            deadReckoningEngine.setHardwareStepAvailable(true);
            Log.d("StepDebug", "Hardware STEP_DETECTOR available: YES");
        } else {
            deadReckoningEngine.setHardwareStepAvailable(false);
            Log.d("StepDebug", "Hardware STEP_DETECTOR available: NO");
        }
    }

    private void registerSensors() {
        if (sensorGravity != null) {
            sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorMagnetic != null) {
            sensorManager.registerListener(this, sensorMagnetic, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorGyroscope != null) {
            sensorManager.registerListener(this, sensorGyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorLinearAcceleration != null) {
            sensorManager.registerListener(this, sensorLinearAcceleration, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorAccelerometer != null) {
            sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorStepDetector != null) {
            sensorManager.registerListener(this, sensorStepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleGPSUpdate(location);
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (hasLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) handleGPSUpdate(location);
            });
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void initMap(View view) {
        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        
        RotationGestureOverlay rotationGestureOverlay = new RotationGestureOverlay(requireContext(), mapView);
        rotationGestureOverlay.setEnabled(true);
        mapView.getOverlays().add(rotationGestureOverlay);
        
        IMapController mapController = mapView.getController();
        mapController.setZoom(18.0);
        
        pathOverlay = new Polyline();
        pathOverlay.getOutlinePaint().setColor(Color.parseColor("#1976D2"));
        pathOverlay.getOutlinePaint().setStrokeWidth(12.0f);
        pathOverlay.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        mapView.getOverlays().add(pathOverlay);
        
        noGPSPathOverlay = new Polyline();
        noGPSPathOverlay.getOutlinePaint().setColor(Color.parseColor("#F44336"));
        noGPSPathOverlay.getOutlinePaint().setStrokeWidth(12.0f);
        noGPSPathOverlay.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        mapView.getOverlays().add(noGPSPathOverlay);
        
        uncertaintyPolygon = new Polygon(mapView);
        uncertaintyPolygon.getFillPaint().setColor(Color.argb(40, 211, 47, 47)); // Translucent vanilla red
        uncertaintyPolygon.getOutlinePaint().setColor(Color.argb(150, 211, 47, 47));
        uncertaintyPolygon.getOutlinePaint().setStrokeWidth(3.0f);
        mapView.getOverlays().add(uncertaintyPolygon);
        
        routingOverlay = new Polyline();
        routingOverlay.getOutlinePaint().setColor(Color.parseColor("#FF9800"));
        routingOverlay.getOutlinePaint().setStrokeWidth(8.0f);
        routingOverlay.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        mapView.getOverlays().add(routingOverlay);

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (isAddingMarker) {
                    showMarkerDialog(p);
                    return true;
                }
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };

        mapView.getOverlays().add(new MapEventsOverlay(mReceive));

        enableLocationComponent();
    }

    @SuppressLint("MissingPermission")
    private void enableLocationComponent() {
        if (hasLocationPermission()) {
            locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
            locationOverlay.enableMyLocation();
            locationOverlay.enableFollowLocation();
            mapView.getOverlays().add(locationOverlay);
        }
    }

    private void updatePathOnMap() {
        if (!pathPoints.isEmpty()) {
            pathOverlay.setPoints(pathPoints);
        }
        if (!noGPSPathPoints.isEmpty()) {
            noGPSPathOverlay.setPoints(noGPSPathPoints);
        }
        mapView.invalidate();
    }

    private void initEngine() {
        deadReckoningEngine = new DeadReckoningEngine();
        headingEstimator = new PreciseHeadingEstimator();
        insProcessor = new INSProcessor(requireContext());
        speedEstimator = new SpeedEstimator(requireContext());
        insProcessor.setSpeedEstimator(speedEstimator);
        gnssQualityManager = new GNSSQualityManager();
        loadMountingCalibration();
    }

    private void loadMountingCalibration() {
        SharedPreferences prefs = requireContext().getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("mount_calibrated", false)) {
            DMatrixRMaj R = new DMatrixRMaj(3, 3);
            for (int i = 0; i < 9; i++) {
                R.data[i] = prefs.getFloat("mount_r" + i, 0);
            }
            insProcessor.setMountingRotation(R);
        }
    }

    public void startTracking() {
        if (!hasLocationPermission()) {
            Toast.makeText(requireContext(), "GPS Permission required", Toast.LENGTH_LONG).show();
            return;
        }

        isTracking = true;
        isPaused = false;
        
        lastGPSFixTime = System.currentTimeMillis();
        if (textGPSStatus != null) {
            textGPSStatus.setText("● Acquiring GNSS...");
            textGPSStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.navWarning));
        }
        
        Intent serviceIntent = new Intent(requireContext(), TrackingService.class);
        serviceIntent.setAction(TrackingService.ACTION_START);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
        
        // Also bind to it immediately
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        StepCounterPreferences.StepMode stepMode = stepPrefs.getStepMode();
        deadReckoningEngine.setStepMode(stepMode);
        deadReckoningEngine.setMotionMode(motionMode);
        if (trackingService != null) {
            trackingService.setMotionMode(motionMode);
        }
        deadReckoningEngine.setPaused(isPaused);
        
        deadReckoningEngine.start();
        insProcessor.reset();
        loadMountingCalibration();
        pathPoints.clear();
        noGPSPathPoints.clear();
        startPosition = null;
        lastGPSPoint = null;
        lastDeadReckoningPoint = null;
        lastDeadReckoningDistance = 0;
        noGPSFixedHeading = 0;
        trackingStartTime = System.currentTimeMillis();
        
        forceNoGPSMode = false;
        if (buttonNoGPS != null) {
            buttonNoGPS.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navSurfaceElevated));
        }

        if (isManualMode || forceNoGPSMode) {
            manualTurnControls.setVisibility(View.VISIBLE);
        }
        
        if (buttonPause != null) {
            buttonPause.setVisibility(View.VISIBLE);
            buttonPause.setText(R.string.pause);
            buttonPause.setIconResource(R.drawable.ic_pause);
        }
        if (buttonStartStop != null) {
            buttonStartStop.setText(R.string.stop);
            buttonStartStop.setIconResource(R.drawable.ic_stop);
            buttonStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navError));
            buttonStartStop.setTextColor(ContextCompat.getColor(requireContext(), R.color.navTextPrimary));
            buttonStartStop.setIconTintResource(R.color.navTextPrimary);
        }
        if (cardDirection != null) {
            cardDirection.setVisibility(View.VISIBLE);
        }

        updatePathOnMap();
        Toast.makeText(requireContext(), "Tracking started", Toast.LENGTH_SHORT).show();
    }

    public void stopTracking() {
        isTracking = false;
        isPaused = false;
        deadReckoningEngine.stop();

        Intent serviceIntent = new Intent(requireContext(), TrackingService.class);
        serviceIntent.setAction(TrackingService.ACTION_STOP);
        requireContext().startService(serviceIntent);
        
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }

        manualTurnControls.setVisibility(View.GONE);

        if (buttonPause != null) {
            buttonPause.setVisibility(View.GONE);
        }
        if (buttonStartStop != null) {
            buttonStartStop.setText(R.string.start);
            buttonStartStop.setIconResource(R.drawable.ic_play);
            buttonStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.navAccent));
            buttonStartStop.setTextColor(ContextCompat.getColor(requireContext(), R.color.navBackground));
            buttonStartStop.setIconTintResource(R.color.navBackground);
        }

        // Trip saving happens only in TrackingService now.
        Toast.makeText(requireContext(), "Tracking stopped", Toast.LENGTH_SHORT).show();

        Toast.makeText(requireContext(), "Tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void handleGPSUpdate(Location location) {
        if (isPaused || !isAdded() || getContext() == null) return;
        
        lastKnownGPS = location;
        lastGPSFixTime = System.currentTimeMillis();
        
        double accuracy = location.getAccuracy();
        if (textGPSAccuracy != null) {
            textGPSAccuracy.setText(String.format(Locale.getDefault(), "%.1f m", accuracy));
        }
        
        GeoPoint gpsPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        
        String statusText;
        int statusColor;
        
        if (forceNoGPSMode) {
            statusText = "● DR / NO GNSS";
            statusColor = R.color.navError;
            useGPSForTrace = false;
        } else if (accuracy < 15) {
            statusText = String.format(Locale.getDefault(), "● GNSS + DR (%.1fm)", accuracy);
            statusColor = R.color.navAccent;
            useGPSForTrace = true;
            lastGPSMoveTime = System.currentTimeMillis();
        } else if (accuracy < 25) {
            statusText = String.format(Locale.getDefault(), "● GNSS (%.1fm)", accuracy);
            statusColor = R.color.navWarning;
            useGPSForTrace = accuracy < 20;
        } else {
            statusText = String.format(Locale.getDefault(), "● NO GNSS (%.1fm)", accuracy);
            statusColor = R.color.navError;
            useGPSForTrace = false;
        }

        if (textGPSStatus != null && getContext() != null) {
            textGPSStatus.setText(statusText);
            textGPSStatus.setTextColor(ContextCompat.getColor(getContext(), statusColor));
        }

        if (isTracking && !forceNoGPSMode) {
            // Local engines are disabled during tracking because TrackingService handles them.
            // We just use the raw GPS points for the GPS trace overlay.
            if (useGPSForTrace && accuracy < 15) {
                if (lastGPSPoint == null) {
                    pathPoints.add(gpsPoint);
                } else {
                    double distance = distanceBetween(lastGPSPoint, gpsPoint);
                    if (distance > 1.0) {
                        pathPoints.add(gpsPoint);
                    }
                }
                lastGPSPoint = gpsPoint;
                currentPosition = gpsPoint;

                recentGPSPoints.add(gpsPoint);
                if (recentGPSPoints.size() > 20) {
                    recentGPSPoints.remove(0);
                }

                if (lastDeadReckoningPoint == null) {
                    lastDeadReckoningPoint = gpsPoint;
                    if (isServiceBound) {
                        lastDeadReckoningDistance = trackingService.getDistance();
                        noGPSFixedHeading = trackingService.getHeading();
                        trackingService.setFixedHeading(noGPSFixedHeading);
                    } else {
                        lastDeadReckoningDistance = deadReckoningEngine.getDistance();
                        noGPSFixedHeading = deadReckoningEngine.getHeading();
                        deadReckoningEngine.setFixedHeading(noGPSFixedHeading);
                    }
                }

                updatePathOnMap();
            }

            if (startPosition == null && accuracy < 20) {
                startPosition = gpsPoint;
                mapView.getController().setCenter(startPosition);
            }
        }

        if (!isTracking && accuracy < 20) {
            currentPosition = gpsPoint;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(this::updateRemainingDistance);
            }
        }
    }

    private double distanceBetween(GeoPoint p1, GeoPoint p2) {
        return p1.distanceToAsDouble(p2);
    }

    private long lastAiSampleTimeNs = 0;
    private float[] latestAccel = new float[3];
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isTracking) {
            // When tracking, the TrackingService handles all sensor processing. MapFragment is display-only.
            // But we might need compass for rotation if we aren't getting UI updates yet, 
            // though the service provides everything. We'll skip doing DR here.
            return;
        }

        float[] values = event.values.clone();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            latestAccel[0] = values[0];
            latestAccel[1] = values[1];
            latestAccel[2] = values[2];
        }

        switch (event.sensor.getType()) {
            case Sensor.TYPE_GRAVITY:
                headingEstimator.updateGravity(values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                headingEstimator.updateMagneticField(values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                headingEstimator.updateGyroscope(values, event.timestamp);
                break;
        }
    }


    private void updateRemainingDistance() {
        if (destinationPoint == null || currentPosition == null || textRemainingDistance == null) {
            if (textRemainingDistance != null) textRemainingDistance.setText("No destination");
            if (textRoadName != null) textRoadName.setText("Navigation");
            return;
        }

        double distance;
        if (currentRoutePoints != null && !currentRoutePoints.isEmpty()) {
            distance = calculateRemainingRouteDistance(currentPosition, currentRoutePoints);
        } else {
            distance = currentPosition.distanceToAsDouble(destinationPoint);
        }

        if (distance < 10) {
            textRemainingDistance.setText("Reached");
            if (textRoadName != null) textRoadName.setText("Destination");
        } else {
            if (textRoadName != null) textRoadName.setText("En Route");
            if (distance < 1000) {
                textRemainingDistance.setText(String.format(Locale.getDefault(), "%.0f m", distance));
            } else {
                textRemainingDistance.setText(String.format(Locale.getDefault(), "%.1f km", distance / 1000.0));
            }
        }
    }

    private double calculateRemainingRouteDistance(GeoPoint current, List<GeoPoint> route) {
        if (route == null || route.isEmpty()) return 0;

        // Find the index of the closest point on the route
        int closestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = current.distanceToAsDouble(route.get(i));
            if (d < minDistance) {
                minDistance = d;
                closestIndex = i;
            }
        }

        // Sum distance from current to closest point, then sum remaining segments
        double remainingDistance = current.distanceToAsDouble(route.get(closestIndex));
        for (int i = closestIndex; i < route.size() - 1; i++) {
            remainingDistance += route.get(i).distanceToAsDouble(route.get(i + 1));
        }

        return remainingDistance;
    }

    private void openDialOverlay() {
        if (dialOverlayContainer == null || degreeDialView == null) return;
        
        float currentHeading = (float) noGPSFixedHeading;
        if (deadReckoningEngine != null && isTracking) {
            currentHeading = (float) deadReckoningEngine.getHeading();
        }
        
        degreeDialView.setDegree(currentHeading);
        dialOverlayContainer.setVisibility(View.VISIBLE);
    }

    private void closeDialOverlay() {
        if (dialOverlayContainer != null) {
            dialOverlayContainer.setVisibility(View.GONE);
        }
    }

    private void confirmDialSelection() {
        if (degreeDialView != null && isTracking) {
            float selectedDegree = degreeDialView.getDegree();
            
            if (forceNoGPSMode) {
                noGPSFixedHeading = selectedDegree;
                if (isServiceBound) {
                    trackingService.setFixedHeading(selectedDegree);
                } else if (deadReckoningEngine != null) {
                    deadReckoningEngine.setFixedHeading(selectedDegree);
                }
                showTurnIndicator(String.format("%.0f°", selectedDegree));
            } else if (isManualMode) {
                if (isServiceBound) {
                    trackingService.setFixedHeading(selectedDegree);
                } else if (deadReckoningEngine != null) {
                    deadReckoningEngine.setFixedHeading(selectedDegree);
                }
            }
        }
        
        closeDialOverlay();
    }

    private GeoPoint calculateEstimatedPosition(double lat, double lon, double heading, double distanceMeters) {
        double earthRadius = 6371000;
        double headingRad = Math.toRadians(heading);

        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);

        double newLatRad = latRad + (distanceMeters * Math.cos(headingRad)) / earthRadius;
        double newLonRad = lonRad + (distanceMeters * Math.sin(headingRad)) / (earthRadius * Math.cos(latRad));

        return new GeoPoint(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        registerSensors();
        startLocationUpdates();
        
        Intent serviceIntent = new Intent(requireContext(), TrackingService.class);
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        uiUpdateHandler.post(uiUpdateRunnable);
        
        isManualMode = turnModePrefs.getTurnMode() == TurnModePreferences.TurnMode.MANUAL;
        updateTurnModeUI();
        updateMyLocationIcon();
        
        try {
            requireContext().registerReceiver(screenStateReceiver, screenStateFilter);
        } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        // Keep sensors registered for background tracking
        sensorManager.unregisterListener(this);
        
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        try {
            requireContext().unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {}
        
        stopLocationUpdates();
    }

    private boolean isScreenOn = true;
    private final IntentFilter screenStateFilter = new IntentFilter();
    {
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
    }

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
            }
        }
    };

    private void toggleAddMarkerMode() {
        isAddingMarker = !isAddingMarker;
        if (isAddingMarker) {
            fabAddMarker.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorSuccess));
            Toast.makeText(requireContext(), R.string.tap_location, Toast.LENGTH_SHORT).show();
        } else {
            fabAddMarker.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorAccent));
            pendingMarkerPosition = null;
        }
    }

    private void showMarkerDialog(GeoPoint position) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_marker, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        TextView[] emojiViews = {
            dialogView.findViewById(R.id.emoji_pin),
            dialogView.findViewById(R.id.emoji_pushpin),
            dialogView.findViewById(R.id.emoji_home),
            dialogView.findViewById(R.id.emoji_building),
            dialogView.findViewById(R.id.emoji_restaurant),
            dialogView.findViewById(R.id.emoji_pizza),
            dialogView.findViewById(R.id.emoji_coffee),
            dialogView.findViewById(R.id.emoji_shopping),
            dialogView.findViewById(R.id.emoji_hospital),
            dialogView.findViewById(R.id.emoji_fuel),
            dialogView.findViewById(R.id.emoji_hotel),
            dialogView.findViewById(R.id.emoji_park),
            dialogView.findViewById(R.id.emoji_car),
            dialogView.findViewById(R.id.emoji_plane),
            dialogView.findViewById(R.id.emoji_star),
            dialogView.findViewById(R.id.emoji_heart),
            dialogView.findViewById(R.id.emoji_red_dot),
            dialogView.findViewById(R.id.emoji_green_dot)
        };
        
        String[] emojis = {"📍", "📌", "🏠", "🏢", "🍔", "🍕", "☕", "🛒", "🏥", "⛽", "🏨", "🏞", "🚗", "✈️", "⭐", "❤️", "🔴", "🟢"};
        
        for (int i = 0; i < emojiViews.length; i++) {
            final int index = i;
            emojiViews[i].setOnClickListener(v -> {
                selectedEmoji = emojis[index];
                for (TextView tv : emojiViews) {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                }
                emojiViews[index].setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight));
            });
        }
        
        emojiViews[0].setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight));
        
        TextInputEditText editLabel = dialogView.findViewById(R.id.editMarkerLabel);
        
        dialogView.findViewById(R.id.buttonCancelMarker).setOnClickListener(v -> {
            dialog.dismiss();
            isAddingMarker = false;
            fabAddMarker.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorAccent));
            pendingMarkerPosition = null;
        });
        
        dialogView.findViewById(R.id.buttonConfirmMarker).setOnClickListener(v -> {
            String label = editLabel.getText() != null ? editLabel.getText().toString().trim() : "";
            
            Marker marker = new Marker(position.getLatitude(), position.getLongitude(), selectedEmoji);
            if (!label.isEmpty()) {
                marker.setLabel(label);
            }
            
            markerStorage.saveMarker(marker);
            addMarkerToMap(marker);
            
            dialog.dismiss();
            isAddingMarker = false;
            fabAddMarker.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorAccent));
            Toast.makeText(requireContext(), R.string.marker_added, Toast.LENGTH_SHORT).show();
        });
        
        dialog.show();
    }

    private void addMarkerToMap(Marker marker) {
        org.osmdroid.views.overlay.Marker mapMarker = new org.osmdroid.views.overlay.Marker(mapView);
        mapMarker.setPosition(new GeoPoint(marker.getLatitude(), marker.getLongitude()));
        
        String title = marker.getEmoji();
        if (marker.getLabel() != null && !marker.getLabel().isEmpty()) {
            title = marker.getLabel() + " " + marker.getEmoji();
        }
        mapMarker.setTitle(title);
        mapMarker.setSnippet(marker.getEmoji());
        mapMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);
        
        mapMarker.setOnMarkerClickListener((m, mv) -> {
            m.showInfoWindow();
            return true;
        });

        mapMarker.setOnMarkerDragListener(new org.osmdroid.views.overlay.Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(org.osmdroid.views.overlay.Marker m) {}

            @Override
            public void onMarkerDragEnd(org.osmdroid.views.overlay.Marker m) {
                GeoPoint pos = m.getPosition();
                for (int i = 0; i < markerOverlays.size(); i++) {
                    if (markerOverlays.get(i) == m) {
                        Marker modelMarker = mapMarkers.get(i);
                        modelMarker.setLatitude(pos.getLatitude());
                        modelMarker.setLongitude(pos.getLongitude());
                        markerStorage.updateMarker(modelMarker);
                        break;
                    }
                }
            }

            @Override
            public void onMarkerDragStart(org.osmdroid.views.overlay.Marker m) {}
        });

        mapView.getOverlays().add(mapMarker);
        markerOverlays.add(mapMarker);
        mapMarkers.add(marker);
        mapView.invalidate();
    }

    private void loadMarkers() {
        List<Marker> markers = markerStorage.getAllMarkers();
        for (Marker marker : markers) {
            addMarkerToMap(marker);
        }
    }

    private void showClearAllDialog() {
        String[] options = {getString(R.string.clear_tracks_only), getString(R.string.clear_markers_only), getString(R.string.clear_everything)};
        
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_confirmation)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        clearTracksOnly();
                        Toast.makeText(requireContext(), R.string.trips_cleared, Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        clearMarkersOnly();
                        Toast.makeText(requireContext(), R.string.markers_cleared, Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        clearTracksOnly();
                        clearMarkersOnly();
                        Toast.makeText(requireContext(), R.string.everything_cleared, Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void clearTracksOnly() {
        pathPoints.clear();
        noGPSPathPoints.clear();
        destinationPoint = null;
        currentRoutePoints.clear();
        if (routingOverlay != null) {
            routingOverlay.setPoints(new ArrayList<>());
        }
        updatePathOnMap();
    }

    private void clearMarkersOnly() {
        for (org.osmdroid.views.overlay.Marker m : markerOverlays) {
            mapView.getOverlays().remove(m);
        }
        markerOverlays.clear();
        mapMarkers.clear();
        markerStorage.clearAllMarkers();
        destinationPoint = null;
        currentRoutePoints.clear();
        mapView.invalidate();
    }

    private void showSearchPathDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_path, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextInputEditText editStart = dialogView.findViewById(R.id.editStartPoint);
        TextInputEditText editDest = dialogView.findViewById(R.id.editDestination);

        dialogView.findViewById(R.id.buttonCancelSearch).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.buttonFindRoute).setOnClickListener(v -> {
            String startStr = editStart.getText() != null ? editStart.getText().toString().trim() : "";
            String destStr = editDest.getText() != null ? editDest.getText().toString().trim() : "";

            if (startStr.isEmpty() || destStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both points", Toast.LENGTH_SHORT).show();
                return;
            }

            findRoute(startStr, destStr);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void findRoute(String startStr, String destStr) {
        Toast.makeText(requireContext(), R.string.calculating_route, Toast.LENGTH_SHORT).show();
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            GeoPoint startPoint = null;
            if (startStr.equalsIgnoreCase(getString(R.string.current_location))) {
                if (lastKnownGPS != null) {
                    startPoint = new GeoPoint(lastKnownGPS.getLatitude(), lastKnownGPS.getLongitude());
                } else if (currentPosition != null) {
                    startPoint = currentPosition;
                } else {
                    // Try to get last known from provider if fragment field is null
                    startPoint = geocodeAddress(startStr);
                }
            } else {
                startPoint = geocodeAddress(startStr);
            }

            GeoPoint destPoint = geocodeAddress(destStr);

            if (startPoint == null || destPoint == null) {
                final String failedLoc = startPoint == null ? startStr : destStr;
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(requireContext(), getString(R.string.geocoding_error, failedLoc), Toast.LENGTH_LONG).show()
                    );
                }
                return;
            }

            fetchRouteFromOSRM(startPoint, destPoint);
        });
    }

    private GeoPoint geocodeAddress(String addressStr) {
        // Try built-in Geocoder first
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocationName(addressStr, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    return new GeoPoint(address.getLatitude(), address.getLongitude());
                }
            } catch (Exception ignored) {}
        }
        
        // Fallback to Nominatim API (OpenStreetMap geocoding)
        try {
            String encodedAddress = URLEncoder.encode(addressStr, "UTF-8");
            String urlString = "https://nominatim.openstreetmap.org/search?q=" + encodedAddress + "&format=json&limit=1";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "DeadReckoningPro/1.0");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject obj = jsonArray.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    return new GeoPoint(lat, lon);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }

    private void fetchRouteFromOSRM(GeoPoint start, GeoPoint end) {
        String urlString = String.format(Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=polyline",
                start.getLongitude(), start.getLatitude(),
                end.getLongitude(), end.getLatitude());

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "DeadReckoningPro/1.0");
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "OSRM Server Error: " + responseCode, Toast.LENGTH_LONG).show()
                    );
                }
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray routes = jsonResponse.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                String encodedPolyline = route.getString("geometry");
                List<GeoPoint> routePoints = decodePolyline(encodedPolyline);

                destinationPoint = end;
                currentRoutePoints.clear();
                currentRoutePoints.addAll(routePoints);

                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        routingOverlay.setPoints(routePoints);
                        mapView.invalidate();
                        
                        if (!routePoints.isEmpty()) {
                            BoundingBox boundingBox = BoundingBox.fromGeoPoints(routePoints);
                            mapView.zoomToBoundingBox(boundingBox, true, 100);
                        }
                        
                        Toast.makeText(requireContext(), "Route found", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(requireContext(), R.string.route_not_found, Toast.LENGTH_SHORT).show()
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> 
                    Toast.makeText(requireContext(), "Error fetching route: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }
    }

    private List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            GeoPoint p = new GeoPoint(lat / 100000.0, lng / 100000.0);
            poly.add(p);
        }
        return poly;
    }
}
