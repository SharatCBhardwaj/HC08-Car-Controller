# HC-08 Car Controller

An Android application for controlling an Arduino-based car through an HC-08 Bluetooth Low Energy (BLE) module.

This is the first working version of the project. The current focus is establishing reliable BLE communication and basic car control. The next stage will focus on improving the controller UI.
---

## Project Status

**Current version:** `v0.1.0`

**Status:** Working BLE car controller

### Confirmed working

- Android application running on a physical Android phone
- HC-08 BLE device scanning
- BLE connection
- GATT service discovery
- Command transmission to the car
- Forward, backward, left, right and stop controls
- Raw command transmission
- TX/RX logging for debugging

---
## What it does

- Scans for BLE devices, including HC-08.
- Connects with GATT.
- Discovers services and characteristics.
- Automatically selects the first characteristic that supports WRITE or WRITE_NO_RESPONSE.
- Lets you choose a different writable characteristic if needed.
- Sends ASCII commands:
  - F = `f` forward
  - B = `b` backward
  - L = `l` left
  - R = `r` right
  - STOP = `s`
- The directional buttons send their command on press and `s` on release.
- A raw command box is included for testing other commands.
The Android application communicates directly with the HC-08 module. No Internet connection or cloud service is required for the current version.
## Important detail from the supplied LightBlue screenshot

The device exposes a custom service whose UUID appears to be:

`0000fff0-0000-1000-8000-00805f9b34fb`

The screenshot also shows several characteristics, with one characteristic supporting:

  `Read, Write Without Response, Notify`

- The app therefore does NOT assume a fixed characteristic UUID. It discovers the GATT table and lets you select the writable characteristic. This is safer because HC-08 firmware variants can expose different characteristic UUIDs.
---

## BLE Configuration

During testing, the following BLE service and characteristic were confirmed.

### Service UUID

```text
0000fff0-0000-1000-8000-00805f9b34fb
```
### Command / Write Characteristic
```text
0000ffe1-0000-1000-8000-00805f9b34fb
```
The FFE1 characteristic is the confirmed characteristic used to send car-control commands.
## Car Commands

The Arduino car currently uses the following commands:

### Action	Command
Forward	f
Backward	b
Left	l
Right	r
Stop	s

The directional controls send the corresponding command while pressed and send s when released.

### Android Application

The application is written in:

- Kotlin
- Android SDK
- Android Bluetooth Low Energy APIs

The current application provides:

- BLE device scanning
- Device selection
- GATT connection
- Service and characteristic discovery
- BLE command transmission
- Directional controls
- Raw command input
- TX/RX debugging log
- Current UI

The current UI is primarily a functional testing interface.

It contains BLE connection controls, command controls, raw command input and communication logs.

The UI will be redesigned in the next development phase to provide a more practical car-controller interface.

## Development Roadmap
v0.1.0 
- First Working Controller
  - BLE scanning
  - HC-08 connection
  - GATT discovery
  - Confirmed command characteristic
  - Basic car commands
  - Directional controls
  - Debug logging
- v0.1.1 
  - Documentation
  - Document confirmed BLE UUIDs
  - Document command protocol
  - Update project documentation
- v0.2.0 
Controller UI

## Planned improvements:

Cleaner controller layout
Large directional controls
Dedicated STOP control
Connection status indicator
Improved phone usability
Move debugging controls into a separate Debug section
Use the confirmed FFE1 characteristic directly
Future Development

### Possible future features include:

Improved connection handling
Automatic HC-08 detection
Connection/reconnection handling
Car speed control
Additional vehicle commands
Configurable controls
Better visual feedback
## Project documentation and hardware diagrams
### Project Structure
```text
HC08-Car-Controller/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/hc08car/
│           │       └── MainActivity.kt
│           │
│           ├── res/
│           │   └── values/
│           │       ├── strings.xml
│           │       └── styles.xml
│           │
│           └── AndroidManifest.xml
│
├── gradle/
│   └── wrapper/
│
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```
## Version History
Version	Description
v0.1.0	First working HC-08 BLE car controller
v0.1.1	Documentation and confirmed BLE configuration
v0.2.0	Planned controller UI redesign
GitHub

This project is being developed incrementally with Git version control.

Each major development stage will be committed and tagged so that known-working versions can be preserved.
## Build

Open this folder in Android Studio, let Gradle sync, then run on an Android phone with BLE.

For Android 12+, grant Nearby Devices permission.

If the HC-08 does not appear:
1. Power the car/module.
2. Make sure the module is advertising.
3. Turn Bluetooth on.
4. Press Scan.
5. Tap the device.
6. If necessary, select the writable characteristic manually.

## Arduino side

The app sends single ASCII bytes. Your existing firmware can keep using the same command parser, e.g.:

`f` forward, `b` backward, `l` left, `r` right, `s` stop.
## License

This project is licensed under the MIT License.
See the `LICENSE` file for details.
