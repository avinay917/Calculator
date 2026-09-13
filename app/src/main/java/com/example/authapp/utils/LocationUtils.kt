package com.example.authapp.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationUtils {

    suspend fun getReadableAddress(context: Context, latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext formatFallbackAddress(latitude, longitude)
                }

                val geocoder = Geocoder(context, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val first = addresses.firstOrNull()
                                val formatted = if (first != null) formatAddressLine(first) else formatFallbackAddress(latitude, longitude)
                                continuation.resume(formatted)
                            }

                            override fun onError(errorMessage: String?) {
                                continuation.resume(formatFallbackAddress(latitude, longitude))
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(latitude, longitude, 1)
                    val first = list?.firstOrNull()
                    if (first != null) formatAddressLine(first) else formatFallbackAddress(latitude, longitude)
                }
            } catch (e: Exception) {
                formatFallbackAddress(latitude, longitude)
            }
        }
    }

    private fun formatAddressLine(address: Address): String {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) {
            return line
        }
        val parts = listOfNotNull(
            address.subLocality ?: address.featureName,
            address.locality,
            address.adminArea
        ).filter { it.isNotBlank() }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Exact Coordinates Acquired"
    }

    private fun formatFallbackAddress(lat: Double, lng: Double): String {
        return String.format(Locale.US, "GPS: %.5f°, %.5f°", lat, lng)
    }
}
