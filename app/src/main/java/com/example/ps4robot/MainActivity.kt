package com.example.ps4robot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {
    companion object {
        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        private val CHAR_UUID = UUID.fromString("abcdefab-1234-1234-1234-abcdefabcdef")
        private const val DEVICE_NAME = "PS4ROBOT-C3"
    }

    private lateinit var adapter: BluetoothAdapter
    private lateinit var status: TextView
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val a = manager.adapter
        if (a == null) { finish(); return }
        adapter = a

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply {
            text = "Status: Belum terhubung"
            textSize = 18f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(status)
        addButton(layout, "HUBUNGKAN ESP32") { connectEsp32() }
        addButton(layout, "MAJU") { send("UP") }
        addButton(layout, "MUNDUR") { send("DOWN") }
        addButton(layout, "KIRI") { send("LEFT") }
        addButton(layout, "KANAN") { send("RIGHT") }
        addButton(layout, "STOP") { send("STOP") }
        addButton(layout, "TOMBOL A") { send("A") }
        addButton(layout, "TOMBOL B") { send("B") }
        addButton(layout, "TOMBOL X") { send("X") }
        addButton(layout, "TOMBOL Y") { send("Y") }
        setContentView(layout)
    }

    private fun addButton(layout: LinearLayout, title: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = title
            setOnClickListener { action() }
        })
    }

    private fun hasPermissions() =
        if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun askPermissions() {
        val p = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        requestPermissions(p, 100)
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 100 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) connectEsp32()
        else if (code == 100) status.text = "Izin Bluetooth diperlukan"
    }

    @SuppressLint("MissingPermission")
    private fun connectEsp32() {
        if (!hasPermissions()) { askPermissions(); return }
        if (!adapter.isEnabled) { status.text = "Aktifkan Bluetooth HP"; return }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { status.text = "BLE scanner tidak tersedia"; return }
        status.text = "Mencari $DEVICE_NAME..."
        scanning = true
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, result: ScanResult) {
            val name = try { result.device.name } catch (_: SecurityException) { null }
            if (name == DEVICE_NAME && scanning) {
                scanning = false
                adapter.bluetoothLeScanner?.stopScan(this)
                runOnUiThread { status.text = "ESP32 ditemukan, menghubungkan..." }
                gatt = result.device.connectGatt(this@MainActivity, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }
        override fun onScanFailed(code: Int) {
            scanning = false
            runOnUiThread { status.text = "Scan gagal: $code" }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, state: Int) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { status.text = "Terhubung! Mencari layanan..." }
                g.discoverServices()
            } else {
                characteristic = null
                g.close()
                if (gatt === g) gatt = null
                runOnUiThread { status.text = "Terputus dari ESP32" }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) return
            characteristic = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            runOnUiThread {
                status.text = if (characteristic != null) "TERHUBUNG ke ESP32-C3!" else "Layanan BLE tidak ditemukan"
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, statusCode: Int) {
            runOnUiThread { status.text = if (statusCode == BluetoothGatt.GATT_SUCCESS) "Perintah terkirim" else "Gagal mengirim" }
        }
    }

    @SuppressLint("MissingPermission")
    private fun send(command: String) {
        if (!hasPermissions()) { askPermissions(); return }
        val g = gatt
        val c = characteristic
        if (g == null || c == null) { status.text = "Hubungkan ESP32 terlebih dahulu"; return }
        val bytes = command.toByteArray(Charsets.UTF_8)
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION") c.value = bytes
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION") g.writeCharacteristic(c)
        }
        status.text = if (ok) "Mengirim: $command" else "Gagal mengirim perintah"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return super.dispatchKeyEvent(event)
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "UP"
            KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            else -> null
        }
        if (command != null) { send(command); return true }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        try {
            if (scanning && hasPermissions()) adapter.bluetoothLeScanner?.stopScan(scanCallback)
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {}
        gatt = null
        super.onDestroy()
    }
}
