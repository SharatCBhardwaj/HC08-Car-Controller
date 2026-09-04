# HC-08 Car Controller

Native Android BLE controller for an HC-08-style BLE UART/car module.

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

## Important detail from the supplied LightBlue screenshot

The device exposes a custom service whose UUID appears to be:

`0000fff0-0000-1000-8000-00805f9b34fb`

The screenshot also shows several characteristics, with one characteristic supporting:

`Read, Write Without Response, Notify`

The app therefore does NOT assume a fixed characteristic UUID. It discovers the GATT table and lets you select the writable characteristic. This is safer because HC-08 firmware variants can expose different characteristic UUIDs.

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
