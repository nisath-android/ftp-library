package com.naminfo.ftpclient.core

import FileUtils.getFileTypeAndExt
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.naminfo.ftpclient.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SelectFiles"

class SelectFiles(private val context: Context) {

    fun chooseFiles(onIntentReady: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*", "video/*", "audio/*", "application/pdf",
                    "text/plain", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
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
            val remoteFilePath = createRemoteFilePath(filePath, fileType, fileExtension)

            val uploaded = ftpUtil.uploadFile(filePath, remoteFilePath)
            ftpUtil.disconnect()

            if (uploaded) {
                val ftpUrl = ftpUtil.makeFtpUrl(username, password, server, filePath=remoteFilePath)
                getURL(ftpUrl)
            }

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

    // Utility function to validate FTP credentials
    private fun validateCredentials(server: String, username: String, password: String) {
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            throw IllegalArgumentException("FTP credentials are incomplete")
        }
    }

    // Utility function to create a remote file path
    private fun createRemoteFilePath(filePath: String, fileType: String, fileExtension: String): String {
        return if (filePath.contains(":")) {
            "${fileType}_${filePath.split(":")[1]}$fileExtension"
        } else {
            if(filePath.contains("/"))
                filePath.substringAfterLast("/").substringBeforeLast(".")
            else
                "${fileType}_unknown$fileExtension"
        }
    }

    // Utility function to get the directory path for downloads
    private fun getDownloadDirectoryPath(): String {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: context.filesDir.absolutePath
    }
}
