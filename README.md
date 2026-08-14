# Smartwatch Sensor Data Collector (Maintained Fork)

An Android **Wear OS** app that collects data from a smartwatch's sensors,
stores it locally in SQLite, and records audio from the microphone — for later
analysis (e.g. activity/health datasets).

> **About this fork**
> This repository is based on the original project by Mahesh Molabanti.
> The original code did not build/run as-is. We fixed the runtime issues and
> added the missing Gradle build files so the project compiles and runs.
>
> - The original project description is preserved in
>   [`README.original.md`](README.original.md).
> - **Going forward, only this `README.md` is maintained.** Please update this
>   file (not the original) when the project changes.

## What the app does

Tap a single button to start/stop collection. While running, the app:

- Registers **8 sensors**: Accelerometer, Gyroscope, Heart Rate, Step Counter,
  Light, Gravity, Magnetic Field, and Rotation Vector (orientation).
- Buffers sensor readings in memory and **flushes them to SQLite every 60s** on
  a background thread.
- **Records microphone audio** to a timestamped `.wav` file in the app's private
  storage.

### Source layout

| File | Responsibility |
|------|----------------|
| `senseiworld/src/main/java/com/example/helloworld/presentation/MainActivity.kt` | UI, permissions, start/stop toggle, lifecycle |
| `.../SensorDataCollector.kt` | Sensor registration, buffering, periodic storage, mic recording |
| `.../SensorCsvWriter.kt` | Per-sensor CSV writer, one numbered folder per collection session |

## Changes made in this fork

Compared to the original ("Original code package" commit), this fork fixes the
bugs that prevented the app from running and makes it buildable:

1. **Permission flow (`MainActivity`)** — switched to the
   `ActivityResultContracts` API for the `BODY_SENSORS` permission, so data
   collection actually starts *after* the user grants access. The legacy
   `requestPermissions` call had no result callback, so collection never began.
2. **Executor lifecycle (`SensorDataCollector`)** — the background executor was
   shut down on stop and never recreated, so a stop→start (e.g.
   `onPause`→`onResume`) silently stopped saving data. It is now recreated on
   restart.
3. **Thread leak (`SensorDataCollector`)** — `storeSensorData()` spawned a new
   thread pool on every 60s tick and never shut it down. It now reuses a single
   member executor.
4. **Build tooling** — added the Gradle wrapper, build scripts, and a
   `.gitignore` so the project builds in Android Studio out of the box.

## Prerequisites

- Android Studio
- A Wear OS smartwatch (or emulator)
- Minimum API level 26 (Android 8.0 Oreo)

## Permissions

| Permission | Purpose |
|------------|---------|
| `BODY_SENSORS` | Access sensors such as heart rate |
| `RECORD_AUDIO` | Capture microphone audio |
| `WAKE_LOCK` | Keep collecting while the screen is off |

## Build & run

```bash
git clone https://github.com/Lenllw/SmartWatchDataCollectApp.git
```

1. Open the project in Android Studio (it will sync Gradle and generate a local
   `local.properties` pointing at your Android SDK).
2. Build and run the `senseiworld` module on a Wear OS device/emulator.
3. Tap the button to start collecting; tap again to stop. Each collection is
   written to its own numbered folder under `sensor_data/` in the app's external
   files dir, with one CSV per sensor (e.g. `sensor_data/1/Accelerometer_1.csv`).
   Every row is one sensor event: `timestampMillis` followed by all of that
   event's values (accelerometer → `timestampMillis,value_0,value_1,value_2`).
   Microphone audio is saved as `.wav` files in the app's private storage.

## Credits

- Original author: Mahesh Molabanti — see [`README.original.md`](README.original.md).
- This maintained fork: [Lenllw](https://github.com/Lenllw).