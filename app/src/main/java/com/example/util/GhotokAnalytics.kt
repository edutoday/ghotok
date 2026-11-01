package com.example.util

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Param

/**
 * Centralized analytics events for Ghotok.
 *
 * Tracks critical funnel events so conversion can be measured:
 *   - sign_up_method: email / google / phone
 *   - profile_completion_step: 0..N (onboarding wizard progress)
 *   - interest_sent: proposal sent to a candidate
 *   - payment_success: Google Play purchase completed
 *   - chat_message_sent: message delivered in a chat thread
 *
 * All events are forwarded to Firebase Analytics. The SDK is initialized
 * automatically by the Firebase BOM in the application build.
 */
object GhotokAnalytics {

    private fun analytics(context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    fun trackEvent(context: Context, name: String, params: Map<String, Any> = emptyMap()) {
        val bundle = android.os.Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Double -> putDouble(k, v)
                    is Boolean -> putBoolean(k, v)
                    else -> putString(k, v.toString())
                }
            }
        }
        analytics(context).logEvent(name, bundle)
    }

    /** User signed up via [method] ("email", "google", "phone"). */
    fun trackSignUp(context: Context, method: String) {
        trackEvent(context, "sign_up", mapOf(
            "method" to method,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User completed step [step] of the onboarding wizard (0 = started, N = finished). */
    fun trackProfileCompletionStep(context: Context, step: Int, total: Int) {
        trackEvent(context, "profile_completion_step", mapOf(
            "step" to step,
            "total" to total,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User sent an interest proposal to [receiverFormattedId]. */
    fun trackInterestSent(context: Context, receiverFormattedId: String, matchScore: Int) {
        trackEvent(context, "interest_sent", mapOf(
            "receiver_id" to receiverFormattedId,
            "match_score" to matchScore,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User sent a chat message in thread [chatId]. */
    fun trackChatMessageSent(context: Context, chatId: String) {
        trackEvent(context, "chat_message_sent", mapOf(
            "chat_id" to chatId,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User viewed a profile detail screen. */
    fun trackProfileView(context: Context, profileCode: String) {
        trackEvent(context, "view_item", mapOf(
            "item_id" to profileCode,
            "item_category" to "profile",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User searched for matches with [query]. */
    fun trackSearch(context: Context, query: String, resultCount: Int) {
        trackEvent(context, "search", mapOf(
            "search_term" to query,
            "result_count" to resultCount,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User opened the packages/payment screen. */
    fun trackPackageView(context: Context, tier: String) {
        trackEvent(context, "view_item", mapOf(
            "item_id" to tier,
            "item_category" to "package",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User initiated a payment for [tier]. */
    fun trackPaymentInitiated(context: Context, tier: String, amountBdt: Int) {
        trackEvent(context, "payment_initiated", mapOf(
            "tier" to tier,
            "amount_bdt" to amountBdt,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User completed a payment via [method] for [tier]. */
    fun trackPaymentSuccess(context: Context, tier: String, method: String, amountBdt: Int) {
        trackEvent(context, "payment_success", mapOf(
            "tier" to tier,
            "method" to method,
            "amount_bdt" to amountBdt,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User logged in via [method]. */
    fun trackLogin(context: Context, method: String) {
        trackEvent(context, "login", mapOf(
            "method" to method,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /** User logged out. */
    fun trackLogout(context: Context) {
        trackEvent(context, "logout", mapOf(
            "timestamp" to System.currentTimeMillis()
        ))
    }
}