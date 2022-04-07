// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package video.api.flutter.livestream

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import kotlin.reflect.KFunction1

class CameraPermissions {
    interface ResultCallback {
        fun onResult(errorCode: String?, errorDescription: String?)
    }

    private var ongoing = false
    fun requestPermissions(
        activity: Activity,
        permissionsRegistry: KFunction1<RequestPermissionsResultListener, Unit>,
        enableAudio: Boolean,
        callback: (String?, String?) -> Unit
    ) {
        if (ongoing) {
            callback("camera_permission", "Camera permission request ongoing")
        }
        if (!hasCameraPermission(activity) || enableAudio && !hasAudioPermission(activity)) {
            permissionsRegistry(
                CameraRequestPermissionsListener(
                    object : ResultCallback {
                        override fun onResult(errorCode: String?, errorDescription: String?) {
                            ongoing = false
                            callback(errorCode, errorDescription)
                        }
                    })
            )
            ongoing = true
            ActivityCompat.requestPermissions(
                activity,
                if (enableAudio) arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ) else arrayOf(
                    Manifest.permission.CAMERA
                ),
                CAMERA_REQUEST_ID
            )
        } else {
            // Permissions already exist. Call the callback with success.
            callback(null, null)
        }
    }

    private fun hasCameraPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun hasAudioPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
    }

    @VisibleForTesting
    internal class CameraRequestPermissionsListener @VisibleForTesting constructor(val callback: ResultCallback) :
        RequestPermissionsResultListener {
        // There's no way to unregister permission listeners in the v1 embedding, so we'll be called
        // duplicate times in cases where the user denies and then grants a permission. Keep track of if
        // we've responded before and bail out of handling the callback manually if this is a repeat
        // call.
        var alreadyCalled = false
        override fun onRequestPermissionsResult(
            id: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ): Boolean {
            if (alreadyCalled || id != CAMERA_REQUEST_ID) {
                return false
            }
            alreadyCalled = true
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                callback.onResult("camera_permission", "MediaRecorderCamera permission not granted")
            } else if (grantResults.size > 1 && grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                callback.onResult("camera_permission", "MediaRecorderAudio permission not granted")
            } else {
                callback.onResult(null, null)
            }
            return true
        }
    }

    companion object {
        private const val CAMERA_REQUEST_ID = 9799
    }
}