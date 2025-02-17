package com.naminfo.ftpclient.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.time.Duration

private const val TAG = "FTPUtil"

class FTPUtil {
    private val ftpClient = FTPClient()

    suspend fun connect(server: String, port: Int, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                ftpClient.connect(server, port)
                val login = ftpClient.login(username, password)
                if (login) {
                    Log.d(TAG, "Login successful")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}", e)
            }
            return@withContext false
        }
    }

    suspend fun uploadFile(
        localFilePath: String,
        remoteFilePath: String = "",
        sender: String, receiver: String,
        fileType: String, //audio,video,
        mimeType: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val localFile = File(localFilePath)
                if (!localFile.exists() || !localFile.canRead()) {
                    Log.e(TAG, "Invalid file: $localFilePath")
                    return@withContext false
                }
                // Ensure the file is not being used by another process
                if (isFileLocked(localFile)) {
                    Log.e(TAG, "File is locked or in use by another process: $localFilePath")
                    return@withContext false
                }

                val remoteDir = "$sender/$receiver/$fileType"
                ensureDirectories(remoteDir)
                val remotePath = "$remoteDir/${remoteFilePath}"
                Log.d(TAG, "uploadFile: localFile.name:${localFile.name} ,remotePath:$remotePath , remoteDir:$remoteDir")
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                FileInputStream(localFile).use { inputStream ->
                    val result = ftpClient.storeFile(remotePath, inputStream)
                    Log.d(TAG, "Upload result: $result")
                    val replyCode = ftpClient.replyCode
                    Log.d(TAG, "FTP Reply Code: $replyCode")
                    Log.d(TAG, "FTP Response: ${ftpClient.replyString}")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}", e)
            }
            return@withContext false
        }
    }


    suspend fun uploadFile(localFilePath: String, remoteFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val localFile = File(localFilePath)

                // Check if the local file exists
                if (!localFile.exists()) {
                    Log.e(TAG, "Local file does not exist at path: $localFilePath")
                    return@withContext false
                }

                // Check if the file is readable
                if (!localFile.canRead()) {
                    Log.e(TAG, "Local file is not readable: $localFilePath")
                    return@withContext false
                }

                // Ensure the file is not being used by another process
                if (isFileLocked(localFile)) {
                    Log.e(TAG, "File is locked or in use by another process: $localFilePath")
                    return@withContext false
                }

                // Get file size
                val fileSize = localFile.length()
                Log.d(TAG, "File size: $fileSize bytes")

                // Open file input stream
                val inputStream = FileInputStream(localFile)

                // Configure FTP client for binary file transfer
                //  ftpClient.enterLocalPassiveMode() // Optional: Enter passive mode
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                // Set timeout for connection and data transfer
                ftpClient.connectTimeout = 10000 // 10 seconds
                ftpClient.dataTimeout = Duration.ofSeconds(10000) // 10 seconds
                // Upload the file
                val result = ftpClient.storeFile(remoteFilePath, inputStream)
                val replyCode = ftpClient.replyCode
                Log.d(TAG, "Upload result: $result")
                Log.d(TAG, "FTP Reply Code: $replyCode")
                Log.d(TAG, "FTP Response: ${ftpClient.replyString}")

                inputStream.close()

                // Return the result of the upload
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file: ${e.message}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }

    /**
     * Helper function to check if a file is locked or in use by another process.
     */
    private fun isFileLocked(file: File): Boolean {
        return try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.close()
            false // File is not locked
        } catch (e: Exception) {
            true // File is locked
        }
    }


    suspend fun downloadFile(
        host: String, port: Int = 21,
        username: String, password: String,
        sender: String, receiver: String,
        fileType: String,//audio,video
        mimeType: String, // image/*,audio/*
        fileName: String,
        localDir: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val remoteFilePath = "$sender/$receiver/$mimeType/$fileName"
                val localDirectory = File(localDir)
                if (!localDirectory.exists()) {
                    localDirectory.mkdirs()
                }
                val localFile = File(localDir, fileName)
                // Ensure the local directory exists

                if (localFile.exists()) {
                    localFile.delete() // If file already exists, delete it
                }
                Log.d(TAG, "downloadFile: sender:$sender,receiver:$receiver,mimeType:$mimeType")
                Log.d(
                    TAG,
                    "downloadFile: fileName:$fileName ,remoteFilePath:$remoteFilePath,localDir:$localDir ,localDirectory:$localDirectory,localFile:$localFile"
                )
                // Connect and login to the FTP server
                ftpClient.connect(host, port)
                ftpClient.login(username, password)

                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                FileOutputStream(localFile).use { outputStream ->
                    val result = ftpClient.retrieveFile(remoteFilePath, outputStream)
                    Log.d(TAG, "Download result: $result")
                    ftpClient.logout()
                    ftpClient.disconnect()
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
            }
            ftpClient.logout()
            ftpClient.disconnect()
            return@withContext false
        }
    }

    suspend fun downloadFileFromFtpAndSave(
        context: Context,
        ftpHost: String,
        ftpPort: Int,
        username: String,
        password: String,
        sender: String, receiver: String,
        remoteFilePath: String,
        fileName: String,
        fileType: String, //audio,video
        mimeType: String,
        openFile: suspend (Boolean, Context, Uri?, String) -> Unit
    ): Boolean {

        return withContext(Dispatchers.IO) {
            val ftpClient = FTPClient()
            try {
                val remotePath = "$sender/$receiver/$fileType/$remoteFilePath"
                Log.d(
                    TAG,
                    "downloadFileFromFtpAndSave() called with:  ftpHost = $ftpHost, ftpPort = $ftpPort, username = $username, password = $password, sender = $sender, receiver = $receiver, remoteFilePath = $remoteFilePath, remotePath =$remotePath , fileName = $fileName, fileType = $fileType, mimeType = $mimeType"
                )
                // Set timeouts
                ftpClient.connectTimeout = 120_000
                ftpClient.defaultTimeout = 120_000
                ftpClient.dataTimeout = Duration.ofMillis(120_000)

                // Enable logging
                ftpClient.addProtocolCommandListener(
                    PrintCommandListener(
                        PrintWriter(System.out),
                        true
                    )
                )

                // Connect to FTP server
                ftpClient.connect(ftpHost, ftpPort)
                ftpClient.login(username, password)
                // ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                // Download the file into a byte array
                val outputStream = ByteArrayOutputStream()
                val success = ftpClient.retrieveFile(remotePath, outputStream)

                if (success) {
                    val fileData = outputStream.toByteArray() // File data in bytes

                    // Save the file to the public Downloads folder
                    val fileUri = saveFileToPublicStorage(context, fileName, fileData, mimeType)

                    // Automatically open the file with the appropriate app
                    fileUri?.let {
                        withContext(Dispatchers.Main) { openFile(success, context, it, mimeType) }
                    }

                } else {
                    //   Toast.makeText(context, "Failed to download the file", Toast.LENGTH_SHORT).show()
                    withContext(Dispatchers.Main) { openFile(success, context, null, mimeType) }
                }
                ftpClient.logout()
                ftpClient.disconnect()
                success
            } catch (e: Exception) {
                e.printStackTrace()
                //   Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                withContext(Dispatchers.Main) { openFile(false, context, null, mimeType) }
                false
            }

        }
    }

    // Function to save a file to the public Downloads directory
    private suspend fun saveFileToPublicStorage(
        context: Context,
        fileName: String,
        data: ByteArray,
        mimeType: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { fileUri ->
            try {
                contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    outputStream.write(data)
                    outputStream.flush()
                    delay(1000)
                    outputStream.close()
                }
                //   Toast.makeText(context, "File saved to Downloads: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                // Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        }
        return uri
    }

    // Function to open the file with the appropriate app
    fun openFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        oldUri = uri
        oldMimeType = mimeType
        intent.setDataAndType(oldUri, oldMimeType)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "No app available to open this file. Please install a compatible app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // FTPUtil class
    suspend fun listFiles(remoteDirectory: String): List<String> {
        return withContext(Dispatchers.IO) {
            val fileList = mutableListOf<String>()
            try {
                ftpClient.changeWorkingDirectory(remoteDirectory)
                val files = ftpClient.listFiles()

                for (file in files) {
                    if (file.isFile) {
                        fileList.add(file.name)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing files: ${e.message}")
                e.printStackTrace()
            }
            return@withContext fileList
        }
    }

    fun makeFtpUrl(
        username: String,
        password: String,
        server: String,
        port: Int = 21, // Default FTP port
        filePath: String = "" // Path to file or directory
    ): String {
        return if (username.isNotEmpty() && password.isNotEmpty()) {
            Log.d(
                TAG,
                "makeFtpUrl() called with: username = $username, password = $password, server = $server, port = $port, filePath = $filePath"
            )
            "ftp://$username:$password->$server:$port/$filePath".trimEnd('/')
        } else {
            "ftp://$server:$port/$filePath".trimEnd('/')
        }
    }


    private fun ensureDirectories(path: String) {
        val parts = path.split('/')
        var currentPath = ""
        for (part in parts) {
            currentPath += "$part/"
            ftpClient.makeDirectory(currentPath)
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Disconnection error: ${e.message}", e)
            }

        }
    }

    companion object {
        var oldUri: Uri? = null
        var oldMimeType = ""
    }

}
