package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MembershipPackage(
    val tier: String,
    val productId: String = "ghotok_plan_${tier.lowercase()}",
    val nameEn: String,
    val nameBn: String,
    val priceBdt: Int,
    val durationText: String,
    val durationDays: Int = 0,
    val interestsCount: Int,
    val contactsCount: Int,
    val canChat: Boolean,
    val hasVerifiedBadge: Boolean,
    val isPopular: Boolean = false,
    val perks: List<String>
) {
    fun computeExpiryMillis(
        activatedAtMs: Long = System.currentTimeMillis(),
        customDurationDays: Int? = null
    ): Long {
        val effectiveDays = customDurationDays ?: durationDays
        if (effectiveDays <= 0 || tier.equals("Free", ignoreCase = true)) return 0L
        return activatedAtMs + effectiveDays * 24L * 60L * 60L * 1000L
    }
}

object MembershipPackages {
    val ALL = listOf(
        MembershipPackage(
            tier = "Free",
            productId = "ghotok_plan_free",
            nameEn = "Free Explorer",
            nameBn = "ফ্রি এক্সপ্লোরার",
            priceBdt = 0,
            durationText = "Lifetime",
            durationDays = 0,
            interestsCount = 2,
            contactsCount = 0,
            canChat = false,
            hasVerifiedBadge = false,
            perks = listOf(
                "Search & filter verified profiles",
                "View family background & preferences",
                "Send up to 2 free interests every week",
                "Contact details locked 🔒"
            )
        ),
        MembershipPackage(
            tier = "Silver",
            productId = "ghotok_plan_silver",
            nameEn = "Silver Rishta",
            nameBn = "সিলভার রিশতা",
            priceBdt = 490,
            durationText = "1 Month",
            durationDays = 30,
            interestsCount = 20,
            contactsCount = 10,
            canChat = true,
            hasVerifiedBadge = false,
            perks = listOf(
                "Send 20 Marriage Interests",
                "Unlock 10 Direct Phone & Guardian Contacts",
                "Private direct chat with matched members",
                "Instant push notifications on interest"
            )
        ),
        MembershipPackage(
            tier = "Gold",
            productId = "ghotok_plan_gold",
            nameEn = "Golden Nikah",
            nameBn = "গোল্ডেন নিকাহ",
            priceBdt = 990,
            durationText = "3 Months",
            durationDays = 90,
            interestsCount = 40,
            contactsCount = 25,
            canChat = true,
            hasVerifiedBadge = true,
            isPopular = true,
            perks = listOf(
                "Send 40 Marriage Interests",
                "Unlock 25 Direct Phone & Guardian Contacts",
                "Official Verified Member Badge included",
                "Unlimited private chat & photo exchange",
                "Personalized family matchmaker suggestions"
            )
        ),
        MembershipPackage(
            tier = "Diamond",
            productId = "ghotok_plan_diamond",
            nameEn = "Family Diamond",
            nameBn = "ফ্যামিলি ডায়মন্ড",
            priceBdt = 1990,
            durationText = "6 Months",
            durationDays = 180,
            interestsCount = 999,
            contactsCount = 999,
            canChat = true,
            hasVerifiedBadge = true,
            perks = listOf(
                "Unlimited Marriage Interests",
                "Unlimited Phone & Address Unlocks",
                "Top Profile Priority Listing",
                "Dedicated Family Ghotok / Marriage Advisor",
                "Discreet VIP privacy controls"
            )
        )
    )

    fun normalizeTierName(raw: String?): String {
        val clean = raw?.trim()?.lowercase().orEmpty()
        return when {
            clean.isEmpty() || clean == "free" || clean == "free explorer" || clean == "basic" || clean == "none" || clean == "ghotok_plan_free" -> "Free"
            clean.contains("silver") -> "Silver"
            clean.contains("gold") -> "Gold"
            clean.contains("diamond") || clean.contains("vip") || clean.contains("platinum") -> "Diamond"
            else -> raw?.trim()?.replaceFirstChar { it.uppercase() } ?: "Free"
        }
    }

    fun byTier(tier: String?): MembershipPackage {
        val normalized = normalizeTierName(tier)
        return ALL.firstOrNull { it.tier.equals(normalized, ignoreCase = true) } ?: ALL.first()
    }

    fun byProductId(productId: String?): MembershipPackage? {
        if (productId.isNullOrBlank()) return null
        return ALL.firstOrNull { it.productId.equals(productId.trim(), ignoreCase = true) }
    }
}
