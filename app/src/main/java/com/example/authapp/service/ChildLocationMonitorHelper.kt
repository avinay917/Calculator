package com.example.authapp.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.config.RemoteConfigManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.GeofenceZone
import com.example.authapp.data.UserLocation
import com.example.authapp.utils.GeofenceUtils
import com.example.authapp.utils.OfflineLocationCache
import com.example.authapp.utils.hasPermission
import com.google.firebase.database.ValueEventListener

class ChildLocationMonitorHelper(
    private val context: Context,
    private val mainHandler: Handler,
    private val isNetworkConnectedSupplier: () -> Boolean,
    private val activeGeofencesSupplier: () -> List<GeofenceZone>
) {
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationRequestListener: ValueEventListener? = null
    private var lastHistoryLocation: Location? = null
    private var lastHistoryTimestamp: Long = 0L

    private fun hasLocationPermission(): Boolean = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun startLocationMonitoring() {
        if (!hasLocationPermission()) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "Location permissions (FINE or COARSE) not granted on child device."
            )
            return
        }
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
                    if (uid.isEmpty()) return
                    val userLoc = UserLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy.toDouble(),
                        timestamp = loc.time,
                        provider = loc.provider ?: "gps"
                    )
                    if (isNetworkConnectedSupplier()) {
                        FirebaseRepository.updateChildLocation(uid, userLoc)
                        if (shouldPersistLocationHistory(loc)) {
                            FirebaseRepository.recordLocationHistoryPoint(uid, userLoc)
                            lastHistoryLocation = Location(loc)
                            lastHistoryTimestamp = System.currentTimeMillis()
                        }
                        OfflineLocationCache.flushCachedLocations(context.applicationContext, uid)
                    } else {
                        OfflineLocationCache.cacheLocation(context.applicationContext, userLoc)
                    }
                    GeofenceUtils.checkGeofences(context.applicationContext, uid, userLoc, activeGeofencesSupplier())
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val lastGps = if (hasFineLocationPermission) {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }
            val lastNet = if (hasFineLocationPermission || hasCoarseLocationPermission) {
                locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                null
            }
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
            if (bestLast != null && uid.isNotEmpty()) {
                val lastLoc = UserLocation(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy.toDouble(),
                    timestamp = bestLast.time,
                    provider = bestLast.provider ?: "last_known"
                )
                if (isNetworkConnectedSupplier()) {
                    FirebaseRepository.updateChildLocation(uid, lastLoc)
                    OfflineLocationCache.flushCachedLocations(context.applicationContext, uid)
                } else {
                    OfflineLocationCache.cacheLocation(context.applicationContext, lastLoc)
                }
            }

            val locationIntervalMs = RemoteConfigManager.getLocationSyncIntervalSec() * 1000L
            val locationDistanceMeters = RemoteConfigManager.getLocationSyncDistanceMeters()
            if (hasFineLocationPermission) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    locationIntervalMs,
                    locationDistanceMeters,
                    locationListener!!
                )
            }
            if (hasFineLocationPermission || hasCoarseLocationPermission) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    locationIntervalMs,
                    locationDistanceMeters,
                    locationListener!!
                )
            }

            if (uid.isNotEmpty()) {
                locationRequestListener = FirebaseRepository.listenToLocationRequests(uid) {
                    fetchSingleLocationFix()
                }
            }
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "SUCCESS",
                "Location listener registered (GPS + Network)"
            )
        } catch (e: SecurityException) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "Location security exception: ${e.localizedMessage}",
                e.localizedMessage
            )
        } catch (e: Exception) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "FAILED",
                "Location init error: ${e.localizedMessage}",
                e.localizedMessage
            )
        }
    }

    private fun shouldPersistLocationHistory(location: Location): Boolean {
        val now = System.currentTimeMillis()
        val intervalMs = RemoteConfigManager.getLocationHistoryIntervalSec() * 1000L
        val minimumDistance = RemoteConfigManager.getLocationHistoryDistanceMeters()
        val previous = lastHistoryLocation ?: return true
        return now - lastHistoryTimestamp >= intervalMs || previous.distanceTo(location) >= minimumDistance
    }

    fun fetchSingleLocationFix() {
        if (!hasLocationPermission()) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "On-demand location refresh skipped: permission denied"
            )
            return
        }
        try {
            val lm = locationManager ?: (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            var locationReceived = false
            val singleListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (locationReceived) return
                    locationReceived = true
                    val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
                    if (uid.isNotEmpty()) {
                        FirebaseRepository.updateChildLocation(
                            uid,
                            UserLocation(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracy = loc.accuracy.toDouble(),
                                timestamp = loc.time,
                                provider = loc.provider ?: "gps_refresh"
                            )
                        )
                        AppHealthTelemetry.logDiagnostic(
                            context.applicationContext,
                            "LOCATION_TRACKING",
                            "SUCCESS",
                            "On-demand single GPS fix acquired (accuracy: ${loc.accuracy}m)"
                        )
                    }
                    try {
                        lm?.removeUpdates(this)
                    } catch (_: Exception) {}
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFineLocationPermission) {
                lm?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    singleListener
                )
            }
            if (hasFineLocationPermission || hasCoarseLocationPermission) {
                lm?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    singleListener
                )
            }

            mainHandler.postDelayed({
                if (!locationReceived) {
                    try {
                        lm?.removeUpdates(singleListener)
                        AppHealthTelemetry.logDiagnostic(
                            context.applicationContext,
                            "LOCATION_TRACKING",
                            "TIMEOUT",
                            "On-demand GPS fix timed out after 15s (indoor or GPS disabled). Listener removed to prevent battery drain."
                        )
                    } catch (_: Exception) {}
                }
            }, 15_000L)
        } catch (e: Exception) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "LOCATION_TRACKING",
                "FAILED",
                "fetchSingleLocationFix error: ${e.localizedMessage}",
                e.localizedMessage
            )
        }
    }

    fun stopLocationMonitoring() {
        try {
            locationListener?.let { locationManager?.removeUpdates(it) }
            locationListener = null
            locationRequestListener?.let { listener ->
                val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
                if (uid.isNotEmpty()) {
                    com.google.firebase.database.FirebaseDatabase.getInstance().reference
                        .child("users").child(uid).child("locationRequest").removeEventListener(listener)
                }
            }
            locationRequestListener = null
        } catch (_: Exception) {}
    }
}
