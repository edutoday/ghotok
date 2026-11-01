package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.OnboardingDraft
import com.example.data.model.UserProfile
import com.example.data.supabase.SupabaseRowMappers
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local in-memory and SharedPreferences cache for user profiles, candidate directory,
 * interests, chats, and onboarding drafts.
 */
class MatrimonyLocalCache(private val appContext: Context?) {

    fun getMemoryProfile(key: String): UserProfile? {
        if (key.isBlank()) return null
        sharedProfileMemoryCache[key]?.let { return it }
        return sharedProfileMemoryCache.values.firstOrNull {
            it.userId.equals(key, ignoreCase = true) ||
                it.id.equals(key, ignoreCase = true) ||
                it.profileCode.equals(key, ignoreCase = true)
        }
    }

    fun putMemoryProfile(key: String, profile: UserProfile) {
        if (key.isNotBlank()) {
            sharedProfileMemoryCache[key] = profile
        }
    }

    fun cacheCandidates(candidates: List<UserProfile>) {
        if (candidates.isEmpty()) return
        sharedCandidatesCache = candidates
        candidates.forEach { p ->
            putMemoryProfile(p.userId, p)
            if (p.id.isNotBlank()) putMemoryProfile(p.id, p)
            if (p.profileCode.isNotBlank()) putMemoryProfile(p.profileCode, p)
        }
        val ctx = appContext ?: return
        runCatching {
            val arr = JSONArray()
            candidates.take(150).forEach { arr.put(JSONObject(it.toJson())) }
            ctx.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("cached_candidates_list", arr.toString())
                .apply()
        }
    }

    fun getCachedCandidates(): List<UserProfile> {
        if (sharedCandidatesCache.isNotEmpty()) return sharedCandidatesCache
        val ctx = appContext ?: return emptyList()
        return runCatching {
            val raw = ctx.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
                .getString("cached_candidates_list", null)
                ?: return emptyList()
            val arr = JSONArray(raw)
            val loaded = ArrayList<UserProfile>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val profile = UserProfile.fromJson(obj.toString()) ?: continue
                if (profile.displayName.isNotBlank()) {
                    loaded.add(profile)
                }
            }
            if (loaded.isNotEmpty()) {
                sharedCandidatesCache = loaded
            }
            loaded
        }.getOrDefault(emptyList())
    }

    fun cacheReceivedInterests(userId: String, list: List<Interest>) {
        if (userId.isNotBlank()) {
            sharedReceivedInterests[userId] = list
        }
    }

    fun getCachedReceivedInterests(userId: String): List<Interest> =
        if (userId.isBlank()) emptyList() else sharedReceivedInterests[userId].orEmpty()

    fun cacheSentInterests(userId: String, list: List<Interest>) {
        if (userId.isNotBlank()) {
            sharedSentInterests[userId] = list
        }
    }

    fun getCachedSentInterests(userId: String): List<Interest> =
        if (userId.isBlank()) emptyList() else sharedSentInterests[userId].orEmpty()

    fun cacheChats(userId: String, list: List<ChatThread>) {
        if (userId.isNotBlank()) {
            sharedChatsByUser[userId] = list
        }
    }

    fun getCachedChats(userId: String): List<ChatThread> =
        if (userId.isBlank()) emptyList() else sharedChatsByUser[userId].orEmpty()

    fun cacheMessages(chatId: String, list: List<ChatMessage>) {
        if (chatId.isNotBlank()) {
            sharedMessagesByChat[chatId] = list
        }
    }

    fun getCachedMessages(chatId: String): List<ChatMessage> =
        if (chatId.isBlank()) emptyList() else sharedMessagesByChat[chatId].orEmpty()

    fun cacheNotifications(list: List<AdminNotification>) {
        sharedNotifications = list
    }

    fun getCachedNotifications(): List<AdminNotification> = sharedNotifications

    fun cacheResolvedProfile(
        userId: String,
        profile: UserProfile,
        allowEmptySavedIds: Boolean = false
    ): UserProfile {
        val existing = getMemoryProfile(userId) ?: loadProfileFromPrefs(userId)
        val merged = if (existing != null) {
            profile.copy(
                gender = profile.gender.ifBlank { existing.gender },
                savedProfileIds = if (allowEmptySavedIds || profile.savedProfileIds.isNotEmpty()) {
                    profile.savedProfileIds
                } else {
                    existing.savedProfileIds
                }
            )
        } else {
            profile
        }
        val normalized = merged.withWeeklyFreeInterestReset()
        if (userId.isNotBlank()) sharedProfileMemoryCache[userId] = normalized
        if (normalized.userId.isNotBlank()) sharedProfileMemoryCache[normalized.userId] = normalized
        if (normalized.id.isNotBlank()) sharedProfileMemoryCache[normalized.id] = normalized
        if (normalized.profileCode.isNotBlank()) sharedProfileMemoryCache[normalized.profileCode] = normalized
        if (sharedCandidatesCache.isNotEmpty() && normalized.displayName.isNotBlank()) {
            val currentList = sharedCandidatesCache
            var replaced = false
            val updatedList = currentList.map { item ->
                if ((normalized.userId.isNotBlank() && item.userId == normalized.userId) ||
                    (normalized.id.isNotBlank() && item.id.equals(normalized.id, ignoreCase = true)) ||
                    (normalized.profileCode.isNotBlank() && item.profileCode.equals(normalized.profileCode, ignoreCase = true))
                ) {
                    replaced = true
                    normalized
                } else {
                    item
                }
            }
            if (replaced) {
                sharedCandidatesCache = updatedList
            }
        }
        saveProfileToPrefs(userId, normalized)
        return normalized
    }

    fun getCachedProfileForUser(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        getMemoryProfile(userId)?.let { return it }
        return loadProfileFromPrefs(userId)?.also { loaded ->
            cacheResolvedProfile(userId, loaded)
        }
    }

    fun clearCachedProfileForUser(userId: String) {
        if (userId.isBlank()) return
        val existing = sharedProfileMemoryCache.remove(userId)
        if (existing != null) {
            if (existing.id.isNotBlank()) sharedProfileMemoryCache.remove(existing.id)
            if (existing.profileCode.isNotBlank()) sharedProfileMemoryCache.remove(existing.profileCode)
        }
        val iterator = sharedProfileMemoryCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.userId == userId || entry.value.id == userId ||
                entry.value.profileCode.equals(userId, ignoreCase = true)
            ) {
                iterator.remove()
            }
        }
        sharedReceivedInterests.remove(userId)
        sharedSentInterests.remove(userId)
        sharedChatsByUser.remove(userId)
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("profile_$userId")
                .apply()
            ctx.getSharedPreferences(LEGACY_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("cached_profile_$userId")
                .apply()
        }
    }

    private fun saveProfileToPrefs(userId: String, profile: UserProfile) {
        val ctx = appContext ?: return
        runCatching {
            val prefs = ctx.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
            val jsonStr = profile.toJson()
            val editor = prefs.edit().putString("profile_$userId", jsonStr)
            if (profile.userId.isNotBlank()) {
                editor.putString("profile_${profile.userId}", jsonStr)
            }
            if (profile.profileCode.isNotBlank()) {
                editor.putString("profile_${profile.profileCode}", jsonStr)
            }
            editor.apply()
        }.onFailure { e ->
            Log.w(TAG, "Failed to persist profile cache for $userId: ${e.message}")
        }
    }

    private fun loadProfileFromPrefs(userId: String): UserProfile? {
        val ctx = appContext ?: return null
        return runCatching {
            val prefs = ctx.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString("profile_$userId", null)
                ?: ctx.getSharedPreferences(LEGACY_CACHE_PREFS, Context.MODE_PRIVATE)
                    .getString("cached_profile_$userId", null)
                ?: return null
            val loaded = UserProfile.fromJson(raw)
                ?: SupabaseRowMappers.jsonToInstance(JSONObject(raw), UserProfile())
            val resolvedCode = when {
                UserProfile.isValidFormattedUserId(loaded.profileCode, loaded.gender) -> loaded.profileCode
                UserProfile.isValidFormattedUserId(loaded.id, loaded.gender) -> loaded.id
                else -> loaded.profileCode
            }
            loaded.copy(
                id = loaded.id.ifBlank { resolvedCode.ifBlank { loaded.userId } },
                userId = loaded.userId.ifBlank { userId },
                profileCode = resolvedCode
            )
        }.getOrNull()
    }

    fun saveDraftToPrefs(userId: String, draft: OnboardingDraft, step: Int) {
        val ctx = appContext ?: return
        if (userId.isBlank()) return
        runCatching {
            val prefs = ctx.getSharedPreferences(DRAFT_CACHE_PREFS, Context.MODE_PRIVATE)
            val wrapper = JSONObject().apply {
                put("userId", userId)
                put("currentStep", step.coerceIn(0, 6))
                put("updatedAtMs", System.currentTimeMillis())
                put("draftJson", draft.toJson())
            }
            prefs.edit().putString("draft_$userId", wrapper.toString()).apply()
        }
    }

    fun loadDraftFromPrefs(userId: String): Pair<OnboardingDraft, Int>? {
        val ctx = appContext ?: return null
        if (userId.isBlank()) return null
        return runCatching {
            val prefs = ctx.getSharedPreferences(DRAFT_CACHE_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString("draft_$userId", null) ?: return null
            val wrapper = JSONObject(raw)
            val step = wrapper.optInt("currentStep", 0).coerceIn(0, 6)
            val draftJsonStr = wrapper.optString("draftJson", "")
            if (draftJsonStr.isBlank()) return null
            OnboardingDraft.fromJson(draftJsonStr) to step
        }.getOrNull()
    }

    fun clearDraftFromPrefs(userId: String) {
        val ctx = appContext ?: return
        if (userId.isBlank()) return
        runCatching {
            val prefs = ctx.getSharedPreferences(DRAFT_CACHE_PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove("draft_$userId").apply()
        }
    }

    companion object {
        private const val TAG = "MatrimonyLocalCache"
        private const val PROFILE_CACHE_PREFS = "ghotok_profile_cache_v2"
        private const val LEGACY_CACHE_PREFS = "ghotok_local_profile_cache"
        private const val DRAFT_CACHE_PREFS = "ghotok_onboarding_draft_v1"

        private val sharedProfileMemoryCache = ConcurrentHashMap<String, UserProfile>()
        @Volatile
        private var sharedCandidatesCache: List<UserProfile> = emptyList()
        private val sharedReceivedInterests = ConcurrentHashMap<String, List<Interest>>()
        private val sharedSentInterests = ConcurrentHashMap<String, List<Interest>>()
        private val sharedChatsByUser = ConcurrentHashMap<String, List<ChatThread>>()
        private val sharedMessagesByChat = ConcurrentHashMap<String, List<ChatMessage>>()
        @Volatile
        private var sharedNotifications: List<AdminNotification> = emptyList()
    }
}
