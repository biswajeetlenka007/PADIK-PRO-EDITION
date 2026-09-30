package padik.deadreckoning.model;

public class TurnEvent {
    
    public enum TurnType {
        LEFT,
        RIGHT,
        SLIGHT_LEFT,
        SLIGHT_RIGHT,
        UTURN
    }
    
    private TurnType type;
    private double headingChange;
    private int stepCount;
    private long timestamp;
    private double latitude;
    private double longitude;
    
    public TurnEvent(TurnType type, double headingChange, int stepCount, double lat, double lon) {
        this.type = type;
        this.headingChange = Math.abs(headingChange);
        this.stepCount = stepCount;
        this.timestamp = System.currentTimeMillis();
        this.latitude = lat;
        this.longitude = lon;
    }
    
    public static TurnType determineTurnType(double headingDelta) {
        double absDelta = Math.abs(headingDelta);
        
        if (absDelta > 150 && absDelta < 210) {
            return TurnType.UTURN;
        } else if (absDelta > 45 && absDelta < 135) {
            return headingDelta > 0 ? TurnType.LEFT : TurnType.RIGHT;
        } else if (absDelta > 15 && absDelta < 45) {
            return headingDelta > 0 ? TurnType.SLIGHT_LEFT : TurnType.SLIGHT_RIGHT;
        } else {
            return null;
        }
    }
    
    public String getType() {
        switch (type) {
            case LEFT: return "Gauche";
            case RIGHT: return "Droite";
            case SLIGHT_LEFT: return "Leger gauche";
            case SLIGHT_RIGHT: return "Leger droite";
            case UTURN: return "Demi-tour";
            default: return "Inconnu";
        }
    }
    
    public String getTurnSymbol() {
        switch (type) {
            case LEFT: return "⬅";
            case RIGHT: return "➡";
            case SLIGHT_LEFT: return "↰";
            case SLIGHT_RIGHT: return "↱";
            case UTURN: return "🔄";
            default: return "";
        }
    }
    
    // Getters
    public TurnType getTypeEnum() { return type; }
    public double getHeadingChange() { return headingChange; }
    public int getStepCount() { return stepCount; }
    public long getTimestamp() { return timestamp; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
