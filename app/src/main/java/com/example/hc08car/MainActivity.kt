package com.example.hc08car

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSIONS = 100
        private const val SCAN_MS = 8000L

        // Service shown in the supplied LightBlue screenshot.
        private val PREFERRED_SERVICE =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    }

    private lateinit var status: TextView
    private lateinit var scanButton: Button
    private lateinit var deviceSpinner: Spinner
    private lateinit var charSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var commandBox: EditText
    private lateinit var sendButton: Button
    private lateinit var logView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var selectedDevice: BluetoothDevice? = null

    private data class DeviceItem(val device: BluetoothDevice, val label: String) {
        override fun toString() = label
    }

    private data class CharItem(
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic
    ) {
        override fun toString(): String {
            val p = characteristic.properties
            val props = buildList {
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("W")
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WNR")
                if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("N")
                if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("R")
            }.joinToString("/")
            return "${characteristic.uuid}  [$props]"
        }
    }

    private val devices = mutableListOf<DeviceItem>()
    private val chars = mutableListOf<CharItem>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = try { d.name } catch (_: SecurityException) { null }
            val displayName = if (name.isNullOrBlank()) "Unnamed BLE device" else name
            val address = try { d.address } catch (_: SecurityException) { "unknown" }
            runOnUiThread {
                if (devices.none { it.device.address == address }) {
                    devices.add(DeviceItem(d, "$displayName  ($address)"))
                    refreshDeviceSpinner()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                scanning = false
                scanButton.text = "Scan"
                setStatus("BLE scan failed: $errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { setStatus("Connected. Discovering services...") }
                try { g.discoverServices() } catch (_: SecurityException) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    setStatus("Disconnected")
                    connectButton.text = "Connect"
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            runOnUiThread {
                chars.clear()

                // Prefer the FFF0 service seen in the supplied screenshot.
                val ordered = g.services.sortedBy { if (it.uuid == PREFERRED_SERVICE) 0 else 1 }
                for (service in ordered) {
                    for (c in service.characteristics) {
                        val p = c.properties
                        if ((p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                            (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            chars.add(CharItem(service, c))
                        }
                    }
                }

                refreshCharSpinner()
                val total = g.services.sumOf { it.characteristics.size }
                setStatus("Ready. ${g.services.size} services, $total characteristics. " +
                        "${chars.size} writable characteristic(s).")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val text = characteristic.value?.toString(Charsets.UTF_8) ?: ""
            runOnUiThread { appendLog("RX ${characteristic.uuid}: ${text.ifEmpty { characteristic.value?.hex() ?: "" }}") }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val text = value.toString(Charsets.UTF_8)
            runOnUiThread { appendLog("RX ${characteristic.uuid}: ${text.ifEmpty { value.hex() }}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBlePermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }

        status = TextView(this).apply {
            text = "HC-08 Car Controller"
            textSize = 18f
            setPadding(0, 0, 0, 12)
        }
        root.addView(status)

        val scanRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scanButton = Button(this).apply {
            text = "Scan"
            setOnClickListener { scan() }
        }
        scanRow.addView(scanButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(scanRow)

        deviceSpinner = Spinner(this)
        root.addView(label("BLE device"))
        root.addView(deviceSpinner)

        connectButton = Button(this).apply {
            text = "Connect"
            setOnClickListener { connectSelected() }
        }
        root.addView(connectButton)

        root.addView(label("Writable characteristic"))
        charSpinner = Spinner(this)
        root.addView(charSpinner)

        root.addView(label("Car control"))
        root.addView(makeDPad())

        commandBox = EditText(this).apply {
            hint = "Raw command, e.g. f"
            setSingleLine(true)
        }
        val rawRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rawRow.addView(commandBox, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                val s = commandBox.text.toString()
                if (s.isNotEmpty()) sendText(s)
            }
        }
        rawRow.addView(sendButton)
        root.addView(rawRow)

        root.addView(label("Log"))
        logView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 6, 0, 0)
        }
        val scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun label(s: String): TextView =
        TextView(this).apply {
            text = s
            textSize = 13f
            setPadding(0, 8, 0, 2)
        }

    private fun makeDPad(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val up = holdButton("▲") { "f" }
        val mid = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        val left = holdButton("◀") { "l" }
        val stop = holdButton("STOP") { "s" }
        val right = holdButton("▶") { "r" }
        val down = holdButton("▼") { "b" }

        mid.addView(left)
        mid.addView(stop)
        mid.addView(right)
        box.addView(up)
        box.addView(mid)
        box.addView(down)
        return box
    }

    private fun holdButton(caption: String, command: () -> String): Button {
        return Button(this).apply {
            text = caption
            minWidth = 90
            minHeight = 60
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sendText(command())
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (caption != "STOP") sendText("s")
                        v.performClick()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun requestBlePermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    @SuppressLint("MissingPermission")
    private fun scan() {
        if (!hasBlePermission()) {
            requestBlePermissions()
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (scanning) {
            scanner?.stopScan(scanCallback)
            scanning = false
            scanButton.text = "Scan"
            setStatus("Scan stopped")
            return
        }

        devices.clear()
        refreshDeviceSpinner()
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(scanCallback)
        scanning = true
        scanButton.text = "Stop"
        setStatus("Scanning for BLE devices...")
        handler.postDelayed({
            if (scanning) {
                scanner?.stopScan(scanCallback)
                scanning = false
                scanButton.text = "Scan"
                setStatus("Scan complete. ${devices.size} device(s) found.")
            }
        }, SCAN_MS)
    }

    private fun hasBlePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSelected() {
        if (!hasBlePermission()) {
            requestBlePermissions()
            return
        }
        val item = devices.getOrNull(deviceSpinner.selectedItemPosition) ?: run {
            setStatus("Select a BLE device first")
            return
        }

        gatt?.close()
        gatt = null
        selectedDevice = item.device
        setStatus("Connecting to ${item.label}...")
        connectButton.text = "Connecting..."
        gatt = item.device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun refreshDeviceSpinner() {
        deviceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, devices
        )
    }

    private fun refreshCharSpinner() {
        charSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, chars
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendText(text: String) {
        val g = gatt
        val item = chars.getOrNull(charSpinner.selectedItemPosition)
        if (g == null || item == null) {
            setStatus("Not connected / no writable characteristic selected")
            return
        }

        val data = text.toByteArray(Charsets.UTF_8)
        val c = item.characteristic
        val writeType =
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        c.writeType = writeType
        c.value = data

        @Suppress("DEPRECATION")
        g.writeCharacteristic(c)

        appendLog("TX ${c.uuid}: ${text.replace("\n", "\\n")}")
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun appendLog(s: String) {
        logView.append("$s\n")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        super.onDestroy()
    }
}

private fun ByteArray.hex(): String =
    joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
