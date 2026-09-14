package com.example.authapp.utils

/**
 * Centralized constants for the application.
 * Reduces hardcoded strings and improves maintainability.
 */
object Constants {
    // SharedPreferences Keys
    object SharedPreferences {
        const val CRASH_LOGS = "crash_logs"
        const val LAST_CRASH_TIME = "last_crash_time"
        const val LAST_CRASH_THREAD = "last_crash_thread"
        const val LAST_CRASH_MESSAGE = "last_crash_message"
        const val LAST_CRASH_STACKTRACE = "last_crash_stacktrace"
        const val USER_SESSION = "user_session"
        const val USER_ID = "user_id"
        const val USER_EMAIL = "user_email"
        const val USER_ROLE = "user_role"
    }

    // Firebase Database Paths
    object Firebase {
        const val PATH_USERS = "users"
        const val PATH_STREAMS = "streams"
        const val PATH_SIGNALING = "signaling"
        const val PATH_RECORDINGS = "recordings"
        const val PATH_ALERTS = "alerts"
        const val PATH_CALL_LOGS = "call_logs"
        const val PATH_NOTIFICATIONS = "notifications"
        const val PATH_SCHEDULES = "schedules"
        const val PATH_SNAPSHOTS = "snapshots"
        const val PATH_DEVICE_HEALTH = "device_health"
        const val PATH_APP_UPDATE = "app_update"
        const val PATH_ICE_SERVERS = "ice_servers"
        const val PATH_CONNECTED_INFO = ".info/connected"

        // Firebase Storage Paths
        const val STORAGE_RECORDINGS = "recordings"
        const val STORAGE_SNAPSHOTS = "snapshots"
    }

    // Stream Types
    object StreamTypes {
        const val AUDIO = "audio"
        const val VIDEO = "video"
        const val AUDIO_DISPLAY = "Audio"
        const val VIDEO_DISPLAY = "Video"
    }

    // Stream Statuses
    object StreamStatus {
        const val REQUESTED = "REQUESTED"
        const val STOPPED = "STOPPED"
        const val DISCONNECTED = "DISCONNECTED"
        const val PROCESSING = "PROCESSING"
    }

    // User Roles
    object UserRoles {
        const val PARENT = "parent"
        const val CHILD = "child"
        const val DEFAULT = CHILD
    }

    // Recording Defaults
    object Recording {
        const val PREFIX = "rec_"
        const val STATUS_SAVED = "SAVED"
        const val EXTENSION_VIDEO = "mp4"
        const val EXTENSION_AUDIO = "m4a"
    }

    // Snapshot Defaults
    object Snapshot {
        const val STATUS_REQUESTED = "REQUESTED"
        const val STATUS_PROCESSING = "PROCESSING"
        const val CAMERA_BACK = "back"
        const val CAMERA_FRONT = "front"
    }

    // Camera Facing
    object Camera {
        const val FRONT = "front"
        const val BACK = "back"
    }

    // ICE Candidate Nodes
    object IceCandidates {
        const val PARENT_CANDIDATES = "parentCandidates"
        const val CHILD_CANDIDATES = "childCandidates"
    }

    // WebRTC SDP Fields
    object WebRTC {
        const val SDP_OFFER = "sdpOffer"
        const val SDP_ANSWER = "sdpAnswer"
        const val CAMERA_FACING = "cameraFacing"
        const val SDP_MID = "sdpMid"
        const val SDP_M_LINE_INDEX = "sdpMLineIndex"
        const val SDP = "sdp"
    }

    // Default Settings
    object Defaults {
        const val AUDIO_SENSITIVITY_MIN = 10f
        const val AUDIO_SENSITIVITY_MAX = 100f
        const val AUDIO_SENSITIVITY_DEFAULT = 50f
    }

    // Timeouts & Delays
    object Timing {
        const val RECORDING_TICK_INTERVAL_MS = 1000L
        const val DEFAULT_TIMEOUT_MS = 30000L
    }

    // Logging Tags
    object Logging {
        const val TAG_FATAL_CRASH = "FATAL_APP_CRASH"
        const val TAG_FIREBASE = "Firebase"
        const val TAG_WEBRTC = "WebRTC"
        const val TAG_SERVICE = "ChildForegroundService"
    }

    // Firebase Database URL - Should be loaded from google-services.json in production
    // This is a fallback/default value. Real values come from FirebaseDatabase.getInstance()
    const val FIREBASE_DB_URL = "https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app"
}
