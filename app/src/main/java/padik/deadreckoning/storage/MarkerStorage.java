package padik.deadreckoning.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import padik.deadreckoning.model.Marker;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MarkerStorage {
    
    private static final String PREFS_NAME = "MarkerStorage";
    private static final String MARKERS_KEY = "markers";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public MarkerStorage(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    public void saveMarker(Marker marker) {
        List<Marker> markers = getAllMarkers();
        markers.add(marker);
        saveAllMarkers(markers);
    }
    
    public void updateMarker(Marker marker) {
        List<Marker> markers = getAllMarkers();
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).getId().equals(marker.getId())) {
                markers.set(i, marker);
                break;
            }
        }
        saveAllMarkers(markers);
    }
    
    public void deleteMarker(String id) {
        List<Marker> markers = getAllMarkers();
        markers.removeIf(m -> m.getId().equals(id));
        saveAllMarkers(markers);
    }
    
    public List<Marker> getAllMarkers() {
        String json = prefs.getString(MARKERS_KEY, "[]");
        Type type = new TypeToken<ArrayList<Marker>>(){}.getType();
        List<Marker> markers = gson.fromJson(json, type);
        return markers != null ? markers : new ArrayList<>();
    }
    
    public Marker getMarker(String id) {
        List<Marker> markers = getAllMarkers();
        for (Marker marker : markers) {
            if (marker.getId().equals(id)) {
                return marker;
            }
        }
        return null;
    }
    
    public void clearAllMarkers() {
        prefs.edit().remove(MARKERS_KEY).apply();
    }
    
    private void saveAllMarkers(List<Marker> markers) {
        String json = gson.toJson(markers);
        prefs.edit().putString(MARKERS_KEY, json).apply();
    }
    
    public int getMarkerCount() {
        return getAllMarkers().size();
    }
}
