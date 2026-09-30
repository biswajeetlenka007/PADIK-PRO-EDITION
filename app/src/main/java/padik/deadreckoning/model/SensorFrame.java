package padik.deadreckoning.model;

/**
 * Represents the coordinate frame for sensor data.
 */
public enum SensorFrame {
    BODY,       // X-forward, Y-right, Z-down
    NAVIGATION, // North, East, Down (NED)
    GEODETIC    // Lat, Lon, Alt
}
