package padik.deadreckoning.model;

import java.util.UUID;

public class Marker {
    private String id;
    private double latitude;
    private double longitude;
    private String label;
    private String emoji;
    private long createdAt;

    public Marker() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public Marker(double latitude, double longitude, String emoji) {
        this();
        this.latitude = latitude;
        this.longitude = longitude;
        this.emoji = emoji;
    }

    public Marker(double latitude, double longitude, String emoji, String label) {
        this(latitude, longitude, emoji);
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
