package com.example.authapp.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

object WebRtcCodecHelper {

    fun createEncoderFactory(eglBaseContext: EglBase.Context): VideoEncoderFactory {
        val baseEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, false)
        return object : VideoEncoderFactory {
            override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? =
                baseEncoderFactory.createEncoder(info)
            override fun getSupportedCodecs(): Array<VideoCodecInfo> =
                baseEncoderFactory.supportedCodecs
                    .filter { it.name.equals("VP8", ignoreCase = true) }
                    .toTypedArray()
        }
    }

    fun createDecoderFactory(eglBaseContext: EglBase.Context): VideoDecoderFactory {
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        return object : VideoDecoderFactory {
            private val hardwareFactory = if (isSamsung) null else try { DefaultVideoDecoderFactory(eglBaseContext) } catch (_: Throwable) { null }
            private val softwareFactory: VideoDecoderFactory? = try {
                SoftwareVideoDecoderFactory()
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(t)
                null
            }

            override fun createDecoder(info: VideoCodecInfo?): VideoDecoder? {
                return try {
                    if (isSamsung) {
                        softwareFactory?.createDecoder(info)
                    } else {
                        try {
                            hardwareFactory?.createDecoder(info) ?: softwareFactory?.createDecoder(info)
                        } catch (_: Throwable) {
                            softwareFactory?.createDecoder(info)
                        }
                    }
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                    null
                }
            }

            override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                val list = mutableListOf<VideoCodecInfo>()
                if (!isSamsung) {
                    try { hardwareFactory?.supportedCodecs?.let { list.addAll(it) } } catch (_: Throwable) {}
                }
                try { softwareFactory?.supportedCodecs?.let { list.addAll(it) } } catch (_: Throwable) {}
                return list.distinctBy { it.name }.toTypedArray()
            }
        }
    }

    fun createAudioDeviceModule(appContext: Context, isReceiverOnly: Boolean): JavaAudioDeviceModule {
        return if (isReceiverOnly) {
            JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
        } else {
            JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
                .setAudioRecordErrorCallback(WebRtcAudioHelper.createAudioRecordErrorCallback(appContext))
                .createAudioDeviceModule()
        }
    }
}
