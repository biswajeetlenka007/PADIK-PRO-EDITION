package padik.deadreckoning.model;

import org.osmdroid.util.GeoPoint;

/**
 * Represents the state of the Inertial Navigation System at a point in time.
 */
public class INSState {
    public long timestampNs;
    
    // Position in local NED frame (meters from origin)
    public double posNorth;
    public double posEast;
    public double posDown;
    
    // Velocity in local NED frame (m/s)
    public double velNorth;
    public double velEast;
    public double velDown;
    
    // Attitude: Quaternion representing rotation from Device to Navigation (NED) frame
    // Convention: [w, x, y, z]
    public double[] quaternion = {1.0, 0.0, 0.0, 0.0};
    
    // Geographic position
    public GeoPoint geoPoint;
    public GeoPoint origin;
    
    public boolean isInitialized = false;

    // AI Status
    public String aiStatus = "MODEL_NOT_FOUND";
    public float aiSpeedMps = -1f;

    // Constraints status
    public boolean zuptActive = false;
    public boolean nhcActive = false;
    public boolean stationary = false;

    public INSState() {}
    
    public INSState(INSState other) {
        if (other == null) return;
        this.timestampNs = other.timestampNs;
        this.posNorth = other.posNorth;
        this.posEast = other.posEast;
        this.posDown = other.posDown;
        this.velNorth = other.velNorth;
        this.velEast = other.velEast;
        this.velDown = other.velDown;
        if (other.quaternion != null) {
            this.quaternion = other.quaternion.clone();
        }
        this.geoPoint = other.geoPoint;
        this.origin = other.origin;
        this.isInitialized = other.isInitialized;
        this.aiStatus = other.aiStatus;
        this.aiSpeedMps = other.aiSpeedMps;
        this.zuptActive = other.zuptActive;
        this.nhcActive = other.nhcActive;
        this.stationary = other.stationary;
    }
}
