package com.example.data.supabase

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.MembershipPackages
import com.example.data.model.OnboardingDraft
import com.example.data.model.PaymentRequest
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRequest
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Supabase PostgREST & Realtime client for Ghotok Matrimony.
 * Delegates authentication/session concerns to [SupabaseAuthManager], WebSocket
 * change streams to [SupabaseRealtimeManager], and JSON row mapping to [SupabaseRowMappers].
 */
object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val authManager = SupabaseAuthManager(
        httpClientProvider = { httpClient },
        supabaseUrlProvider = { supabaseUrl },
        supabaseAnonKeyProvider = { supabaseAnonKey },
        isConfiguredProvider = { isConfigured() },
        selectRowsProvider = { table, query -> selectRows(table, query) }
    )

    private val realtimeManager = SupabaseRealtimeManager(
        httpClientProvider = { httpClient },
        supabaseUrlProvider = { supabaseUrl },
        supabaseAnonKeyProvider = { supabaseAnonKey },
        accessTokenProvider = { authManager.supabaseAccessToken },
        isConfiguredProvider = { isConfigured() }
    )

    val realtimeTableEvents: SharedFlow<String>
        get() = realtimeManager.tableChangeEvents

    val isRealtimeConnected: StateFlow<Boolean>
        get() = realtimeManager.isConnected

    fun ensureRealtimeConnected() = realtimeManager.ensureConnected()

    fun notifyLocalTableMutation(table: String) = realtimeManager.notifyLocalTableMutation(table)

    private fun nowIsoString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    val supabaseUrl: String
        get() = runCatching { BuildConfig.SUPABASE_URL.trim().trimEnd('/') }.getOrDefault("")

    val supabaseAnonKey: String
        get() = runCatching { BuildConfig.SUPABASE_ANON_KEY.trim() }.getOrDefault("")

    fun isConfigured(): Boolean {
        val url = supabaseUrl
        val key = supabaseAnonKey
        return url.isNotBlank() &&
            url != "YOUR_SUPABASE_URL" &&
            (url.startsWith("https://") || url.startsWith("http://")) &&
            key.isNotBlank() &&
            key != "YOUR_SUPABASE_ANON_KEY"
    }

    // =========================================================================
    // AUTH & SESSION DELEGATES (SupabaseAuthManager)
    // =========================================================================

    data class PasswordRecoveryChallenge(
        val email: String,
        val userId: String,
        val supabaseRecoveryEmailSent: Boolean
    )

    val pendingRecoveryDeepLink: StateFlow<String?>
        get() = authManager.pendingRecoveryDeepLink

    val currentAccessToken: String?
        get() = authManager.supabaseAccessToken

    fun setAccessToken(token: String?) {
        authManager.setAccessToken(token)
        realtimeManager.updateAccessToken(token)
    }

    fun setPendingRecoveryDeepLink(uriString: String?) = authManager.setPendingRecoveryDeepLink(uriString)

    fun clearPendingRecoveryDeepLink() = authManager.clearPendingRecoveryDeepLink()

    fun saveSession(
        context: Context?,
        userId: String,
        email: String = "",
        displayName: String = "",
        accessToken: String? = null,
        refreshToken: String? = null,
        password: String? = null
    ) {
        authManager.saveSession(context, userId, email, displayName, accessToken, refreshToken, password)
        if (!accessToken.isNullOrBlank()) {
            realtimeManager.updateAccessToken(accessToken)
        }
    }

    fun getSessionUserId(context: Context? = null): String? = authManager.getSessionUserId(context)

    fun getSessionEmail(context: Context? = null): String? = authManager.getSessionEmail(context)

    fun getSessionDisplayName(context: Context? = null): String? = authManager.getSessionDisplayName(context)

    fun isExplicitlySignedOut(context: Context? = null): Boolean = authManager.isExplicitlySignedOut(context)

    fun clearSession(context: Context? = null) {
        realtimeManager.disconnect()
        authManager.clearSession(context)
    }

    suspend fun checkAndCacheAdminStatus(email: String): Boolean =
        authManager.checkAndCacheAdminStatus(email)

    suspend fun requestPasswordRecovery(
        context: Context,
        email: String
    ): Result<PasswordRecoveryChallenge> = authManager.requestPasswordRecovery(email)

    suspend fun resetPasswordWithRecovery(
        context: Context,
        email: String,
        verificationInput: String,
        newPassword: String
    ): Result<String> = authManager.resetPasswordWithRecovery(context, email, verificationInput, newPassword)

    suspend fun signUpWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        fullName: String
    ): Result<String> = authManager.signUpWithEmailPassword(context, email, password, fullName)

    suspend fun signInWithEmailPassword(
        context: Context,
        email: String,
        password: String
    ): Result<String> = authManager.signInWithEmailPassword(context, email, password)

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String = "",
        displayName: String = "",
        context: Context? = null
    ): Boolean = authManager.signInWithGoogleIdToken(idToken, email, displayName, context)

    fun getAuthenticatedJwtUid(): String? = authManager.getAuthenticatedJwtUid()

    val emailVerifiedTick: StateFlow<Long>
        get() = authManager.emailVerifiedTick

    fun markEmailVerifiedFromDeepLink(context: Context, deepLinkUri: String): Boolean =
        authManager.markEmailVerifiedFromDeepLink(context, deepLinkUri)

    suspend fun requestEmailChangeAndVerificationLink(
        context: Context,
        userId: String,
        newEmail: String
    ): Result<Boolean> = authManager.requestEmailChangeAndVerificationLink(context, userId, newEmail)

    suspend fun checkIsEmailVerifiedRemotely(
        targetEmail: String,
        context: Context? = null
    ): Boolean = authManager.checkIsEmailVerifiedRemotely(context, targetEmail)

    // =========================================================================
    // LOW-LEVEL POSTGREST TRANSPORT
    // =========================================================================

    private fun Request.Builder.addSupabaseHeaders(
        prefer: String? = null,
        forceAnonKey: Boolean = false
    ): Request.Builder {
        val key = supabaseAnonKey
        val bearer = if (forceAnonKey) key else (authManager.supabaseAccessToken ?: key)
        header("apikey", key)
        header("Authorization", "Bearer $bearer")
        header("Accept", "application/json")
        if (!prefer.isNullOrBlank()) {
            header("Prefer", prefer)
        }
        return this
    }

    private fun isJwtOrAuthError(code: Int, bodyStr: String): Boolean {
        return code == 401 ||
            bodyStr.contains("PGRST301", ignoreCase = true) ||
            bodyStr.contains("JWT expired", ignoreCase = true) ||
            bodyStr.contains("invalid JWT", ignoreCase = true) ||
            bodyStr.contains("JWSInvalid", ignoreCase = true) ||
            bodyStr.contains("JWSError", ignoreCase = true)
    }

    private fun extractMissingColumnFromPostgrestError(bodyStr: String): String? {
        if (!bodyStr.contains("PGRST204", ignoreCase = true) &&
            !bodyStr.contains("Could not find the '", ignoreCase = true)
        ) {
            return null
        }
        val regex = Regex("Could not find the '([^']+)' column", RegexOption.IGNORE_CASE)
        return regex.find(bodyStr)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun cloneJsonObjectWithout(source: JSONObject, removeKeys: Set<String> = emptySet()): JSONObject {
        val copy = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!removeKeys.contains(k)) {
                copy.put(k, source.opt(k))
            }
        }
        return copy
    }

    private fun parseTimestampMillis(raw: String, fallbackPayload: JSONObject? = null): Long {
        val clean = raw.trim()
        if (clean.isNotEmpty()) {
            runCatching {
                return Instant.parse(clean).toEpochMilli()
            }
            runCatching {
                val normalized = clean
                    .replace(' ', 'T')
                    .let { s ->
                        when {
                            s.endsWith("+00") -> s.dropLast(3) + "Z"
                            s.endsWith("+00:00") -> s.dropLast(6) + "Z"
                            !s.endsWith("Z") && !s.contains("+") -> "${s}Z"
                            else -> s
                        }
                    }
                return Instant.parse(normalized).toEpochMilli()
            }
        }
        val payloadTs = fallbackPayload?.optLong("updatedAt", 0L) ?: 0L
        return payloadTs
    }

    private fun mergeUserAndProfileRows(primaryRow: JSONObject, secondaryRow: JSONObject): JSONObject {
        val merged = cloneJsonObjectWithout(secondaryRow)
        val primaryPayload = SupabaseRowMappers.extractPayloadJson(primaryRow)
        val secondaryPayload = SupabaseRowMappers.extractPayloadJson(secondaryRow)
        val mergedPayload = cloneJsonObjectWithout(secondaryPayload)
        val pKeys = primaryPayload.keys()
        while (pKeys.hasNext()) {
            val k = pKeys.next()
            val v = primaryPayload.opt(k)
            if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) {
                mergedPayload.put(k, v)
            }
        }
        val rKeys = primaryRow.keys()
        while (rKeys.hasNext()) {
            val k = rKeys.next()
            if (k == "payload") continue
            val v = primaryRow.opt(k)
            if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) {
                merged.put(k, v)
            }
        }
        if (mergedPayload.length() > 0) {
            merged.put("payload", mergedPayload)
        }
        return merged
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    suspend fun selectRowsOrThrow(table: String, queryParams: String = ""): List<JSONObject> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext emptyList()
            if (table != "admins" && (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired())) {
                authManager.ensureValidWriteAccessToken()
            }
            val querySuffix = if (queryParams.isBlank()) "select=*" else "select=*&$queryParams"
            val url = "$supabaseUrl/rest/v1/$table?$querySuffix"

            fun executeGet(forceAnon: Boolean): Pair<Int, String> {
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .addSupabaseHeaders(forceAnonKey = forceAnon)
                    .build()
                httpClient.newCall(req).execute().use { response ->
                    return response.code to response.body?.string().orEmpty()
                }
            }

            try {
                var (code, bodyStr) = executeGet(forceAnon = false)
                if (code !in 200..299 && isJwtOrAuthError(code, bodyStr)) {
                    Log.w(TAG, "SELECT $table got auth error ($code), refreshing/falling back token...")
                    authManager.handleUnauthorizedToken()
                    val retryResult = executeGet(forceAnon = authManager.supabaseAccessToken == null)
                    code = retryResult.first
                    bodyStr = retryResult.second
                    if (code !in 200..299 && authManager.supabaseAccessToken != null) {
                        if (authManager.isCurrentAccessTokenExpired()) {
                            authManager.setAccessToken(null)
                        }
                        val anonRetry = executeGet(forceAnon = true)
                        code = anonRetry.first
                        bodyStr = anonRetry.second
                    }
                }

                if (code !in 200..299) {
                    Log.w(TAG, "SELECT $table failed ($code): $bodyStr")
                    throw java.io.IOException("Supabase SELECT $table HTTP $code")
                }

                val parsed = SupabaseRowMappers.parseJsonArray(bodyStr)
                if (parsed.isEmpty() && authManager.supabaseAccessToken != null &&
                    (table == "users" || table == "profiles" || table == "interests" || table == "chats")
                ) {
                    // Fallback check with anon key in case RLS policies filter custom UIDs under a mismatched JWT
                    val (anonCode, anonBody) = executeGet(forceAnon = true)
                    if (anonCode in 200..299) {
                        val anonParsed = SupabaseRowMappers.parseJsonArray(anonBody)
                        if (anonParsed.isNotEmpty()) {
                            return@withContext anonParsed
                        }
                    }
                }
                parsed
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }

    suspend fun selectRows(table: String, queryParams: String = ""): List<JSONObject> =
        try {
            selectRowsOrThrow(table, queryParams)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SELECT $table error: ${e.message}")
            emptyList()
        }

    suspend fun upsertRow(
        table: String,
        row: JSONObject,
        onConflict: String = "id"
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        if (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired()) {
            authManager.ensureValidWriteAccessToken()
        }
        val conflictParam = if (onConflict.isNotBlank()) "?on_conflict=${urlEncode(onConflict)}" else ""
        val url = "$supabaseUrl/rest/v1/$table$conflictParam"
        var workingRow = cloneJsonObjectWithout(row)

        fun executePost(payloadObj: JSONObject, forceAnon: Boolean): Pair<Int, String> {
            val request = Request.Builder()
                .url(url)
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addSupabaseHeaders(
                    prefer = "resolution=merge-duplicates,return=minimal",
                    forceAnonKey = forceAnon
                )
                .header("Content-Type", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                return response.code to response.body?.string().orEmpty()
            }
        }

        try {
            var (code, err) = executePost(workingRow, forceAnon = false)
            var strippedCount = 0
            while (code == 400 && strippedCount < 8) {
                val missingCol = extractMissingColumnFromPostgrestError(err) ?: break
                if (!workingRow.has(missingCol)) break
                workingRow.remove(missingCol)
                strippedCount++
                val retryCol = executePost(workingRow, forceAnon = false)
                code = retryCol.first
                err = retryCol.second
            }
            if (code !in 200..299 && (isJwtOrAuthError(code, err) || code == 403)) {
                authManager.handleUnauthorizedToken()
                authManager.ensureValidWriteAccessToken()
                val retry = executePost(workingRow, forceAnon = authManager.supabaseAccessToken == null)
                code = retry.first
                err = retry.second
                while (code == 400 && strippedCount < 8) {
                    val missingCol = extractMissingColumnFromPostgrestError(err) ?: break
                    if (!workingRow.has(missingCol)) break
                    workingRow.remove(missingCol)
                    strippedCount++
                    val retryCol = executePost(workingRow, forceAnon = authManager.supabaseAccessToken == null)
                    code = retryCol.first
                    err = retryCol.second
                }
                if (code !in 200..299 && authManager.supabaseAccessToken != null) {
                    val anonFallback = executePost(workingRow, forceAnon = true)
                    if (anonFallback.first in 200..299) {
                        code = anonFallback.first
                        err = anonFallback.second
                    }
                }
            }
            if (code !in 200..299) {
                Log.w(TAG, "UPSERT $table failed ($code): $err")
            } else {
                realtimeManager.notifyLocalTableMutation(table)
            }
            code in 200..299
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "UPSERT $table exception: ${e.message}")
            false
        }
    }

    suspend fun patchRowsReturningCount(
        table: String,
        filterQuery: String,
        updates: JSONObject
    ): Int = withContext(Dispatchers.IO) {
        if (!isConfigured() || filterQuery.isBlank()) return@withContext 0
        if (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired()) {
            authManager.ensureValidWriteAccessToken()
        }
        val url = "$supabaseUrl/rest/v1/$table?$filterQuery"
        var workingUpdates = cloneJsonObjectWithout(updates)

        fun executePatch(payloadObj: JSONObject, forceAnon: Boolean): Pair<Int, String> {
            val request = Request.Builder()
                .url(url)
                .patch(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addSupabaseHeaders(prefer = "return=representation", forceAnonKey = forceAnon)
                .header("Content-Type", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                return response.code to response.body?.string().orEmpty()
            }
        }

        try {
            var (code, bodyStr) = executePatch(workingUpdates, forceAnon = false)
            var strippedCount = 0
            while (code == 400 && strippedCount < 8) {
                val missingCol = extractMissingColumnFromPostgrestError(bodyStr) ?: break
                if (!workingUpdates.has(missingCol)) break
                workingUpdates.remove(missingCol)
                strippedCount++
                val retryCol = executePatch(workingUpdates, forceAnon = false)
                code = retryCol.first
                bodyStr = retryCol.second
            }
            if (code !in 200..299 && (isJwtOrAuthError(code, bodyStr) || code == 403)) {
                authManager.handleUnauthorizedToken()
                authManager.ensureValidWriteAccessToken()
                val retry = executePatch(workingUpdates, forceAnon = authManager.supabaseAccessToken == null)
                code = retry.first
                bodyStr = retry.second
            }
            var updatedRows = if (code in 200..299) SupabaseRowMappers.parseJsonArray(bodyStr).size else 0
            if (updatedRows == 0 && authManager.supabaseAccessToken != null) {
                val anonFallback = executePatch(workingUpdates, forceAnon = true)
                if (anonFallback.first in 200..299) {
                    val anonCount = SupabaseRowMappers.parseJsonArray(anonFallback.second).size
                    if (anonCount > 0) {
                        code = anonFallback.first
                        bodyStr = anonFallback.second
                        updatedRows = anonCount
                    }
                }
            }
            if (code !in 200..299) {
                Log.w(TAG, "PATCH $table?$filterQuery failed ($code): $bodyStr")
            }
            updatedRows
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PATCH $table exception: ${e.message}")
            0
        }
    }

    suspend fun patchRows(
        table: String,
        filterQuery: String,
        updates: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || filterQuery.isBlank()) return@withContext false
        if (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired()) {
            authManager.ensureValidWriteAccessToken()
        }
        val url = "$supabaseUrl/rest/v1/$table?$filterQuery"
        var workingUpdates = cloneJsonObjectWithout(updates)

        fun executePatch(payloadObj: JSONObject, forceAnon: Boolean): Pair<Int, String> {
            val request = Request.Builder()
                .url(url)
                .patch(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addSupabaseHeaders(prefer = "return=minimal", forceAnonKey = forceAnon)
                .header("Content-Type", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                return response.code to response.body?.string().orEmpty()
            }
        }

        try {
            var (code, err) = executePatch(workingUpdates, forceAnon = false)
            var strippedCount = 0
            while (code == 400 && strippedCount < 8) {
                val missingCol = extractMissingColumnFromPostgrestError(err) ?: break
                if (!workingUpdates.has(missingCol)) break
                workingUpdates.remove(missingCol)
                strippedCount++
                val retryCol = executePatch(workingUpdates, forceAnon = false)
                code = retryCol.first
                err = retryCol.second
            }
            if (code !in 200..299 && (isJwtOrAuthError(code, err) || code == 403)) {
                authManager.handleUnauthorizedToken()
                authManager.ensureValidWriteAccessToken()
                val retry = executePatch(workingUpdates, forceAnon = authManager.supabaseAccessToken == null)
                code = retry.first
                err = retry.second
                if (code !in 200..299 && authManager.supabaseAccessToken != null) {
                    val anonFallback = executePatch(workingUpdates, forceAnon = true)
                    if (anonFallback.first in 200..299) {
                        code = anonFallback.first
                        err = anonFallback.second
                    }
                }
            }
            if (code !in 200..299) {
                Log.w(TAG, "PATCH $table?$filterQuery failed ($code): $err")
            } else {
                realtimeManager.notifyLocalTableMutation(table)
            }
            code in 200..299
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PATCH $table exception: ${e.message}")
            false
        }
    }

    suspend fun deleteRows(
        table: String,
        filterQuery: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || filterQuery.isBlank()) return@withContext false
        val url = "$supabaseUrl/rest/v1/$table?$filterQuery"
        try {
            val request = Request.Builder()
                .url(url)
                .delete()
                .addSupabaseHeaders(prefer = "return=minimal")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code in listOf(401, 403)) {
                    authManager.handleUnauthorizedToken()
                    val retryReq = Request.Builder()
                        .url(url)
                        .delete()
                        .addSupabaseHeaders(
                            prefer = "return=minimal",
                            forceAnonKey = authManager.supabaseAccessToken == null
                        )
                        .build()
                    httpClient.newCall(retryReq).execute().use { retryResp ->
                        return@withContext retryResp.isSuccessful
                    }
                }
                response.isSuccessful
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // FIREBASE CLOUD MESSAGING (FCM) TOKEN REGISTRATION IN SUPABASE
    // =========================================================================

    suspend fun registerFcmToken(userId: String, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank() || fcmToken.isBlank() || !isConfigured()) return@withContext false
        val nowIso = nowIsoString()
        val tokenRow = JSONObject().apply {
            put("user_id", userId)
            put("fcm_token", fcmToken)
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("platform", "android")
            put("updated_at", nowIso)
        }
        val savedToken = upsertRow("fcm_tokens", tokenRow, onConflict = "user_id")
        val userPatch = JSONObject().apply {
            put("fcm_token", fcmToken)
            put("updated_at", nowIso)
        }
        patchRows("users", "uid=eq.${urlEncode(userId)}", userPatch)
        patchRows("profiles", "uid=eq.${urlEncode(userId)}", userPatch)
        savedToken
    }

    // =========================================================================
    // USER PROFILE & BIODATA OPERATIONS
    // =========================================================================

    private suspend fun callAllocateProfileCodeRpc(
        uid: String,
        gender: String,
        preferredCode: String,
        yearDigit: String
    ): String? {
        if (!isConfigured()) return null
        val url = "$supabaseUrl/rest/v1/rpc/allocate_unique_profile_code"
        val payload = JSONObject().apply {
            put("p_uid", uid)
            put("p_gender", gender)
            put("p_preferred_code", preferredCode)
            put("p_year_digit", yearDigit)
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addSupabaseHeaders()
            .header("Content-Type", "application/json")
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty().trim().removeSurrounding("\"")
                raw.takeIf { UserProfile.isValidFormattedUserId(it, gender) }
            }
        }.getOrNull()
    }

    suspend fun allocateUniqueProfileCode(
        uid: String,
        gender: String,
        preferredCode: String = "",
        createdAt: Timestamp? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val effectiveGender = gender.ifBlank { "Male" }
        val cleanPref = preferredCode.trim().uppercase(Locale.US)
        if (!isConfigured()) {
            val validPref = cleanPref.takeIf { UserProfile.isValidFormattedUserId(it, effectiveGender) }
            return@withContext validPref ?: UserProfile.formatSerialUserId(effectiveGender, 1, createdAt)
        }

        // 1. Targeted O(1) lookup for the user's own row or preferredCode ownership (instead of scanning all rows)
        val filterParts = mutableListOf<String>()
        if (cleanUid.isNotBlank()) {
            val encUid = urlEncode(cleanUid)
            filterParts.add("uid.eq.$encUid")
            filterParts.add("id.eq.$encUid")
        }
        if (UserProfile.isValidFormattedUserId(cleanPref, effectiveGender)) {
            val encPref = urlEncode(cleanPref)
            filterParts.add("id.eq.$encPref")
            filterParts.add("profile_code.eq.$encPref")
        }
        if (filterParts.isNotEmpty()) {
            val targetedQuery = "or=(${filterParts.joinToString(",")})&limit=5"
            val existingRows = selectRows("matchmaking_users", targetedQuery)
            var ownExistingCode: String? = null
            var prefOwnedByOther = false

            for (row in existingRows) {
                val payload = SupabaseRowMappers.extractPayloadJson(row)
                val rowUid = row.optString("uid", payload.optString("userId", payload.optString("uid", ""))).trim()
                val rowId = row.optString("id", "").trim()
                val rowCode = row.optString("profile_code", payload.optString("profileCode", rowId)).trim()
                    .ifBlank { rowId }
                    .uppercase(Locale.US)

                if (cleanUid.isNotBlank() && rowUid.equals(cleanUid, ignoreCase = true) &&
                    UserProfile.isValidFormattedUserId(rowCode, effectiveGender)
                ) {
                    ownExistingCode = rowCode
                }
                if (cleanPref.isNotBlank() &&
                    (rowCode.equals(cleanPref, ignoreCase = true) || rowId.equals(cleanPref, ignoreCase = true)) &&
                    rowUid.isNotBlank() &&
                    !rowUid.equals(cleanUid, ignoreCase = true)
                ) {
                    prefOwnedByOther = true
                }
            }

            if (!ownExistingCode.isNullOrBlank()) {
                return@withContext ownExistingCode!!
            }
            if (UserProfile.isValidFormattedUserId(cleanPref, effectiveGender) && !prefOwnedByOther) {
                return@withContext cleanPref
            }
        }

        // 2. Try atomic Postgres sequence/counter RPC (allocate_unique_profile_code)
        val cal = Calendar.getInstance().apply {
            if (createdAt != null) time = createdAt.toDate()
        }
        val yearLastDigit = (cal.get(Calendar.YEAR) % 10).toString()
        callAllocateProfileCodeRpc(
            uid = cleanUid,
            gender = effectiveGender,
            preferredCode = cleanPref,
            yearDigit = yearLastDigit
        )?.let { rpcCode ->
            return@withContext rpcCode
        }

        // 3. Fallback O(1) top-serial query (fetches at most 5 highest rows for the gender prefix instead of full table)
        val prefix = if (effectiveGender.trim().lowercase(Locale.US) in setOf("female", "bride", "woman", "girl", "f")) "B" else "G"
        val topRows = selectRows(
            "matchmaking_users",
            "or=(id.ilike.${prefix}*,profile_code.ilike.${prefix}*)&order=id.desc&limit=5"
        )
        var maxSerial = 0
        for (row in topRows) {
            val payload = SupabaseRowMappers.extractPayloadJson(row)
            val rowId = row.optString("id", "").trim()
            val rowCode = row.optString("profile_code", payload.optString("profileCode", rowId)).trim()
            for (candidate in listOf(rowCode, rowId)) {
                if (UserProfile.isValidFormattedUserId(candidate, effectiveGender)) {
                    val s = UserProfile.extractSerialFromUserId(candidate) ?: 0
                    if (s > maxSerial) maxSerial = s
                }
            }
        }
        val nextSerial = (maxSerial + 1).coerceAtLeast(1)
        UserProfile.formatSerialUserId(
            gender = effectiveGender,
            serial = nextSerial,
            createdAt = createdAt
        )
    }

    suspend fun fetchAllUsers(): List<UserProfile> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        var usersError: Exception? = null
        var profilesError: Exception? = null
        val userRows = try {
            selectRowsOrThrow("matchmaking_users", "order=updated_at.desc&limit=150")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            usersError = e
            emptyList()
        }
        val profileRows = try {
            selectRowsOrThrow("matchmaking_profiles", "order=updated_at.desc&limit=150")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            profilesError = e
            emptyList()
        }
        if (usersError != null) {
            Log.w(TAG, "fetchAllUsers could not load matchmaking_users: ${usersError.message}")
        }
        if (profilesError != null) {
            Log.w(TAG, "fetchAllUsers could not load matchmaking_profiles: ${profilesError.message}")
        }
        if (userRows.isEmpty() && profileRows.isEmpty() && (usersError != null || profilesError != null)) {
            throw (usersError ?: profilesError!!)
        }
        val rowsByKey = linkedMapOf<String, Pair<JSONObject, Long>>()
        for (row in (userRows + profileRows)) {
            val payload = SupabaseRowMappers.extractPayloadJson(row)
            val rowId = row.optString("id", "").trim()
            val uid = row.optString("uid", payload.optString("userId", payload.optString("uid", ""))).trim()
                .ifBlank { rowId }
            if (uid.isBlank()) continue
            val ts = parseTimestampMillis(row.optString("updated_at", ""), payload)
            val existing = rowsByKey[uid]
            if (existing == null) {
                rowsByKey[uid] = row to ts
            } else {
                val (newer, older) = if (ts > existing.second) {
                    row to existing.first
                } else {
                    existing.first to row
                }
                rowsByKey[uid] = mergeUserAndProfileRows(newer, older) to maxOf(ts, existing.second)
            }
        }

        val results = ArrayList<UserProfile>(rowsByKey.size)
        for ((_, pair) in rowsByKey) {
            val profile = SupabaseRowMappers.rowToUserProfile(pair.first) ?: continue
            if (profile.displayName.isBlank()) continue
            results.add(profile)
        }
        results
    }

    suspend fun fetchUserProfile(uidOrCode: String): UserProfile? = withContext(Dispatchers.IO) {
        val clean = uidOrCode.trim()
        if (clean.isBlank() || !isConfigured()) return@withContext null
        val encoded = urlEncode(clean)
        val isFormattedCodeQuery = UserProfile.isValidFormattedUserId(clean)
        val filter = if (isFormattedCodeQuery) {
            "or=(id.eq.$encoded,profile_code.eq.$encoded,uid.eq.$encoded)&order=updated_at.desc&limit=5"
        } else {
            "or=(uid.eq.$encoded,id.eq.$encoded)&order=updated_at.desc&limit=5"
        }

        var userRows = selectRows("users", filter)
        var profileRows = emptyList<JSONObject>()
        if (userRows.isEmpty()) {
            userRows = selectRows("matchmaking_users", filter)
        }
        if (userRows.isEmpty()) {
            profileRows = selectRows("matchmaking_profiles", filter)
        }

        val jwtUid = authManager.getAuthenticatedJwtUid()?.trim().orEmpty()
        if (userRows.isEmpty() && profileRows.isEmpty() && !isFormattedCodeQuery) {
            if (jwtUid.isNotBlank() && !jwtUid.equals(clean, ignoreCase = true)) {
                val jwtEncoded = urlEncode(jwtUid)
                val jwtFilter = "or=(uid.eq.$jwtEncoded,id.eq.$jwtEncoded)&order=updated_at.desc&limit=5"
                userRows = selectRows("users", jwtFilter)
                if (userRows.isEmpty()) {
                    userRows = selectRows("matchmaking_users", jwtFilter)
                }
                if (userRows.isEmpty()) {
                    profileRows = selectRows("matchmaking_profiles", jwtFilter)
                }
            }
            val sessionEmail = getSessionEmail()?.trim()?.lowercase(Locale.US).orEmpty()
            if (userRows.isEmpty() && profileRows.isEmpty() && sessionEmail.isNotBlank() &&
                clean == getSessionUserId()?.trim()
            ) {
                val emailFilter = "email=ilike.${urlEncode(sessionEmail)}&order=updated_at.desc&limit=5"
                userRows = selectRows("users", emailFilter)
                if (userRows.isEmpty()) {
                    profileRows = selectRows("matchmaking_users", emailFilter)
                }
                if (userRows.isEmpty()) {
                    profileRows = selectRows("matchmaking_profiles", emailFilter)
                }
            }
        }

        fun matchesQuery(row: JSONObject): Boolean {
            if (isFormattedCodeQuery) return true
            val payload = SupabaseRowMappers.extractPayloadJson(row)
            val rowUid = row.optString("uid", payload.optString("userId", payload.optString("uid", ""))).trim()
            val rowId = row.optString("id", "").trim()
            val rowEmail = row.optString("email", payload.optString("email", "")).trim()
            val sessionEmail = getSessionEmail()?.trim().orEmpty()
            return rowUid.equals(clean, ignoreCase = true) ||
                rowId.equals(clean, ignoreCase = true) ||
                (jwtUid.isNotBlank() && (rowUid.equals(jwtUid, ignoreCase = true) || rowId.equals(jwtUid, ignoreCase = true))) ||
                (sessionEmail.isNotBlank() && clean == getSessionUserId()?.trim() && rowEmail.equals(sessionEmail, ignoreCase = true))
        }

        val userRow = userRows.firstOrNull { matchesQuery(it) } ?: userRows.firstOrNull()
        val profileMirrorRow = profileRows.firstOrNull { matchesQuery(it) } ?: profileRows.firstOrNull()

        val mainRow = when {
            userRow != null && profileMirrorRow != null -> {
                val userPayload = SupabaseRowMappers.extractPayloadJson(userRow)
                val profPayload = SupabaseRowMappers.extractPayloadJson(profileMirrorRow)
                val userUpdatedMs = parseTimestampMillis(userRow.optString("updated_at", ""), userPayload)
                val profUpdatedMs = parseTimestampMillis(profileMirrorRow.optString("updated_at", ""), profPayload)
                if (profUpdatedMs > userUpdatedMs) {
                    mergeUserAndProfileRows(profileMirrorRow, userRow)
                } else {
                    mergeUserAndProfileRows(userRow, profileMirrorRow)
                }
            }
            userRow != null -> userRow
            else -> profileMirrorRow
        } ?: return@withContext null

        val mainPayloadInitial = SupabaseRowMappers.extractPayloadJson(mainRow)
        val rawDbTier = MembershipPackages.normalizeTierName(
            mainRow.optString("package_tier", mainPayloadInitial.optString("packageTier", "Free"))
        )
        var profile = SupabaseRowMappers.rowToUserProfile(mainRow) ?: return@withContext null

        val targetUid = profile.userId.ifBlank { clean }
        val profileCode = profile.profileCode.ifBlank { profile.id }
        val emailToCheck = profile.email.ifBlank { getSessionEmail().orEmpty() }

        // Execute enrichment queries in parallel instead of 4 sequential HTTP round-trips
        val (paymentRows, verifRows, contactRows, isAdminVerified) = coroutineScope {
            val paymentDeferred = async {
                selectRows(
                    "payment_requests",
                    "user_id=eq.${urlEncode(targetUid)}&order=updated_at.desc&limit=1"
                )
            }
            val verifDeferred = async {
                val verifFilter = if (profileCode.isNotBlank() && profileCode != targetUid) {
                    "or=(user_id.eq.${urlEncode(targetUid)},id.eq.${urlEncode(profileCode)})&order=updated_at.desc&limit=1"
                } else {
                    "user_id=eq.${urlEncode(targetUid)}&order=updated_at.desc&limit=1"
                }
                selectRows("verification_requests", verifFilter)
            }
            val contactDeferred = async {
                selectRows("profile_contacts", "user_id=eq.${urlEncode(targetUid)}&limit=1")
            }
            val adminDeferred = async {
                if (emailToCheck.isNotBlank()) checkAndCacheAdminStatus(emailToCheck) else false
            }
            Quadruple(
                paymentDeferred.await(),
                verifDeferred.await(),
                contactDeferred.await(),
                adminDeferred.await()
            )
        }

        paymentRows.firstOrNull()?.let { payRow ->
            val status = payRow.optString("status", "").uppercase(Locale.US)
            val reqId = payRow.optString("id", "")
            val rawPlan = payRow.optString("plan_name", payRow.optString("plan_id", ""))
            val targetTier = MembershipPackages.normalizeTierName(rawPlan)
            val mainPayload = SupabaseRowMappers.extractPayloadJson(mainRow)
            val lastAppliedReqId = mainPayload.optString("lastAppliedPaymentRequestId", "")
            val payPayload = SupabaseRowMappers.extractPayloadJson(payRow)
            val pkgSpec = MembershipPackages.byTier(targetTier)
            val payActivatedMs = payPayload.optLong("packageActivatedAt", 0L).takeIf { it > 0L }
                ?: parseTimestampMillis(payRow.optString("updated_at", payRow.optString("created_at", "")), payPayload).takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val customDays = payPayload.optInt("durationDays", pkgSpec.durationDays).takeIf { it > 0 }
            val payExpiresMs = payPayload.optLong("packageExpiresAt", 0L).takeIf { it > 0L }
                ?: pkgSpec.computeExpiryMillis(payActivatedMs, customDays)
            val isPaymentStillValid = pkgSpec.tier == "Free" || payExpiresMs <= 0L || System.currentTimeMillis() < payExpiresMs

            if ((status == "APPROVED" || status == "COMPLETED" || status == "ACTIVE") &&
                rawPlan.isNotBlank() &&
                reqId.isNotBlank() &&
                reqId != lastAppliedReqId &&
                isPaymentStillValid &&
                !profile.packageTier.equals(targetTier, ignoreCase = true)
            ) {
                val customInterests = payPayload.optInt("interestsToCredit", pkgSpec.interestsCount).takeIf { it >= 0 } ?: pkgSpec.interestsCount
                val customContacts = payPayload.optInt("contactsToCredit", pkgSpec.contactsCount).takeIf { it >= 0 } ?: pkgSpec.contactsCount
                profile = profile.copy(
                    packageTier = pkgSpec.tier,
                    pendingPackageTier = "",
                    packageActivatedAt = if (pkgSpec.tier == "Free") 0L else payActivatedMs,
                    packageExpiresAt = if (pkgSpec.tier == "Free") 0L else payExpiresMs,
                    interestsRemaining = customInterests,
                    contactsRemaining = customContacts,
                    isVerified = profile.isVerified || pkgSpec.hasVerifiedBadge
                )
            } else if (status == "REJECTED" || status == "DECLINED") {
                if (profile.pendingPackageTier.equals(targetTier, ignoreCase = true)) {
                    profile = profile.copy(pendingPackageTier = "")
                }
            }
        }

        verifRows.firstOrNull()?.let { verifRow ->
            val verifStatus = verifRow.optString("status", "").trim().uppercase(Locale.US)
            val verifPayload = SupabaseRowMappers.extractPayloadJson(verifRow)
            val docType = verifRow.optString("document_type", verifPayload.optString("docType", profile.verificationDocType))
                .ifBlank { profile.verificationDocType }
            val docNumber = verifRow.optString("document_number", verifPayload.optString("idNumber", profile.verificationIdNumber))
                .ifBlank { profile.verificationIdNumber }
            val docUrl = verifRow.optString("document_front_url", verifPayload.optString("docUrl", profile.verificationDocUri))
                .ifBlank { profile.verificationDocUri }
            when (verifStatus) {
                "APPROVED", "VERIFIED" -> {
                    profile = profile.copy(
                        isVerified = true,
                        verificationStatus = "VERIFIED",
                        verificationDocType = docType,
                        verificationIdNumber = docNumber,
                        verificationDocUri = docUrl
                    )
                }
                "REJECTED", "DECLINED" -> {
                    if (!profile.isVerified) {
                        profile = profile.copy(
                            verificationStatus = "REJECTED",
                            verificationDocType = docType,
                            verificationIdNumber = docNumber,
                            verificationDocUri = docUrl
                        )
                    }
                }
                "PENDING" -> {
                    if (!profile.isVerified &&
                        (profile.verificationStatus.isBlank() || profile.verificationStatus.equals("UNSUBMITTED", ignoreCase = true))
                    ) {
                        profile = profile.copy(
                            verificationStatus = "PENDING",
                            verificationDocType = docType,
                            verificationIdNumber = docNumber,
                            verificationDocUri = docUrl
                        )
                    }
                }
            }
        }

        contactRows.firstOrNull()?.let { contactRow ->
            val contactPayload = SupabaseRowMappers.extractPayloadJson(contactRow)
            profile = profile.copy(
                contactPhone = contactPayload.optString("contactPhone", contactRow.optString("candidate_mobile", profile.contactPhone)).ifBlank { profile.contactPhone },
                guardianPhone = contactPayload.optString("guardianPhone", contactRow.optString("guardian_mobile", profile.guardianPhone)).ifBlank { profile.guardianPhone },
                whatsapp = contactPayload.optString("whatsapp", contactRow.optString("whatsapp_number", profile.whatsapp)).ifBlank { profile.whatsapp },
                email = contactPayload.optString("email", contactRow.optString("contact_email", profile.email)).ifBlank { profile.email },
                presentAddress = contactPayload.optString("presentAddress", contactRow.optString("present_address", profile.presentAddress)).ifBlank { profile.presentAddress },
                permanentAddress = contactPayload.optString("permanentAddress", contactRow.optString("permanent_address", profile.permanentAddress)).ifBlank { profile.permanentAddress }
            )
        }

        if (isAdminVerified) {
            profile = profile.copy(
                role = "ADMIN",
                isAdmin = true
            )
        }
        profile = profile.withWeeklyFreeInterestReset()
        if (rawDbTier != "Free" && profile.packageTier.equals("Free", ignoreCase = true)) {
            runCatching { upsertUserProfile(profile) }
        }
        profile
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    suspend fun upsertUserProfile(profile: UserProfile, fcmToken: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        if (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired()) {
            authManager.ensureValidWriteAccessToken()
        }
        val requestedUid = profile.userId.ifBlank { profile.id }.trim()
        if (requestedUid.isBlank()) return@withContext false
        val jwtUid = authManager.getAuthenticatedJwtUid()?.trim().orEmpty()
        val uid = requestedUid

        val rawPreferredCode = profile.profileCode.takeIf { UserProfile.isValidFormattedUserId(it, profile.gender) }
            ?: profile.id.takeIf { UserProfile.isValidFormattedUserId(it, profile.gender) }
            ?: ""
        val profileCode = allocateUniqueProfileCode(
            uid = uid,
            gender = profile.gender,
            preferredCode = rawPreferredCode,
            createdAt = profile.createdAt
        )

        val normalizedTier = MembershipPackages.normalizeTierName(profile.packageTier)
        val normalizedProfile = profile.copy(
            id = profileCode,
            userId = uid,
            profileCode = profileCode,
            packageTier = normalizedTier,
            updatedAt = Timestamp.now()
        )
        val completionScore = normalizedProfile.calculateCompletionPercentage()
        val isUserAdmin = normalizedProfile.isAdmin ||
            normalizedProfile.role.equals("ADMIN", ignoreCase = true) ||
            normalizedProfile.role.equals("SUPER_ADMIN", ignoreCase = true)

        val existingUserRowForPayload = selectRows(
            "users",
            "or=(uid.eq.${urlEncode(uid)},id.eq.${urlEncode(profileCode)},profile_code.eq.${urlEncode(profileCode)})&limit=1"
        ).firstOrNull()
        val existingInterestRecords = existingUserRowForPayload
            ?.let { SupabaseRowMappers.extractPayloadJson(it).optJSONArray("interestRecords") }

        fun buildPayloadJson(effectiveUid: String): JSONObject {
            return runCatching { JSONObject(normalizedProfile.copy(userId = effectiveUid).toJson()) }.getOrElse {
                SupabaseRowMappers.instanceToJsonObject(normalizedProfile.copy(userId = effectiveUid))
            }.apply {
                put("id", profileCode)
                put("userId", effectiveUid)
                put("uid", effectiveUid)
                put("profileCode", profileCode)
                put("fullName", normalizedProfile.displayName)
                put("displayNameBangla", normalizedProfile.displayNameBangla)
                put("email", normalizedProfile.email)
                put("phoneNumber", normalizedProfile.contactPhone.ifBlank { normalizedProfile.guardianPhone })
                put("highestDegree", normalizedProfile.education)
                put("occupation", normalizedProfile.profession)
                put("packageTier", normalizedTier)
                put("planName", normalizedTier)
                put("planId", normalizedTier.lowercase(Locale.US))
                put("pendingPackageTier", normalizedProfile.pendingPackageTier)
                put("packageActivatedAt", normalizedProfile.packageActivatedAt)
                put("packageExpiresAt", normalizedProfile.packageExpiresAt)
                put("interestsRemaining", normalizedProfile.interestsRemaining)
                put("contactsRemaining", normalizedProfile.contactsRemaining)
                put("profileCompletionScore", completionScore)
                put("onboardingCompleted", normalizedProfile.displayName.isNotBlank())
                put("verificationStatus", if (normalizedProfile.isVerified) "VERIFIED" else normalizedProfile.verificationStatus.ifBlank { "UNSUBMITTED" })
                put("updatedAt", System.currentTimeMillis())
                if (fcmToken.isNotBlank()) put("fcmToken", fcmToken)
                val mergedInterestsArr = buildMirroredInterestsArrayForUser(
                    candidateIds = setOf(effectiveUid, uid, profileCode, jwtUid).filter { it.isNotBlank() }.toSet(),
                    existingArray = existingInterestRecords
                )
                if (mergedInterestsArr.length() > 0) {
                    put("interestRecords", mergedInterestsArr)
                }
            }
        }

        val nowIso = nowIsoString()

        fun buildUserRow(effectiveUid: String): JSONObject {
            val pJson = buildPayloadJson(effectiveUid)
            return JSONObject().apply {
                put("id", profileCode)
                put("uid", effectiveUid)
                put("profile_code", profileCode)
                put("full_name", normalizedProfile.displayName)
                put("email", normalizedProfile.email)
                put("phone_number", normalizedProfile.contactPhone.ifBlank { normalizedProfile.guardianPhone })
                put("gender", normalizedProfile.gender)
                put("date_of_birth", normalizedProfile.dateOfBirth)
                put("religion", normalizedProfile.religion)
                put("marital_status", normalizedProfile.maritalStatus)
                put("present_division", normalizedProfile.presentDivision.ifBlank { normalizedProfile.locationDivision })
                put("present_district", normalizedProfile.presentDistrict.ifBlank { normalizedProfile.locationDistrict })
                put("present_upazila", normalizedProfile.presentUpazila)
                put("present_address", normalizedProfile.presentAddress)
                put("permanent_division", normalizedProfile.permanentDivision)
                put("permanent_district", normalizedProfile.permanentDistrict)
                put("permanent_upazila", normalizedProfile.permanentUpazila)
                put("permanent_address", normalizedProfile.permanentAddress)
                put("highest_degree", normalizedProfile.education)
                put("occupation", normalizedProfile.profession)
                put("employment_type", normalizedProfile.employmentType.ifBlank { normalizedProfile.jobEmploymentStatus })
                put("work_location_district", normalizedProfile.workLocationDistrict)
                put("account_status", normalizedProfile.accountStatus.ifBlank { "ACTIVE" })
                put("verification_status", if (normalizedProfile.isVerified) "VERIFIED" else normalizedProfile.verificationStatus.ifBlank { "UNSUBMITTED" })
                put("is_verified", normalizedProfile.isVerified)
                put("package_tier", normalizedTier)
                put("pending_package_tier", normalizedProfile.pendingPackageTier)
                put("interests_remaining", normalizedProfile.interestsRemaining)
                put("contacts_remaining", normalizedProfile.contactsRemaining)
                put("package_activated_at", normalizedProfile.packageActivatedAt)
                put("package_expires_at", normalizedProfile.packageExpiresAt)
                put("is_premium", !normalizedProfile.packageTier.equals("Free", ignoreCase = true))
                put("is_featured", normalizedProfile.packageTier.equals("Diamond", ignoreCase = true))
                put("is_onboarding_completed", normalizedProfile.displayName.isNotBlank())
                put("onboarding_completed", normalizedProfile.displayName.isNotBlank())
                put("profile_completion_score", completionScore)
                put("photo_url", normalizedProfile.photoUrl)
                if (!isUserAdmin) {
                    put("role", "USER")
                    put("is_admin", false)
                }
                if (fcmToken.isNotBlank()) put("fcm_token", fcmToken)
                put("payload", pJson)
                put("updated_at", nowIso)
            }
        }

        fun buildProfileMirrorRow(effectiveUid: String): JSONObject {
            val pJson = buildPayloadJson(effectiveUid)
            return JSONObject().apply {
                put("id", profileCode)
                put("uid", effectiveUid)
                put("profile_code", profileCode)
                put("full_name", normalizedProfile.displayName)
                put("email", normalizedProfile.email)
                put("phone_number", normalizedProfile.contactPhone.ifBlank { normalizedProfile.guardianPhone })
                put("gender", normalizedProfile.gender)
                put("present_division", normalizedProfile.presentDivision.ifBlank { normalizedProfile.locationDivision })
                put("present_district", normalizedProfile.presentDistrict.ifBlank { normalizedProfile.locationDistrict })
                put("account_status", normalizedProfile.accountStatus.ifBlank { "ACTIVE" })
                put("verification_status", if (normalizedProfile.isVerified) "VERIFIED" else normalizedProfile.verificationStatus.ifBlank { "UNSUBMITTED" })
                put("is_verified", normalizedProfile.isVerified)
                put("package_activated_at", normalizedProfile.packageActivatedAt)
                put("package_expires_at", normalizedProfile.packageExpiresAt)
                put("is_onboarding_completed", normalizedProfile.displayName.isNotBlank())
                put("photo_url", normalizedProfile.photoUrl)
                if (!isUserAdmin) {
                    put("role", "USER")
                    put("is_admin", false)
                }
                if (fcmToken.isNotBlank()) put("fcm_token", fcmToken)
                put("payload", pJson)
                put("updated_at", nowIso)
            }
        }

        val primaryUserRow = buildUserRow(uid)
        val primaryUserPatch = cloneJsonObjectWithout(primaryUserRow, setOf("id"))
        val filterForUidOrCode = "or=(uid.eq.${urlEncode(uid)},id.eq.${urlEncode(profileCode)},profile_code.eq.${urlEncode(profileCode)},id.eq.${urlEncode(uid)})"

        var patchedUsersCount = patchRowsReturningCount("users", filterForUidOrCode, primaryUserPatch)
        var upsertedUsers = upsertRow("users", primaryUserRow, onConflict = "id")

        var activeUidForWrite = uid
        if (patchedUsersCount == 0 && !upsertedUsers && jwtUid.isNotBlank() && !jwtUid.equals(uid, ignoreCase = true)) {
            Log.w(TAG, "Profile write with uid=$uid rejected by RLS; retrying with authenticated JWT uid=$jwtUid")
            activeUidForWrite = jwtUid
            val jwtUserRow = buildUserRow(jwtUid)
            val jwtUserPatch = cloneJsonObjectWithout(jwtUserRow, setOf("id"))
            val jwtFilter = "or=(uid.eq.${urlEncode(jwtUid)},uid.eq.${urlEncode(uid)},id.eq.${urlEncode(profileCode)},profile_code.eq.${urlEncode(profileCode)})"
            patchedUsersCount = patchRowsReturningCount("users", jwtFilter, jwtUserPatch)
            upsertedUsers = upsertRow("users", jwtUserRow, onConflict = "id")
        }

        val savedUsers = patchedUsersCount > 0 || upsertedUsers

        val profileMirrorRow = buildProfileMirrorRow(activeUidForWrite)
        val profileMirrorPatch = cloneJsonObjectWithout(profileMirrorRow, setOf("id"))
        val profileFilter = "or=(uid.eq.${urlEncode(activeUidForWrite)},uid.eq.${urlEncode(uid)},id.eq.${urlEncode(profileCode)},profile_code.eq.${urlEncode(profileCode)},id.eq.${urlEncode(activeUidForWrite)})"
        val patchedProfilesCount = patchRowsReturningCount("profiles", profileFilter, profileMirrorPatch)
        val upsertedProfiles = upsertRow("profiles", profileMirrorRow, onConflict = "id")
        val savedProfiles = patchedProfilesCount > 0 || upsertedProfiles

        if (upsertedUsers) {
            deleteRows("users", "uid=eq.${urlEncode(activeUidForWrite)}&id=neq.${urlEncode(profileCode)}")
        }
        if (upsertedProfiles) {
            deleteRows("profiles", "uid=eq.${urlEncode(activeUidForWrite)}&id=neq.${urlEncode(profileCode)}")
        }

        val contactRow = JSONObject().apply {
            put("user_id", activeUidForWrite)
            put("candidate_mobile", normalizedProfile.contactPhone)
            put("guardian_mobile", normalizedProfile.guardianPhone)
            put("whatsapp_number", normalizedProfile.whatsapp)
            put("contact_email", normalizedProfile.email)
            put("present_address", normalizedProfile.presentAddress)
            put("permanent_address", normalizedProfile.permanentAddress)
            put("payload", JSONObject().apply {
                put("userId", activeUidForWrite)
                put("profileCode", profileCode)
                put("contactPhone", normalizedProfile.contactPhone)
                put("guardianPhone", normalizedProfile.guardianPhone)
                put("whatsapp", normalizedProfile.whatsapp)
                put("imo", normalizedProfile.imo)
                put("facebook", normalizedProfile.facebook)
                put("instagram", normalizedProfile.instagram)
                put("email", normalizedProfile.email)
                put("presentAddress", normalizedProfile.presentAddress)
                put("permanentAddress", normalizedProfile.permanentAddress)
            })
            put("updated_at", nowIso)
        }
        upsertRow("profile_contacts", contactRow, onConflict = "user_id")

        val prefRow = JSONObject().apply {
            put("user_id", activeUidForWrite)
            put("preferred_age_min", normalizedProfile.partnerAgeMin)
            put("preferred_age_max", normalizedProfile.partnerAgeMax)
            put("preferred_education", normalizedProfile.partnerEducation)
            put("preferred_occupation", normalizedProfile.partnerProfession)
            put("preferred_location", normalizedProfile.partnerDistrict)
            put("preferred_marital_status", normalizedProfile.partnerMaritalStatuses.joinToString(", "))
            put("payload", JSONObject().apply {
                put("userId", activeUidForWrite)
                put("partnerAgeMin", normalizedProfile.partnerAgeMin)
                put("partnerAgeMax", normalizedProfile.partnerAgeMax)
                put("partnerHeightMin", normalizedProfile.partnerHeightMin)
                put("partnerHeightMax", normalizedProfile.partnerHeightMax)
                put("partnerEducation", normalizedProfile.partnerEducation)
                put("partnerProfession", normalizedProfile.partnerProfession)
                put("partnerDistrict", normalizedProfile.partnerDistrict)
                put("partnerReligion", normalizedProfile.partnerReligion)
                put("partnerExpectations", normalizedProfile.partnerExpectations)
            })
            put("updated_at", nowIso)
        }
        upsertRow("preferences", prefRow, onConflict = "user_id")

        savedUsers || savedProfiles
    }

    suspend fun deleteUserAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !isConfigured()) return@withContext false
        val encoded = urlEncode(userId)
        deleteRows("onboarding_drafts", "user_id=eq.$encoded")
        deleteRows("profile_contacts", "user_id=eq.$encoded")
        deleteRows("preferences", "user_id=eq.$encoded")
        deleteRows("fcm_tokens", "user_id=eq.$encoded")
        deleteRows("profiles", "or=(id.eq.$encoded,uid.eq.$encoded)")
        deleteRows("users", "or=(id.eq.$encoded,uid.eq.$encoded)")
    }

    // =========================================================================
    // ONBOARDING DRAFT OPERATIONS
    // =========================================================================

    suspend fun fetchOnboardingDraft(userId: String): Pair<OnboardingDraft, Int>? = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !isConfigured()) return@withContext null
        val rows = selectRows("onboarding_drafts", "user_id=eq.${urlEncode(userId)}&limit=1")
        val row = rows.firstOrNull() ?: return@withContext null
        val step = row.optInt("current_step", 0)
        val payload = SupabaseRowMappers.extractPayloadJson(row)
        val draftJsonStr = payload.optString("draftJson", payload.toString())
        val draft = runCatching { OnboardingDraft.fromJson(draftJsonStr) }.getOrNull() ?: return@withContext null
        Pair(draft, step)
    }

    suspend fun upsertOnboardingDraft(userId: String, draft: OnboardingDraft, step: Int): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !isConfigured()) return@withContext false
        val payload = JSONObject().apply {
            put("draftJson", draft.toJson())
            put("onboardingStep", step)
        }
        val row = JSONObject().apply {
            put("user_id", userId)
            put("current_step", step)
            put("payload", payload)
            put("updated_at", nowIsoString())
        }
        upsertRow("onboarding_drafts", row, onConflict = "user_id")
    }

    suspend fun deleteOnboardingDraft(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !isConfigured()) return@withContext false
        deleteRows("onboarding_drafts", "user_id=eq.${urlEncode(userId)}")
    }

    // =========================================================================
    // INTEREST PROPOSALS OPERATIONS
    // =========================================================================

    private val mirroredInterestRecords = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()

    private fun interestCanonicalKey(interest: Interest): String {
        val s = interest.senderFormattedId.ifBlank { interest.senderId }.trim().lowercase(Locale.US)
        val r = interest.receiverFormattedId.ifBlank { interest.receiverId }.trim().lowercase(Locale.US)
        return if (s.isNotBlank() && r.isNotBlank()) "${s}__${r}" else interest.id.lowercase(Locale.US)
    }

    private fun buildMirroredInterestsArrayForUser(
        candidateIds: Set<String>,
        existingArray: org.json.JSONArray? = null
    ): org.json.JSONArray {
        val normalizedIds = candidateIds.map { it.trim().lowercase(Locale.US) }.filter { it.isNotBlank() }.toSet()
        val byKey = linkedMapOf<String, JSONObject>()
        if (existingArray != null) {
            for (i in 0 until existingArray.length()) {
                val obj = existingArray.optJSONObject(i) ?: continue
                val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
                byKey[interestCanonicalKey(parsed)] = obj
            }
        }
        for ((key, obj) in mirroredInterestRecords) {
            val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
            val sMatch = parsed.senderId.lowercase(Locale.US) in normalizedIds ||
                parsed.senderFormattedId.lowercase(Locale.US) in normalizedIds
            val rMatch = parsed.receiverId.lowercase(Locale.US) in normalizedIds ||
                parsed.receiverFormattedId.lowercase(Locale.US) in normalizedIds
            if (sMatch || rMatch) {
                byKey[key] = obj
            }
        }
        val result = org.json.JSONArray()
        for (obj in byKey.values) {
            result.put(obj)
        }
        return result
    }

    private suspend fun resolveUserCandidateIds(
        userId: String,
        secondaryUserId: String = "",
        profileCode: String = ""
    ): Set<String> {
        val ids = linkedSetOf<String>()
        fun addId(raw: String?) {
            val clean = raw?.trim().orEmpty()
            if (clean.isNotBlank()) ids.add(clean)
        }
        addId(userId)
        addId(secondaryUserId)
        addId(profileCode)
        addId(authManager.getAuthenticatedJwtUid())
        addId(getSessionUserId())

        val initialQueryIds = ids.toList()
        if (initialQueryIds.isNotEmpty() && isConfigured()) {
            val orClauses = initialQueryIds.flatMap { idVal ->
                val enc = urlEncode(idVal)
                if (UserProfile.isValidFormattedUserId(idVal)) {
                    listOf("id.eq.$enc", "profile_code.eq.$enc", "uid.eq.$enc")
                } else {
                    listOf("uid.eq.$enc", "id.eq.$enc")
                }
            }.distinct()
            if (orClauses.isNotEmpty()) {
                val userRows = selectRows("matchmaking_users", "or=(${orClauses.joinToString(",")})&limit=5")
                for (row in userRows) {
                    val payload = SupabaseRowMappers.extractPayloadJson(row)
                    addId(row.optString("uid", ""))
                    addId(row.optString("id", ""))
                    addId(row.optString("profile_code", ""))
                    addId(payload.optString("userId", ""))
                    addId(payload.optString("uid", ""))
                    addId(payload.optString("profileCode", ""))
                }
            }
        }
        return ids
    }

    private fun mergeInterestLists(lists: List<Interest>): List<Interest> {
        val merged = linkedMapOf<String, Interest>()
        for (item in lists) {
            val key = interestCanonicalKey(item)
            val existing = merged[key]
            if (existing == null) {
                merged[key] = item
            } else {
                val itemNonPending = !item.status.equals("pending", ignoreCase = true)
                val existingNonPending = !existing.status.equals("pending", ignoreCase = true)
                val itemTs = (item.updatedAt ?: item.createdAt)?.toDate()?.time ?: 0L
                val existingTs = (existing.updatedAt ?: existing.createdAt)?.toDate()?.time ?: 0L
                val preferNew = when {
                    itemNonPending && !existingNonPending -> true
                    !itemNonPending && existingNonPending -> false
                    else -> itemTs >= existingTs
                }
                val winner = if (preferNew) item else existing
                val loser = if (preferNew) existing else item
                merged[key] = winner.copy(
                    senderFormattedId = winner.senderFormattedId.ifBlank { loser.senderFormattedId },
                    receiverFormattedId = winner.receiverFormattedId.ifBlank { loser.receiverFormattedId },
                    senderPhoto = winner.senderPhoto.ifBlank { loser.senderPhoto },
                    receiverPhoto = winner.receiverPhoto.ifBlank { loser.receiverPhoto }
                )
            }
        }
        return merged.values.sortedByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
    }

    suspend fun fetchReceivedInterests(
        userId: String,
        secondaryUserId: String = "",
        profileCode: String = ""
    ): List<Interest> = withContext(Dispatchers.IO) {
        if (userId.isBlank() && secondaryUserId.isBlank() && profileCode.isBlank()) return@withContext emptyList()
        if (!isConfigured()) return@withContext emptyList()

        val candidateIds = resolveUserCandidateIds(userId, secondaryUserId, profileCode)
        val lowerCandidateIds = candidateIds.map { it.lowercase(Locale.US) }.toSet()
        val collected = mutableListOf<Interest>()

        if (candidateIds.isNotEmpty()) {
            val orFilter = candidateIds.joinToString(",") { "receiver_id.eq.${urlEncode(it)}" }
            val directRows = selectRows("interests", "or=($orFilter)&order=updated_at.desc")
            directRows.mapNotNullTo(collected) { SupabaseRowMappers.rowToInterest(it) }
        }

        val allVisibleRows = selectRows("interests", "order=updated_at.desc&limit=100")
        for (row in allVisibleRows) {
            val payload = SupabaseRowMappers.extractPayloadJson(row)
            val parsed = SupabaseRowMappers.rowToInterest(row) ?: continue
            val origReceiver = payload.optString("originalReceiverId", "").lowercase(Locale.US)
            val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds ||
                (origReceiver.isNotBlank() && origReceiver in lowerCandidateIds)
            val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds
            if (isReceiver && !isSender) {
                collected.add(parsed)
            }
        }

        // Also inspect mirrored interestRecords stored in users/profiles payload for cross-UID resilience
        val userRows = selectRows("users", "order=updated_at.desc&limit=100")
        for (uRow in userRows) {
            val uPayload = SupabaseRowMappers.extractPayloadJson(uRow)
            val arr = uPayload.optJSONArray("interestRecords") ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
                mirroredInterestRecords[interestCanonicalKey(parsed)] = obj
                val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                    parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds
                val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                    parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds
                if (isReceiver && !isSender) {
                    collected.add(parsed)
                }
            }
        }

        for (obj in mirroredInterestRecords.values) {
            val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
            val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds
            val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds
            if (isReceiver && !isSender) {
                collected.add(parsed)
            }
        }

        mergeInterestLists(collected)
    }

    suspend fun fetchSentInterests(
        userId: String,
        secondaryUserId: String = "",
        profileCode: String = ""
    ): List<Interest> = withContext(Dispatchers.IO) {
        if (userId.isBlank() && secondaryUserId.isBlank() && profileCode.isBlank()) return@withContext emptyList()
        if (!isConfigured()) return@withContext emptyList()

        val candidateIds = resolveUserCandidateIds(userId, secondaryUserId, profileCode)
        val lowerCandidateIds = candidateIds.map { it.lowercase(Locale.US) }.toSet()
        val collected = mutableListOf<Interest>()

        if (candidateIds.isNotEmpty()) {
            val orFilter = candidateIds.joinToString(",") { "sender_id.eq.${urlEncode(it)}" }
            val directRows = selectRows("interests", "or=($orFilter)&order=updated_at.desc")
            directRows.mapNotNullTo(collected) { SupabaseRowMappers.rowToInterest(it) }
        }

        val allVisibleRows = selectRows("interests", "order=updated_at.desc&limit=100")
        for (row in allVisibleRows) {
            val payload = SupabaseRowMappers.extractPayloadJson(row)
            val parsed = SupabaseRowMappers.rowToInterest(row) ?: continue
            val origSender = payload.optString("originalSenderId", "").lowercase(Locale.US)
            val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds ||
                (origSender.isNotBlank() && origSender in lowerCandidateIds)
            val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds
            if (isSender && !isReceiver) {
                collected.add(parsed)
            }
        }

        // Also inspect mirrored interestRecords stored in users/profiles payload for cross-UID resilience
        val userRows = selectRows("users", "order=updated_at.desc&limit=100")
        for (uRow in userRows) {
            val uPayload = SupabaseRowMappers.extractPayloadJson(uRow)
            val arr = uPayload.optJSONArray("interestRecords") ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
                mirroredInterestRecords[interestCanonicalKey(parsed)] = obj
                val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                    parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds
                val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                    parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds
                if (isSender && !isReceiver) {
                    collected.add(parsed)
                }
            }
        }

        for (obj in mirroredInterestRecords.values) {
            val parsed = SupabaseRowMappers.rowToInterest(obj) ?: continue
            val isSender = parsed.senderId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.senderFormattedId.lowercase(Locale.US) in lowerCandidateIds
            val isReceiver = parsed.receiverId.lowercase(Locale.US) in lowerCandidateIds ||
                parsed.receiverFormattedId.lowercase(Locale.US) in lowerCandidateIds
            if (isSender && !isReceiver) {
                collected.add(parsed)
            }
        }

        mergeInterestLists(collected)
    }

    suspend fun upsertInterest(interest: Interest): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        if (authManager.supabaseAccessToken.isNullOrBlank() || authManager.isCurrentAccessTokenExpired()) {
            authManager.ensureValidWriteAccessToken()
        }
        val jwtUid = authManager.getAuthenticatedJwtUid()?.trim().orEmpty()
        val sessionUid = getSessionUserId()?.trim().orEmpty()

        // Resolve receiver's canonical DB uid and profile_code if available
        val recLookupClauses = buildList {
            if (interest.receiverId.isNotBlank()) {
                add("uid.eq.${urlEncode(interest.receiverId)}")
                add("id.eq.${urlEncode(interest.receiverId)}")
            }
            if (interest.receiverFormattedId.isNotBlank()) {
                add("id.eq.${urlEncode(interest.receiverFormattedId)}")
                add("profile_code.eq.${urlEncode(interest.receiverFormattedId)}")
            }
        }.distinct()
        val receiverRow = if (recLookupClauses.isNotEmpty()) {
            selectRows("matchmaking_users", "or=(${recLookupClauses.joinToString(",")})&limit=1").firstOrNull()
        } else null
        val receiverDbUid = receiverRow?.optString("uid", "")?.trim().orEmpty()
        val receiverDbCode = receiverRow?.optString("profile_code", receiverRow.optString("id", ""))?.trim().orEmpty()

        val resolvedReceiverId = receiverDbUid.ifBlank { interest.receiverId }
        val resolvedReceiverCode = interest.receiverFormattedId.ifBlank {
            receiverDbCode.takeIf { UserProfile.isValidFormattedUserId(it) }.orEmpty()
        }

        val id = interest.id.ifBlank { interest.interestId.ifBlank { "int_${interest.senderId}_${resolvedReceiverId}" } }
        val normalized = interest.copy(
            id = id,
            interestId = id,
            receiverId = resolvedReceiverId.ifBlank { interest.receiverId },
            receiverFormattedId = resolvedReceiverCode
        )
        val nowIso = nowIsoString()
        val payload = SupabaseRowMappers.instanceToJsonObject(normalized).apply {
            put("originalSenderId", interest.senderId)
            put("originalReceiverId", interest.receiverId)
            if (normalized.senderFormattedId.isNotBlank()) put("senderFormattedId", normalized.senderFormattedId)
            if (normalized.receiverFormattedId.isNotBlank()) put("receiverFormattedId", normalized.receiverFormattedId)
            put("updatedAt", System.currentTimeMillis())
        }

        mirroredInterestRecords[interestCanonicalKey(normalized)] = payload

        val patchBody = JSONObject().apply {
            put("status", normalized.status)
            put("payload", payload)
            put("updated_at", nowIso)
        }
        val patchedById = patchRowsReturningCount("interests", "id=eq.${urlEncode(id)}", patchBody)
        var savedInTable = patchedById > 0

        if (!savedInTable) {
            val row = JSONObject().apply {
                put("id", id)
                put("sender_id", normalized.senderId)
                put("receiver_id", normalized.receiverId)
                put("status", normalized.status)
                put("payload", payload)
                put("updated_at", nowIso)
            }
            savedInTable = upsertRow("interests", row, onConflict = "id")
        }

        if (!savedInTable && jwtUid.isNotBlank() && !jwtUid.equals(normalized.senderId, ignoreCase = true)) {
            val jwtInterestId = "int_${jwtUid}_${normalized.receiverId}"
            val jwtPayload = cloneJsonObjectWithout(payload).apply {
                put("id", jwtInterestId)
                put("interestId", jwtInterestId)
            }
            mirroredInterestRecords[interestCanonicalKey(normalized)] = jwtPayload
            val jwtRow = JSONObject().apply {
                put("id", jwtInterestId)
                put("sender_id", jwtUid)
                put("receiver_id", normalized.receiverId)
                put("status", normalized.status)
                put("payload", jwtPayload)
                put("updated_at", nowIso)
            }
            val patchedJwt = patchRowsReturningCount("interests", "id=eq.${urlEncode(jwtInterestId)}", patchBody)
            savedInTable = patchedJwt > 0 || upsertRow("interests", jwtRow, onConflict = "id")
        }

        // Also mirror into current user's own users/profiles payload so cross-device sync succeeds even across UID variations
        val ownCandidateIds = setOf(jwtUid, sessionUid, normalized.senderId, normalized.receiverId, normalized.senderFormattedId, normalized.receiverFormattedId)
            .filter { it.isNotBlank() }
            .toSet()
        val ownClauses = listOfNotNull(
            jwtUid.takeIf { it.isNotBlank() }?.let { "uid.eq.${urlEncode(it)}" },
            sessionUid.takeIf { it.isNotBlank() }?.let { "uid.eq.${urlEncode(it)}" },
            normalized.senderFormattedId.takeIf { it.isNotBlank() }?.let { "id.eq.${urlEncode(it)}" }
        ).distinct()
        if (ownClauses.isNotEmpty()) {
            val ownRows = selectRows("users", "or=(${ownClauses.joinToString(",")})&limit=2")
            for (ownRow in ownRows) {
                val rowId = ownRow.optString("id", "").trim()
                val rowUid = ownRow.optString("uid", "").trim()
                if (rowId.isBlank()) continue
                if (jwtUid.isNotBlank() && rowUid.isNotBlank() && !rowUid.equals(jwtUid, ignoreCase = true) && !rowUid.equals(sessionUid, ignoreCase = true)) {
                    continue
                }
                val ownPayload = cloneJsonObjectWithout(SupabaseRowMappers.extractPayloadJson(ownRow))
                val updatedArr = buildMirroredInterestsArrayForUser(
                    candidateIds = ownCandidateIds + setOf(rowId, rowUid),
                    existingArray = ownPayload.optJSONArray("interestRecords")
                )
                ownPayload.put("interestRecords", updatedArr)
                val patchUser = JSONObject().apply {
                    put("payload", ownPayload)
                    put("updated_at", nowIso)
                }
                patchRows("users", "id=eq.${urlEncode(rowId)}", patchUser)
                patchRows("profiles", "id=eq.${urlEncode(rowId)}", patchUser)
            }
        }

        savedInTable
    }

    // =========================================================================
    // CHATS & MESSAGES OPERATIONS
    // =========================================================================

    suspend fun fetchChatsForUser(userId: String): List<ChatThread> = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !isConfigured()) return@withContext emptyList()
        val candidateIds = resolveUserCandidateIds(userId)
        val clauses = candidateIds.flatMap {
            val enc = urlEncode(it)
            listOf("participant_a.eq.$enc", "participant_b.eq.$enc")
        }.distinct()
        val filter = if (clauses.isNotEmpty()) {
            "or=(${clauses.joinToString(",")})&order=last_message_timestamp.desc"
        } else {
            val encoded = urlEncode(userId)
            "or=(participant_a.eq.$encoded,participant_b.eq.$encoded)&order=last_message_timestamp.desc"
        }
        val rows = selectRowsOrThrow("chats", filter)
        rows.mapNotNull { SupabaseRowMappers.rowToChatThread(it) }
    }

    suspend fun upsertChatThread(thread: ChatThread): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        val id = thread.id.ifBlank { thread.chatId }
        if (id.isBlank()) return@withContext false
        val pA = thread.participantIds.getOrNull(0).orEmpty()
        val pB = thread.participantIds.getOrNull(1).orEmpty()
        val nowMs = System.currentTimeMillis()
        val payload = SupabaseRowMappers.instanceToJsonObject(thread.copy(id = id, chatId = id))
        val row = JSONObject().apply {
            put("id", id)
            put("participant_a", pA)
            put("participant_b", pB)
            if (thread.interestId.isNotBlank()) put("interest_id", thread.interestId)
            put("last_message", thread.lastMessage)
            put("last_message_sender_id", thread.lastMessageSenderId)
            put("last_message_timestamp", nowMs)
            put("payload", payload)
            put("updated_at", nowIsoString())
        }
        upsertRow("chats", row, onConflict = "id")
    }

    suspend fun fetchChatMessages(chatId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || !isConfigured()) return@withContext emptyList()
        val rows = selectRowsOrThrow(
            "chat_messages",
            "chat_id=eq.${urlEncode(chatId)}&order=timestamp_ms.asc"
        )
        rows.mapNotNull { SupabaseRowMappers.rowToChatMessage(it) }
    }

    suspend fun insertChatMessage(chatId: String, message: ChatMessage): Boolean = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || !isConfigured()) return@withContext false
        val msgId = message.id.ifBlank { message.messageId.ifBlank { "msg_${System.currentTimeMillis()}" } }
        val nowMs = message.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val normalized = message.copy(id = msgId, messageId = msgId)
        val payload = SupabaseRowMappers.instanceToJsonObject(normalized).apply {
            put("chatId", chatId)
            put("timestampMs", nowMs)
        }
        val receiverId = normalized.participantIds.firstOrNull { it != normalized.senderId }.orEmpty()
        val row = JSONObject().apply {
            put("id", msgId)
            put("chat_id", chatId)
            put("sender_id", normalized.senderId)
            put("receiver_id", receiverId)
            put("text", normalized.text)
            put("status", "SENT")
            put("timestamp_ms", nowMs)
            put("payload", payload)
            put("created_at", nowIsoString())
        }
        upsertRow("chat_messages", row, onConflict = "id")
    }

    // =========================================================================
    // KYC VERIFICATION & PAYMENT REQUESTS
    // =========================================================================

    suspend fun upsertVerificationRequest(request: VerificationRequest): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        val id = request.id.ifBlank { request.requestId.ifBlank { "kyc_${request.userId}_${System.currentTimeMillis()}" } }
        val normalized = request.copy(id = id, requestId = id)
        val payload = SupabaseRowMappers.instanceToJsonObject(normalized)
        val row = JSONObject().apply {
            put("id", id)
            put("user_id", normalized.userId)
            put("user_name", normalized.displayName)
            put("user_gender", normalized.gender)
            put("document_type", normalized.docType)
            put("document_number", normalized.idNumber)
            put("status", normalized.status)
            put("admin_note", normalized.adminNote)
            put("payload", payload)
            put("updated_at", nowIsoString())
        }
        upsertRow("verification_requests", row, onConflict = "id")
    }

    suspend fun upsertPaymentRequest(request: PaymentRequest): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        val id = request.id.ifBlank { request.requestId.ifBlank { "pay_${request.userId}_${System.currentTimeMillis()}" } }
        val normalized = request.copy(id = id, requestId = id)
        val payload = SupabaseRowMappers.instanceToJsonObject(normalized)
        val row = JSONObject().apply {
            put("id", id)
            put("user_id", normalized.userId)
            put("user_name", normalized.displayName)
            put("plan_id", normalized.packageTier.lowercase())
            put("plan_name", normalized.packageTier)
            put("amount_bdt", normalized.amountBdt)
            put("payment_method", normalized.paymentMethod)
            put("sender_number", normalized.senderPhone)
            put("transaction_id", normalized.transactionId)
            put("status", normalized.status)
            put("admin_note", normalized.adminNote)
            put("payload", payload)
            put("updated_at", nowIsoString())
        }
        upsertRow("payment_requests", row, onConflict = "id")
    }

    suspend fun verifyGooglePlayPurchase(
        purchaseToken: String,
        productId: String
    ): JSONObject = withContext(Dispatchers.IO) {
        check(isConfigured()) { "Supabase is not configured for purchase verification." }
        require(purchaseToken.isNotBlank()) { "Google Play purchase token is missing." }
        require(MembershipPackages.byProductId(productId)?.priceBdt?.let { it > 0 } == true) {
            "Google Play product is not a paid membership."
        }

        if (authManager.supabaseAccessToken.isNullOrBlank() ||
            authManager.isCurrentAccessTokenExpired()
        ) {
            authManager.ensureValidWriteAccessToken()
        }
        val accessToken = authManager.supabaseAccessToken
            ?: throw IllegalStateException("Sign in again before verifying a Google Play purchase.")
        val requestBody = JSONObject().apply {
            put("purchaseToken", purchaseToken)
            put("productId", productId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$supabaseUrl/functions/v1/verify-google-play-purchase")
            .post(requestBody)
            .header("apikey", supabaseAnonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            val responseJson = runCatching { JSONObject(responseText) }.getOrNull()
            if (!response.isSuccessful) {
                val message = responseJson?.optString("error")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Google Play purchase verification failed (HTTP ${response.code})."
                throw IllegalStateException(message)
            }
            responseJson ?: throw IllegalStateException(
                "Google Play verification returned an invalid response."
            )
        }
    }

    suspend fun adminChangeUserPackage(
        uidOrCode: String,
        newTier: String,
        customInterests: Int? = null,
        customContacts: Int? = null,
        customDurationDays: Int? = null,
        adminNote: String = "Manual Package Update by Admin"
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = fetchUserProfile(uidOrCode)
                ?: throw IllegalArgumentException("User profile not found for '$uidOrCode'")
            val pkgSpec = MembershipPackages.byTier(newTier)
            val resolvedInterests = customInterests ?: pkgSpec.interestsCount
            val resolvedContacts = customContacts ?: pkgSpec.contactsCount
            val nowMs = System.currentTimeMillis()
            val resolvedDurationDays = if (pkgSpec.tier == "Free") 0 else (customDurationDays ?: pkgSpec.durationDays)
            val activatedAtMs = if (pkgSpec.tier == "Free") 0L else nowMs
            val expiresAtMs = if (pkgSpec.tier == "Free") 0L else pkgSpec.computeExpiryMillis(nowMs, resolvedDurationDays)

            val updatedProfile = existing.copy(
                packageTier = pkgSpec.tier,
                pendingPackageTier = "",
                packageActivatedAt = activatedAtMs,
                packageExpiresAt = expiresAtMs,
                interestsRemaining = resolvedInterests,
                contactsRemaining = resolvedContacts,
                freeInterestWeekKey = if (pkgSpec.tier == "Free") UserProfile.getCurrentWeekKey() else existing.freeInterestWeekKey,
                isVerified = existing.isVerified || pkgSpec.hasVerifiedBadge,
                verificationStatus = if (existing.isVerified || pkgSpec.hasVerifiedBadge) "VERIFIED" else existing.verificationStatus
            )

            upsertUserProfile(updatedProfile)

            val reqId = "admin_pkg_${updatedProfile.getFormattedUserId()}_$nowMs"
            upsertPaymentRequest(
                PaymentRequest(
                    id = reqId,
                    requestId = reqId,
                    userId = updatedProfile.userId,
                    profileCode = updatedProfile.getFormattedUserId(),
                    displayName = updatedProfile.displayName,
                    packageTier = pkgSpec.tier,
                    amountBdt = pkgSpec.priceBdt,
                    paymentMethod = "ADMIN_MANUAL",
                    senderPhone = updatedProfile.contactPhone,
                    transactionId = "ADMIN-${pkgSpec.tier.uppercase(Locale.US)}",
                    interestsToCredit = resolvedInterests,
                    contactsToCredit = resolvedContacts,
                    durationDays = resolvedDurationDays,
                    packageActivatedAt = activatedAtMs,
                    packageExpiresAt = expiresAtMs,
                    status = "APPROVED",
                    adminNote = adminNote
                )
            )

            val notifId = "notif_pkg_${updatedProfile.getFormattedUserId()}_$nowMs"
            val notifTitleEn = if (pkgSpec.tier == "Free") {
                "Membership Plan Changed to Free Plan"
            } else {
                "${pkgSpec.nameEn} (${pkgSpec.tier}) Package Activated!"
            }
            val notifTitleBn = if (pkgSpec.tier == "Free") {
                "আপনার মেম্বারশিপ প্ল্যান ফ্রি প্ল্যানে পরিবর্তন করা হয়েছে"
            } else {
                "অভিনন্দন! আপনার ${pkgSpec.nameBn} (${pkgSpec.tier}) প্যাকেজ চালু হয়েছে!"
            }
            val expiryFormatted = updatedProfile.getFormattedPackageExpiryDate()
            val validitySuffixEn = if (expiryFormatted.isNotBlank()) " Valid until $expiryFormatted ($resolvedDurationDays days)." else ""
            val validitySuffixBn = if (expiryFormatted.isNotBlank()) " মেয়াদ: $expiryFormatted পর্যন্ত ($resolvedDurationDays দিন)।" else ""
            val notifBodyEn = "Your active plan is now ${pkgSpec.tier} with ${if (resolvedInterests >= 999) "Unlimited" else resolvedInterests} proposals and ${if (resolvedContacts >= 999) "Unlimited" else resolvedContacts} contact unlocks.$validitySuffixEn"
            val notifBodyBn = "আপনার বর্তমান প্ল্যান এখন ${pkgSpec.tier} (${if (resolvedInterests >= 999) "আনলিমিটেড" else resolvedInterests}টি প্রস্তাব ও ${if (resolvedContacts >= 999) "আনলিমিটেড" else resolvedContacts}টি কন্টাক্ট আনলক)।$validitySuffixBn"

            val notifPayload = JSONObject().apply {
                put("id", notifId)
                put("notificationId", notifId)
                put("type", "PACKAGE_APPROVED")
                put("targetUserId", updatedProfile.userId)
                put("targetProfileCode", updatedProfile.getFormattedUserId())
                put("packageTier", pkgSpec.tier)
                put("title", notifTitleEn)
                put("titleEn", notifTitleEn)
                put("titleBn", notifTitleBn)
                put("body", notifBodyEn)
                put("bodyEn", notifBodyEn)
                put("bodyBn", notifBodyBn)
                put("actionRoute", "PACKAGES")
                put("showBanner", true)
                put("pushNotification", true)
                put("createdAt", nowMs)
            }
            val notifRow = JSONObject().apply {
                put("id", notifId)
                put("target_user_id", updatedProfile.userId)
                put("title", notifTitleEn)
                put("message", notifBodyEn)
                put("type", "PACKAGE_APPROVED")
                put("is_read", false)
                put("created_at_ms", nowMs)
                put("payload", notifPayload)
                put("created_at", nowIsoString())
            }
            upsertRow("notifications", notifRow, onConflict = "id")

            updatedProfile
        }
    }

    // =========================================================================
    // ADMIN NOTIFICATIONS & BROADCASTS
    // =========================================================================

    suspend fun fetchNotificationsAndBroadcasts(): List<AdminNotification> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val notifResult = runCatching { selectRowsOrThrow("notifications", "order=created_at.desc&limit=50") }
        val broadcastResult = runCatching { selectRowsOrThrow("broadcasts", "order=created_at.desc&limit=50") }
        if (notifResult.isFailure && broadcastResult.isFailure) {
            throw notifResult.exceptionOrNull()!!
        }
        val notifRows = notifResult.getOrDefault(emptyList())
        val broadcastRows = broadcastResult.getOrDefault(emptyList())
        (notifRows + broadcastRows).mapNotNull { SupabaseRowMappers.rowToAdminNotification(it) }
    }
}
