package com.example.authapp.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
    SPEAKER,   // Main loudspeaker
    EARPIECE,  // Phone earpiece receiver
    BLUETOOTH  // Wireless Bluetooth headset / earbuds
}

class AudioRouteManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    init {
        try {
            registerListeners()
            checkAndHandleBluetooth()
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] Init error: ${t.localizedMessage}")
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().log("[AudioRouteManager] registerAudioDeviceCallback error: ${t.localizedMessage}")
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
                appContext,
                bluetoothReceiver!!,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] registerReceiver error: ${t.localizedMessage}")
        }
    }

    fun checkAndHandleBluetooth() {
        try {
            val am = audioManager ?: return
            val hasBt: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasBluetoothPermission()) {
                    am.availableCommunicationDevices.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                } else false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else {
                false
            }

            val wasConnected = _isBluetoothConnected.value
            _isBluetoothConnected.value = hasBt

            // Auto-switch to Bluetooth when newly connected
            if (hasBt && !wasConnected) {
                setRoute(AudioOutputRoute.BLUETOOTH)
            } else if (!hasBt && _currentRoute.value == AudioOutputRoute.BLUETOOTH) {
                setRoute(AudioOutputRoute.SPEAKER)
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] checkAndHandleBluetooth error: ${t.localizedMessage}")
        }
    }

    fun setRoute(route: AudioOutputRoute) {
        val am = audioManager ?: return
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
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
                        if (hasBluetoothPermission()) {
                            val bt = am.availableCommunicationDevices.firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                            }
                            if (bt != null) {
                                am.setCommunicationDevice(bt)
                                _currentRoute.value = AudioOutputRoute.BLUETOOTH
                            } else {
                                setRoute(AudioOutputRoute.SPEAKER)
                            }
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
                        } catch (_: Exception) {}
                        am.isSpeakerphoneOn = true
                        _currentRoute.value = AudioOutputRoute.SPEAKER
                    }
                    AudioOutputRoute.EARPIECE -> {
                        try {
                            am.stopBluetoothSco()
                            am.isBluetoothScoOn = false
                        } catch (_: Exception) {}
                        am.isSpeakerphoneOn = false
                        _currentRoute.value = AudioOutputRoute.EARPIECE
                    }
                    AudioOutputRoute.BLUETOOTH -> {
                        am.isSpeakerphoneOn = false
                        try {
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        } catch (_: Exception) {
                            setRoute(AudioOutputRoute.SPEAKER)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] setRoute error: ${t.localizedMessage}")
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
                    appContext.unregisterReceiver(bluetoothReceiver)
                } catch (_: Exception) {}
            }
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            try {
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
            } catch (_: Exception) {}
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] release error: ${t.localizedMessage}")
        }
    }
}
