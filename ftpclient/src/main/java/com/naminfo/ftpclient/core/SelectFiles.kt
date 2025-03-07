package com.naminfo.ftpclient.core

import FTPFileUtils.getFileTypeAndExt
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
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
        senderName: String,
        receiverName: String,
        selectedFileUri: Uri,
        modifyRemotePath: (String, String, String, String, String) -> Pair<String, String>,
        isRemotePathModified: Boolean = false,
        getFilePath: (Uri) -> String?,
        ftpUtil: FTPUtil,
        getURL: (String) -> Unit,
        error: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(
            TAG,
            "uploadFiles: server:$server,username:$username,password:$password,senderName:$senderName,receiverName:$receiverName"
        )
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
            val encodeSenderName = PhoneNumberHandler().encodePhoneNumber(senderName)
            val encodeReceiverName = PhoneNumberHandler().encodePhoneNumber(receiverName)
            Log.d(
                TAG,
                "uploadFiles: encodeSenderName =${encodeSenderName}, encodeReceiverName =${encodeReceiverName} "
            )
            var remoteFilePath: Pair<String, String> =
                createRemoteFilePath(
                    filePath,
                    fileType,
                    fileExtension,
                    encodeSenderName,
                    encodeReceiverName
                )
            if (isRemotePathModified) {
                remoteFilePath =
                    modifyRemotePath(
                        remoteFilePath.first,
                        fileType,
                        remoteFilePath.second,
                        encodeSenderName ?: senderName,
                        encodeReceiverName ?: receiverName
                    )
                Log.d(TAG, "uploadFiles: RemotePathModified = ${remoteFilePath.first}")
            }

            var newRemotePath = if (!hasValidExtension(remoteFilePath.first)) {
                "${remoteFilePath.first}$fileExtension".trim()
            } else {
                remoteFilePath.first.trim()
            }
            newRemotePath = replaceMultipleDots(newRemotePath)
            newRemotePath = if (newRemotePath.startsWith("image_") || newRemotePath.startsWith("audio_") || newRemotePath.startsWith(
                    "rec_"
                ) || newRemotePath.startsWith("video_") || newRemotePath.startsWith("document_") || newRemotePath.startsWith(
                    "unknown_"
                ) || newRemotePath.startsWith("other_") || newRemotePath.startsWith("file_")
            ) {
                newRemotePath.replaceFirst("_", "_${encodeSenderName}_${encodeReceiverName}_")
            } else {
                "file_${encodeSenderName}_${encodeReceiverName}_${newRemotePath}"
            }
            Log.d(
                TAG,
                "uploadFiles:remoteFilePath =${newRemotePath}, remoteFilePath.first =${remoteFilePath.first} ext=${remoteFilePath.second} "
            )


            val uploaded = ftpUtil.uploadFile(
                localFilePath = filePath,
                remoteFilePath = newRemotePath,
                sender = senderName,
                receiver = receiverName,
                fileType = getFileCategory(newRemotePath) ?: fileType,
                mimeType = "*/*"
            )


            if (uploaded) {
                val ftpUrl =
                    ftpUtil.makeFtpUrl(username, password, server, filePath = newRemotePath)
                getURL(ftpUrl)
            }
            ftpUtil.disconnect()
            uploaded
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading files: ${e.message}", e)
            false
        }
    }

    fun replaceMultipleDots(input: String): String {
        return input.replace(Regex("\\.{2,}"), ".")
    }

    // Function to check if a file has a valid extension
    fun hasValidExtension(filePath: String): Boolean {
        val validExtensions = listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".bmp",
            ".tiff",
            ".webp",
            ".svg",
            ".ico",
            ".heic",
            ".raw",
            ".mp3",
            ".wav",
            ".aac",
            ".flac",
            ".ogg",
            ".wma",
            ".m4a",
            ".aiff",
            ".mid",
            ".midi",
            ".amr",
            ".pdf",
            ".doc",
            ".docx",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx",
            ".txt",
            ".rtf",
            ".odt",
            ".ods",
            ".odp",
            ".csv",
            ".html",
            ".htm",
            ".xml",
            ".epub",
            ".mobi",
            ".mp4",
            ".avi",
            ".mkv",
            ".mov",
            ".wmv",
            ".flv",
            ".webm",
            ".mpeg",
            ".mpg",
            ".3gp",
            ".vob"
        )
        return validExtensions.any { filePath.endsWith(it, ignoreCase = false) }
    }

    fun getFileCategory(filePath: String): String {
        val imageExtensions = listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".bmp",
            ".tiff",
            ".webp",
            ".svg",
            ".ico",
            ".heic",
            ".raw"
        )
        val audioExtensions = listOf(
            ".mp3",
            ".wav",
            ".aac",
            ".flac",
            ".ogg",
            ".wma",
            ".m4a",
            ".aiff",
            ".mid",
            ".midi",
            ".amr"
        )
        val videoExtensions = listOf(
            ".mp4",
            ".avi",
            ".mkv",
            ".mov",
            ".wmv",
            ".flv",
            ".webm",
            ".mpeg",
            ".mpg",
            ".3gp",
            ".vob"
        )
        val documentExtensions = listOf(
            ".pdf",
            ".doc",
            ".docx",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx",
            ".txt",
            ".rtf",
            ".odt",
            ".ods",
            ".odp",
            ".csv",
            ".html",
            ".htm",
            ".xml",
            ".epub",
            ".mobi"
        )

        val extension = filePath.substringAfterLast('.', "").lowercase()

        return when {
            imageExtensions.contains(".$extension") -> "Image"
            audioExtensions.contains(".$extension") -> "Audio"
            videoExtensions.contains(".$extension") -> "Video"
            documentExtensions.contains(".$extension") -> "Document"
            else -> "Other"
        }
    }


    suspend fun downloadFileViaFtpURL(
        ftpUrl: String,
        localFilePath: String,
        ftpUtil: FTPUtil,
        sender: String, receiver: String,

        onDownloadComplete: (Boolean, Context, Uri?, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadFileViaFtpURL: ftpUtil=$ftpUtil ,ftpUrl=$ftpUrl")
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
            ftpHost = host,
            ftpPort = port,
            username = username,
            password = password,
            sender = sender,
            receiver = receiver,
            remoteFilePath = remoteFilePath,
            fileName = localFile.name,
            fileType = getFileCategory(remoteFilePath),
            "*/*"
        ) { status, _, uri, mimetype ->
            withContext(Dispatchers.Main) {
                onDownloadComplete(status, context, uri, mimetype)
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
    fun createRemoteFilePath(
        filePath: String,
        fileType: String,
        fileExtension: String,
        encodeSenderName: String?,
        encodeReceiverName: String?,
    ): Pair<String, String> {
        return if (filePath.contains(":")) {
            Log.d(
                TAG,
                "createRemoteFilePath:contains colon: is => ${fileType}_${filePath.split(":")[1]}$fileExtension"
            )
            Pair("${fileType}_${filePath.split(":")[1]}$fileExtension", fileExtension)
        } else {
            if (filePath.contains("/")) {
                Log.d(
                    TAG,
                    "createRemoteFilePath: ${
                        filePath.substringAfterLast("/").substringBeforeLast(".")
                    }"
                )
                Pair(
                    filePath.substringAfterLast("/").substringBeforeLast("."),
                    filePath.substringAfterLast("/").substringAfterLast(".")
                )
            } else {
                Log.d(TAG, "createRemoteFilePath: else = ${fileType}_unknown$fileExtension}")
                "${fileType}_unknown$fileExtension"
                Pair(
                    filePath.substringAfterLast("/").substringBeforeLast("."),
                    filePath.substringAfterLast("/").substringAfterLast(".")
                )
            }
        }
    }

    // Utility function to get the directory path for downloads
    fun getDownloadDirectoryPath(): String {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: context.filesDir.absolutePath
    }

}
