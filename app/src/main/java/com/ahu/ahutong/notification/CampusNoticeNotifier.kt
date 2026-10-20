package com.ahu.ahutong.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.notice.CampusNotice
import com.ahu.ahutong.data.notice.CampusNoticeUrlPolicy
import com.ahu.ahutong.data.notice.noticeSourceLabel
import java.security.MessageDigest

object CampusNoticeNotifier {
    const val CHANNEL_ID = "campus_notice_v1"
    private const val TAG_PREFIX = "campus_notice_account_"
    const val ACTION_OPEN = "com.ahu.ahutong.action.OPEN_CAMPUS_NOTICE"
    const val EXTRA_ACCOUNT_ID = "campus_notice_account"
    const val EXTRA_ARTICLE_ID = "campus_notice_article"
    const val EXTRA_ARTICLE_URL = "campus_notice_url"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "校园通知", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "安徽大学及教务处官网新公告" })
    }

    fun post(context: Context, accountId: String, notices: List<CampusNotice>) {
        if (notices.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)
        notices.forEach { notice ->
            if (!CampusNoticeUrlPolicy.isTrustedArticle(notice.sourceId, notice.originalUrl)) return@forEach
            val target = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN
                data = Uri.parse("ahutong://campus-notice/${tagFor(accountId)}/${notice.articleId}")
                putExtra(EXTRA_ACCOUNT_ID, accountId)
                putExtra(EXTRA_ARTICLE_ID, notice.articleId)
                putExtra(EXTRA_ARTICLE_URL, notice.originalUrl)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(
                context, accountId.hashCode() xor notice.articleId.hashCode(), target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("校园通知 · ${noticeSourceLabel(notice.sourceId)}")
                .setContentText(notice.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.title))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(tagFor(accountId), notice.articleId.hashCode(), notification) }
        }
    }

    fun cancelOtherAccounts(context: Context, accountId: String) {
        runCatching {
            val active = context.getSystemService(NotificationManager::class.java) ?: return
            val keepTag = tagFor(accountId)
            active.activeNotifications.forEach { item ->
                val tag = item.tag
                if (tag?.startsWith(TAG_PREFIX) == true && tag != keepTag) {
                    active.cancel(tag, item.id)
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        runCatching {
            val active = context.getSystemService(NotificationManager::class.java) ?: return
            active.activeNotifications.forEach { item ->
                val tag = item.tag
                if (tag?.startsWith(TAG_PREFIX) == true) active.cancel(tag, item.id)
            }
        }
    }

    private fun tagFor(accountId: String): String = TAG_PREFIX +
        MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
