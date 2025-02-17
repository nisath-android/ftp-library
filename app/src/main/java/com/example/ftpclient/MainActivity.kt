package com.example.ftpclient

import FTPFileUtils
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ftpclient.databinding.ActivityMainBinding
import com.naminfo.ftpclient.core.FTPUtil
import com.naminfo.ftpclient.core.SelectFiles
import com.naminfo.ftpclient.permission.PermissionManager
import kotlinx.coroutines.launch


private const val TAG = "==>>MainActivity"
private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var permissionManager: PermissionManager? = null
    private val selectFiles: SelectFiles = SelectFiles(this)
    private lateinit var selectFilesLauncher: ActivityResultLauncher<Intent>
    private var selectedFileUri: Uri? = null
    private var selectedFileUris: MutableList<Uri> = mutableListOf()
    private val ftpUtil = FTPUtil()
    private var ftpURL = ""
    var server = ""
    var username = ""
    var password = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        permissionManager = PermissionManager(this@MainActivity)

        if (!permissionManager?.checkPermissions()!!) {
            permissionManager?.requestPermissions(this, PERMISSION_REQUEST_CODE)
        }
        setContentView(binding.root)
        uiInit()
    }


    private fun uiInit() {
        selectFilesLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d(TAG, "uiInit: ------${result.resultCode == Activity.RESULT_OK}")
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    // Handle the selected files
                    data?.data?.let { uri ->
                        // Handle the single file Uri
                        Log.d(TAG, "Selected file URI: $uri")
                        selectedFileUri = uri
                        binding.btnUpload.isEnabled = true
                    }

                    data?.clipData?.let { clipData ->
                        // Handle multiple files if supported
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            Log.d(TAG, "Selected file URI: $uri")
                            selectedFileUris.add(uri) // Add to the list if needed
                        }
                        binding.btnUpload.isEnabled = true
                    }
                }
            }
        binding.btnSelectFile.setOnClickListener {
            selectFiles.chooseFiles { intentObj ->
                selectFilesLauncher.launch(intentObj)
            }
        }
        binding.btnUpload.setOnClickListener {
            server = binding.edtServer.text.toString().trim()
            username = binding.edtUsername.text.toString().trim()
            password = binding.edtPassword.text.toString().trim()
            if (server.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                if (selectedFileUris.isNotEmpty()) {
                    for (uri in selectedFileUris) {
                        // val filePath = uri?.let { u -> FileUtils.getPath(this, u) }
                        lifecycleScope.launch {
                            if (selectFiles.uploadFiles(server, username, password,
                                    senderName = "222",
                                    receiverName = "2232", uri,
                                    ftpUtil = ftpUtil,
                                    isRemotePathModified = true,
                                    modifyRemotePath = { path,filetype, ext ->
                                        selectFiles.createRemoteFilePath(path, filetype, ext)
                                    },
                                    getFilePath = { uri ->
                                        getFilePathFromUri(uri)
                                    }, getURL = {
                                        Log.d(TAG, "uiInit: uploaded url is $it")
                                    }, error = {

                                    })
                            ) Toast.makeText(
                                this@MainActivity,
                                "File upload successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            else Toast.makeText(
                                this@MainActivity,
                                "File upload failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else if (selectedFileUri != null) {
                    lifecycleScope.launch {
                        if (selectFiles.uploadFiles(server, username, password,
                                    senderName = "222",
                                receiverName = "2232",
                                selectedFileUri!!,
                                ftpUtil = ftpUtil,
                                isRemotePathModified = false,
                                modifyRemotePath = { path,filetype, ext ->
                                    selectFiles.createRemoteFilePath(path, filetype, ext)
                                },
                                getFilePath = { uri ->
                                    getFilePathFromUri(uri)
                                }, getURL = {
                                    Log.d(TAG, "uiInit: uploaded url is $it")
                                    ftpURL = it
                                }, error = {

                                })
                        ) Toast.makeText(
                            this@MainActivity,
                            "File upload successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        else Toast.makeText(
                            this@MainActivity,
                            "File upload failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(TAG, "No files selected for upload.")
                }
            } else {
                Log.e(TAG, "uiInit: valid user name and password")
            }

        }
        binding.btnDownload.setOnClickListener {
                lifecycleScope.launch {
                    selectFiles.downloadFileViaFtpURL(
                        ftpURL,
                        selectFiles.getDownloadDirectoryPath(),
                        ftpUtil,
                        sender = "222",
                        receiver = "2232"
                    ) { status, ctx, savedFileUri, mimetype ->
                        if (status) {
                            Log.d(TAG, "Download successful")

                            Glide.with(binding.savedImage.context)
                                .load(savedFileUri) // Pass the File object
                                .into(binding.savedImage)

                            if (savedFileUri != null) ftpUtil.openFile(
                                this@MainActivity,
                                savedFileUri,
                                mimetype
                            )
                        } else {
                            Log.e(TAG, "Download failed")
                        }
                    }
                }


        }
        binding.savedImage.setOnClickListener { ftpUtil.openFile(
            this@MainActivity,
            FTPUtil.oldUri!!,
            FTPUtil.oldMimeType
        ) }
    }

    private fun getFilePathFromUri(uris: Uri): String? {

        return uris?.let { uri -> FTPFileUtils.getPath(this, uri) }
    }

    private fun getSelectedFileUri(): Uri? {
        // Replace this logic with your implementation for retrieving the selected file URI
        return selectedFileUri
    }


}