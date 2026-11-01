package com.example.util

import android.util.Log

/**
 * Sanitized logger wrapper for Ghotok.
 *
 * Production builds must never log PII (phone numbers, emails, passwords,
 * transaction IDs, profile codes). This wrapper strips or masks sensitive fields
 * before forwarding to android.util.Log.
 *
 * Usage:
 *   GhotokLog.i("User logged in", "user_id=${profile.userId}")
 *   GhotokLog.e("Upload failed", ex, "url=${url.sanitize()}")
 */
object GhotokLog {

    private const val TAG = "Ghotok"

    // Sensitive patterns to mask in log messages
    private val SENSITIVE_PATTERNS = listOf(
        Regex("\\b[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}\\b"), // emails
        Regex("\\b\\d{10,16}\\b"), // phone numbers / account numbers
        Regex("(?i)(password|passwd|secret|token|apikey|api_key)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(transaction_id|txn_id|txnid)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(profile_code|formatted_id)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(bearer|authorization)\\s*[=:]\\s*\\S+"),
    )

    private fun sanitize(message: String): String {
        var sanitized = message
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { match ->
                // Keep first 2 chars of the matched value for debugging, mask the rest
                val value = match.value
                if (value.length <= 4) "***" else value.substring(0, 2) + "***"
            }
        }
        return sanitized
    }

    private fun buildMessage(vararg args: Any?): String {
        return args.joinToString(" ") { it?.toString() ?: "null" }
    }

    fun i(vararg args: Any?) = Log.i(TAG, sanitize(buildMessage(*args)))
    fun d(vararg args: Any?) = Log.d(TAG, sanitize(buildMessage(*args)))
    fun w(vararg args: Any?) = Log.w(TAG, sanitize(buildMessage(*args)))
    fun e(vararg args: Any?) = Log.e(TAG, sanitize(buildMessage(*args)))

    /** Log an error with a throwable stack trace. */
    fun e(throwable: Throwable, vararg args: Any?) {
        Log.e(TAG, sanitize(buildMessage(*args)), throwable)
    }

    /** Log an error with a throwable stack trace (alias for callers using named parameter). */
    fun e(throwable: Throwable, message: String = "") {
        Log.e(TAG, sanitize(message), throwable)
    }
}