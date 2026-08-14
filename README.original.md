# Smartwatch Sensor Data Collection and Transmission

This project is an Android application designed to collect sensor data from a smartwatch running Wear OS. The app gathers data from various sensors, such as accelerometer, gyroscope, heart rate monitor, and more, and provides functionality to record and transfer this data for further analysis.

# Features

- Sensor Data Collection
  - Collect data from multiple sensors, including:
    - Accelerometer
    - Gyroscope
    - Heart Rate Monitor
    - Step Counter
    - Light Sensor
    - Magnetic Field Sensor
    - Gravity Sensor
    - Orientation Sensor (Rotation Vector)

- Periodic Data Storage
  - Aggregates sensor data and stores it locally in an SQLite database for efficient retrieval and analysis.

- Microphone Recording
  - Records audio data and saves it in a `.3gp` format with a timestamped filename.

- Data Transmission
  - Moves collected files to public storage for easier access and sharing.

# Prerequisites

- Android Studio installed on your machine.
- Wear OS-enabled smartwatch.
- Minimum API level: 26 (Android 8.0, Oreo).

# Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/maheshmolabanti/smartwatch-sensor-collector.git
   ```
2. Open the project in Android Studio.
3. Build and run the app on a Wear OS-enabled smartwatch.

# Permissions

The app requires the following permissions:

- `RECORD_AUDIO`: To capture audio data using the microphone.
- `WRITE_EXTERNAL_STORAGE` (optional): To move files to public storage.
- `BODY_SENSORS`: To access sensor data like heart rate.

Ensure you grant these permissions for the app to function correctly.

# Usage

1. Start Data Collection:
   - Launch the app and tap the button to start collecting sensor data.
   - The app will begin gathering data and storing it in the SQLite database.

2. Stop Data Collection:
   - Tap the stop button to end the data collection process.
   - All aggregated data will be saved, and microphone recording will stop.

3. Access Stored Data:
   - The app saves files in the app's private directory or moves them to public storage for sharing.

# Repository Contents

- `app/src/main/java/com/example/helloworld/presentation/`:
  - Contains the main implementation of the `SensorDataCollector` class.
- `app/src/main/res/`:
  - UI resources for the app.
- `SensorDBHelper.kt`:
  - SQLite database helper for managing sensor data storage.

# License

This project is licensed under the [MIT License](LICENSE).

---

# Contributing

Feel free to fork this repository, open issues, and submit pull requests. Contributions are welcome!

# Contact

For questions or collaborations, reach out to Mahesh Molabanti at [maheshmolabanti@gmail.com](mailto:maheshmolabanti@gmail.com).
