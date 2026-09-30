package padik.deadreckoning.navigation;

import padik.deadreckoning.model.MotionMode;

public class MotionModeDetector {

    private MotionMode currentMode = MotionMode.WALKING;

    public MotionMode getCurrentMode() {
        return currentMode;
    }

    public void setMode(MotionMode mode) {
        if (mode != null) {
            currentMode = mode;
        }
    }

    public boolean isWalking() {
        return currentMode == MotionMode.WALKING;
    }

    public boolean isVehicle() {
        return currentMode == MotionMode.VEHICLE;
    }

    public boolean isStationary() {
        return currentMode == MotionMode.STATIONARY;
    }
}
