package com.example.authapp.utils

import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * SharedPreferences Property Delegates (Point 10).
 * Eliminates repetitive getSharedPreferences().getString() / edit().putString() boilerplate.
 */
fun SharedPreferences.string(key: String, defaultValue: String = ""): ReadWriteProperty<Any, String> =
    object : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String =
            getString(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
            edit().putString(key, value).apply()
        }
    }

fun SharedPreferences.boolean(key: String, defaultValue: Boolean = false): ReadWriteProperty<Any, Boolean> =
    object : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean =
            getBoolean(key, defaultValue)

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
            edit().putBoolean(key, value).apply()
        }
    }

fun SharedPreferences.long(key: String, defaultValue: Long = 0L): ReadWriteProperty<Any, Long> =
    object : ReadWriteProperty<Any, Long> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Long =
            getLong(key, defaultValue)

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) {
            edit().putLong(key, value).apply()
        }
    }
