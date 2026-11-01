package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.auth.AuthScreen
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.launch

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        // Crashlytics: initialize crash reporting so production crashes (and ANRs)
        // are captured and reported to the Firebase console. This must run after
        // FirebaseApp.initializeApp so the SDK is bound to the default app.
        FirebaseCrashlytics.getInstance()

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                val isPlaceholder = siteKey.isBlank() ||
                    siteKey.startsWith("YOUR_", ignoreCase = true) ||
                    siteKey.contains("RECAPTCHA", ignoreCase = true)
                if (!isPlaceholder) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    val msg = "RELEASE BUILD: recaptcha_site_key is blank or placeholder; " +
                        "Firebase App Check is NOT installed. Set RECAPTCHA_SITE_KEY for the release build."
                    Log.e(TAG, msg)
                    FirebaseCrashlytics.getInstance().log(msg)
                    throw IllegalStateException(msg)
                }
            } else {
                throw IllegalStateException("recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        if (!BuildConfig.DEBUG) throw e
    }
}

class MainActivity : ComponentActivity() {
    private val pendingPushActionState = mutableStateOf<Pair<String, String>?>(null)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Log analytics event for app launch
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, Bundle().apply {
            putString(FirebaseAnalytics.Param.MEDIUM, "android")
            putString(FirebaseAnalytics.Param.SOURCE, "app_store")
        })

        configureAppCheck(this, intent)
        com.example.util.GhotokNotificationManager.ensureChannelsCreated(this)
        handleIncomingDeepLink(intent)
        handleIncomingNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        pendingPushAction = pendingPushActionState.value,
                        onConsumePushAction = { pendingPushActionState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
        handleIncomingNotificationIntent(intent)
    }

    private fun handleIncomingNotificationIntent(intent: android.content.Intent?) {
        val notifType = intent?.getStringExtra("extra_notification_type").orEmpty()
        val actionRoute = intent?.getStringExtra("extra_action_route").orEmpty()
        if (notifType.isNotBlank() || actionRoute.isNotBlank()) {
            pendingPushActionState.value = notifType to actionRoute
            intent?.removeExtra("extra_notification_type")
            intent?.removeExtra("extra_action_route")
        }
    }

    private fun handleIncomingDeepLink(intent: android.content.Intent?) {
        val dataStr = intent?.dataString ?: return
        val isEmailVerifyLink = dataStr.startsWith("ghotok://verify-email", ignoreCase = true) ||
            dataStr.contains("type=magiclink", ignoreCase = true) ||
            dataStr.contains("type=signup", ignoreCase = true) ||
            dataStr.contains("type=email_change", ignoreCase = true)

        val hasLoggedInSession = !com.example.data.supabase.SupabaseClient.getSessionUserId(this).isNullOrBlank() ||
            Firebase.auth.currentUser != null

        if (isEmailVerifyLink || (hasLoggedInSession && !dataStr.contains("type=recovery", ignoreCase = true))) {
            val verified = com.example.data.supabase.SupabaseClient.markEmailVerifiedFromDeepLink(this, dataStr)
            if (verified) {
                android.widget.Toast.makeText(
                    this,
                    "Email verified successfully!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        if (dataStr.startsWith("ghotok://reset-password", ignoreCase = true) ||
            dataStr.contains("type=recovery", ignoreCase = true) ||
            dataStr.contains("access_token=", ignoreCase = true)
        ) {
            com.example.data.supabase.SupabaseClient.setPendingRecoveryDeepLink(dataStr)
        }
    }
}

@Composable
fun AppContent(
    pendingPushAction: Pair<String, String>? = null,
    onConsumePushAction: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val navViewModel: com.example.ui.navigation.MainNavigationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var activeUserId by remember {
        val explicitlyOut = com.example.data.supabase.SupabaseClient.isExplicitlySignedOut(context)
        val initialUid = if (explicitlyOut) {
            null
        } else {
            com.example.data.supabase.SupabaseClient.getSessionUserId(context)?.takeIf { it.isNotBlank() }
                ?: Firebase.auth.currentUser?.uid
        }
        mutableStateOf(initialUid)
    }

    LaunchedEffect(pendingPushAction, activeUserId) {
        if (pendingPushAction != null && !activeUserId.isNullOrBlank()) {
            navViewModel.handlePushNotificationAction(
                type = pendingPushAction.first,
                actionRoute = pendingPushAction.second
            )
            onConsumePushAction()
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val supabaseUid = com.example.data.supabase.SupabaseClient.getSessionUserId(context)?.takeIf { it.isNotBlank() }
            if (auth.currentUser == null && supabaseUid == null) {
                activeUserId = null
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (activeUserId.isNullOrBlank()) {
        AuthScreen(
            onAuthSuccess = {
                activeUserId = com.example.data.supabase.SupabaseClient.getSessionUserId(context)?.takeIf { it.isNotBlank() }
                    ?: Firebase.auth.currentUser?.uid
            }
        )
    } else {
        AppNavigation(
            currentUserId = activeUserId!!,
            navViewModel = navViewModel,
            onSignOut = {
                com.example.data.supabase.SupabaseClient.clearSession(context)
                runCatching { Firebase.auth.signOut() }
                navViewModel.resetForSignOut()
                activeUserId = null
                scope.launch {
                    runCatching {
                        androidx.credentials.CredentialManager.create(context)
                            .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                    }
                }
            }
        )
    }
}
