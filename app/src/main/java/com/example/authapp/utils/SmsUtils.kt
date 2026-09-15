package com.example.authapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.authapp.data.SmsItem
import com.google.firebase.crashlytics.FirebaseCrashlytics

object SmsUtils {

    fun getRecentSms(context: Context, limit: Int = 30): List<SmsItem> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val list = mutableListOf<SmsItem>()
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "address", "body", "date", "type")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")

                while (it.moveToNext() && list.size < limit) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val address = if (addrIdx >= 0) it.getString(addrIdx) ?: "Unknown" else "Unknown"
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else 1
                    val typeStr = if (rawType == 1) "INCOMING" else "OUTGOING"

                    list.add(
                        SmsItem(
                            id = id,
                            address = address,
                            body = body,
                            timestamp = date,
                            type = typeStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        return list
    }
}
