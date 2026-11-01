package com.example.data.supabase

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseAuthManagerTest {

    private lateinit var context: Context
    private val recordedRequests = mutableListOf<Pair<String, String>>()
    private var nextResponseProvider: (url: String, method: String, body: String) -> Pair<Int, String> =
        { _, _, _ -> 200 to "{}" }

    private val mockOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val req = chain.request()
                    val url = req.url.toString()
                    val bodyBuffer = okio.Buffer()
                    req.body?.writeTo(bodyBuffer)
                    val bodyStr = bodyBuffer.readUtf8()
                    recordedRequests.add(url to bodyStr)

                    val (code, respBody) = nextResponseProvider(url, req.method, bodyStr)
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code in 200..299) "OK" else "Error")
                        .body(respBody.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()
    }

    private lateinit var authManager: SupabaseAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ghotok_supabase_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        recordedRequests.clear()

        authManager = SupabaseAuthManager(
            httpClientProvider = { mockOkHttpClient },
            supabaseUrlProvider = { "https://example.supabase.co" },
            supabaseAnonKeyProvider = { "test-anon-key" },
            isConfiguredProvider = { true },
            selectRowsProvider = { _, _ -> emptyList() }
        )
    }

    private fun buildSyntheticJwt(sub: String, expEpochSeconds: Long): String {
        val header = Base64.encodeToString(
            """{"alg":"HS256","typ":"JWT"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val payload = Base64.encodeToString(
            JSONObject().apply {
                put("sub", sub)
                put("exp", expEpochSeconds)
            }.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$header.$payload.signature"
    }

    @Test
    fun signInWithEmailPassword_persistsRefreshTokenAndNeverStoresPlaintextPassword() = runTest {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        val fakeAccessJwt = buildSyntheticJwt("user-uuid-777", futureExp)

        nextResponseProvider = { url, _, _ ->
            if (url.contains("/auth/v1/token?grant_type=password")) {
                200 to JSONObject().apply {
                    put("access_token", fakeAccessJwt)
                    put("refresh_token", "refresh-token-abc")
                    put(
                        "user",
                        JSONObject().apply {
                            put("id", "user-uuid-777")
                            put("email", "member@ghotok.com")
                            put("user_metadata", JSONObject().put("full_name", "Karim Ahmed"))
                        }
                    )
                }.toString()
            } else {
                200 to "{}"
            }
        }

        val result = authManager.signInWithEmailPassword(
            context = context,
            email = "Member@Ghotok.com",
            password = "MySecretPassword123!"
        )

        assertTrue(result.isSuccess)
        assertEquals("user-uuid-777", result.getOrNull())
        assertEquals(fakeAccessJwt, authManager.supabaseAccessToken)
        assertEquals("refresh-token-abc", authManager.supabaseRefreshToken)
        assertEquals("user-uuid-777", authManager.getAuthenticatedJwtUid())
        assertFalse(authManager.isCurrentAccessTokenExpired())

        val prefs = context.getSharedPreferences("ghotok_supabase_session", Context.MODE_PRIVATE)
        assertEquals("user-uuid-777", prefs.getString("user_id", null))
        assertEquals("member@ghotok.com", prefs.getString("email", null))
        assertEquals("refresh-token-abc", prefs.getString("refresh_token", null))
        // Verify plaintext password is NEVER stored in SharedPreferences
        assertNull(prefs.getString("last_auth_password", null))
        assertFalse(prefs.contains("last_auth_password"))
    }

    @Test
    fun saveSession_activelyPurgesLegacyPlaintextPasswordFromSharedPreferences() {
        val prefs = context.getSharedPreferences("ghotok_supabase_session", Context.MODE_PRIVATE)
        prefs.edit().putString("last_auth_password", "legacy_plaintext_pwd").commit()
        assertTrue(prefs.contains("last_auth_password"))

        authManager.saveSession(
            context = context,
            userId = "uid_100",
            email = "user@ghotok.com",
            displayName = "Test User",
            accessToken = "tok_1",
            refreshToken = "ref_1",
            password = "ignored_password"
        )

        assertFalse(prefs.contains("last_auth_password"))
        assertNull(prefs.getString("last_auth_password", null))
    }

    @Test
    fun handleUnauthorizedToken_rotatesExpiredTokenUsingRefreshTokenGrant() = runTest {
        val expiredEpoch = (System.currentTimeMillis() / 1000L) - 120L
        val freshEpoch = (System.currentTimeMillis() / 1000L) + 3600L
        val expiredJwt = buildSyntheticJwt("user-uuid-900", expiredEpoch)
        val rotatedJwt = buildSyntheticJwt("user-uuid-900", freshEpoch)

        authManager.saveSession(
            context = context,
            userId = "user-uuid-900",
            email = "rotate@ghotok.com",
            accessToken = expiredJwt,
            refreshToken = "valid-refresh-token-1"
        )
        assertTrue(authManager.isCurrentAccessTokenExpired())

        nextResponseProvider = { url, _, body ->
            if (url.contains("/auth/v1/token?grant_type=refresh_token")) {
                val reqJson = JSONObject(body)
                assertEquals("valid-refresh-token-1", reqJson.optString("refresh_token"))
                200 to JSONObject().apply {
                    put("access_token", rotatedJwt)
                    put("refresh_token", "valid-refresh-token-2")
                }.toString()
            } else {
                400 to """{"error":"unexpected"}"""
            }
        }

        val refreshed = authManager.handleUnauthorizedToken()
        assertEquals(rotatedJwt, refreshed)
        assertEquals(rotatedJwt, authManager.supabaseAccessToken)
        assertEquals("valid-refresh-token-2", authManager.supabaseRefreshToken)
        assertFalse(authManager.isCurrentAccessTokenExpired())
    }

    @Test
    fun clearSession_removesTokensAndMarksExplicitSignOut() {
        authManager.saveSession(
            context = context,
            userId = "user-uuid-500",
            email = "signout@ghotok.com",
            accessToken = "acc-500",
            refreshToken = "ref-500"
        )
        assertFalse(authManager.isExplicitlySignedOut(context))

        authManager.clearSession(context)

        assertTrue(authManager.isExplicitlySignedOut(context))
        assertNull(authManager.supabaseAccessToken)
        assertNull(authManager.supabaseRefreshToken)
        assertNull(authManager.getSessionUserId(context))
    }
}
