package com.example.authapp.utils

import android.content.Context
import android.location.Location
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.GeofenceZone
import com.example.authapp.data.SecurityAlert
import com.example.authapp.data.UserLocation

object GeofenceUtils {

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun checkGeofences(
        context: Context,
        childId: String,
        location: UserLocation,
        zones: List<GeofenceZone>
    ) {
        if (childId.isEmpty() || zones.isEmpty() || location.latitude == 0.0) return

        val prefs = context.getSharedPreferences("geofence_state_$childId", Context.MODE_PRIVATE)

        for (zone in zones) {
            if (!zone.isEnabled || zone.latitude == 0.0) continue

            val dist = calculateDistanceMeters(
                location.latitude,
                location.longitude,
                zone.latitude,
                zone.longitude
            )
            val isInsideNow = dist <= zone.radiusMeters
            val wasInside = prefs.getBoolean("zone_${zone.id}_inside", isInsideNow)

            if (wasInside && !isInsideNow) {
                // Exit event
                prefs.edit().putBoolean("zone_${zone.id}_inside", false).apply()
                val alert = SecurityAlert(
                    type = "GEOFENCE_EXIT",
                    title = "Safe Zone Exit 📍",
                    message = "Child moved outside ${zone.name} (${dist.toInt()}m away)",
                    timestamp = System.currentTimeMillis(),
                    severity = "WARNING"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
            } else if (!wasInside && isInsideNow) {
                // Enter event
                prefs.edit().putBoolean("zone_${zone.id}_inside", true).apply()
                val alert = SecurityAlert(
                    type = "GEOFENCE_ENTER",
                    title = "Safe Zone Arrival 🏠",
                    message = "Child arrived at ${zone.name}",
                    timestamp = System.currentTimeMillis(),
                    severity = "INFO"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
            }
        }
    }
}
