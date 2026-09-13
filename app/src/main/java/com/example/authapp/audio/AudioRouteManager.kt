package com.example.authapp.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOutputRoute {
    SPEAKER,   // Neeche waala main speaker (Loudspeaker)
    EARPIECE,  // Upar waala phone receiver (Private ear listening)
    BLUETOOTH  // Wireless Bluetooth headset / earbuds
}

class AudioRouteManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    init {
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            setRoute(AudioOutputRoute.SPEAKER)
            registerListeners()
            checkAndHandleBluetooth()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] Init error: ${e.localizedMessage}")
        }
    }

    private fun registerListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkAndHandleBluetooth()
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkAndHandleBluetooth()
                }
            }
            try {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[AudioRouteManager] registerAudioDeviceCallback error: ${e.localizedMessage}")
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                @Suppress("DEPRECATION")
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            bluetoothReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    checkAndHandleBluetooth()
                }
            }
            ContextCompat.registerReceiver(
                context,
                bluetoothReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] registerReceiver error: ${e.localizedMessage}")
        }
    }

    fun checkAndHandleBluetooth() {
        val am = audioManager ?: return
        val hasBt: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        } else {
            false
        }

        val wasConnected = _isBluetoothConnected.value
        _isBluetoothConnected.value = hasBt

        // Auto-switch to Bluetooth when newly connected!
        if (hasBt && !wasConnected) {
            setRoute(AudioOutputRoute.BLUETOOTH)
        } else if (!hasBt && _currentRoute.value == AudioOutputRoute.BLUETOOTH) {
            // Fall back to Speaker if Bluetooth disconnected
            setRoute(AudioOutputRoute.SPEAKER)
        }
    }

    fun setRoute(route: AudioOutputRoute) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (route) {
                    AudioOutputRoute.SPEAKER -> {
                        val speaker = am.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speaker != null) {
                            am.setCommunicationDevice(speaker)
                        } else {
                            am.clearCommunicationDevice()
                            am.isSpeakerphoneOn = true
                        }
                        _currentRoute.value = AudioOutputRoute.SPEAKER
                    }
                    AudioOutputRoute.EARPIECE -> {
                        val earpiece = am.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        }
                        if (earpiece != null) {
                            am.setCommunicationDevice(earpiece)
                        } else {
                            am.clearCommunicationDevice()
                            am.isSpeakerphoneOn = false
                        }
                        _currentRoute.value = AudioOutputRoute.EARPIECE
                    }
                    AudioOutputRoute.BLUETOOTH -> {
                        val bt = am.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        }
                        if (bt != null) {
                            am.setCommunicationDevice(bt)
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        } else {
                            setRoute(AudioOutputRoute.SPEAKER)
                        }
                    }
                }
            } else {
                when (route) {
                    AudioOutputRoute.SPEAKER -> {
                        try {
                            am.stopBluetoothSco()
                            am.isBluetoothScoOn = false
                        } catch (e: Exception) {}
                        am.isSpeakerphoneOn = true
                        _currentRoute.value = AudioOutputRoute.SPEAKER
                    }
                    AudioOutputRoute.EARPIECE -> {
                        try {
                            am.stopBluetoothSco()
                            am.isBluetoothScoOn = false
                        } catch (e: Exception) {}
                        am.isSpeakerphoneOn = false
                        _currentRoute.value = AudioOutputRoute.EARPIECE
                    }
                    AudioOutputRoute.BLUETOOTH -> {
                        am.isSpeakerphoneOn = false
                        try {
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        } catch (e: Exception) {
                            setRoute(AudioOutputRoute.SPEAKER)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] setRoute error: ${e.localizedMessage}")
        }
    }

    fun toggleRoute() {
        if (_isBluetoothConnected.value) {
            when (_currentRoute.value) {
                AudioOutputRoute.SPEAKER -> setRoute(AudioOutputRoute.EARPIECE)
                AudioOutputRoute.EARPIECE -> setRoute(AudioOutputRoute.BLUETOOTH)
                AudioOutputRoute.BLUETOOTH -> setRoute(AudioOutputRoute.SPEAKER)
            }
        } else {
            when (_currentRoute.value) {
                AudioOutputRoute.SPEAKER -> setRoute(AudioOutputRoute.EARPIECE)
                AudioOutputRoute.EARPIECE -> setRoute(AudioOutputRoute.SPEAKER)
                AudioOutputRoute.BLUETOOTH -> setRoute(AudioOutputRoute.SPEAKER)
            }
        }
    }

    fun release() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
            if (bluetoothReceiver != null) {
                try {
                    context.unregisterReceiver(bluetoothReceiver)
                } catch (e: Exception) {}
            }
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            try {
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
            } catch (e: Exception) {}
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] release error: ${e.localizedMessage}")
        }
    }
}
