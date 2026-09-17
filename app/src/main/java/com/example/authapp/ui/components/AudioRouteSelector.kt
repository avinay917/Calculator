package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.authapp.audio.AudioRouteManager

/**
 * Reusable Audio Route Selector for WebRTC Live Stream and Audio Monitoring.
 */
@Composable
fun AudioRouteSelector(
    currentRoute: AudioRouteManager.AudioRoute,
    onRouteSelected: (AudioRouteManager.AudioRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = currentRoute == AudioRouteManager.AudioRoute.SPEAKER,
            onClick = { onRouteSelected(AudioRouteManager.AudioRoute.SPEAKER) },
            label = { Text("Speaker") },
            leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
        )
        FilterChip(
            selected = currentRoute == AudioRouteManager.AudioRoute.EARPIECE,
            onClick = { onRouteSelected(AudioRouteManager.AudioRoute.EARPIECE) },
            label = { Text("Earpiece") },
            leadingIcon = { Icon(Icons.Default.Headset, contentDescription = null) }
        )
        FilterChip(
            selected = currentRoute == AudioRouteManager.AudioRoute.BLUETOOTH,
            onClick = { onRouteSelected(AudioRouteManager.AudioRoute.BLUETOOTH) },
            label = { Text("Bluetooth") },
            leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
        )
    }
}
