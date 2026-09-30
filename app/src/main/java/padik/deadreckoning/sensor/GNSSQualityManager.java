package padik.deadreckoning.sensor;

import android.location.Location;

public class GNSSQualityManager {
    
    public enum GNSSQuality {
        GOOD,
        DEGRADED,
        LOST
    }
    
    public GNSSQuality assess(Location location) {
        if (location == null) return GNSSQuality.LOST;
        
        float accuracy = location.getAccuracy();
        if (accuracy < 10) return GNSSQuality.GOOD;
        if (accuracy < 25) return GNSSQuality.DEGRADED;
        return GNSSQuality.LOST;
    }
}
