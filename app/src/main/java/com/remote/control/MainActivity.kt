package com.remote.control

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.math.BigInteger
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteOrder

/**
 * Main activity for the Android remote control server
 * Handles the initial setup and starts the services required for remote control
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RemoteControl"
        private const val REQUEST_MEDIA_PROJECTION = 1
    }

    private lateinit var ipAddressText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var isServerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipAddressText = findViewById(R.id.ip_address)
        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)

        startButton.setOnClickListener {
            startScreenCapture()
        }

        stopButton.setOnClickListener {
            stopServer()
        }

        updateIPAddress()
        updateUIState()
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startServer(resultCode, data)
            } else {
                Toast.makeText(this, "Screen capturing permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startServer(resultCode: Int, data: Intent) {
        // Start the screen capture service
        val screenCaptureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        startService(screenCaptureIntent)
        
        isServerRunning = true
        updateUIState()
        
        Toast.makeText(this, "Remote control server started", Toast.LENGTH_SHORT).show()
    }

    private fun stopServer() {
        // Stop the screen capture service
        stopService(Intent(this, ScreenCaptureService::class.java))
        
        isServerRunning = false
        updateUIState()
        
        Toast.makeText(this, "Remote control server stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateIPAddress() {
        val ipAddress = getLocalIpAddress()
        ipAddressText.text = if (ipAddress != null) {
            getString(R.string.ip_address_format, ipAddress, WebSocketServer.PORT)
        } else {
            getString(R.string.ip_unknown)
        }
    }

    private fun updateUIState() {
        if (isServerRunning) {
            statusText.text = getString(R.string.status_running)
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
        } else {
            statusText.text = getString(R.string.status_stopped)
            startButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            var ipAddress = wifiManager.connectionInfo.ipAddress

            // Convert little-endian to big-endian if needed
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                ipAddress = Integer.reverseBytes(ipAddress)
            }

            val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
            val ipAddressString = InetAddress.getByAddress(ipByteArray).hostAddress
            return ipAddressString
        } catch (ex: UnknownHostException) {
            Log.e(TAG, "Error getting IP address", ex)
            return null
        }
    }

    override fun onDestroy() {
        if (isServerRunning) {
            stopServer()
        }
        super.onDestroy()
    }
}
