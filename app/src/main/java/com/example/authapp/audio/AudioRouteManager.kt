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
    BLUETOOTH, // Wireless Bluetooth headset / earbuds
    HEADSET    // Wired 3.5mm or USB-C headset / earphones
}

class AudioRouteManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _isHeadsetConnected = MutableStateFlow(false)
    val isHeadsetConnected: StateFlow<Boolean> = _isHeadsetConnected.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var headsetPlugReceiver: BroadcastReceiver? = null

    init {
        try {
            registerListeners()
            checkAndAutoRoute()
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
                    checkAndAutoRoute()
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkAndAutoRoute()
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
            headsetPlugReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    checkAndAutoRoute()
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                headsetPlugReceiver!!,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] registerReceiver error: ${t.localizedMessage}")
        }
    }

    /**
     * Scans currently connected audio output devices and automatically routes
     * to Bluetooth Earbuds or Wired Earphones if connected, avoiding loudspeaker leakage.
     */
    fun checkAndAutoRoute() {
        try {
            val am = audioManager ?: return

            val hasBt: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasBluetoothPermission()) {
                    am.availableCommunicationDevices.any { isBluetoothDevice(it.type) } ||
                    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it.type) }
                } else false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it.type) }
            } else {
                false
            }

            val hasHeadset: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isWiredHeadsetDevice(it.type) }
            } else {
                @Suppress("DEPRECATION")
                am.isWiredHeadsetOn
            }

            _isBluetoothConnected.value = hasBt
            _isHeadsetConnected.value = hasHeadset

            // Auto-prioritize: Bluetooth Earbuds -> Wired Earphones -> Speaker
            if (hasBt) {
                setRoute(AudioOutputRoute.BLUETOOTH)
            } else if (hasHeadset) {
                setRoute(AudioOutputRoute.HEADSET)
            } else if (_currentRoute.value == AudioOutputRoute.BLUETOOTH || _currentRoute.value == AudioOutputRoute.HEADSET) {
                setRoute(AudioOutputRoute.SPEAKER)
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] checkAndAutoRoute error: ${t.localizedMessage}")
        }
    }

    private fun isBluetoothDevice(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
               type == AudioDeviceInfo.TYPE_HEARING_AID
    }

    private fun isWiredHeadsetDevice(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
               type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
               type == AudioDeviceInfo.TYPE_USB_HEADSET ||
               type == AudioDeviceInfo.TYPE_USB_DEVICE
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
                        val btComm = if (hasBluetoothPermission()) {
                            am.availableCommunicationDevices.firstOrNull { isBluetoothDevice(it.type) }
                        } else null

                        if (btComm != null) {
                            am.setCommunicationDevice(btComm)
                            am.isSpeakerphoneOn = false
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        } else {
                            // Fallback to clearing communication device so OS routes audio to standard Bluetooth A2DP/BLE device
                            am.clearCommunicationDevice()
                            am.isSpeakerphoneOn = false
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        }
                    }
                    AudioOutputRoute.HEADSET -> {
                        val wiredComm = am.availableCommunicationDevices.firstOrNull { isWiredHeadsetDevice(it.type) }
                        if (wiredComm != null) {
                            am.setCommunicationDevice(wiredComm)
                        } else {
                            am.clearCommunicationDevice()
                        }
                        am.isSpeakerphoneOn = false
                        _currentRoute.value = AudioOutputRoute.HEADSET
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
                            am.isSpeakerphoneOn = false
                            _currentRoute.value = AudioOutputRoute.BLUETOOTH
                        }
                    }
                    AudioOutputRoute.HEADSET -> {
                        try {
                            am.stopBluetoothSco()
                            am.isBluetoothScoOn = false
                        } catch (_: Exception) {}
                        am.isSpeakerphoneOn = false
                        _currentRoute.value = AudioOutputRoute.HEADSET
                    }
                }
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[AudioRouteManager] setRoute error: ${t.localizedMessage}")
        }
    }

    fun toggleRoute() {
        val hasBt = _isBluetoothConnected.value
        val hasHeadset = _isHeadsetConnected.value

        when (_currentRoute.value) {
            AudioOutputRoute.SPEAKER -> {
                if (hasBt) setRoute(AudioOutputRoute.BLUETOOTH)
                else if (hasHeadset) setRoute(AudioOutputRoute.HEADSET)
                else setRoute(AudioOutputRoute.EARPIECE)
            }
            AudioOutputRoute.BLUETOOTH -> {
                if (hasHeadset) setRoute(AudioOutputRoute.HEADSET)
                else setRoute(AudioOutputRoute.SPEAKER)
            }
            AudioOutputRoute.HEADSET -> {
                setRoute(AudioOutputRoute.SPEAKER)
            }
            AudioOutputRoute.EARPIECE -> {
                setRoute(AudioOutputRoute.SPEAKER)
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
            if (headsetPlugReceiver != null) {
                try {
                    appContext.unregisterReceiver(headsetPlugReceiver)
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
