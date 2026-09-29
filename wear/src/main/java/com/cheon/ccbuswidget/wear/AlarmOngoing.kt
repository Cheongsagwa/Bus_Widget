package com.cheon.ccbuswidget.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * 워치의 '진행 중 활동' — 워치 화면 아래쪽과 최근 앱에 버스 아이콘이 떠 있고,
 * 누르면 알람 화면([AlarmActivity])이 열린다. 폰의 Live Update 와 같은 역할.
 */
object AlarmOngoing {
    private const val CHANNEL = "bus_alarm_ongoing"
    private const val ID = 7100
    private const val CHANNEL_ALERT = "bus_alarm_alert"
    private const val ALERT_ID = 7200

    /** 길고 강하게 2번: 0.8초 진동 · 0.4초 쉼 · 0.8초 진동 (최대 세기) */
    private val ALERT_TIMINGS = longArrayOf(0, 800, 400, 800)
    private val ALERT_AMPLITUDES = intArrayOf(0, 255, 0, 255)

    /**
     * 승하차 알림 (2정류장 전 · 다음 정류장 하차 등).
     * 알림은 소리 · 진동 없이 띄우고, 진동은 앱이 직접 울린다 (시스템 알림 진동 패턴을 따르지 않도록).
     */
    fun alert(context: Context, title: String, text: String, color: Int) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.os.Vibrator::class.java)
        }
        runCatching {
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(ALERT_TIMINGS, ALERT_AMPLITUDES, -1))
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "승하차 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_bus)
            // 작은 아이콘만 있으면 워치가 앱 색 원 안에 작게 그린다 → 노선 색 원 + 흰 버스를 크게 보여 준다
            .setLargeIcon(routeBitmap(context, color))
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(ALERT_ID, n) }
    }

    private var iconCache: Pair<Int, android.graphics.drawable.Icon>? = null

    /** 노선 색 원 + 흰 버스 아이콘 */
    private fun routeIcon(context: Context, color: Int): android.graphics.drawable.Icon {
        iconCache?.takeIf { it.first == color }?.let { return it.second }
        return android.graphics.drawable.Icon.createWithBitmap(routeBitmap(context, color)).also { iconCache = color to it }
    }

    private fun routeBitmap(context: Context, color: Int): android.graphics.Bitmap {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bmp = androidx.core.graphics.createBitmap(size, size)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_bus)?.mutate()?.let { bus ->
            bus.setTint(android.graphics.Color.WHITE)
            val inset = size * 5 / 24
            bus.setBounds(inset, inset, size - inset, size - inset)
            bus.draw(canvas)
        }
        return bmp
    }

    fun show(context: Context, alarm: WearAlarm?) {
        val nm = NotificationManagerCompat.from(context)
        if (alarm == null) {
            nm.cancel(ID)
            return
        }
        if (!nm.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "승하차 알람 진행", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = listOf(alarm.subtitle, alarm.time).filter { it.isNotBlank() }.joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setColor(alarm.color)
            .setContentTitle(alarm.title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
        val status = Status.Builder()
            .addTemplate("#title#")
            .addPart("title", Status.TextPart(alarm.status.ifBlank { alarm.chip.ifBlank { alarm.title } }))
            .build()
        OngoingActivity.Builder(context, ID, builder)
            // 노선 색 원 + 흰 버스 (폰 알림 · 정류장 화면의 버스 아이콘과 같은 모양)
            .setStaticIcon(routeIcon(context, alarm.color))
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(ID, builder.build()) }
    }
}
