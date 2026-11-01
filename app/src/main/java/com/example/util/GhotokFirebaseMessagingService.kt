package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles Firebase Cloud Messaging (FCM) push notifications exclusively.
 * All application database state is stored in Supabase; Firebase is used for Push Notifications.
 */
class GhotokFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM push token: $token")
        saveLocalFcmToken(applicationContext, token)

        val currentUid = SupabaseClient.getSessionUserId(applicationContext)?.takeIf { it.isNotBlank() }
            ?: runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (!currentUid.isNullOrBlank()) {
            serviceScope.launch {
                SupabaseClient.registerFcmToken(userId = currentUid, fcmToken = token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = data["title"]
            ?: data["titleEn"]
            ?: notification?.title
            ?: "Ghotok Matrimony"

        val body = data["body"]
            ?: data["message"]
            ?: data["bodyEn"]
            ?: notification?.body
            ?: ""

        val type = data["type"] ?: "BROADCAST"
        val actionRoute = data["actionRoute"] ?: data["action_route"] ?: ""
        val notificationId = (remoteMessage.messageId ?: "${title}_${body}_${System.currentTimeMillis()}").hashCode()

        if (title.isNotBlank() || body.isNotBlank()) {
            GhotokNotificationManager.showSystemNotification(
                context = applicationContext,
                notificationId = notificationId,
                title = title,
                body = body,
                type = type,
                actionRoute = actionRoute,
                isBangla = false
            )
        }
    }

    companion object {
        private const val TAG = "GhotokFCMService"
        private const val PREFS_FCM = "ghotok_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        fun saveLocalFcmToken(context: Context, token: String) {
            if (token.isBlank()) return
            context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }

        fun getLocalFcmToken(context: Context): String {
            return context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, "")
                .orEmpty()
        }
    }
}
