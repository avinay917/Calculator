package com.example.authapp.utils

import android.content.Context
import android.widget.Toast

/**
 * Context & Toast Extension Helpers (Point 19).
 * Replaces repetitive Toast.makeText(context, ..., Toast.LENGTH_SHORT).show() calls.
 */
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.longToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
