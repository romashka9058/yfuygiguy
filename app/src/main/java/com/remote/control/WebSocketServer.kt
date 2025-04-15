package com.remote.control

import android.content.Context
import android.util.Base64
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer as JavaWebSocketServer
import java.net.InetSocketAddress
import org.json.JSONObject
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket server that handles communication with client devices
 * Receives control commands and sends screen updates
 */
class WebSocketServer(private val context: Context) : JavaWebSocketServer(InetSocketAddress(PORT)) {

    companion object {
        const val PORT = 8000
        private const val TAG = "WebSocketServer"
        
        // Message types
        private const val TYPE_SCREEN_UPDATE = "screen_update"
        private const val TYPE_TOUCH_EVENT = "touch_event"
        private const val TYPE_TEXT_INPUT = "text_input"
        private const val TYPE_KEY_EVENT = "key_event"
        private const val TYPE_FILE_REQUEST = "file_request"
        private const val TYPE_DEVICE_INFO = "device_info"
    }

    // Map to keep track of connected clients
    private val clients = ConcurrentHashMap<WebSocket, String>()
    
    // Input event manager for processing touch and key events
    private val inputEventManager = InputEventManager(context)
    
    // File access manager for handling file operations
    private val fileAccessManager = FileAccessManager(context)

    override fun onStart() {
        Log.d(TAG, "WebSocket server started on port $port")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val clientAddress = conn.remoteSocketAddress.address.hostAddress
        Log.d(TAG, "New connection from: $clientAddress")
        
        clients[conn] = clientAddress
        
        // Send device information to the new client
        sendDeviceInfo(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val clientAddress = clients.remove(conn) ?: "unknown"
        Log.d(TAG, "Connection closed from: $clientAddress (code: $code, reason: $reason, remote: $remote)")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val clientAddress = clients[conn] ?: "unknown"
            Log.d(TAG, "Message from $clientAddress: $message")
            
            val json = JSONObject(message)
            val type = json.getString("type")
            
            when (type) {
                TYPE_TOUCH_EVENT -> {
                    val action = json.getInt("action")
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    inputEventManager.injectTouchEvent(action, x, y)
                }
                
                TYPE_TEXT_INPUT -> {
                    val text = json.getString("text")
                    inputEventManager.injectText(text)
                }
                
                TYPE_KEY_EVENT -> {
                    val keyCode = json.getInt("keyCode")
                    val down = json.getBoolean("down")
                    inputEventManager.injectKeyEvent(keyCode, down)
                }
                
                TYPE_FILE_REQUEST -> {
                    val requestType = json.getString("requestType")
                    val path = json.optString("path", "")
                    
                    when (requestType) {
                        "list" -> {
                            val fileList = fileAccessManager.listFiles(path)
                            sendFileList(conn, path, fileList)
                        }
                        "content" -> {
                            val fileContent = fileAccessManager.getFileContent(path)
                            sendFileContent(conn, path, fileContent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        val clientAddress = conn?.let { clients[it] } ?: "server"
        Log.e(TAG, "Error for $clientAddress: ${ex.message}", ex)
    }

    /**
     * Sends a screen update to all connected clients
     */
    fun sendScreenUpdate(jpegBytes: ByteArray) {
        if (connections.isEmpty()) return
        
        // Convert image to Base64 for sending over JSON
        val base64Image = Base64.encodeToString(jpegBytes, Base64.DEFAULT)
        
        val json = JSONObject().apply {
            put("type", TYPE_SCREEN_UPDATE)
            put("image", base64Image)
            put("timestamp", System.currentTimeMillis())
        }
        
        broadcast(json.toString())
    }

    /**
     * Sends device information to a specific client
     */
    private fun sendDeviceInfo(conn: WebSocket) {
        val displayMetrics = context.resources.displayMetrics
        
        val json = JSONObject().apply {
            put("type", TYPE_DEVICE_INFO)
            put("displayWidth", displayMetrics.widthPixels)
            put("displayHeight", displayMetrics.heightPixels)
            put("displayDensity", displayMetrics.density)
        }
        
        conn.send(json.toString())
    }

    /**
     * Sends a file list response to a specific client
     */
    private fun sendFileList(conn: WebSocket, path: String, files: List<FileAccessManager.FileItem>) {
        val fileArray = files.map { file ->
            JSONObject().apply {
                put("name", file.name)
                put("path", file.path)
                put("isDirectory", file.isDirectory)
                put("size", file.size)
                put("lastModified", file.lastModified)
            }
        }
        
        val json = JSONObject().apply {
            put("type", "file_list_response")
            put("path", path)
            put("files", fileArray)
        }
        
        conn.send(json.toString())
    }

    /**
     * Sends file content to a specific client
     */
    private fun sendFileContent(conn: WebSocket, path: String, content: ByteArray?) {
        val json = JSONObject().apply {
            put("type", "file_content_response")
            put("path", path)
            
            if (content != null) {
                put("content", Base64.encodeToString(content, Base64.DEFAULT))
                put("success", true)
            } else {
                put("success", false)
                put("error", "Could not read file content")
            }
        }
        
        conn.send(json.toString())
    }
}
