package padik.deadreckoning.model;

public class GNSSSample {

    private long timestamp;

    private double latitude;
    private double longitude;
    private double altitude;

    private float speed;
    private float bearing;
    private float accuracy;

    private boolean hasSpeed;
    private boolean hasBearing;

    public GNSSSample(
            long timestamp,
            double latitude,
            double longitude,
            double altitude,
            float speed,
            float bearing,
            float accuracy,
            boolean hasSpeed,
            boolean hasBearing) {

        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.hasSpeed = hasSpeed;
        this.hasBearing = hasBearing;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public float getBearing() {
        return bearing;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public boolean hasSpeed() {
        return hasSpeed;
    }

    public boolean hasBearing() {
        return hasBearing;
    }
}
