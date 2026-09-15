package com.example.authapp.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.authapp.data.RecordingSchedule
import com.example.authapp.service.ChildForegroundService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.Calendar

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val durationMinutes = intent?.getIntExtra("duration_minutes", 5) ?: 5
        val scheduleId = intent?.getStringExtra("schedule_id") ?: ""

        // Start the recording service — uses local mic (CallRecorder), NOT WebRTC
        // WebRTC requires a live parent receiver. Scheduled recordings are standalone local captures.
        val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
            action = ChildForegroundService.ACTION_SCHEDULED_RECORDING
            putExtra("duration_minutes", durationMinutes)
            putExtra("schedule_id", scheduleId)
        }
        try {
            context.startForegroundService(serviceIntent)
            FirebaseCrashlytics.getInstance().log("[ScheduleAlarm] Triggered schedule=$scheduleId, duration=$durationMinutes min")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        // ✅ FIX: Re-schedule for next day automatically (daily repeat)
        if (scheduleId.isNotEmpty()) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val nextIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                    putExtra("duration_minutes", durationMinutes)
                    putExtra("schedule_id", scheduleId)
                }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    context,
                    scheduleId.hashCode(),
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val nextTrigger = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, nextPendingIntent)
                } else {
                    alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, nextPendingIntent)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }
}

object RecordingScheduler {

    fun setSchedule(context: Context, schedule: RecordingSchedule) {
        // ✅ FIX: If schedule is disabled, cancel any existing alarm for it
        if (!schedule.isEnabled) {
            cancelSchedule(context, schedule.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra("duration_minutes", schedule.durationMinutes)
            putExtra("schedule_id", schedule.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If this time already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            FirebaseCrashlytics.getInstance().log(
                "[RecordingScheduler] Set alarm for schedule=${schedule.id} at ${schedule.hour}:${schedule.minute}, duration=${schedule.durationMinutes}min"
            )
        } catch (se: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
