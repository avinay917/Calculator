package com.example.authapp.ui.enums

enum class ParentTab(val title: String) {
    CHILDREN("Children"),
    LIVE_MONITORING("Live Monitoring"),
    ACTIVITY_CENTER("Activity Center"),
    SETTINGS("Settings")
}

enum class ActivityCenterTab(val title: String) {
    CALLS("Calls"),
    WHATSAPP("WhatsApp"),
    SMS("SMS"),
    NOTIFICATIONS("Notifications"),
    WEB_HISTORY("Web"),
    NETWORK("Network"),
    SIM("SIM Info")
}
