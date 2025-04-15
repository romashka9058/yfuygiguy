package com.remote.control

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method

/**
 * Manages injection of input events (touches, key presses) into the Android system
 * Uses reflection to access the InputManager service
 */
class InputEventManager(private val context: Context) {
    companion object {
        private const val TAG = "InputEventManager"
    }

    // Variables for reflection objects
    private var inputManagerClass: Class<*>? = null
    private var inputManagerObject: Any? = null
    private var injectInputEventMethod: Method? = null
    
    // Constants for injection
    private val INJECT_INPUT_EVENT_MODE_ASYNC = 0
    private val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 1
    
    // Touch event action constants
    val ACTION_DOWN = MotionEvent.ACTION_DOWN
    val ACTION_MOVE = MotionEvent.ACTION_MOVE
    val ACTION_UP = MotionEvent.ACTION_UP

    init {
        try {
            // Get InputManager class and instance via reflection
            inputManagerClass = Class.forName("android.hardware.input.InputManager")
            val getInstanceMethod = inputManagerClass?.getMethod("getInstance")
            inputManagerObject = getInstanceMethod?.invoke(null)
            
            // Get injectInputEvent method
            injectInputEventMethod = inputManagerClass?.getMethod(
                "injectInputEvent", InputEvent::class.java, Int::class.java
            )
            
            Log.d(TAG, "InputEventManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize InputEventManager", e)
        }
    }

    /**
     * Injects a touch event at the specified coordinates
     * @param action The touch action (DOWN, MOVE, UP)
     * @param x The x-coordinate as a ratio of screen width (0.0-1.0)
     * @param y The y-coordinate as a ratio of screen height (0.0-1.0)
     */
    fun injectTouchEvent(action: Int, x: Float, y: Float) {
        try {
            // Convert normalized coordinates to actual screen coordinates
            val displayMetrics = context.resources.displayMetrics
            val screenX = x * displayMetrics.widthPixels
            val screenY = y * displayMetrics.heightPixels
            
            Log.d(TAG, "Injecting touch event: action=$action, x=$screenX, y=$screenY")
            
            // Obtain the current time for the event
            val now = SystemClock.uptimeMillis()
            
            // Create a MotionEvent
            val event = MotionEvent.obtain(
                now, now, action, screenX, screenY, 1.0f, 0.0f, 0,
                1.0f, 1.0f, 0, 0
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            
            // Inject the event
            injectInputEvent(event)
            
            // Recycle the event
            event.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting touch event", e)
        }
    }

    /**
     * Injects text as a series of key events
     * @param text The text to inject
     */
    fun injectText(text: String) {
        try {
            Log.d(TAG, "Injecting text: $text")
            
            // Use KeyCharacterMap to convert characters to key events
            val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            
            // Convert the string to key events
            val events = keyCharacterMap.getEvents(text.toCharArray())
            
            // Inject each key event
            events?.forEach { event ->
                injectInputEvent(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting text", e)
        }
    }

    /**
     * Injects a key event
     * @param keyCode The key code to inject
     * @param down True for key down, false for key up
     */
    fun injectKeyEvent(keyCode: Int, down: Boolean) {
        try {
            Log.d(TAG, "Injecting key event: keyCode=$keyCode, down=$down")
            
            val now = SystemClock.uptimeMillis()
            val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            
            val event = KeyEvent(
                now, now, action, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD
            )
            
            injectInputEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting key event", e)
        }
    }

    /**
     * Injects an input event using the InputManager
     * @param event The event to inject
     */
    private fun injectInputEvent(event: InputEvent) {
        try {
            injectInputEventMethod?.invoke(
                inputManagerObject,
                event,
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting input event", e)
        }
    }

    /**
     * Checks if the app has proper permissions to inject input events
     * This requires either root access or the app to be a system app
     * @return True if injection is likely to work, false otherwise
     */
    fun canInjectInputEvents(): Boolean {
        return inputManagerObject != null && injectInputEventMethod != null
    }
}
