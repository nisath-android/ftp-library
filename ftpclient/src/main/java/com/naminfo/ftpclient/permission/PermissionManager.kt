package com.naminfo.ftpclient.permission

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    // List of permissions to request
    private val permissionsList = listOf(
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.USE_FULL_SCREEN_INTENT,
        android.Manifest.permission.VIBRATE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.MANAGE_DOCUMENTS,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.GET_ACCOUNTS,
        android.Manifest.permission.MANAGE_OWN_CALLS,
        android.Manifest.permission.READ_PHONE_NUMBERS,
        android.Manifest.permission.SYSTEM_ALERT_WINDOW,
        android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_CAMERA,
        android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
        android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
        android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
    )

    // Function to check if all permissions are granted
    fun checkPermissions(): Boolean {
        for (permission in permissionsList) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // Function to request permissions
    fun requestPermissions(ctx: Context, requestCode: Int) {
        val permissionsToRequest = permissionsList.filter { 
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(ctx as Activity, permissionsToRequest.toTypedArray(), requestCode)
        }
    }
}