package padik.deadreckoning.model;

public class NavigationState {

    // Position
    public double latitude;
    public double longitude;
    public double altitude;

    // Velocity
    public double velocityNorth;
    public double velocityEast;
    public double velocityDown;

    // Attitude
    public double roll;
    public double pitch;
    public double yaw;

    // Accelerometer bias
    public double accelBiasX;
    public double accelBiasY;
    public double accelBiasZ;

    // Gyroscope bias
    public double gyroBiasX;
    public double gyroBiasY;
    public double gyroBiasZ;

    public NavigationState() {
        latitude = 0.0;
        longitude = 0.0;
        altitude = 0.0;

        velocityNorth = 0.0;
        velocityEast = 0.0;
        velocityDown = 0.0;

        roll = 0.0;
        pitch = 0.0;
        yaw = 0.0;

        accelBiasX = 0.0;
        accelBiasY = 0.0;
        accelBiasZ = 0.0;

        gyroBiasX = 0.0;
        gyroBiasY = 0.0;
        gyroBiasZ = 0.0;
    }
}
