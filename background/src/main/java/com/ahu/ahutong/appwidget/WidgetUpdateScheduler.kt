package com.ahu.ahutong.appwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetUpdateScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_WIDGETS && intent.action != ACTION_RENDER_WIDGETS) return

        val shouldRefresh = intent.action == ACTION_UPDATE_WIDGETS
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                runWidgetBroadcast(
                    finish = { pendingResult.finish() },
                    scheduleNext = { if (shouldRefresh) scheduleNext(appContext) }
                ) {
                    WidgetRenderWorker.enqueue(appContext)
                    if (shouldRefresh) requestScheduleRefresh(appContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue widget update", e)
            }
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateScheduler"
        const val ACTION_UPDATE_WIDGETS = "com.ahu.ahutong.appwidget.ACTION_UPDATE_WIDGETS"
        const val ACTION_RENDER_WIDGETS = "com.ahu.ahutong.appwidget.ACTION_RENDER_WIDGETS"
        const val ACTION_REFRESH_SCHEDULE = "com.ahu.ahutong.appwidget.ACTION_REFRESH_SCHEDULE_FROM_NETWORK"
        private const val REQUEST_CODE = 3001
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun renderCached(context: Context) {
            context.sendBroadcast(Intent(context, WidgetUpdateScheduler::class.java).apply {
                action = ACTION_RENDER_WIDGETS
            })
        }

        fun requestScheduleRefresh(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH_SCHEDULE).setClassName(
                    context.packageName,
                    "com.ahu.ahutong.appwidget.WidgetScheduleRefreshReceiver"
                )
            )
        }

        fun scheduleNext(context: Context) {
            val now = LocalDateTime.now()
            val nextTrigger = calculateNextTrigger(now)
            
            val triggerMillis = nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val delayMillis = triggerMillis - System.currentTimeMillis()
            Log.i(TAG, "scheduleNext: Scheduling next update at $nextTrigger (delay: ${delayMillis}ms)")

            // 测试模式：如果延时小于 10 秒，直接使用 Handler，绕过 AlarmManager 的限制
//            if (delayMillis < 10000) {
//                Handler(Looper.getMainLooper()).postDelayed({
//                    val intent = Intent(context, WidgetUpdateScheduler::class.java).apply {
//                        action = ACTION_UPDATE_WIDGETS
//                    }
//                    context.sendBroadcast(intent)
//                }, delayMillis.coerceAtLeast(0))
//                return
//            }

            val intent = Intent(context, WidgetUpdateScheduler::class.java).apply {
                action = ACTION_UPDATE_WIDGETS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                 // Fallback or request permission handled elsewhere, just try best effort
                 alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                 alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }

        fun calculateNextTrigger(now: LocalDateTime): LocalDateTime {
            // 每天的刷新时刻：01:00，以及 07:30–22:00 每 30 分钟一次。
            // 今天的时刻都已过去时，下一次一定是明天 01:00（它早于明天其余时刻）。
            val today = now.toLocalDate()
            val todaySlots = listOf(today.atTime(1, 0)) +
                generateSequence(today.atTime(7, 30)) { it.plusMinutes(30) }
                    .takeWhile { !it.isAfter(today.atTime(22, 0)) }
            return todaySlots.firstOrNull { it.isAfter(now) } ?: today.plusDays(1).atTime(1, 0)
        }

        fun cancel(context: Context) {
             val intent = Intent(context, WidgetUpdateScheduler::class.java).apply {
                action = ACTION_UPDATE_WIDGETS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
        }
    }
}
