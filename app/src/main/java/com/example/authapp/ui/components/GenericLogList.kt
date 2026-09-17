package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Universal Generic Log List Composable Container (Module 3).
 * Eliminates duplicate LazyColumn, SearchBar, and Empty State layouts across ChildActivityDialog tabs.
 */
@Composable
fun <T> GenericLogList(
    items: List<T>,
    emptyText: String = "No logs recorded yet.",
    searchPlaceholder: String = "Search logs...",
    searchQueryFilter: ((T, String) -> Boolean)? = null,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank() || searchQueryFilter == null) {
            items
        } else {
            items.filter { searchQueryFilter(it, searchQuery.trim().lowercase()) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (searchQueryFilter != null && items.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching results found." else emptyText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredItems) { item ->
                    itemContent(item)
                }
            }
        }
    }
}
