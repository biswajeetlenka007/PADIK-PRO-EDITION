package padik.deadreckoning.model;

import org.osmdroid.util.GeoPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class Trip {
    
    private String id;
    private String name;
    private long startTime;
    private long endTime;
    private double totalDistance;
    private int totalSteps;
    private double startLatitude;
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;
    private List<GeoPoint> pathPoints;
    private List<Long> pathTimestamps;
    private List<TurnEvent> turnEvents;
    private boolean isExported;
    
    public Trip() {
        this.id = UUID.randomUUID().toString();
        this.pathPoints = new ArrayList<>();
        this.pathTimestamps = new ArrayList<>();
        this.turnEvents = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }
    
    public Trip(String name) {
        this();
        this.name = name;
    }
    
    private void ensureListsInitialized() {
        if (pathPoints == null) pathPoints = new ArrayList<>();
        if (pathTimestamps == null) pathTimestamps = new ArrayList<>();
        if (turnEvents == null) turnEvents = new ArrayList<>();
    }
    
    public void addPoint(GeoPoint point) {
        ensureListsInitialized();
        if (point != null) {
            pathPoints.add(point);
            pathTimestamps.add(System.currentTimeMillis());
        }
    }
    
    public void addTurnEvent(TurnEvent event) {
        ensureListsInitialized();
        if (event != null) {
            turnEvents.add(event);
        }
    }
    
    public void finish() {
        ensureListsInitialized();
        this.endTime = System.currentTimeMillis();
        if (!pathPoints.isEmpty()) {
            GeoPoint first = pathPoints.get(0);
            GeoPoint last = pathPoints.get(pathPoints.size() - 1);
            this.startLatitude = first.getLatitude();
            this.startLongitude = first.getLongitude();
            this.endLatitude = last.getLatitude();
            this.endLongitude = last.getLongitude();
        }
    }
    
    public long getDuration() {
        if (endTime > startTime) {
            return endTime - startTime;
        } else if (startTime > 0) {
            return System.currentTimeMillis() - startTime;
        }
        return 0;
    }
    
    public String getFormattedDuration() {
        long duration = getDuration();
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format(Locale.US, "%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%dm %ds", minutes, seconds % 60);
        } else {
            return String.format(Locale.US, "%ds", seconds);
        }
    }
    
    private long getTimestampForPoint(int index) {
        if (pathTimestamps != null && index < pathTimestamps.size()) {
            return pathTimestamps.get(index);
        }
        // Fallback for old data: estimate based on start time
        return startTime;
    }
    
    public String toCSV() {
        ensureListsInitialized();
        StringBuilder sb = new StringBuilder();
        sb.append("Trip ID,Name,Start Time,End Time,Duration,Distance (m),Steps,Start Lat,Start Lon,End Lat,End Lon\n");
        sb.append(String.format(Locale.US, "%s,%s,%d,%d,%s,%.2f,%d,%.6f,%.6f,%.6f,%.6f\n",
            id, name != null ? name : "", startTime, endTime, getFormattedDuration(), totalDistance, totalSteps,
            startLatitude, startLongitude, endLatitude, endLongitude));
        
        sb.append("\nPath Points\n");
        sb.append("Latitude,Longitude,Altitude,Time\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (int i = 0; i < pathPoints.size(); i++) {
            GeoPoint point = pathPoints.get(i);
            long time = getTimestampForPoint(i);
            double altitude = !Double.isNaN(point.getAltitude()) ? point.getAltitude() : 0;
            sb.append(String.format(Locale.US, "%.6f,%.6f,%.2f,%s\n", 
                point.getLatitude(), point.getLongitude(), altitude, sdf.format(new Date(time))));
        }
        
        sb.append("\nTurn Events\n");
        sb.append("Turn Type,Heading Change,Step Count\n");
        for (TurnEvent event : turnEvents) {
            sb.append(String.format(Locale.US, "%s,%.1f,%d\n", 
                event.getType(), event.getHeadingChange(), event.getStepCount()));
        }
        
        return sb.toString();
    }
    
    public String toGPX() {
        ensureListsInitialized();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"DeadReckoningPro\" \n");
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\" \n");
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        sb.append("  <metadata>\n");
        if (name != null && !name.isEmpty()) {
            sb.append(String.format("    <name>%s</name>\n", escapeXml(name)));
        }
        sb.append(String.format("    <time>%s</time>\n", sdf.format(new Date(startTime))));
        sb.append("  </metadata>\n");
        
        sb.append("  <trk>\n");
        String trkName = name != null && !name.isEmpty() ? name : "Trip " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(startTime));
        sb.append(String.format("    <name>%s</name>\n", escapeXml(trkName)));
        sb.append("    <trkseg>\n");
        
        for (int i = 0; i < pathPoints.size(); i++) {
            GeoPoint point = pathPoints.get(i);
            long time = getTimestampForPoint(i);
            double lat = point.getLatitude();
            double lon = point.getLongitude();
            double ele = !Double.isNaN(point.getAltitude()) ? point.getAltitude() : 0.0;
            
            sb.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n", lat, lon));
            sb.append(String.format(Locale.US, "        <ele>%.2f</ele>\n", ele));
            sb.append(String.format("        <time>%s</time>\n", sdf.format(new Date(time))));
            sb.append("      </trkpt>\n");
        }
        
        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>");
        
        return sb.toString();
    }
    
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
    
    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
    
    public double getStartLatitude() { return startLatitude; }
    public void setStartLatitude(double lat) { this.startLatitude = lat; }
    
    public double getStartLongitude() { return startLongitude; }
    public void setStartLongitude(double lon) { this.startLongitude = lon; }
    
    public double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(double lat) { this.endLatitude = lat; }
    
    public double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(double lon) { this.endLongitude = lon; }
    
    public List<GeoPoint> getPathPoints() { 
        ensureListsInitialized();
        return pathPoints; 
    }
    public void setPathPoints(List<GeoPoint> pathPoints) { this.pathPoints = pathPoints; }
    
    public List<TurnEvent> getTurnEvents() { 
        ensureListsInitialized();
        return turnEvents; 
    }
    public void setTurnEvents(List<TurnEvent> turnEvents) { this.turnEvents = turnEvents; }
    
    public boolean isExported() { return isExported; }
    public void setExported(boolean exported) { isExported = exported; }
}
