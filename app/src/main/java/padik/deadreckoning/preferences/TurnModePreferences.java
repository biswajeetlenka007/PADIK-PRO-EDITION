package padik.deadreckoning.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class TurnModePreferences {

    private static final String PREFS_NAME = "TurnModePrefs";
    private static final String KEY_TURN_MODE = "turn_mode";
    private static final String KEY_MANUAL_TURN_COUNT = "manual_turn_count";

    public enum TurnMode {
        AUTO("auto"),
        MANUAL("manual");

        private final String value;

        TurnMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static TurnMode fromValue(String value) {
            for (TurnMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return AUTO;
        }
    }

    private final SharedPreferences prefs;

    public TurnModePreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public TurnMode getTurnMode() {
        String value = prefs.getString(KEY_TURN_MODE, TurnMode.AUTO.getValue());
        return TurnMode.fromValue(value);
    }

    public void setTurnMode(TurnMode mode) {
        prefs.edit().putString(KEY_TURN_MODE, mode.getValue()).apply();
    }

    public void toggleTurnMode() {
        TurnMode current = getTurnMode();
        TurnMode newMode = (current == TurnMode.AUTO) ? TurnMode.MANUAL : TurnMode.AUTO;
        setTurnMode(newMode);
    }

    public int getManualTurnCount() {
        return prefs.getInt(KEY_MANUAL_TURN_COUNT, 0);
    }

    public void incrementManualTurnCount() {
        int count = getManualTurnCount();
        prefs.edit().putInt(KEY_MANUAL_TURN_COUNT, count + 1).apply();
    }

    public void resetManualTurnCount() {
        prefs.edit().putInt(KEY_MANUAL_TURN_COUNT, 0).apply();
    }
}
