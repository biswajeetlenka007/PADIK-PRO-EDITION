package padik.deadreckoning.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class StepCounterPreferences {

    private static final String PREFS_NAME = "StepCounterPrefs";
    private static final String KEY_STEP_MODE = "step_mode";

    public enum StepMode {
        DYNAMIC("dynamic"),
        ANDROID("android"),
        STATIC("static");

        private final String value;

        StepMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static StepMode fromValue(String value) {
            for (StepMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return DYNAMIC;
        }
    }

    private final SharedPreferences prefs;

    public StepCounterPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public StepMode getStepMode() {
        String value = prefs.getString(KEY_STEP_MODE, StepMode.DYNAMIC.getValue());
        return StepMode.fromValue(value);
    }

    public void setStepMode(StepMode mode) {
        prefs.edit().putString(KEY_STEP_MODE, mode.getValue()).apply();
    }

    public boolean useDynamic() {
        return getStepMode() == StepMode.DYNAMIC;
    }

    public boolean useAndroid() {
        return getStepMode() == StepMode.ANDROID;
    }

    public boolean useStatic() {
        return getStepMode() == StepMode.STATIC;
    }
}
