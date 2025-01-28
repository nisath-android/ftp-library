import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

private const val TAG = "FileUtils"
object FileUtils {

    fun saveFileToPublicStorage(context: Context, fileName: String, data: ByteArray, mimeType: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)  // Use the correct MIME type for the file
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)  // Storing in Downloads folder
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { fileUri ->
            // Open output stream and write the data to the file
            contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(data)
            }
        }
    }


    fun getPath(context: Context, uri: Uri): String? {
        // Check if the Uri is a file Uri
        if (uri.scheme.equals("content", ignoreCase = true)) {
            // Check for API level 29+ to handle scoped storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return copyFileToAppSpecificStorage(context, uri)
            } else {
                // For lower API versions, return the file path directly
                return getRealPathFromURI(context, uri)
            }
        } else if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path
        }

        return null
    }


    private fun copyFileToAppSpecificStorage(context: Context, uri: Uri): String? {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(context.filesDir, File(uri.path!!).name) // Save the file in app-specific directory
            Log.d(TAG,"copyFileToAppSpecificStorage: ${file.absolutePath}")
            val outputStream: OutputStream = context.openFileOutput(file.name, Context.MODE_PRIVATE)

            val inputChannel: FileChannel = (inputStream as java.io.FileInputStream).channel
            val outputChannel: FileChannel = (outputStream as java.io.FileOutputStream).channel
            inputChannel.transferTo(0, inputChannel.size(), outputChannel)

            inputStream.close()
            outputStream.close()

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.moveToFirst()
        val columnIndex: Int = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA) ?: -1
        val filePath = if (columnIndex != -1) cursor?.getString(columnIndex) else null
        cursor?.close()
        return filePath
    }

    // Function to determine the file type and extension based on filePath or file extension
     fun getFileTypeAndExt(
        context: Context,
        filePath: String,
        fileUri: Uri? = null
    ): Pair<String, String> {

        // Fallback: determine type based on the file extension from the MIME type
        val fileExtension = getFileExtension(context, fileUri ?: Uri.EMPTY) ?: ""
        val fileType = fileTypeMapping.entries.firstOrNull {
            it.value.contains(fileExtension)
        }?.key ?: "unknown"
        Log.d(TAG, "getFileTypeAndExtension: $fileExtension")
        return fileType to ".$fileExtension"
    }
    private fun getFileExtension(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }
    }
    val fileTypeMapping = mapOf(
        "image" to listOf("png", "jpg", "jpeg", "gif", "bmp"),
        "pdf" to listOf("pdf"),
        "text" to listOf("txt", "csv", "xml"),
        "audio" to listOf("mp3", "wav", "aac", "m4a"),
        "video" to listOf("mp4", "avi", "mov", "mkv"),
        "document" to listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
    )
    // Map MIME types for each category
    val mimeTypeMapping = mapOf(
        "image" to "image/",
        "pdf" to "application/pdf",
        "text" to "text/",
        "audio" to "audio/",
        "video" to "video/",
        "document" to "application/"
    )
    fun getMimeType(file: File): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private fun getMimeTypes(file: File): String {
        val extension = file.extension.lowercase()

        for ((type, extensions) in fileTypeMapping) {
            if (extension in extensions) {
                // Special case for "pdf" which doesn't use a slash
                return if (type == "pdf") {
                    mimeTypeMapping[type] ?: "*/*"
                } else {
                    // Generate MIME dynamically
                    "${mimeTypeMapping[type]}$extension"
                }
            }
        }
        return "*/*" // Default MIME type for unknown extensions
    }
}
