package com.naminfo.ftpclient.core

import FileUtils
import FileUtils.getFileTypeAndExt
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.naminfo.ftpclient.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "==>>SelectFiles"

class SelectFiles(private val context: Context) {

    fun chooseFiles(onIntentReady: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*",
                    "video/*",
                    "audio/*",
                    "application/pdf",
                    "text/plain",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        onIntentReady(intent)
    }

    suspend fun uploadFiles(
        server: String,
        username: String,
        password: String,
        selectedFileUri: Uri,
        getFilePath: (Uri) -> String?,
        ftpUtil: FTPUtil,
        getURL: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            validateCredentials(server, username, password)
            val filePath = getFilePath(selectedFileUri)
                ?: throw IllegalArgumentException("File path is null or invalid")

            if (!ftpUtil.connect(server, 21, username, password)) {
                throw IllegalStateException("Unable to connect to FTP server")
            }

            val (fileType, fileExtension) = getFileTypeAndExt(context, filePath, selectedFileUri)
            Log.d(
                TAG,
                "uploadFiles: fileType =$fileType ,fileExtension =$fileExtension filePath =$filePath"
            )
            val remoteFilePath = createRemoteFilePath(filePath, fileType, fileExtension)

            Log.d(TAG, "uploadFiles:remoteFilePath =$remoteFilePath")
            val uploaded = ftpUtil.uploadFile(filePath, remoteFilePath)


            if (uploaded) {
                val ftpUrl =
                    ftpUtil.makeFtpUrl(username, password, server, filePath = remoteFilePath)
                getURL(ftpUrl)
            }
            ftpUtil.disconnect()
            uploaded
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading files: ${e.message}", e)
            false
        }
    }

    suspend fun downloadFiles(
        file: FileItem,
        server: String,
        username: String,
        password: String,
        ftpUtil: FTPUtil,
        onDownloadComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            validateCredentials(server, username, password)

            val localDirectoryPath = getDownloadDirectoryPath()
            val localFileName = file.name

            if (!ftpUtil.connect(server, 21, username, password)) {
                throw IllegalStateException("Unable to connect to FTP server")
            }

            val downloadSuccess = ftpUtil.downloadFile(file.name, localDirectoryPath, localFileName)
            ftpUtil.disconnect()

            withContext(Dispatchers.Main) {
                onDownloadComplete(downloadSuccess)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading files: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onDownloadComplete(false)
            }
        }
    }

    suspend fun downloadFileViaFtpURL(
        ftpUrl: String,
        localFilePath: String,
        ftpUtil: FTPUtil,
        onDownloadComplete: (Boolean, Context, Uri?, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Parse the FTP URL
        val urlParts = ftpUrl.substring(6).split("->")
        val credentials = urlParts[0].split(":")
        val hostInfo = urlParts[1].split(":")
        Log.d(
            TAG,
            "downloadFileViaFtpURL() called with: ftpUrl = $ftpUrl, localFilePath = $localFilePath"
        )
        Log.d(
            TAG,
            "downloadFileViaFtpURL: urlParts:$urlParts, credentials:$credentials ,hostInfo:$hostInfo"
        )


        val username = credentials[0]
        val password = credentials[1]
        val host = hostInfo[0]
        val fileNameInfo = hostInfo[1].split("/")
        val port = fileNameInfo[0].toInt()
        val remoteFilePath = fileNameInfo[1]
        val localFile = File(localFilePath, "/$remoteFilePath")
        Log.d(
            TAG, "downloadFileViaFtpURL: username:$username, password:$password ," +
                    "host:$host port:$port fileName:$remoteFilePath ,localFile:$localFile ext =${
                        remoteFilePath.substringAfter(
                            "."
                        )
                    }"
        )

        ftpUtil.downloadFileFromFtpAndSave(
            context,
            host,
            port,
            username,
            password,
            remoteFilePath,
            localFile.name,
            "*/*"
        ) { status, ctx, uri, mimetype ->
            withContext(Dispatchers.Main) {
                    onDownloadComplete(true, context, uri, mimetype)

            }
        }


    }

    // Utility function to validate FTP credentials
    private fun validateCredentials(server: String, username: String, password: String) {
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            throw IllegalArgumentException("FTP credentials are incomplete")
        }
    }

    // Utility function to create a remote file path
    private fun createRemoteFilePath(
        filePath: String,
        fileType: String,
        fileExtension: String
    ): String {
        return if (filePath.contains(":")) {
            "${fileType}_${filePath.split(":")[1]}$fileExtension"
        } else {
            if (filePath.contains("/")) {
                Log.d(
                    TAG,
                    "createRemoteFilePath: ${
                        filePath.substringAfterLast("/").substringBeforeLast(".")
                    }"
                )
                filePath.substringAfterLast("/").substringBeforeLast(".")
            } else {
                Log.d(TAG, "createRemoteFilePath: else = ${fileType}_unknown$fileExtension}")
                "${fileType}_unknown$fileExtension"
            }
        }
    }

    // Utility function to get the directory path for downloads
    fun getDownloadDirectoryPath(): String {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: context.filesDir.absolutePath
    }

}
