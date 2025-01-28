package com.naminfo.ftpclient.core

import FileUtils.saveFileToPublicStorage
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.time.Duration

private const val TAG = "==>>FTPUtil"
class FTPUtil {
    private val ftpClient = FTPClient()

    // Connect to the FTP server in a background thread
    suspend fun connect(server: String, port: Int, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                ftpClient.connect(server, port)
                val login = ftpClient.login(username, password)
                if (login) {
                    // ftpClient.enterLocalPassiveMode() // Set the passive mode
                    Log.d("===>>>FTPUtil", "Login response: " + ftpClient.replyString)
                    return@withContext true
                }
            } catch (e: Exception) {

                Log.d(TAG, "connect: ${e.message}")
                e.printStackTrace()
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


   suspend fun downloadFile(host: String, port: Int,
                     username: String, password: String,
                     localFile: File,remoteFilePath: String): Boolean  {
     return withContext(Dispatchers.IO){

         // Connect and login to the FTP server
         ftpClient.connect(host, port)
         ftpClient.login(username, password)

         ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

         // Create output stream for the local file
         val outputStream = FileOutputStream(localFile)

         // Download the file
         val success = ftpClient.retrieveFile(remoteFilePath, outputStream)

         // Close streams and disconnect
         outputStream.close()
         ftpClient.logout()
         ftpClient.disconnect()
         success
     }
    }





    // Download file from the FTP server to a specific folder
    suspend fun downloadFile(remoteFilePath: String, localDirectoryPath: String, localFileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val remoteFilePathD =remoteFilePath
                // Ensure the local directory exists
                val localDirectory = File(localDirectoryPath)
                if (!localDirectory.exists()) {
                    localDirectory.mkdirs()
                }

                // Create the local file path
                val localFile = File(localDirectory, localFileName)
                Log.d(TAG, "downloadFile: localFile is exist =${localFile.exists()} localFileName =$localFileName")
                Log.d(TAG, "downloadFile: remoteFilePath =$remoteFilePathD localDirectoryPath =$localDirectoryPath")
                if (localFile.exists()) {

                    localFile.delete() // If file already exists, delete it
                }

                // Create an OutputStream to write the downloaded file
                val outputStream = FileOutputStream(localFile)
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                // Retrieve the file from FTP server
                val result = ftpClient.retrieveFile(remoteFilePathD, outputStream)
                val replyCode = ftpClient.replyCode
                Log.d(TAG, "Download result: $result")
                Log.d(TAG, "FTP Reply Code: $replyCode")
                Log.d(TAG, "FTP Response: " + ftpClient.replyString)
                outputStream.flush()
                outputStream.close()

                return@withContext result
            } catch (e: Exception) {
                // Log.e(TAG, "Error downloading file: ${e.printStackTrace()}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }




   suspend fun downloadFileFromFtpAndSave(
        context: Context,
        ftpHost: String,
        ftpPort: Int,
        username: String,
        password: String,
        remoteFilePath: String,
        fileName: String,
        mimeType: String,
       openFile:suspend (Boolean,Context,Uri?,String) ->Unit
    ) : Boolean {
       return withContext(Dispatchers.IO) {
           val ftpClient = FTPClient()
           try {
               // Set timeouts
               ftpClient.connectTimeout = 120_000
               ftpClient.defaultTimeout = 120_000
               ftpClient.dataTimeout = Duration.ofMillis(120_000)

               // Enable logging
               ftpClient.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out), true))

               // Connect to FTP server
               ftpClient.connect(ftpHost, ftpPort)
               ftpClient.login(username, password)
              // ftpClient.enterLocalPassiveMode()
               ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
               // Download the file into a byte array
               val outputStream = ByteArrayOutputStream()
               val success = ftpClient.retrieveFile(remoteFilePath, outputStream)

               if (success) {
                   val fileData = outputStream.toByteArray() // File data in bytes

                   // Save the file to the public Downloads folder
                   val fileUri = saveFileToPublicStorage(context, fileName, fileData, mimeType)

                   // Automatically open the file with the appropriate app
                   fileUri?.let {
                       withContext(Dispatchers.Main){openFile(success,context, it, mimeType)}
                   }

               } else {
                //   Toast.makeText(context, "Failed to download the file", Toast.LENGTH_SHORT).show()
                  withContext(Dispatchers.Main){openFile(success,context, null, mimeType)}
               }
               ftpClient.logout()
               ftpClient.disconnect()
               success
           } catch (e: Exception) {
               e.printStackTrace()
            //   Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
               withContext(Dispatchers.Main){openFile(false,context, null, mimeType)}
               false
           }

       }
   }

    // Function to save a file to the public Downloads directory
   suspend fun saveFileToPublicStorage(context: Context, fileName: String, data: ByteArray, mimeType: String): Uri? {
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
    fun openFile(context: Context, uri: Uri, mimeType: String)  {
        val intent = Intent(Intent.ACTION_VIEW)
        oldUri =uri
        oldMimeType=mimeType
        intent.setDataAndType(oldUri, oldMimeType)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
             Toast.makeText(context, "No app available to open this file. Please install a compatible app.", Toast.LENGTH_SHORT).show()
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
            "ftp://$username:$password->$server:$port/$filePath".trimEnd('/')
        } else {
            "ftp://$server:$port/$filePath".trimEnd('/')
        }
    }


    // Disconnect from the FTP server
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

companion object{
    var oldUri:Uri? =null
    var oldMimeType =""
}

}
