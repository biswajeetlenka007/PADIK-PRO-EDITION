package padik.deadreckoning.model;

import java.util.Locale;

import padik.deadreckoning.model.MotionMode;

public class IMUGNSSSample {
    public long timestampNs;
    public long timestampMs;

    // Raw Accelerometer
    public float accelX = Float.NaN;
    public float accelY = Float.NaN;
    public float accelZ = Float.NaN;

    // Gyroscope
    public float gyroX = Float.NaN;
    public float gyroY = Float.NaN;
    public float gyroZ = Float.NaN;

    // Magnetometer
    public float magX = Float.NaN;
    public float magY = Float.NaN;
    public float magZ = Float.NaN;

    // Gravity
    public float gravityX = Float.NaN;
    public float gravityY = Float.NaN;
    public float gravityZ = Float.NaN;

    // Linear Acceleration
    public float linearAccelX = Float.NaN;
    public float linearAccelY = Float.NaN;
    public float linearAccelZ = Float.NaN;

    // GNSS
    public double latitude = Double.NaN;
    public double longitude = Double.NaN;
    public double altitude = Double.NaN;
    public float gnssSpeed = Float.NaN;
    public float gnssBearing = Float.NaN;
    public float horizontalAccuracy = Float.NaN;
    public float verticalAccuracy = Float.NaN;
    public long locationTimestampMs = 0;
    public long gnssAgeMs = -1;

    // Navigation State
    public int stepCount = 0;
    public float heading = Float.NaN;
    public String motionMode = "STATIONARY";

    public String toCsvRow() {
        return String.format(Locale.US,
            "%d,%d," + // timestamps (2)
            "%f,%f,%f," + // accel (3)
            "%f,%f,%f," + // gyro (3)
            "%f,%f,%f," + // mag (3)
            "%f,%f,%f," + // gravity (3)
            "%f,%f,%f," + // linear accel (3)
            "%.8f,%.8f,%.2f,%.3f,%.2f,%.2f,%.2f,%d,%d," + // gnss + age (9)
            "%d,%.2f,%s", // pdr (3)
            timestampNs, timestampMs,
            accelX, accelY, accelZ,
            gyroX, gyroY, gyroZ,
            magX, magY, magZ,
            gravityX, gravityY, gravityZ,
            linearAccelX, linearAccelY, linearAccelZ,
            latitude, longitude, altitude, gnssSpeed, gnssBearing, horizontalAccuracy, verticalAccuracy, locationTimestampMs, gnssAgeMs,
            stepCount, heading, motionMode
        );
    }

    public static String getCsvHeader() {
        return "timestamp_ns,timestamp_ms," +
               "accel_x,accel_y,accel_z," +
               "gyro_x,gyro_y,gyro_z," +
               "mag_x,mag_y,mag_z," +
               "gravity_x,gravity_y,gravity_z," +
               "linear_accel_x,linear_accel_y,linear_accel_z," +
               "latitude,longitude,altitude,gnss_speed,gnss_bearing,horizontal_accuracy,vertical_accuracy,location_timestamp_ms,gnss_age_ms," +
               "step_count,heading,motion_mode";
    }
}
