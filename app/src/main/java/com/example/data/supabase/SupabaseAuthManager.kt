package com.example.data.supabase

import android.content.Context
import android.util.Log
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Manages Supabase GoTrue Authentication, session persistence, OTP password recovery,
 * and database-backed admin role verification.
 * Extracted from [SupabaseClient] to separate authentication from PostgREST table operations.
 */
class SupabaseAuthManager(
    private val httpClientProvider: () -> OkHttpClient,
    private val supabaseUrlProvider: () -> String,
    private val supabaseAnonKeyProvider: () -> String,
    private val isConfiguredProvider: () -> Boolean,
    private val selectRowsProvider: suspend (String, String) -> List<JSONObject>
) {
    companion object {
        private const val TAG = "SupabaseAuthManager"
        private const val SESSION_PREFS = "ghotok_supabase_session"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    @Volatile
    var supabaseAccessToken: String? = null
        private set

    @Volatile
    var supabaseRefreshToken: String? = null
        private set

    @Volatile
    private var appContextRef: Context? = null

    @Volatile
    private var sessionUserId: String? = null

    @Volatile
    private var sessionEmail: String? = null

    @Volatile
    private var sessionDisplayName: String? = null

    private val _pendingRecoveryDeepLink = MutableStateFlow<String?>(null)
    val pendingRecoveryDeepLink: StateFlow<String?> = _pendingRecoveryDeepLink

    private fun decodeJwtPayload(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val decoded = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            JSONObject(String(decoded, Charsets.UTF_8))
        }.getOrNull()
    }

    fun getAuthenticatedJwtUid(): String? {
        val token = supabaseAccessToken ?: appContextRef
            ?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            ?.getString("access_token", null)
        return decodeJwtPayload(token)?.optString("sub", "")?.takeIf { it.isNotBlank() }
    }

    fun isCurrentAccessTokenExpired(): Boolean {
        val token = supabaseAccessToken ?: return true
        val payload = decodeJwtPayload(token) ?: return false
        val expSeconds = payload.optLong("exp", 0L)
        if (expSeconds <= 0L) return false
        val nowSeconds = System.currentTimeMillis() / 1000L
        return (nowSeconds + 30L) >= expSeconds
    }

    fun setAccessToken(token: String?, refreshToken: String? = null) {
        supabaseAccessToken = token?.takeIf { it.isNotBlank() }
        if (!refreshToken.isNullOrBlank()) {
            supabaseRefreshToken = refreshToken
        }
        appContextRef?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
            if (supabaseAccessToken != null) {
                putString("access_token", supabaseAccessToken)
            } else {
                remove("access_token")
            }
            if (!refreshToken.isNullOrBlank()) {
                putString("refresh_token", refreshToken)
            }
            apply()
        }
    }

    private suspend fun exchangeEmailPasswordForToken(email: String, password: String): Pair<String, String?>? {
        if (!isConfiguredProvider() || email.isBlank() || password.isBlank()) return null
        val url = "${supabaseUrlProvider()}/auth/v1/token?grant_type=password"
        val payload = JSONObject().apply {
            put("email", email.trim().lowercase(Locale.US))
            put("password", password)
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseAnonKeyProvider())
            .header("Content-Type", "application/json")
            .build()
        return runCatching {
            httpClientProvider().newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.isSuccessful && bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val access = json.optString("access_token", "").takeIf { it.isNotBlank() }
                    val refresh = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                    val uid = json.optJSONObject("user")?.optString("id", "").orEmpty()
                    if (access != null) {
                        setAccessToken(access, refresh)
                        return@use Pair(access, uid.takeIf { it.isNotBlank() })
                    }
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun ensureShadowSupabaseSession(email: String, displayName: String = ""): Pair<String, String?>? {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!isConfiguredProvider() || cleanEmail.isBlank() || !cleanEmail.contains("@")) return null
        val shadowPwd = deriveVerifierPassword(cleanEmail)
        exchangeEmailPasswordForToken(cleanEmail, shadowPwd)?.let { return it }

        val signupUrl = "${supabaseUrlProvider()}/auth/v1/signup"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", shadowPwd)
            if (displayName.isNotBlank()) {
                put("data", JSONObject().apply {
                    put("full_name", displayName)
                    put("display_name", displayName)
                })
            }
        }
        val req = Request.Builder()
            .url(signupUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseAnonKeyProvider())
            .header("Content-Type", "application/json")
            .build()
        val signedUp = runCatching {
            httpClientProvider().newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.isSuccessful && bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val access = json.optString("access_token", "").takeIf { it.isNotBlank() }
                    val refresh = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                    val userObj = json.optJSONObject("user") ?: json
                    val uid = userObj.optString("id", "").takeIf { it.isNotBlank() }
                    if (access != null) {
                        setAccessToken(access, refresh)
                        return@use Pair(access, uid)
                    }
                }
                null
            }
        }.getOrNull()
        return signedUp ?: exchangeEmailPasswordForToken(cleanEmail, shadowPwd)
    }

    suspend fun ensureValidWriteAccessToken(): String? = withContext(Dispatchers.IO) {
        getSessionUserId()
        val currentToken = supabaseAccessToken
        if (!currentToken.isNullOrBlank() && !isCurrentAccessTokenExpired()) {
            return@withContext currentToken
        }
        handleUnauthorizedToken()
    }

    suspend fun handleUnauthorizedToken(): String? = withContext(Dispatchers.IO) {
        getSessionUserId()
        val prefs = appContextRef?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val currentRefresh = supabaseRefreshToken ?: prefs
            ?.getString("refresh_token", null)
            ?.takeIf { it.isNotBlank() }

        if (!currentRefresh.isNullOrBlank() && isConfiguredProvider()) {
            val refreshUrl = "${supabaseUrlProvider()}/auth/v1/token?grant_type=refresh_token"
            val payload = JSONObject().apply {
                put("refresh_token", currentRefresh)
            }
            val request = Request.Builder()
                .url(refreshUrl)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", supabaseAnonKeyProvider())
                .header("Content-Type", "application/json")
                .build()

            val refreshedToken = runCatching {
                httpClientProvider().newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val newAccess = json.optString("access_token", "").takeIf { it.isNotBlank() }
                        val newRefresh = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                        if (newAccess != null) {
                            setAccessToken(newAccess, newRefresh ?: currentRefresh)
                            return@use newAccess
                        }
                    }
                    null
                }
            }.getOrNull()

            if (!refreshedToken.isNullOrBlank()) {
                return@withContext refreshedToken
            }
        }

        // Actively purge any legacy plaintext password key from SharedPreferences
        if (prefs?.contains("last_auth_password") == true) {
            prefs.edit().remove("last_auth_password").apply()
        }

        // Fallback: Shadow session for Google Sign-In or passwordless email sessions when refresh token is absent
        val fbUser = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }.getOrNull()
        val storedEmail = (sessionEmail ?: prefs?.getString("email", null) ?: fbUser?.email).orEmpty().trim()
        val storedName = (sessionDisplayName ?: prefs?.getString("display_name", null) ?: fbUser?.displayName).orEmpty().trim()
        if (storedEmail.isNotBlank() && sessionEmail.isNullOrBlank()) {
            sessionEmail = storedEmail
            prefs?.edit()?.putString("email", storedEmail)?.apply()
        }
        if (storedEmail.isNotBlank()) {
            ensureShadowSupabaseSession(storedEmail, storedName)?.let {
                return@withContext it.first
            }
        }

        // Only clear access_token if it is actually expired
        if (isCurrentAccessTokenExpired()) {
            supabaseAccessToken = null
            prefs?.edit()?.remove("access_token")?.apply()
        }
        supabaseAccessToken
    }

    fun setPendingRecoveryDeepLink(uriString: String?) {
        _pendingRecoveryDeepLink.value = uriString?.takeIf { it.isNotBlank() }
    }

    fun clearPendingRecoveryDeepLink() {
        _pendingRecoveryDeepLink.value = null
    }

    @Volatile
    private var explicitlySignedOut: Boolean = false

    fun isExplicitlySignedOut(context: Context? = null): Boolean {
        context?.applicationContext?.let { appContextRef = it }
        if (explicitlySignedOut) return true
        val prefs = (context?.applicationContext ?: appContextRef)
            ?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            ?: return false
        return prefs.getBoolean("explicit_sign_out", false)
    }

    fun saveSession(
        context: Context?,
        userId: String,
        email: String = "",
        displayName: String = "",
        accessToken: String? = null,
        refreshToken: String? = null,
        @Suppress("UNUSED_PARAMETER") password: String? = null
    ) {
        if (userId.isBlank()) return
        context?.applicationContext?.let { appContextRef = it }
        explicitlySignedOut = false
        sessionUserId = userId
        if (email.isNotBlank()) sessionEmail = email
        if (displayName.isNotBlank()) sessionDisplayName = displayName
        if (!accessToken.isNullOrBlank()) supabaseAccessToken = accessToken
        if (!refreshToken.isNullOrBlank()) supabaseRefreshToken = refreshToken
        (context?.applicationContext ?: appContextRef)
            ?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                putBoolean("explicit_sign_out", false)
                putString("user_id", userId)
                if (email.isNotBlank()) putString("email", email)
                if (displayName.isNotBlank()) putString("display_name", displayName)
                if (!accessToken.isNullOrBlank()) putString("access_token", accessToken)
                if (!refreshToken.isNullOrBlank()) putString("refresh_token", refreshToken)
                // Never persist plaintext passwords; actively remove any legacy key
                remove("last_auth_password")
                commit()
            }
    }

    fun getSessionUserId(context: Context? = null): String? {
        context?.applicationContext?.let { appContextRef = it }
        if (explicitlySignedOut) return null
        sessionUserId?.takeIf { it.isNotBlank() }?.let { return it }
        val prefs = (context?.applicationContext ?: appContextRef)
            ?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            ?: return null
        if (prefs.getBoolean("explicit_sign_out", false)) {
            explicitlySignedOut = true
            return null
        }
        if (prefs.contains("last_auth_password")) {
            prefs.edit().remove("last_auth_password").apply()
        }
        val stored = prefs.getString("user_id", null)?.takeIf { it.isNotBlank() }
        if (stored != null) {
            sessionUserId = stored
            sessionEmail = prefs.getString("email", null)
            sessionDisplayName = prefs.getString("display_name", null)
            supabaseAccessToken = supabaseAccessToken ?: prefs.getString("access_token", null)
            supabaseRefreshToken = supabaseRefreshToken ?: prefs.getString("refresh_token", null)
        }
        return stored
    }

    fun getSessionEmail(context: Context? = null): String? {
        if (explicitlySignedOut) return null
        sessionEmail?.takeIf { it.isNotBlank() }?.let { return it }
        getSessionUserId(context)
        return sessionEmail?.takeIf { it.isNotBlank() }
    }

    fun getSessionDisplayName(context: Context? = null): String? {
        if (explicitlySignedOut) return null
        sessionDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        getSessionUserId(context)
        return sessionDisplayName?.takeIf { it.isNotBlank() }
    }

    fun clearSession(context: Context? = null) {
        val appCtx = context?.applicationContext ?: appContextRef
        if (appCtx != null) appContextRef = appCtx
        explicitlySignedOut = true
        supabaseAccessToken = null
        supabaseRefreshToken = null
        sessionUserId = null
        sessionEmail = null
        sessionDisplayName = null
        _pendingRecoveryDeepLink.value = null
        appCtx
            ?.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("explicit_sign_out", true)
            ?.remove("user_id")
            ?.remove("email")
            ?.remove("display_name")
            ?.remove("access_token")
            ?.remove("refresh_token")
            ?.remove("last_auth_password")
            ?.commit()
        runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        }
        if (appCtx != null) {
            runCatching {
                getOrCreateEmailVerifierAuth(appCtx)?.signOut()
            }
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun deriveDeterministicUid(email: String): String =
        UUID.nameUUIDFromBytes("ghotok_user:${email.trim().lowercase(Locale.US)}".toByteArray(Charsets.UTF_8)).toString()

    suspend fun checkAndCacheAdminStatus(email: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) return@withContext false
        if (UserProfile.isConfiguredOrVerifiedAdminEmail(cleanEmail)) return@withContext true
        if (!isConfiguredProvider()) return@withContext false

        val adminRows = selectRowsProvider(
            "admins",
            "email=ilike.${urlEncode(cleanEmail)}&is_active=eq.true&limit=1"
        )
        if (adminRows.isNotEmpty()) {
            UserProfile.registerVerifiedAdminEmail(cleanEmail)
            return@withContext true
        }
        false
    }

    private suspend fun findAccountByEmail(email: String): JSONObject? {
        val cleanEmail = email.trim().lowercase(Locale.US)
        var userId = ""
        var fullName = ""
        var phone = ""

        if (isConfiguredProvider()) {
            val encodedEmail = urlEncode(cleanEmail)
            for (table in listOf("users", "profiles")) {
                val rows = selectRowsProvider(table, "email=ilike.$encodedEmail&limit=5")
                for (row in rows) {
                    val payload = SupabaseRowMappers.extractPayloadJson(row)
                    val rowUid = row.optString("uid")
                        .ifBlank { row.optString("id") }
                        .ifBlank { payload.optString("uid") }
                    val rowName = row.optString("full_name")
                        .ifBlank { row.optString("display_name") }
                        .ifBlank { payload.optString("fullName") }
                    val rowPhone = row.optString("contact_number")
                        .ifBlank { payload.optString("contactNumber") }

                    if (userId.isBlank() && rowUid.isNotBlank() && !rowUid.matches(Regex("^[GB]\\d+$", RegexOption.IGNORE_CASE))) {
                        userId = rowUid
                    }
                    if (fullName.isBlank() && rowName.isNotBlank()) {
                        fullName = rowName
                    }
                    if (phone.isBlank() && rowPhone.isNotBlank()) {
                        phone = rowPhone
                    }
                }
            }
        }

        if (userId.isBlank() && fullName.isBlank() && phone.isBlank()) {
            return null
        }
        if (userId.isBlank()) {
            userId = deriveDeterministicUid(cleanEmail)
        }
        return JSONObject().apply {
            put("userId", userId)
            put("email", cleanEmail)
            put("fullName", fullName)
            put("phone", phone)
        }
    }

    suspend fun requestPasswordRecovery(
        email: String
    ): Result<SupabaseClient.PasswordRecoveryChallenge> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        var supabaseRecoverySent = false
        var lastErrorMsg = ""
        if (isConfiguredProvider()) {
            val recoverUrl = "${supabaseUrlProvider()}/auth/v1/recover?redirect_to=${urlEncode("ghotok://reset-password")}"
            val payload = JSONObject().apply {
                put("email", cleanEmail)
            }
            val request = Request.Builder()
                .url(recoverUrl)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", supabaseAnonKeyProvider())
                .header("Content-Type", "application/json")
                .build()
            runCatching {
                httpClientProvider().newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        supabaseRecoverySent = true
                    } else {
                        Log.w(TAG, "Supabase /auth/v1/recover (${response.code}): $bodyStr")
                        val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                        lastErrorMsg = errJson?.optString("msg")
                            ?.ifBlank { errJson.optString("message") }
                            ?.ifBlank { errJson.optString("error_description") }
                            .orEmpty()
                    }
                }
            }
        }

        if (!supabaseRecoverySent && lastErrorMsg.isNotBlank()) {
            return@withContext Result.failure(IllegalStateException(lastErrorMsg))
        }

        val accountInfo = findAccountByEmail(cleanEmail)
        val resolvedUid = accountInfo?.optString("userId", "")?.ifBlank { null }
            ?: deriveDeterministicUid(cleanEmail)

        Result.success(
            SupabaseClient.PasswordRecoveryChallenge(
                email = cleanEmail,
                userId = resolvedUid,
                supabaseRecoveryEmailSent = supabaseRecoverySent
            )
        )
    }

    suspend fun resetPasswordWithRecovery(
        context: Context,
        email: String,
        verificationInput: String,
        newPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var cleanEmail = email.trim().lowercase(Locale.US)
        val cleanOtp = verificationInput.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter your registered email address.")
            )
        }
        if (cleanOtp.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter the 6-digit OTP code sent to your email.")
            )
        }
        if (newPassword.length < 6) {
            return@withContext Result.failure(
                IllegalArgumentException("New password must be at least 6 characters long.")
            )
        }

        val accountInfo = findAccountByEmail(cleanEmail)
        var storedFullName = accountInfo?.optString("fullName", "").orEmpty()
        var resolvedUserId = accountInfo?.optString("userId", "").orEmpty()
        var resolvedAccessToken: String? = null
        var resolvedRefreshToken: String? = null

        if (isConfiguredProvider()) {
            val verifyUrl = "${supabaseUrlProvider()}/auth/v1/verify"
            val verifyPayload = JSONObject().apply {
                put("type", "recovery")
                put("email", cleanEmail)
                put("token", cleanOtp)
            }
            val verifyReq = Request.Builder()
                .url(verifyUrl)
                .post(verifyPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", supabaseAnonKeyProvider())
                .header("Content-Type", "application/json")
                .build()

            runCatching {
                httpClientProvider().newCall(verifyReq).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val token = json.optString("access_token", "").takeIf { it.isNotBlank() }
                        val refresh = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                        val userObj = json.optJSONObject("user")
                        val uid = userObj?.optString("id", "").orEmpty()
                        val userEmail = userObj?.optString("email", "").orEmpty().lowercase(Locale.US)
                        if (token != null) {
                            resolvedAccessToken = token
                            resolvedRefreshToken = refresh
                            if (uid.isNotBlank()) resolvedUserId = uid
                            if (cleanEmail.isBlank() && userEmail.isNotBlank()) cleanEmail = userEmail
                        }
                    } else {
                        Log.w(TAG, "Supabase /auth/v1/verify OTP failed (${response.code}): $bodyStr")
                    }
                }
            }

            val activeToken = resolvedAccessToken
            if (!activeToken.isNullOrBlank()) {
                val updateUrl = "${supabaseUrlProvider()}/auth/v1/user"
                val updatePayload = JSONObject().apply {
                    put("password", newPassword)
                }
                val updateReq = Request.Builder()
                    .url(updateUrl)
                    .put(updatePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("apikey", supabaseAnonKeyProvider())
                    .header("Authorization", "Bearer $activeToken")
                    .header("Content-Type", "application/json")
                    .build()

                val updatedOk = runCatching {
                    httpClientProvider().newCall(updateReq).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            val uid = json.optString("id", "")
                            val meta = json.optJSONObject("user_metadata")
                            val metaName = meta?.optString("full_name")?.ifBlank { meta.optString("display_name") }.orEmpty()
                            if (uid.isNotBlank()) resolvedUserId = uid
                            if (storedFullName.isBlank() && metaName.isNotBlank()) storedFullName = metaName
                            true
                        } else {
                            Log.w(TAG, "Supabase PUT /auth/v1/user failed (${response.code}): $bodyStr")
                            false
                        }
                    }
                }.getOrDefault(false)

                if (!updatedOk) {
                    return@withContext Result.failure(
                        IllegalArgumentException("The OTP code has expired or already been used. Please request a new OTP code.")
                    )
                }
            } else {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid or expired 6-digit OTP code. Please check your email and try again.")
                )
            }
        } else {
            return@withContext Result.failure(
                IllegalStateException("Supabase is not configured.")
            )
        }

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(
                IllegalArgumentException("Could not determine account email. Please enter your registered email address.")
            )
        }
        if (resolvedUserId.isBlank()) {
            resolvedUserId = deriveDeterministicUid(cleanEmail)
        }

        checkAndCacheAdminStatus(cleanEmail)
        clearPendingRecoveryDeepLink()
        saveSession(
            context = context,
            userId = resolvedUserId,
            email = cleanEmail,
            displayName = storedFullName,
            accessToken = resolvedAccessToken,
            refreshToken = resolvedRefreshToken
        )
        Result.success(resolvedUserId)
    }

    suspend fun signUpWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        fullName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        val cleanName = fullName.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your Full Name."))
        }

        if (!isConfiguredProvider()) {
            return@withContext Result.failure(IllegalStateException("Authentication service is not configured."))
        }

        val existingUserRow = selectRowsProvider("users", "email=ilike.${urlEncode(cleanEmail)}&limit=1").firstOrNull()
        if (existingUserRow != null && existingUserRow.optString("uid").isNotBlank()) {
            return@withContext Result.failure(
                IllegalStateException("This email is already registered. Please switch to 'Sign In' to log in.")
            )
        }

        var signupErrorMessage = ""
        val url = "${supabaseUrlProvider()}/auth/v1/signup"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("data", JSONObject().apply {
                put("full_name", cleanName)
                put("display_name", cleanName)
            })
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseAnonKeyProvider())
            .header("Content-Type", "application/json")
            .build()

        var resolvedRefresh: String? = null
        val authAttempt = runCatching {
            httpClientProvider().newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val token = json.optString("access_token", "").takeIf { it.isNotBlank() }
                    resolvedRefresh = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                    val userObj = json.optJSONObject("user") ?: json
                    val identities = userObj.optJSONArray("identities")
                    if (identities != null && identities.length() == 0) {
                        return@withContext Result.failure(
                            IllegalStateException("This email is already registered. Please switch to 'Sign In' to log in.")
                        )
                    }
                    val uid = userObj.optString("id", "")
                    Pair(uid, token)
                } else {
                    val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val rawMsg = listOfNotNull(
                        errJson?.optString("msg")?.takeIf { it.isNotBlank() },
                        errJson?.optString("error_description")?.takeIf { it.isNotBlank() },
                        errJson?.optString("message")?.takeIf { it.isNotBlank() },
                        errJson?.optString("error")?.takeIf { it.isNotBlank() }
                    ).firstOrNull().orEmpty()

                    if (rawMsg.contains("already registered", ignoreCase = true) ||
                        rawMsg.contains("already exists", ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("This email is already registered. Please switch to 'Sign In' to log in.")
                        )
                    }
                    signupErrorMessage = rawMsg
                    Log.w(TAG, "Supabase /auth/v1/signup non-200 (${response.code}): $bodyStr")
                    Pair("", null)
                }
            }
        }.getOrNull()

        val resolvedUserId: String
        val resolvedToken: String?
        if (authAttempt != null && authAttempt.first.isNotBlank()) {
            resolvedUserId = authAttempt.first
            resolvedToken = authAttempt.second
        } else {
            return@withContext Result.failure(
                IllegalStateException(
                    signupErrorMessage.ifBlank { "Unable to complete sign up right now. Please try again." }
                )
            )
        }

        checkAndCacheAdminStatus(cleanEmail)
        saveSession(
            context = context,
            userId = resolvedUserId,
            email = cleanEmail,
            displayName = cleanName,
            accessToken = resolvedToken,
            refreshToken = resolvedRefresh
        )
        Result.success(resolvedUserId)
    }

    suspend fun signInWithEmailPassword(
        context: Context,
        email: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(IllegalStateException("Authentication service is not configured."))
        }

        val url = "${supabaseUrlProvider()}/auth/v1/token?grant_type=password"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", supabaseAnonKeyProvider())
            .header("Content-Type", "application/json")
            .build()

        var authErrorMsg = ""
        runCatching {
            httpClientProvider().newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token", "").takeIf { it.isNotBlank() }
                    val refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                    val userObj = json.optJSONObject("user")
                    val uid = userObj?.optString("id", "").orEmpty()
                    val meta = userObj?.optJSONObject("user_metadata")
                    val displayName = meta?.optString("full_name")?.ifBlank {
                        meta.optString("display_name")
                    }.orEmpty()
                    if (uid.isNotBlank()) {
                        checkAndCacheAdminStatus(cleanEmail)
                        saveSession(
                            context = context,
                            userId = uid,
                            email = cleanEmail,
                            displayName = displayName,
                            accessToken = accessToken,
                            refreshToken = refreshToken
                        )
                        return@withContext Result.success(uid)
                    }
                } else {
                    Log.w(TAG, "Supabase /auth/v1/token password login (${response.code}): $bodyStr")
                    val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                    authErrorMsg = listOfNotNull(
                        errJson?.optString("error_description")?.takeIf { it.isNotBlank() },
                        errJson?.optString("msg")?.takeIf { it.isNotBlank() },
                        errJson?.optString("message")?.takeIf { it.isNotBlank() }
                    ).firstOrNull().orEmpty()
                }
            }
        }

        Result.failure(
            IllegalArgumentException(
                authErrorMsg.ifBlank { "Incorrect email or password, or account not found. Please register first." }
            )
        )
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String = "",
        displayName: String = "",
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        context?.applicationContext?.let { appContextRef = it }
        if (!isConfiguredProvider()) return@withContext false

        if (idToken.isNotBlank()) {
            val url = "${supabaseUrlProvider()}/auth/v1/token?grant_type=id_token"
            val payload = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", supabaseAnonKeyProvider())
                .header("Content-Type", "application/json")
                .build()

            val exchanged = runCatching {
                httpClientProvider().newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val accessToken = json.optString("access_token", "")
                        val refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() }
                        val goTrueUid = json.optJSONObject("user")?.optString("id", "").orEmpty()
                        if (accessToken.isNotBlank()) {
                            setAccessToken(accessToken, refreshToken)
                            if (goTrueUid.isNotBlank()) {
                                sessionUserId = goTrueUid
                            }
                            return@use true
                        }
                    } else {
                        Log.w(TAG, "Supabase Google ID token exchange (${response.code}): $bodyStr")
                    }
                    false
                }
            }.getOrDefault(false)

            if (exchanged) {
                if (email.isNotBlank()) checkAndCacheAdminStatus(email)
                return@withContext true
            }
        }

        // Fallback: if Google OAuth provider is not enabled in Supabase GoTrue, ensure a shadow GoTrue session
        if (email.isNotBlank()) {
            val shadow = ensureShadowSupabaseSession(email, displayName)
            checkAndCacheAdminStatus(email)
            if (shadow != null) {
                return@withContext true
            }
        }
        false
    }

    private val _emailVerifiedTick = MutableStateFlow(0L)
    val emailVerifiedTick: StateFlow<Long> = _emailVerifiedTick

    private fun getOrCreateEmailVerifierAuth(context: Context): com.google.firebase.auth.FirebaseAuth? {
        return runCatching {
            val appContext = context.applicationContext
            val primaryApp = if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(appContext)
            } else {
                com.google.firebase.FirebaseApp.getInstance()
            } ?: return null
            val verifierApp = try {
                com.google.firebase.FirebaseApp.getInstance("GhotokEmailVerifier")
            } catch (_: IllegalStateException) {
                com.google.firebase.FirebaseApp.initializeApp(
                    appContext,
                    primaryApp.options,
                    "GhotokEmailVerifier"
                )
            }
            com.google.firebase.auth.FirebaseAuth.getInstance(verifierApp)
        }.getOrNull()
    }

    private fun deriveVerifierPassword(email: String): String =
        "GhotokVerify!${deriveDeterministicUid(email).replace("-", "").take(12)}"

    private suspend fun sendFirebaseEmailVerificationLink(
        context: Context,
        targetEmail: String
    ): Boolean {
        val cleanEmail = targetEmail.trim().lowercase(Locale.US)
        // 1. Try primary Firebase user verifyBeforeUpdateEmail if signed in recently
        val primarySent = runCatching {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                currentUser.verifyBeforeUpdateEmail(cleanEmail).await()
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (primarySent) return true

        // 2. Try dedicated secondary FirebaseAuth verifier instance so we never disturb the main session
        return runCatching {
            val verifierAuth = getOrCreateEmailVerifierAuth(context) ?: return@runCatching false
            val pwd = deriveVerifierPassword(cleanEmail)
            val user = try {
                verifierAuth.createUserWithEmailAndPassword(cleanEmail, pwd).await()?.user
            } catch (_: Exception) {
                verifierAuth.signInWithEmailAndPassword(cleanEmail, pwd).await()?.user
            } ?: return@runCatching false

            user.sendEmailVerification().await()
            true
        }.getOrDefault(false)
    }

    fun markEmailVerifiedFromDeepLink(context: Context, deepLinkUri: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences("ghotok_security_prefs", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        var anyVerified = false
        val editor = prefs.edit()
        for ((key, value) in allEntries) {
            if (key.startsWith("unverified_email_") && value is String && value.isNotBlank()) {
                val userKey = key.removePrefix("unverified_email_")
                editor.putString("account_email_$userKey", value.trim())
                editor.remove(key)
                anyVerified = true
            }
        }
        val tokenMatch = Regex("access_token=([^&]+)").find(deepLinkUri)?.groupValues?.getOrNull(1)
        if (!tokenMatch.isNullOrBlank()) {
            setAccessToken(tokenMatch)
            anyVerified = true
        }
        editor.apply()
        if (anyVerified) {
            _emailVerifiedTick.value = System.currentTimeMillis()
        }
        return anyVerified
    }

    suspend fun requestEmailChangeAndVerificationLink(
        context: Context,
        userId: String,
        newEmail: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanNewEmail = newEmail.trim().lowercase(Locale.US)
        if (cleanNewEmail.isBlank() || !cleanNewEmail.contains("@") || !cleanNewEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        // Ensure stored session and access token are loaded into memory
        getSessionUserId(context)

        var verificationSent = false
        var lastErrorMsg = ""

        // 1. Send verification link via Supabase GoTrue /auth/v1/otp directly to cleanNewEmail.
        // Unlike PUT /auth/v1/user (which skips emails when Autoconfirm is enabled or sends to old email first),
        // POST /auth/v1/otp always sends the verification link email directly to cleanNewEmail.
        if (isConfiguredProvider()) {
            val candidateOtpUrls = listOf(
                "${supabaseUrlProvider()}/auth/v1/otp?redirect_to=${urlEncode("ghotok://reset-password")}",
                "${supabaseUrlProvider()}/auth/v1/otp"
            )
            for (otpUrl in candidateOtpUrls) {
                if (verificationSent) break
                val otpPayload = JSONObject().apply {
                    put("email", cleanNewEmail)
                    put("create_user", true)
                }
                val otpReq = Request.Builder()
                    .url(otpUrl)
                    .post(otpPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("apikey", supabaseAnonKeyProvider())
                    .header("Content-Type", "application/json")
                    .build()
                runCatching {
                    httpClientProvider().newCall(otpReq).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            verificationSent = true
                        } else {
                            Log.w(TAG, "Supabase POST /auth/v1/otp (${response.code}): $bodyStr")
                            val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                            val msg = listOfNotNull(
                                errJson?.optString("msg")?.takeIf { it.isNotBlank() },
                                errJson?.optString("message")?.takeIf { it.isNotBlank() },
                                errJson?.optString("error_description")?.takeIf { it.isNotBlank() }
                            ).firstOrNull().orEmpty()
                            if (msg.isNotBlank()) lastErrorMsg = msg
                        }
                    }
                }
            }

            // Fallback: if /auth/v1/otp was not sent and not rate-limited, try /auth/v1/recover
            if (!verificationSent && !lastErrorMsg.contains("60 seconds", ignoreCase = true)) {
                val recoverUrl = "${supabaseUrlProvider()}/auth/v1/recover?redirect_to=${urlEncode("ghotok://reset-password")}"
                val recoverPayload = JSONObject().apply {
                    put("email", cleanNewEmail)
                }
                val recoverReq = Request.Builder()
                    .url(recoverUrl)
                    .post(recoverPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("apikey", supabaseAnonKeyProvider())
                    .header("Content-Type", "application/json")
                    .build()
                runCatching {
                    httpClientProvider().newCall(recoverReq).execute().use { response ->
                        if (response.isSuccessful) {
                            verificationSent = true
                        }
                    }
                }
            }
        }

        // 2. Also attempt sending verification link via Firebase Authentication
        val firebaseSent = sendFirebaseEmailVerificationLink(context, cleanNewEmail)
        if (firebaseSent) {
            verificationSent = true
        }

        if (!verificationSent && lastErrorMsg.isNotBlank()) {
            return@withContext Result.failure(IllegalStateException(lastErrorMsg))
        }
        Result.success(verificationSent)
    }

    suspend fun checkIsEmailVerifiedRemotely(
        context: Context? = null,
        targetEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanTarget = targetEmail.trim().lowercase(Locale.US)
        if (cleanTarget.isBlank()) return@withContext false

        val resolvedContext = context?.applicationContext ?: appContextRef
        val userKey = (sessionUserId ?: getSessionUserId(resolvedContext)).orEmpty().ifBlank { "default" }

        fun promoteVerifiedEmail() {
            if (resolvedContext != null) {
                val prefs = resolvedContext.getSharedPreferences("ghotok_security_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("account_email_$userKey", cleanTarget)
                    .remove("unverified_email_$userKey")
                    .apply()
                if (userKey != "default") {
                    saveSession(context = resolvedContext, userId = userKey, email = cleanTarget)
                }
            }
            _emailVerifiedTick.value = System.currentTimeMillis()
        }

        if (resolvedContext != null) {
            // 1. Check if deep link already cleared unverified_email_<userId>
            val prefs = resolvedContext.getSharedPreferences("ghotok_security_prefs", Context.MODE_PRIVATE)
            val currentUnverified = prefs.getString("unverified_email_$userKey", "")?.trim().orEmpty()
            val storedOverride = prefs.getString("account_email_$userKey", "")?.trim().orEmpty()
            if (storedOverride.equals(cleanTarget, ignoreCase = true) && currentUnverified.isBlank()) {
                return@withContext true
            }

            // 2. Check secondary Firebase Email Verifier instance
            val firebaseVerified = runCatching {
                val verifierAuth = getOrCreateEmailVerifierAuth(resolvedContext)
                if (verifierAuth != null) {
                    val pwd = deriveVerifierPassword(cleanTarget)
                    val user = verifierAuth.currentUser?.takeIf {
                        it.email.equals(cleanTarget, ignoreCase = true)
                    } ?: verifierAuth.signInWithEmailAndPassword(cleanTarget, pwd).await()?.user
                    if (user != null) {
                        user.reload().await()
                        user.isEmailVerified
                    } else {
                        false
                    }
                } else {
                    false
                }
            }.getOrDefault(false)
            if (firebaseVerified) {
                promoteVerifiedEmail()
                return@withContext true
            }
        }

        // 3. Check primary Firebase user
        val primaryFirebaseVerified = runCatching {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                currentUser.reload().await()
                currentUser.email.equals(cleanTarget, ignoreCase = true) && currentUser.isEmailVerified
            } else {
                false
            }
        }.getOrDefault(false)
        if (primaryFirebaseVerified) {
            promoteVerifiedEmail()
            return@withContext true
        }

        // 4. Check Supabase /auth/v1/user if access token is available
        if (!isConfiguredProvider()) return@withContext false
        val activeToken = supabaseAccessToken ?: return@withContext false
        val url = "${supabaseUrlProvider()}/auth/v1/user"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("apikey", supabaseAnonKeyProvider())
            .header("Authorization", "Bearer $activeToken")
            .build()
        val supabaseVerified = runCatching {
            httpClientProvider().newCall(req).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val confirmedEmail = json.optString("email", "").trim().lowercase(Locale.US)
                    val confirmedAt = json.optString("email_confirmed_at", "")
                    val newEmailPending = json.optString("new_email", "").trim().lowercase(Locale.US)
                    confirmedEmail == cleanTarget && confirmedAt.isNotBlank() && newEmailPending != cleanTarget
                } else {
                    false
                }
            }
        }.getOrDefault(false)
        if (supabaseVerified) {
            promoteVerifiedEmail()
        }
        supabaseVerified
    }
}
