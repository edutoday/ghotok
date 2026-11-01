package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AdminNotification
import java.util.concurrent.ConcurrentHashMap

object GhotokNotificationManager {
    private const val CHANNEL_ALERTS_ID = "ghotok_admin_alerts"
    private const val CHANNEL_BROADCASTS_ID = "ghotok_broadcasts"
    private const val PUSHED_PREFS_PREFIX = "ghotok_pushed_notifs_"

    private val memoryPushedKeysByUser = ConcurrentHashMap<String, MutableSet<String>>()

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Account, Package & Match Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts for KYC verification, package activation, marriage proposals, and chats"
                enableVibration(true)
            }
            val broadcastsChannel = NotificationChannel(
                CHANNEL_BROADCASTS_ID,
                "Ghotok Offers & Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Targeted offers, discounts, and announcements from Ghotok Admin"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(broadcastsChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getPushedSetForUser(context: Context, userId: String): MutableSet<String> {
        val cleanUser = userId.ifBlank { "guest" }
        return memoryPushedKeysByUser.getOrPut(cleanUser) {
            val prefs = context.getSharedPreferences("$PUSHED_PREFS_PREFIX$cleanUser", Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("pushed_keys", emptySet()).orEmpty()
            ConcurrentHashMap.newKeySet<String>().apply { addAll(saved) }
        }
    }

    /**
     * Checks if a notification with [uniqueKey] has already been pushed to the Android system tray
     * for [userId]; if not, posts it to the Android Notification Drawer and records its key.
     */
    fun notifyOnceIfNew(
        context: Context,
        userId: String,
        notification: AdminNotification,
        isBangla: Boolean
    ) {
        if (!notification.pushNotification) return
        if (!hasNotificationPermission(context)) return
        val key = notification.uniqueKey()
        if (key.isBlank()) return

        val cleanUser = userId.ifBlank { "guest" }
        val pushedSet = getPushedSetForUser(context, cleanUser)
        if (!pushedSet.add(key)) return

        val prefs = context.getSharedPreferences("$PUSHED_PREFS_PREFIX$cleanUser", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("pushed_keys", HashSet(pushedSet)).apply()

        val title = notification.resolvedTitle(isBangla).ifBlank {
            if (isBangla) "ঘটক ম্যাট্রিমনি" else "Ghotok Matrimony"
        }
        val body = notification.resolvedBody(isBangla)
        if (body.isBlank() && title.isBlank()) return

        val resolvedActionRoute = notification.actionRoute.ifBlank {
            when (notification.type.uppercase()) {
                "INTEREST_RECEIVED", "INTEREST_ACCEPTED", "PROPOSAL" -> "INTERESTS"
                "CHAT_MESSAGE", "CHAT" -> "CHATS"
                "PACKAGE_APPROVED", "PACKAGE_REJECTED", "PACKAGE_CHANGED_BY_ADMIN", "DISCOUNT" -> "PACKAGES"
                "VERIFICATION_APPROVED", "KYC_APPROVED", "KYC_REJECTED" -> "VERIFY"
                else -> "NOTIFICATIONS"
            }
        }

        showSystemNotification(
            context = context,
            notificationId = key.hashCode(),
            title = title,
            body = body,
            type = notification.type,
            actionRoute = resolvedActionRoute,
            isBangla = isBangla
        )
    }

    fun showSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        type: String = "BROADCAST",
        actionRoute: String = "",
        isBangla: Boolean = false
    ) {
        ensureChannelsCreated(context)
        if (!hasNotificationPermission(context)) return

        val channelId = if (type.equals("BROADCAST", ignoreCase = true) ||
            type.equals("ADMIN_BROADCAST", ignoreCase = true) ||
            type.equals("DISCOUNT", ignoreCase = true)
        ) {
            CHANNEL_BROADCASTS_ID
        } else {
            CHANNEL_ALERTS_ID
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_notification_type", type)
            putExtra("extra_action_route", actionRoute)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            pendingIntentFlags
        )

        val viewDetailsLabel = if (isBangla) "বিস্তারিত দেখুন" else "View Details"
        val largeIconBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ghotok_logo)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, viewDetailsLabel, pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
