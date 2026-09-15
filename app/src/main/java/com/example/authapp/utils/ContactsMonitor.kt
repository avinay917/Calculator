package com.example.authapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ContactsMonitor {

    fun checkNewContacts(context: Context, childId: String) {
        if (childId.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone._ID} DESC"
            )

            cursor?.use {
                val totalContacts = it.count
                val prefs = context.getSharedPreferences("contacts_monitor_prefs", Context.MODE_PRIVATE)
                val lastKnownCount = prefs.getInt("last_contact_count", -1)

                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val latestName = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val latestNumber = if (numberIdx >= 0) it.getString(numberIdx) ?: "" else ""

                    if (lastKnownCount in 0 until totalContacts) {
                        // New contact was added
                        val alert = SecurityAlert(
                            type = "NEW_CONTACT",
                            title = "New Contact Added 👤",
                            message = "New contact saved: $latestName ($latestNumber)",
                            timestamp = System.currentTimeMillis(),
                            severity = "INFO"
                        )
                        FirebaseRepository.pushSecurityAlert(childId, alert)
                    }
                }

                prefs.edit().putInt("last_contact_count", totalContacts).apply()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ContactsMonitor] checkNewContacts notice: ${e.localizedMessage}")
        }
    }
}
