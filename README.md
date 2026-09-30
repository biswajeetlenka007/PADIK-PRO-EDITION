# PADIK PRO EDITION

*A comprehensive GPS-free navigation and tracking app for Android using Pedestrian Dead Reckoning (PDR), 6-DOF INS (Inertial Navigation System), and AI-based speed estimation. Built with Android, OSMDroid, and TensorFlow Lite.*

---

## 🚀 Key Features

### 🧠 AI-Powered Sensor Fusion & Tracking
- **Dead Reckoning (PDR):** Continue tracking your position when GPS is unavailable using accelerometer-based step counting and sensor fusion heading estimation.
- **6-DOF INS (Vehicle Mode):** Switch to Vehicle mode for 6-Degree-Of-Freedom Inertial Navigation System tracking.
- **AI Speed Estimation:** Uses a TensorFlow Lite model (`TFLiteSpeedModel`) to estimate movement speed from raw IMU data, independent of GPS.
- **Dynamic & Static Step Counting:** Advanced peak detection step counting with hardware sensor fallback.
- **GPS Calibration & Correction:** Continuous scale factor and heading bias calibration when GPS is available to correct IMU drift.

### 🗺️ Advanced Mapping & Routing
- **OSMDroid Map Integration:** Smooth map rendering with OpenStreetMap tiles.
- **OSRM Routing:** Built-in address geocoding and pathfinding using the OSRM routing engine.
- **360° Map Rotation:** Two-finger intuitive map rotation with rotation gesture overlays.
- **Custom Emoji Markers:** Add persistent custom markers to the map with over 18 emoji icons and custom labels. Supports drag-to-reposition.
- **Uncertainty Ellipse:** Visualizes position uncertainty which shrinks upon GPS lock and grows during prolonged dead reckoning.

### 🚶 Navigation Modes
- **Auto Mode:** Automatically tracks direction based on gyroscope and magnetometer data.
- **Manual Mode:** Override headings using left/right/U-turn arrows or a custom 360° visual compass dial.
- **No GPS (DR Only) Mode:** Force the app to rely purely on dead reckoning and IMU data—perfect for testing or extreme environments.

### 📊 Data Management & Background Tracking
- **Trip History & Export:** Save past trips and export them in GPX or CSV formats.
- **Foreground Service:** Continuous tracking even when the app is in the background or the screen is off.
- **Wake Lock Support:** Prevents the device from sleeping during active tracking.

---

## 🛠️ Technical Details

- **Map Provider:** OSMDroid (OpenStreetMap)
- **Routing Engine:** OSRM (Project OSRM)
- **AI & Math:** TensorFlow Lite (TFLite), EJML (Efficient Java Matrix Library), AChartEngine
- **Location:** Google Play Services FusedLocationProvider
- **Target SDK:** 34 (Android 14)
- **Min SDK:** 23 (Android 6.0)
- **Java Compatibility:** Java 17

### Core Sensors Utilized
| Sensor | Purpose |
|--------|---------|
| `TYPE_GRAVITY` | Stable gravity vector for orientation and INS |
| `TYPE_MAGNETIC_FIELD` | Compass heading |
| `TYPE_GYROSCOPE` | Rate of turn for short-term heading |
| `TYPE_LINEAR_ACCELERATION` | Step detection and movement |
| `TYPE_STEP_DETECTOR` | Hardware step counting (if available) |

---

## 📦 Installation & Build

### Prerequisites
- Android Studio
- Java JDK 17
- Android SDK (Min API 23, Target API 34)

### Build Steps
1. Clone the repository and open the project in Android Studio.
2. Wait for Gradle to sync (uses `com.android.application` plugin).
3. The project requires no external API keys (OSMDroid and OSRM are open source).
4. Build and install the APK via USB debugging or emulator.

### Permissions
The app requests runtime permissions for:
- Precise & Background Location
- Physical Activity (Step counting)
- High Sampling Rate Sensors
- Post Notifications (For background foreground service)

---

## 💡 Usage Guide

1. **Start Tracking:** Open the app, grant permissions, wait for a GNSS lock (or tap "No GPS" to force DR mode), and hit **Start**.
2. **Switching Modes:** Tap the "Mode: Auto" button to toggle between Walking (Auto/Manual) and Vehicle (6-DOF INS) modes.
3. **Adding Markers:** Tap the orange **+** button to place a marker, select an emoji, and drag it if needed.
4. **Routing:** Tap the Route (Map) icon, enter a start and destination address, and let OSRM draw a path for you.
5. **Diagnostics:** Long-press the Turn indicator or Speed value to toggle a diagnostics panel showing real-time AI vs GPS speed metrics.
6. **Clearing Data:** Tap the trash icon to clear your current path lines, clear all markers, or clear everything at once.

---

## 🤝 Contributing & License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Feel free to fork this project, submit pull requests, and report issues.

*Note: This application is intended for research, educational purposes, and testing GPS-denied navigation. PDR and INS systems inherently drift over time without absolute reference updates.*
