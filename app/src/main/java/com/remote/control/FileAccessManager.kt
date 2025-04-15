package com.remote.control

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Manages access to files on the device for remote browsing
 */
class FileAccessManager(private val context: Context) {
    companion object {
        private const val TAG = "FileAccessManager"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB max for file content
    }

    /**
     * Data class to represent file information
     */
    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    /**
     * Lists files in the specified directory
     * @param path The directory path to list files from, or empty for root storage
     * @return List of FileItem objects
     */
    fun listFiles(path: String): List<FileItem> {
        val directory = if (path.isNotEmpty()) {
            File(path)
        } else {
            Environment.getExternalStorageDirectory()
        }
        
        if (!directory.exists() || !directory.isDirectory) {
            Log.e(TAG, "Invalid directory path: $path")
            return emptyList()
        }
        
        return try {
            Log.d(TAG, "Listing files in: ${directory.absolutePath}")
            
            val files = directory.listFiles() ?: emptyArray()
            files.map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            emptyList()
        }
    }

    /**
     * Gets the content of a file
     * @param path The path to the file
     * @return The file content as a byte array, or null if the file cannot be read
     */
    fun getFileContent(path: String): ByteArray? {
        val file = File(path)
        
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "Invalid file path: $path")
            return null
        }
        
        if (file.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "File is too large: ${file.length()} bytes")
            return null
        }
        
        return try {
            Log.d(TAG, "Reading file content: ${file.absolutePath}")
            
            val inputStream = FileInputStream(file)
            val bytes = ByteArray(file.length().toInt())
            inputStream.read(bytes)
            inputStream.close()
            bytes
        } catch (e: IOException) {
            Log.e(TAG, "Error reading file content", e)
            null
        }
    }

    /**
     * Gets the available storage directories
     * @return List of storage directories
     */
    fun getStorageDirectories(): List<FileItem> {
        val directories = mutableListOf<FileItem>()
        
        // Add primary external storage
        val externalStorage = Environment.getExternalStorageDirectory()
        directories.add(
            FileItem(
                name = "Internal Storage",
                path = externalStorage.absolutePath,
                isDirectory = true,
                size = 0,
                lastModified = externalStorage.lastModified()
            )
        )
        
        // Add app-specific storage
        val appStorage = context.getExternalFilesDir(null)?.parentFile
        if (appStorage != null) {
            directories.add(
                FileItem(
                    name = "App Storage",
                    path = appStorage.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = appStorage.lastModified()
                )
            )
        }
        
        return directories
    }
}
