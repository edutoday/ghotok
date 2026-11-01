package com.example.data.supabase

import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.MembershipPackages
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.util.Date

/**
 * Pure row-mapping and JSON serialization utilities for Supabase PostgREST payloads.
 * Extracted from [SupabaseClient] to keep transport and mapping concerns decoupled.
 */
object SupabaseRowMappers {

    fun parseJsonArray(raw: String): List<JSONObject> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        val list = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    fun extractPayloadJson(row: JSONObject): JSONObject {
        val payloadObj = row.optJSONObject("payload")
        if (payloadObj != null) return payloadObj
        val payloadStr = row.optString("payload", "")
        if (payloadStr.startsWith("{")) {
            runCatching { return JSONObject(payloadStr) }
        }
        return JSONObject()
    }

    fun instanceToJsonObject(instance: Any): JSONObject {
        val json = JSONObject()
        for (field in instance.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
            runCatching {
                field.isAccessible = true
                val name = field.name
                when (val value = field.get(instance)) {
                    null -> {}
                    is String, is Int, is Long, is Double, is Boolean -> json.put(name, value)
                    is Float -> json.put(name, value.toDouble())
                    is Timestamp -> json.put(name, value.toDate().time)
                    is List<*> -> {
                        val arr = JSONArray()
                        value.forEach { item -> if (item != null) arr.put(item.toString()) }
                        json.put(name, arr)
                    }
                    else -> json.put(name, value.toString())
                }
            }
        }
        return json
    }

    fun <T : Any> jsonToInstance(json: JSONObject, defaultInstance: T): T {
        for (field in defaultInstance.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
            val name = field.name
            if (!json.has(name) || json.isNull(name)) continue
            runCatching {
                field.isAccessible = true
                when (field.type) {
                    String::class.java -> field.set(defaultInstance, json.optString(name, ""))
                    Int::class.javaPrimitiveType, Int::class.javaObjectType ->
                        field.set(defaultInstance, json.optInt(name, field.get(defaultInstance) as? Int ?: 0))
                    Long::class.javaPrimitiveType, Long::class.javaObjectType ->
                        field.set(defaultInstance, json.optLong(name, field.get(defaultInstance) as? Long ?: 0L))
                    Double::class.javaPrimitiveType, Double::class.javaObjectType ->
                        field.set(defaultInstance, json.optDouble(name, field.get(defaultInstance) as? Double ?: 0.0))
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType ->
                        field.set(defaultInstance, json.optBoolean(name, field.get(defaultInstance) as? Boolean ?: false))
                    List::class.java -> {
                        val arr = json.optJSONArray(name)
                        if (arr != null) {
                            val items = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                val s = arr.optString(i, "")
                                if (s.isNotBlank()) items.add(s)
                            }
                            field.set(defaultInstance, items.toList())
                        }
                    }
                    Timestamp::class.java -> {
                        val ms = json.optLong(name, 0L)
                        if (ms > 0L) {
                            field.set(defaultInstance, Timestamp(Date(ms)))
                        }
                    }
                }
            }
        }
        return defaultInstance
    }

    fun rowToUserProfile(row: JSONObject): UserProfile? {
        val payload = extractPayloadJson(row)
        val base = UserProfile.fromJson(payload.toString()) ?: jsonToInstance(payload, UserProfile())

        val rowId = row.optString("id", base.id)
        val uid = row.optString("uid", payload.optString("userId", payload.optString("uid", base.userId))).ifBlank { rowId }
        val profileCode = row.optString("profile_code", payload.optString("profileCode", rowId))
        val fullName = row.optString("full_name", payload.optString("fullName", base.displayName)).ifBlank { base.displayName }
        val gender = row.optString("gender", base.gender).ifBlank { base.gender }
        val dob = row.optString("date_of_birth", base.dateOfBirth).ifBlank { base.dateOfBirth }
        val religion = row.optString("religion", base.religion).ifBlank { base.religion }
        val maritalStatus = row.optString("marital_status", base.maritalStatus).ifBlank { base.maritalStatus }
        val presentDivision = row.optString("present_division", base.presentDivision).ifBlank { base.presentDivision }
        val presentDistrict = row.optString("present_district", base.presentDistrict).ifBlank { base.presentDistrict }
        val presentUpazila = row.optString("present_upazila", base.presentUpazila).ifBlank { base.presentUpazila }
        val presentAddress = row.optString("present_address", base.presentAddress).ifBlank { base.presentAddress }
        val permanentDivision = row.optString("permanent_division", base.permanentDivision).ifBlank { base.permanentDivision }
        val permanentDistrict = row.optString("permanent_district", base.permanentDistrict).ifBlank { base.permanentDistrict }
        val permanentUpazila = row.optString("permanent_upazila", base.permanentUpazila).ifBlank { base.permanentUpazila }
        val permanentAddress = row.optString("permanent_address", base.permanentAddress).ifBlank { base.permanentAddress }
        val highestDegree = row.optString("highest_degree", base.education).ifBlank { base.education }
        val occupation = row.optString("occupation", base.profession).ifBlank { base.profession }
        val employmentType = row.optString("employment_type", base.employmentType).ifBlank { base.employmentType }
        val workLocationDistrict = row.optString("work_location_district", base.workLocationDistrict).ifBlank { base.workLocationDistrict }
        val accountStatus = row.optString("account_status", base.accountStatus).ifBlank { "ACTIVE" }
        val rawVerificationStatus = row.optString(
            "verification_status",
            payload.optString("verificationStatus", base.verificationStatus)
        ).trim().uppercase()
        val isApprovedOrVerified = rawVerificationStatus == "VERIFIED" || rawVerificationStatus == "APPROVED"
        val isVerified = row.optBoolean("is_verified", base.isVerified) || isApprovedOrVerified
        val normalizedVerificationStatus = when {
            isVerified -> "VERIFIED"
            rawVerificationStatus == "UNVERIFIED" || rawVerificationStatus.isBlank() -> "UNSUBMITTED"
            else -> rawVerificationStatus
        }
        val photoUrl = row.optString("photo_url", base.photoUrl).ifBlank { base.photoUrl }

        val topLevelTierRaw = listOf(
            row.optString("package_tier", ""),
            row.optString("plan_name", ""),
            row.optString("plan_id", ""),
            row.optString("membership_tier", ""),
            row.optString("tier", "")
        ).firstOrNull { it.isNotBlank() }

        val payloadTierRaw = listOf(
            payload.optString("packageTier", ""),
            payload.optString("planName", ""),
            payload.optString("planId", ""),
            payload.optString("membershipTier", ""),
            payload.optString("subscriptionTier", ""),
            payload.optString("tier", ""),
            payload.optString("plan", "")
        ).firstOrNull { it.isNotBlank() }

        val resolvedTier = when {
            !topLevelTierRaw.isNullOrBlank() -> MembershipPackages.normalizeTierName(topLevelTierRaw)
            !payloadTierRaw.isNullOrBlank() -> MembershipPackages.normalizeTierName(payloadTierRaw)
            row.optBoolean("is_featured", false) -> "Diamond"
            row.has("is_premium") && !row.optBoolean("is_premium", false) && base.packageTier != "Free" && payloadTierRaw.isNullOrBlank() -> "Free"
            else -> MembershipPackages.normalizeTierName(base.packageTier)
        }

        val rawPendingTier = row.optString(
            "pending_package_tier",
            payload.optString("pendingPackageTier", base.pendingPackageTier)
        )
        val resolvedPendingTier = if (rawPendingTier.equals(resolvedTier, ignoreCase = true)) "" else rawPendingTier

        val pkgSpec = MembershipPackages.byTier(resolvedTier)

        fun parseEpochMs(rawVal: Any?): Long {
            when (rawVal) {
                is Number -> return rawVal.toLong()
                is String -> {
                    val trimmed = rawVal.trim()
                    if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return 0L
                    trimmed.toLongOrNull()?.let { return it }
                    return runCatching { java.time.Instant.parse(trimmed).toEpochMilli() }.getOrDefault(0L)
                }
                else -> return 0L
            }
        }

        val rawActivatedAt = listOf(
            parseEpochMs(row.opt("package_activated_at")),
            parseEpochMs(payload.opt("packageActivatedAt")),
            parseEpochMs(payload.opt("planActivatedAt")),
            base.packageActivatedAt
        ).firstOrNull { it > 0L } ?: 0L

        val rawExpiresAt = listOf(
            parseEpochMs(row.opt("package_expires_at")),
            parseEpochMs(row.opt("plan_expires_at")),
            parseEpochMs(row.opt("subscription_expires_at")),
            parseEpochMs(payload.opt("packageExpiresAt")),
            parseEpochMs(payload.opt("planExpiresAt")),
            parseEpochMs(payload.opt("subscriptionExpiresAt")),
            base.packageExpiresAt
        ).firstOrNull { it > 0L } ?: 0L

        val resolvedActivatedAt = if (resolvedTier == "Free") 0L else rawActivatedAt
        val resolvedExpiresAt = when {
            resolvedTier == "Free" -> 0L
            rawExpiresAt > 0L -> rawExpiresAt
            resolvedActivatedAt > 0L -> pkgSpec.computeExpiryMillis(resolvedActivatedAt)
            else -> 0L
        }

        val explicitInterests = when {
            row.has("interests_remaining") && !row.isNull("interests_remaining") -> row.optInt("interests_remaining", base.interestsRemaining)
            row.has("interest_quota") && !row.isNull("interest_quota") -> row.optInt("interest_quota", base.interestsRemaining)
            payload.has("interestsRemaining") -> payload.optInt("interestsRemaining", base.interestsRemaining)
            payload.has("interestQuota") -> payload.optInt("interestQuota", base.interestsRemaining)
            payload.has("interestsQuota") -> payload.optInt("interestsQuota", base.interestsRemaining)
            else -> base.interestsRemaining
        }

        val explicitContacts = when {
            row.has("contacts_remaining") && !row.isNull("contacts_remaining") -> row.optInt("contacts_remaining", base.contactsRemaining)
            row.has("contact_quota") && !row.isNull("contact_quota") -> row.optInt("contact_quota", base.contactsRemaining)
            payload.has("contactsRemaining") -> payload.optInt("contactsRemaining", base.contactsRemaining)
            payload.has("contactQuota") -> payload.optInt("contactQuota", base.contactsRemaining)
            payload.has("contactsQuota") -> payload.optInt("contactsQuota", base.contactsRemaining)
            else -> base.contactsRemaining
        }

        val resolvedInterests = when {
            resolvedTier == "Free" -> if (explicitInterests > 2) 2 else explicitInterests.coerceAtLeast(0)
            explicitInterests <= 2 && explicitContacts == 0 -> pkgSpec.interestsCount
            else -> explicitInterests
        }
        val resolvedContacts = when {
            resolvedTier == "Free" -> 0
            explicitContacts == 0 && explicitInterests <= 2 -> pkgSpec.contactsCount
            else -> explicitContacts
        }

        val resolvedEmail = row.optString("email", base.email).ifBlank { base.email }
        val isConfiguredOrDbAdmin = UserProfile.isConfiguredOrVerifiedAdminEmail(resolvedEmail)
        val rawRole = row.optString("role", payload.optString("role", base.role))
        val resolvedIsAdmin = row.optBoolean("is_admin", payload.optBoolean("isAdmin", payload.optBoolean("is_admin", base.isAdmin))) ||
            rawRole.equals("ADMIN", ignoreCase = true) ||
            rawRole.equals("SUPER_ADMIN", ignoreCase = true) ||
            isConfiguredOrDbAdmin
        val resolvedRole = if (resolvedIsAdmin) "ADMIN" else rawRole.ifBlank { "USER" }

        val calculatedAgeFromDob = run {
            val parts = dob.split("-")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null) {
                    UserProfile.calculateAgeFromDob(y, m, d).takeIf { it > 0 }
                } else null
            } else null
        }
        val publicAge = row.optInt("age", base.age).takeIf { it > 0 }
        val resolvedAge = calculatedAgeFromDob ?: publicAge ?: base.age

        return base.copy(
            id = profileCode.ifBlank { rowId },
            userId = uid,
            profileCode = profileCode.ifBlank { rowId },
            displayName = fullName,
            gender = gender,
            dateOfBirth = dob,
            age = resolvedAge,
            religion = religion,
            maritalStatus = maritalStatus,
            presentDivision = presentDivision,
            presentDistrict = presentDistrict,
            presentUpazila = presentUpazila,
            presentAddress = presentAddress,
            locationDivision = presentDivision.ifBlank { base.locationDivision },
            locationDistrict = presentDistrict.ifBlank { base.locationDistrict },
            permanentDivision = permanentDivision,
            permanentDistrict = permanentDistrict,
            permanentUpazila = permanentUpazila,
            permanentAddress = permanentAddress,
            education = highestDegree,
            profession = occupation,
            employmentType = employmentType,
            jobEmploymentStatus = employmentType.ifBlank { base.jobEmploymentStatus },
            workLocationDistrict = workLocationDistrict,
            accountStatus = accountStatus,
            isVerified = isVerified || pkgSpec.hasVerifiedBadge,
            verificationStatus = if (isVerified || pkgSpec.hasVerifiedBadge) "VERIFIED" else normalizedVerificationStatus,
            packageTier = resolvedTier,
            pendingPackageTier = resolvedPendingTier,
            packageActivatedAt = resolvedActivatedAt,
            packageExpiresAt = resolvedExpiresAt,
            interestsRemaining = resolvedInterests,
            contactsRemaining = resolvedContacts,
            photoUrl = photoUrl,
            email = resolvedEmail,
            contactPhone = row.optString("phone_number", base.contactPhone).ifBlank { base.contactPhone },
            role = resolvedRole,
            isAdmin = resolvedIsAdmin
        ).withWeeklyFreeInterestReset()
    }

    fun rowToInterest(row: JSONObject): Interest? {
        val payload = if (row.has("payload")) extractPayloadJson(row) else row
        val base = jsonToInstance(payload, Interest())
        val id = row.optString("id", payload.optString("id", base.id.ifBlank { base.interestId }))
        val senderId = row.optString("sender_id", payload.optString("senderId", base.senderId))
        val receiverId = row.optString("receiver_id", payload.optString("receiverId", base.receiverId))
        val senderFormattedId = payload.optString("senderFormattedId", base.senderFormattedId)
        val receiverFormattedId = payload.optString("receiverFormattedId", base.receiverFormattedId)
        if (senderId.isBlank() && senderFormattedId.isBlank()) return null
        if (receiverId.isBlank() && receiverFormattedId.isBlank()) return null
        val status = row.optString("status", payload.optString("status", base.status)).lowercase().ifBlank { "pending" }
        val resolvedId = id.ifBlank { "int_${senderId.ifBlank { senderFormattedId }}_${receiverId.ifBlank { receiverFormattedId }}" }
        return base.copy(
            id = resolvedId,
            interestId = resolvedId,
            senderId = senderId.ifBlank { senderFormattedId },
            receiverId = receiverId.ifBlank { receiverFormattedId },
            senderFormattedId = senderFormattedId,
            receiverFormattedId = receiverFormattedId,
            status = status
        )
    }

    fun rowToChatThread(row: JSONObject): ChatThread? {
        val payload = extractPayloadJson(row)
        val base = jsonToInstance(payload, ChatThread())
        val id = row.optString("id", base.id)
        val pA = row.optString("participant_a", "")
        val pB = row.optString("participant_b", "")
        val participants = if (base.participantIds.isNotEmpty()) {
            base.participantIds
        } else {
            listOf(pA, pB).filter { it.isNotBlank() }
        }
        val tsMs = row.optLong("last_message_timestamp", 0L)
        val updatedAt = if (tsMs > 0L) Timestamp(Date(tsMs)) else base.updatedAt
        return base.copy(
            id = id,
            chatId = id,
            participantIds = participants,
            lastMessage = row.optString("last_message", base.lastMessage),
            lastMessageSenderId = row.optString("last_message_sender_id", base.lastMessageSenderId),
            updatedAt = updatedAt
        )
    }

    fun rowToChatMessage(row: JSONObject): ChatMessage? {
        val payload = extractPayloadJson(row)
        val base = jsonToInstance(payload, ChatMessage())
        val id = row.optString("id", base.id)
        val senderId = row.optString("sender_id", base.senderId)
        val text = row.optString("text", base.text)
        val tsMs = row.optLong("timestamp_ms", 0L)
        val createdAt = if (tsMs > 0L) Timestamp(Date(tsMs)) else base.createdAt ?: Timestamp.now()
        return base.copy(
            id = id,
            messageId = id,
            senderId = senderId,
            text = text,
            createdAt = createdAt
        )
    }

    fun rowToAdminNotification(row: JSONObject): AdminNotification? {
        val payload = extractPayloadJson(row)
        val base = jsonToInstance(payload, AdminNotification())
        val id = row.optString("id", base.id.ifBlank { base.notificationId.ifBlank { base.broadcastId } })
        val title = row.optString("title", base.title.ifBlank { base.titleEn })
        val message = row.optString("message", base.message.ifBlank { base.body.ifBlank { base.bodyEn } })
        val targetUserId = row.optString(
            "target_user_id",
            payload.optString("targetUserId", payload.optString("userId", base.targetUserId))
        )
        val targetProfileCode = row.optString(
            "target_profile_code",
            payload.optString("targetProfileCode", payload.optString("profileCode", base.targetProfileCode))
        )
        val targetSegment = row.optString(
            "target_segment",
            payload.optString(
                "targetSegment",
                payload.optString("segment", payload.optString("audience", base.targetSegment))
            )
        ).ifBlank { "ALL" }
        val type = row.optString("type", base.type).ifBlank { "BROADCAST" }
        val actionRoute = row.optString("action_route", payload.optString("actionRoute", base.actionRoute))
        val pushEnabled = if (payload.has("pushNotification")) {
            payload.optBoolean("pushNotification", true)
        } else if (payload.has("sendPush")) {
            payload.optBoolean("sendPush", true)
        } else {
            true
        }
        val createdMs = row.optLong("created_at_ms", payload.optLong("createdAt", 0L))
        val createdAt = when {
            createdMs > 0L -> Timestamp(Date(createdMs))
            base.createdAt != null -> base.createdAt
            else -> {
                val rawIso = row.optString("created_at", "").trim()
                val parsedMs = runCatching {
                    java.time.Instant.parse(
                        rawIso.replace(' ', 'T').let { s ->
                            when {
                                s.endsWith("+00") -> s.dropLast(3) + "Z"
                                s.endsWith("+00:00") -> s.dropLast(6) + "Z"
                                s.isNotEmpty() && !s.endsWith("Z") && !s.contains("+") -> "${s}Z"
                                else -> s
                            }
                        }
                    ).toEpochMilli()
                }.getOrDefault(0L)
                if (parsedMs > 0L) Timestamp(Date(parsedMs)) else Timestamp.now()
            }
        }
        return base.copy(
            id = id,
            notificationId = id,
            title = title,
            body = base.body.ifBlank { message },
            message = message,
            targetUserId = targetUserId,
            targetProfileCode = targetProfileCode,
            targetSegment = targetSegment,
            type = type,
            actionRoute = actionRoute,
            pushNotification = pushEnabled,
            createdAt = createdAt
        )
    }
}
