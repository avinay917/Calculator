package com.example.authapp.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.authapp.service.ChildForegroundService

class ScreenCapturePromptActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val sessionId = intent.getStringExtra(ChildForegroundService.EXTRA_SESSION_ID) ?: ""
            val serviceIntent = Intent(this, ChildForegroundService::class.java).apply {
                action = ChildForegroundService.ACTION_START_SCREEN_STREAM
                putExtra("MEDIA_PROJECTION_DATA", result.data)
                putExtra("RESULT_CODE", result.resultCode)
                putExtra(ChildForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            startService(serviceIntent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
