package com.remote.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Service that captures the screen and sends the frames to the WebSocketServer
 */
class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "remote_control_channel"
        
        // Screen capture parameters
        private const val SCREEN_DENSITY_DPI = 160 // Medium density
        private const val IMAGE_QUALITY = 50 // JPEG quality (0-100)
        private const val FRAMES_PER_SECOND = 10
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var webSocketServer: WebSocketServer? = null
    private var displayWidth = 0
    private var displayHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private val frameExecutor = Executors.newSingleThreadExecutor()

    private val screenCaptureRunnable = object : Runnable {
        override fun run() {
            captureScreen()
            handler.postDelayed(this, 1000 / FRAMES_PER_SECOND.toLong())
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize the WebSocket server
        webSocketServer = WebSocketServer(applicationContext).apply {
            start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            
            if (resultCode != 0 && resultData != null) {
                startScreenCapture(resultCode, resultData)
            }
        }
        
        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        
        // Scale down resolution to reduce bandwidth
        val scaleFactor = 2.0f // Adjust this for quality/performance balance
        displayWidth = (metrics.widthPixels / scaleFactor).toInt()
        displayHeight = (metrics.heightPixels / scaleFactor).toInt()
        
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        imageReader = ImageReader.newInstance(
            displayWidth, displayHeight, PixelFormat.RGBA_8888, 2
        )
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            displayWidth, displayHeight, SCREEN_DENSITY_DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        
        // Start capturing frames
        handler.post(screenCaptureRunnable)
        
        Log.d(TAG, "Screen capture started: $displayWidth x $displayHeight")
    }

    private fun captureScreen() {
        imageReader?.acquireLatestImage()?.use { image ->
            try {
                val bitmap = imageToBitmap(image)
                val jpegBytes = compressBitmap(bitmap)
                
                // Send the image to all connected clients
                webSocketServer?.sendScreenUpdate(jpegBytes)
                
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screen", e)
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * displayWidth
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(
            displayWidth + rowPadding / pixelStride,
            displayHeight, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Remote Control Service"
            val descriptionText = "Screen capture for remote control"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Remote Control Active")
            .setContentText("Your screen is being shared")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        // Stop screen capture
        handler.removeCallbacks(screenCaptureRunnable)
        
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        // Stop WebSocket server
        webSocketServer?.stop()
        
        // Shutdown executor
        frameExecutor.shutdown()
        
        super.onDestroy()
    }
}
