package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val requestId: String = "",
    val userId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val gender: String = "",
    val docType: String = "",
    val idNumber: String = "",
    val docUrl: String = "",
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val adminNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@IgnoreExtraProperties
data class PaymentRequest(
    @DocumentId val id: String = "",
    val requestId: String = "",
    val userId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val packageTier: String = "", // "Silver", "Gold", "Diamond"
    val amountBdt: Int = 0,
    val paymentMethod: String = "bKash", // "bKash", "Nagad", "Rocket", "Bank / Card"
    val senderPhone: String = "",
    val transactionId: String = "",
    val interestsToCredit: Int = 0,
    val contactsToCredit: Int = 0,
    val durationDays: Int = 0,
    val packageActivatedAt: Long = 0L,
    val packageExpiresAt: Long = 0L,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val adminNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Represents both:
 * 1. Targeted Audience Segment Broadcasts from Admin Web App (`/broadcasts/{id}` or `/notifications/{id}`)
 * 2. Direct User Alerts (`/notifications/{id}`) for Account/KYC Approval, Package Approval, Match Found, etc.
 */
@Immutable
@IgnoreExtraProperties
data class AdminNotification(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val broadcastId: String = "",
    val type: String = "BROADCAST", // "BROADCAST", "DISCOUNT", "ACCOUNT_APPROVED", "KYC_APPROVED", "KYC_REJECTED", "PACKAGE_APPROVED", "PACKAGE_REJECTED", "MATCH_FOUND", "SYSTEM"
    val targetSegment: String = "ALL",
    val targetUserId: String = "",
    val targetProfileCode: String = "",
    val title: String = "",
    val titleEn: String = "",
    val titleBn: String = "",
    val body: String = "",
    val bodyEn: String = "",
    val bodyBn: String = "",
    val message: String = "",
    val matchedUserId: String = "",
    val matchedProfileCode: String = "",
    val matchedUserName: String = "",
    val matchedUserPhoto: String = "",
    val matchScore: Int = 0,
    val packageTier: String = "",
    val actionRoute: String = "", // "VERIFY", "PACKAGES", "MATCHES", "PROFILE", "INTERESTS"
    val showBanner: Boolean = true,
    val pushNotification: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun uniqueKey(): String {
        return notificationId.ifBlank { broadcastId.ifBlank { id.ifBlank { "${type}_${title}_${titleBn}" } } }
    }

    fun resolvedTitle(isBangla: Boolean): String {
        val bn = titleBn.trim()
        val en = titleEn.trim().ifBlank { title.trim() }
        return if (isBangla) {
            bn.ifBlank { en }
        } else {
            en.ifBlank { bn }
        }
    }

    fun resolvedBody(isBangla: Boolean): String {
        val bn = bodyBn.trim()
        val en = bodyEn.trim().ifBlank { body.trim().ifBlank { message.trim() } }
        return if (isBangla) {
            bn.ifBlank { en }
        } else {
            en.ifBlank { bn }
        }
    }

    /**
     * Evaluates whether this notification / broadcast targets the given user profile.
     * Supports all 7 Audience Segments in the Ghotok Admin Dashboard plus direct UID / ProfileCode targeting.
     */
    fun matchesUser(
        profile: UserProfile,
        currentAuthUid: String,
        hasIncompleteDraft: Boolean = false
    ): Boolean {
        val formattedId = profile.getFormattedUserId()
        val userKeys = setOfNotNull(
            currentAuthUid.trim().takeIf { it.isNotBlank() }?.lowercase(),
            profile.userId.trim().takeIf { it.isNotBlank() }?.lowercase(),
            profile.id.trim().takeIf { it.isNotBlank() }?.lowercase(),
            formattedId.trim().takeIf { it.isNotBlank() }?.lowercase()
        )

        val explicitUid = targetUserId.trim()
        val explicitCode = targetProfileCode.trim()
        val knownSegmentKeywords = listOf(
            "ALL", "*", "UNVERIFIED", "INCOMPLETE_DRAFT", "FREE_TIER",
            "DIAMOND_VIP", "DHAKA_DIVISION", "OVERSEAS_NRB",
            "ALL REGISTERED", "FREE-TIER", "FREE TIER", "ONBOARDINGDRAFTS"
        )
        val uidLooksLikeSegment = explicitUid.isEmpty() ||
            knownSegmentKeywords.any { explicitUid.equals(it, ignoreCase = true) || explicitUid.contains(it, ignoreCase = true) }
        val isBroadcastTarget = uidLooksLikeSegment &&
            (explicitCode.isEmpty() || explicitCode.equals("ALL", ignoreCase = true) || explicitCode == "*")

        if (!isBroadcastTarget) {
            val matchesUid = explicitUid.isNotEmpty() && explicitUid.lowercase() in userKeys
            val matchesCode = explicitCode.isNotEmpty() && explicitCode.lowercase() in userKeys
            return matchesUid || matchesCode
        }

        val seg = targetSegment.trim().takeIf { it.isNotEmpty() && !it.equals("ALL", ignoreCase = true) }
            ?: explicitUid
        if (seg.isEmpty() || seg.equals("ALL", ignoreCase = true) || seg == "*" || seg.contains("All Registered", ignoreCase = true)) {
            return true
        }
        return when {
            seg.equals("UNVERIFIED", ignoreCase = true) || seg.contains("Unverified", ignoreCase = true) -> {
                !profile.isVerified
            }
            seg.equals("INCOMPLETE_DRAFT", ignoreCase = true) ||
                seg.contains("Incomplete", ignoreCase = true) ||
                seg.contains("onboardingDrafts", ignoreCase = true) -> {
                hasIncompleteDraft || profile.displayName.isBlank() || profile.profession.isBlank()
            }
            seg.equals("FREE_TIER", ignoreCase = true) ||
                seg.contains("Free-Tier", ignoreCase = true) ||
                seg.contains("Free Tier", ignoreCase = true) -> {
                profile.packageTier.equals("Free", ignoreCase = true)
            }
            seg.equals("DIAMOND_VIP", ignoreCase = true) ||
                seg.contains("Diamond", ignoreCase = true) -> {
                profile.packageTier.equals("Diamond", ignoreCase = true)
            }
            seg.equals("DHAKA_DIVISION", ignoreCase = true) ||
                seg.contains("Dhaka", ignoreCase = true) -> {
                val locs = listOf(
                    profile.presentDivision,
                    profile.permanentDivision,
                    profile.locationDivision,
                    profile.presentDistrict,
                    profile.locationDistrict
                )
                locs.any { it.contains("Dhaka", ignoreCase = true) || it.contains("ঢাকা") }
            }
            seg.equals("OVERSEAS_NRB", ignoreCase = true) ||
                seg.contains("Overseas", ignoreCase = true) ||
                seg.contains("NRB", ignoreCase = true) -> {
                val country = profile.overseasCountry.trim()
                val citizenship = profile.citizenshipStatus.trim()
                val visa = profile.overseasVisaStatus.trim()
                (country.isNotEmpty() && !country.equals("None", ignoreCase = true) && !country.equals("Bangladesh", ignoreCase = true)) ||
                    (citizenship.isNotEmpty() && !citizenship.equals("None", ignoreCase = true) && !citizenship.equals("Bangladeshi", ignoreCase = true)) ||
                    (visa.isNotEmpty() && !visa.equals("None", ignoreCase = true))
            }
            else -> true
        }
    }
}
