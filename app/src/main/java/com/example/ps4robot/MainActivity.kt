package com.example.ps4robot

import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    private val espIp = "192.168.4.1"
    private val direction = AtomicReference("stop")
    private lateinit var status: TextView
    private lateinit var worker: ScheduledExecutorService
    @Volatile private var connected = false

    private var up = false
    private var down = false
    private var left = false
    private var right = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            text = "PS4 ROBOT\n\n1. Pair stik PS4 ke HP via Bluetooth\n2. Sambungkan Wi-Fi ke PS4-ROBOT\n3. Gerakkan D-pad atau joystick kiri"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            addView(status, LinearLayout.LayoutParams(-1, -1))
        }
        setContentView(root)

        worker = Executors.newSingleThreadScheduledExecutor()
        worker.scheduleAtFixedRate({ sendCommand(direction.get()) }, 0, 180, TimeUnit.MILLISECONDS)
    }

    private fun updateDirection() {
        direction.set(when {
            up -> "forward"
            down -> "backward"
            left -> "left"
            right -> "right"
            else -> "stop"
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> up = pressed
            KeyEvent.KEYCODE_DPAD_DOWN -> down = pressed
            KeyEvent.KEYCODE_DPAD_LEFT -> left = pressed
            KeyEvent.KEYCODE_DPAD_RIGHT -> right = pressed
            else -> return super.dispatchKeyEvent(event)
        }
        updateDirection()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            event.action == MotionEvent.ACTION_MOVE) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            up = y < -0.5f
            down = y > 0.5f
            left = x < -0.5f
            right = x > 0.5f
            updateDirection()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            up = false; down = false; left = false; right = false
            updateDirection()
        }
    }

    private fun sendCommand(dir: String) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("http://$espIp/cmd?dir=$dir").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 700
                readTimeout = 700
                useCaches = false
            }
            val code = connection.responseCode
            connected = code == 200
            runOnUiThread {
                status.text = "PS4 ROBOT\n\nStatus ESP32: ${if (connected) "TERHUBUNG" else "MENUNGGU"}\nPerintah: $dir\nHTTP: $code\n\nWi-Fi: PS4-ROBOT\nIP ESP32: $espIp\n\nGunakan D-pad atau joystick kiri stik PS4."
            }
        } catch (_: Exception) {
            connected = false
            runOnUiThread {
                status.text = "PS4 ROBOT\n\nStatus ESP32: MENUNGGU\nPerintah: $dir\n\nPeriksa Wi-Fi PS4-ROBOT dan ESP32-C3."
            }
        } finally {
            connection?.disconnect()
        }
    }

    override fun onDestroy() {
        if (::worker.isInitialized) worker.shutdownNow()
        super.onDestroy()
    }
}
