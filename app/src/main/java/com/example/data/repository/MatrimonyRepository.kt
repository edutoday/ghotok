package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.SampleData
import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.MembershipPackage
import com.example.data.model.MembershipPackages
import com.example.data.model.OnboardingDraft
import com.example.data.model.PaymentRequest
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRequest
import com.example.data.supabase.SupabaseClient
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow. filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Primary repository for Ghotok BD Matrimony data operations.
 *
 * Architectural notes:
 * - Primary backend: Supabase PostgREST (`SupabaseClient`).
 * - Local caching: Delegated to `MatrimonyLocalCache`.
 * - Legacy/Emulator fallback: Delegated to `FirestoreMatrimonyDataSource` when `appContext == null`
 *   or Supabase is unconfigured (such as in `MatrimonyRepositoryRuleTest`).
 * - Network timeouts: Uses `NETWORK_TIMEOUT_MS = 15_000L` instead of aggressive 1.8s-3.5s
 *   `withTimeoutOrNull` wrappers so slow mobile networks do not silently drop requests.
 */
class MatrimonyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val appContext: Context? = null
) {
    private val useSupabaseBackend: Boolean = appContext != null && SupabaseClient.isConfigured()
    private val localCache = MatrimonyLocalCache(appContext)
    private val firestoreDataSource by lazy { FirestoreMatrimonyDataSource(db, localCache) }

    fun currentUserId(): String? {
        val supabaseUid = SupabaseClient.getSessionUserId(appContext)?.takeIf { it.isNotBlank() }
        if (supabaseUid != null) return supabaseUid
        return runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    }

    fun currentUserEmail(): String? {
        val supabaseEmail = SupabaseClient.getSessionEmail(appContext)?.takeIf { it.isNotBlank() }
        if (supabaseEmail != null) return supabaseEmail
        return runCatching { Firebase.auth.currentUser?.email }.getOrNull()
    }

    fun currentUserDisplayName(): String? {
        val supabaseName = SupabaseClient.getSessionDisplayName(appContext)?.takeIf { it.isNotBlank() }
        if (supabaseName != null) return supabaseName
        return runCatching { Firebase.auth.currentUser?.displayName }.getOrNull()
    }

    fun getMemoryCachedProfile(userId: String): UserProfile? =
        localCache.getMemoryProfile(userId)

    fun getCachedProfileForUser(userId: String): UserProfile? =
        localCache.getCachedProfileForUser(userId)

    fun getCachedCandidates(): List<UserProfile> =
        localCache.getCachedCandidates()

    fun getCachedReceivedInterests(userId: String): List<Interest> =
        localCache.getCachedReceivedInterests(userId)

    fun getCachedSentInterests(userId: String): List<Interest> =
        localCache.getCachedSentInterests(userId)

    fun getCachedChats(userId: String): List<ChatThread> =
        localCache.getCachedChats(userId)

    fun getCachedNotifications(): List<AdminNotification> =
        localCache.getCachedNotifications()

    fun clearCachedProfileForUser(userId: String) =
        localCache.clearCachedProfileForUser(userId)

    suspend fun prefetchUserProfile(userId: String): UserProfile? =
        getUserProfile(userId)

    suspend fun getUserProfileById(userId: String): UserProfile? =
        getUserProfile(userId)

    // --- User Profile Operations ---

    suspend fun getUserProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        if (!useSupabaseBackend) {
            return firestoreDataSource.getUserProfile(userId)
        }
        return try {
            val remote = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.fetchUserProfile(userId)
            }
            if (remote != null) {
                localCache.cacheResolvedProfile(userId, remote)
            } else {
                localCache.getCachedProfileForUser(userId)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                throw e
            }
            Log.w(TAG, "getUserProfile failed for $userId: ${e.message}")
            localCache.getCachedProfileForUser(userId)
        }
    }

    private suspend fun awaitRealtimeOrFallback(
        tables: Set<String>,
        disconnectedFallbackMs: Long,
        extraSignal: Flow<Unit>? = null
    ) {
        // Protect against Realtime reconnection loops that could overrun battery and
        // network quota. If we've already been waiting on the existing (dead) WebSocket,
        // honor the existing REALTIME_CONNECTED_FALLBACK_SYNC_MS longer than a fresh
        // connection attempt. This also eliminates the case where the connection
        // repeatedly fails and immediately restarts, causing a tight loop.
        SupabaseClient.ensureRealtimeConnected()
        val timeoutMs = if (SupabaseClient.isRealtimeConnected.value) {
            REALTIME_CONNECTED_FALLBACK_SYNC_MS
        } else {
            disconnectedFallbackMs
        }
        val tableFlow = SupabaseClient.realtimeTableEvents.filter { it in tables }
        withTimeoutOrNull(timeoutMs) {
            if (extraSignal != null) {
                merge(tableFlow, extraSignal).first()
            } else {
                tableFlow.first()
            }
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeUserProfile(userId)
        }
        return flow {
            if (userId.isBlank()) {
                emit(null)
                return@flow
            }
            SupabaseClient.ensureRealtimeConnected()
            localCache.getCachedProfileForUser(userId)?.let { emit(it) }
            while (true) {
                val pollStartMs = System.currentTimeMillis()
                try {
                    val remote = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchUserProfile(userId)
                    }
                    val lastLocalSaveMs = lastLocalProfileWriteMs[userId] ?: 0L
                    val localCached = localCache.getCachedProfileForUser(userId)
                    if (remote != null) {
                        if (lastLocalSaveMs > pollStartMs && localCached != null) {
                            // A local profile save happened while this network fetch was in flight; emit the newer local state
                            emit(localCached)
                        } else {
                            emit(localCache.cacheResolvedProfile(userId, remote))
                        }
                    } else if (localCached != null) {
                        emit(localCached)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeUserProfile sync failed for $userId: ${e.message}")
                    localCache.getCachedProfileForUser(userId)?.let { emit(it) }
                }
                // If the realtime WebSocket is connected, changes arrive via push and this
                // await is a no-op safety net. When disconnected, poll at the fallback interval
                // so a live profile still refreshes on a slow/blocked network instead of
                // silently stalling the flow.
                awaitRealtimeOrFallback(
                    tables = setOf("users", "profiles", "payment_requests", "verification_requests"),
                    disconnectedFallbackMs = PROFILE_FALLBACK_SYNC_INTERVAL_MS
                )
            }
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<UserProfile> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.saveUserProfile(profile)
        }
        return runCatching {
            val currentWeek = UserProfile.getCurrentWeekKey()
            val requestedUid = profile.userId.ifBlank { profile.id.ifBlank { currentUserId().orEmpty() } }
            val resolvedEmail = profile.email.ifBlank { currentUserEmail().orEmpty() }

            val existingRemote = runCatching {
                withTimeout(NETWORK_TIMEOUT_MS) { SupabaseClient.fetchUserProfile(requestedUid) }
            }.getOrNull() ?: localCache.getCachedProfileForUser(requestedUid)

            val uid = existingRemote?.userId?.takeIf { it.isNotBlank() && !UserProfile.isValidFormattedUserId(it) }
                ?: requestedUid

            val allocatedCode = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.allocateUniqueProfileCode(
                    uid = uid,
                    gender = profile.gender,
                    preferredCode = profile.profileCode.ifBlank { existingRemote?.profileCode.orEmpty() },
                    createdAt = profile.createdAt ?: existingRemote?.createdAt
                )
            }

            val fcmToken = runCatching {
                FirebaseMessaging.getInstance().token.await()
            }.getOrDefault("")

            val prepared = profile.copy(
                id = allocatedCode,
                userId = uid,
                profileCode = allocatedCode,
                email = resolvedEmail,
                freeInterestWeekKey = profile.freeInterestWeekKey.ifBlank { currentWeek },
                accountStatus = profile.accountStatus.ifBlank {
                    existingRemote?.accountStatus?.takeIf { it.isNotBlank() } ?: "ACTIVE"
                },
                packageTier = existingRemote?.packageTier?.takeIf { it.isNotBlank() }
                    ?: profile.packageTier.ifBlank { "Free" },
                pendingPackageTier = existingRemote?.pendingPackageTier ?: profile.pendingPackageTier,
                packageActivatedAt = existingRemote?.packageActivatedAt?.takeIf { it > 0L } ?: profile.packageActivatedAt,
                packageExpiresAt = existingRemote?.packageExpiresAt?.takeIf { it > 0L } ?: profile.packageExpiresAt,
                isVerified = (existingRemote?.isVerified == true) || profile.isVerified || profile.hasAdminAccess(),
                verificationStatus = when {
                    (existingRemote?.isVerified == true) || profile.isVerified || profile.hasAdminAccess() -> "VERIFIED"
                    existingRemote != null && existingRemote.verificationStatus.isNotBlank() &&
                        !existingRemote.verificationStatus.equals("UNSUBMITTED", ignoreCase = true) -> existingRemote.verificationStatus
                    else -> profile.verificationStatus.ifBlank { "UNSUBMITTED" }
                },
                updatedAt = Timestamp.now()
            )

            val nowMs = System.currentTimeMillis()
            lastLocalProfileWriteMs[uid] = nowMs
            lastLocalProfileWriteMs[requestedUid] = nowMs
            val cached = localCache.cacheResolvedProfile(uid, prepared, allowEmptySavedIds = true)
            if (requestedUid.isNotBlank() && requestedUid != uid) {
                localCache.cacheResolvedProfile(requestedUid, cached, allowEmptySavedIds = true)
            }

            val savedRemotely = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(prepared, fcmToken)
            }
            if (!savedRemotely) {
                Log.w(TAG, "saveUserProfile: remote upsert reported false for uid=$uid code=$allocatedCode")
            }
            val finalTimeMs = System.currentTimeMillis()
            lastLocalProfileWriteMs[uid] = finalTimeMs
            lastLocalProfileWriteMs[requestedUid] = finalTimeMs
            clearOnboardingDraft(uid)
            cached
        }
    }

    // --- Onboarding Draft Persistence ---

    suspend fun saveOnboardingDraft(userId: String, draft: OnboardingDraft, step: Int): Result<Unit> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.saveOnboardingDraft(userId, draft, step)
        }
        return runCatching {
            if (userId.isBlank()) return@runCatching
            localCache.saveDraftToPrefs(userId, draft, step)
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertOnboardingDraft(userId, draft, step)
            }
        }
    }

    suspend fun getOnboardingDraft(userId: String): Pair<OnboardingDraft, Int>? {
        if (userId.isBlank()) return null
        if (!useSupabaseBackend) {
            return firestoreDataSource.getOnboardingDraft(userId)
        }
        val localDraft = localCache.loadDraftFromPrefs(userId)
        return try {
            val remoteDraft = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.fetchOnboardingDraft(userId)
            }
            if (remoteDraft != null) {
                localCache.saveDraftToPrefs(userId, remoteDraft.first, remoteDraft.second)
                remoteDraft
            } else {
                localDraft
            }
        } catch (e: Exception) {
            Log.w(TAG, "getOnboardingDraft fallback to local cache for $userId: ${e.message}")
            localDraft
        }
    }

    suspend fun clearOnboardingDraft(userId: String) {
        if (userId.isBlank()) return
        if (!useSupabaseBackend) {
            firestoreDataSource.clearOnboardingDraft(userId)
            return
        }
        localCache.clearDraftFromPrefs(userId)
        runCatching {
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.deleteOnboardingDraft(userId)
            }
        }
    }

    // --- Candidate & Admin Directory Observation ---

    fun observeCandidates(): Flow<List<UserProfile>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeCandidates()
        }
        return flow {
            SupabaseClient.ensureRealtimeConnected()
            val initialCached = localCache.getCachedCandidates()
            if (initialCached.isNotEmpty()) {
                emit(initialCached)
            }
            while (true) {
                try {
                    val all = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchAllUsers()
                    }
                    val filtered = all.filter { profile ->
                        val statusActive = profile.accountStatus.equals("ACTIVE", ignoreCase = true) ||
                            profile.accountStatus.isBlank()
                        statusActive && profile.displayName.isNotBlank()
                    }
                    if (filtered.isNotEmpty()) {
                        localCache.cacheCandidates(filtered)
                        emit(filtered)
                    } else {
                        val fallback = localCache.getCachedCandidates()
                        if (fallback.isNotEmpty()) {
                            emit(fallback)
                        } else {
                            emit(emptyList())
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeCandidates sync failed: ${e.message}")
                    val fallback = localCache.getCachedCandidates()
                    if (fallback.isNotEmpty()) {
                        emit(fallback)
                    }
                }
                awaitRealtimeOrFallback(
                    tables = setOf("users", "profiles"),
                    disconnectedFallbackMs = DIRECTORY_FALLBACK_SYNC_INTERVAL_MS
                )
            }
        }
    }

    fun observeNotificationsAndBroadcasts(): Flow<List<AdminNotification>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeNotificationsAndBroadcasts()
        }
        return flow {
            SupabaseClient.ensureRealtimeConnected()
            val cached = localCache.getCachedNotifications()
            if (cached.isNotEmpty()) {
                emit(cached)
            }
            while (true) {
                try {
                    val list = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchNotificationsAndBroadcasts()
                    }
                    if (list.isNotEmpty() || localCache.getCachedNotifications().isEmpty()) {
                        localCache.cacheNotifications(list)
                        emit(list)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeNotificationsAndBroadcasts sync failed: ${e.message}")
                }
                awaitRealtimeOrFallback(
                    tables = setOf("notifications", "broadcasts"),
                    disconnectedFallbackMs = NOTIFICATIONS_FALLBACK_SYNC_INTERVAL_MS
                )
            }
        }
    }

    // --- Shortlist / Saved Profiles & Account Controls ---

    suspend fun toggleSaveProfile(currentUserId: String, targetUserId: String): Result<List<String>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.toggleSaveProfile(currentUserId, targetUserId)
        }
        return runCatching {
            val profile = localCache.getCachedProfileForUser(currentUserId)
                ?: getUserProfile(currentUserId)
                ?: UserProfile(userId = currentUserId)
            val currentList = profile.savedProfileIds.toMutableList()
            if (currentList.contains(targetUserId)) {
                currentList.remove(targetUserId)
            } else {
                currentList.add(targetUserId)
            }
            val updatedProfile = profile.copy(savedProfileIds = currentList)
            lastLocalProfileWriteMs[currentUserId] = System.currentTimeMillis()
            localCache.cacheResolvedProfile(currentUserId, updatedProfile, allowEmptySavedIds = true)
            if (updatedProfile.displayName.isNotBlank()) {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    SupabaseClient.upsertUserProfile(updatedProfile)
                }
            }
            currentList
        }
    }

    suspend fun updateSavedProfiles(
        currentProfile: UserProfile,
        updatedSavedIds: List<String>
    ): Result<List<String>> = runCatching {
        val uid = currentProfile.userId.ifBlank { currentProfile.id }
        val updatedProfile = currentProfile.copy(savedProfileIds = updatedSavedIds)
        if (uid.isNotBlank()) {
            lastLocalProfileWriteMs[uid] = System.currentTimeMillis()
            localCache.cacheResolvedProfile(uid, updatedProfile, allowEmptySavedIds = true)
        }
        if (useSupabaseBackend) {
            if (updatedProfile.displayName.isNotBlank()) {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    SupabaseClient.upsertUserProfile(updatedProfile)
                }
            }
        } else {
            firestoreDataSource.saveUserProfile(updatedProfile).getOrThrow()
        }
        updatedSavedIds
    }

    suspend fun unlockCandidateContact(currentProfile: UserProfile): Result<UserProfile> = runCatching {
        val checkedProfile = currentProfile.withWeeklyFreeInterestReset()
        if (currentProfile.isPackageExpired()) {
            localCache.cacheResolvedProfile(checkedProfile.userId, checkedProfile)
            if (useSupabaseBackend) {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    SupabaseClient.upsertUserProfile(checkedProfile)
                }
            } else {
                firestoreDataSource.saveUserProfile(checkedProfile).getOrThrow()
            }
            throw IllegalStateException("Your membership package has expired. Please renew or upgrade your package to unlock contacts.")
        }
        if (checkedProfile.contactsRemaining <= 0) {
            throw IllegalStateException("No contact unlocks remaining in your current package.")
        }
        val updated = checkedProfile.copy(
            contactsRemaining = (checkedProfile.contactsRemaining - 1).coerceAtLeast(0)
        )
        localCache.cacheResolvedProfile(updated.userId, updated)
        if (useSupabaseBackend) {
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(updated)
            }
        } else {
            firestoreDataSource.saveUserProfile(updated).getOrThrow()
        }
        updated
    }

    suspend fun updateProfilePhoto(userId: String, photoUrl: String): Result<UserProfile> = runCatching {
        val cleanUrl = photoUrl.trim()
        val current = getUserProfile(userId) ?: UserProfile(userId = userId)
        val updated = current.copy(photoUrl = cleanUrl)
        localCache.cacheResolvedProfile(userId, updated)
        if (useSupabaseBackend) {
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(updated)
            }
        } else {
            firestoreDataSource.saveUserProfile(updated).getOrThrow()
        }
        updated
    }

    suspend fun toggleHideAccount(userId: String, hidden: Boolean): Result<UserProfile> = runCatching {
        val current = getUserProfile(userId) ?: UserProfile(userId = userId)
        val newStatus = if (hidden) "HIDDEN" else "ACTIVE"
        val updated = current.copy(accountStatus = newStatus)
        localCache.cacheResolvedProfile(userId, updated)
        if (useSupabaseBackend) {
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(updated)
            }
        } else {
            firestoreDataSource.saveUserProfile(updated).getOrThrow()
        }
        updated
    }

    suspend fun updateUserEmail(userId: String, newEmail: String): Result<UserProfile> = runCatching {
        val cleanEmail = newEmail.trim().lowercase()
        val current = getUserProfile(userId) ?: UserProfile(userId = userId)
        val updated = current.copy(email = cleanEmail)
        localCache.cacheResolvedProfile(userId, updated)
        if (useSupabaseBackend) {
            if (updated.displayName.isNotBlank()) {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    SupabaseClient.upsertUserProfile(updated)
                }
            }
        } else {
            firestoreDataSource.saveUserProfile(updated).getOrThrow()
        }
        updated
    }

    suspend fun deleteUserAccount(userId: String): Result<Unit> = runCatching {
        localCache.clearCachedProfileForUser(userId)
        localCache.clearDraftFromPrefs(userId)
        if (useSupabaseBackend) {
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.deleteUserAccount(userId)
            }
        }
    }

    // --- Interest System ---

    suspend fun sendInterest(
        fromProfile: UserProfile,
        toProfile: UserProfile
    ): Result<Interest> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.sendInterest(fromProfile, toProfile)
        }
        return runCatching {
            val refreshedSender = fromProfile.withWeeklyFreeInterestReset()
            if (refreshedSender.interestsRemaining <= 0) {
                throw IllegalStateException("No interests remaining in your current package.")
            }

            val senderUid = refreshedSender.userId.ifBlank { currentUserId() ?: refreshedSender.id }
            val receiverUid = toProfile.userId.ifBlank { toProfile.id }
            val interestId = "int_${senderUid}_${receiverUid}"
            val matchScore = SampleData.calculateCompatibility(refreshedSender, toProfile)
            val interest = Interest(
                id = interestId,
                interestId = interestId,
                senderId = senderUid,
                receiverId = receiverUid,
                senderFormattedId = refreshedSender.getFormattedUserId(),
                receiverFormattedId = toProfile.getFormattedUserId(),
                senderName = refreshedSender.displayName,
                senderPhoto = refreshedSender.photoUrl,
                senderAge = refreshedSender.age,
                senderUpazila = refreshedSender.presentUpazila.ifBlank { refreshedSender.presentDistrict },
                senderProfession = refreshedSender.profession,
                senderVerified = refreshedSender.isVerified,
                receiverName = toProfile.displayName,
                receiverPhoto = toProfile.photoUrl,
                receiverAge = toProfile.age,
                receiverUpazila = toProfile.presentUpazila.ifBlank { toProfile.presentDistrict },
                receiverProfession = toProfile.profession,
                receiverVerified = toProfile.isVerified,
                matchScore = matchScore,
                status = "pending",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )

            val newRemaining = (refreshedSender.interestsRemaining - 1).coerceAtLeast(0)
            val updatedSender = refreshedSender.copy(interestsRemaining = newRemaining)
            localCache.cacheResolvedProfile(senderUid, updatedSender)

            val cacheKeys = setOf(senderUid, currentUserId().orEmpty(), refreshedSender.id).filter { it.isNotBlank() }
            for (k in cacheKeys) {
                val existingSent = localCache.getCachedSentInterests(k).filterNot {
                    it.id == interest.id || (it.receiverId == interest.receiverId && it.senderId == interest.senderId)
                }
                localCache.cacheSentInterests(k, listOf(interest) + existingSent)
            }
            interestsRefreshSignal.tryEmit(Unit)

            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(updatedSender)
                SupabaseClient.upsertInterest(interest)
            }
            interestsRefreshSignal.tryEmit(Unit)
            interest
        }
    }

    suspend fun respondToInterest(interest: Interest, accept: Boolean): Result<String?> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.respondToInterest(interest, accept)
        }
        return runCatching {
            val newStatus = if (accept) "accepted" else "declined"
            val updatedInterest = interest.copy(
                status = newStatus,
                updatedAt = Timestamp.now()
            )
            val sortedIds = listOf(interest.senderId, interest.receiverId).sorted()
            val chatId = if (accept) "chat_${sortedIds[0]}_${sortedIds[1]}" else null

            val activeUid = currentUserId().orEmpty()
            for (k in setOf(activeUid, interest.receiverId, interest.receiverFormattedId).filter { it.isNotBlank() }) {
                val currentRec = localCache.getCachedReceivedInterests(k)
                if (currentRec.isNotEmpty()) {
                    localCache.cacheReceivedInterests(
                        k,
                        currentRec.map { if (it.id == interest.id || it.senderId == interest.senderId) updatedInterest else it }
                    )
                }
            }
            interestsRefreshSignal.tryEmit(Unit)

            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertInterest(updatedInterest)
                if (accept && chatId != null) {
                    val thread = ChatThread(
                        id = chatId,
                        chatId = chatId,
                        interestId = interest.id.ifBlank { interest.interestId },
                        participantIds = sortedIds,
                        otherUserName = interest.senderName,
                        otherUserPhoto = interest.senderPhoto,
                        lastMessage = "Interest Accepted! Say Assalamu Alaikum.",
                        lastMessageSenderId = interest.receiverId,
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                    SupabaseClient.upsertChatThread(thread)
                }
            }
            interestsRefreshSignal.tryEmit(Unit)
            chatId
        }
    }

    fun observeReceivedInterests(
        userId: String,
        secondaryUserId: String = "",
        profileCode: String = ""
    ): Flow<List<Interest>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeReceivedInterests(userId)
        }
        return flow {
            if (userId.isBlank() && secondaryUserId.isBlank() && profileCode.isBlank()) {
                emit(emptyList())
                return@flow
            }
            val cacheKey = userId.ifBlank { secondaryUserId.ifBlank { profileCode } }
            val cached = localCache.getCachedReceivedInterests(cacheKey)
            if (cached.isNotEmpty()) {
                emit(cached)
            }
            while (true) {
                val latestCached = localCache.getCachedReceivedInterests(cacheKey)
                if (latestCached.isNotEmpty()) {
                    emit(latestCached)
                }
                try {
                    val list = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchReceivedInterests(
                            userId = userId,
                            secondaryUserId = secondaryUserId,
                            profileCode = profileCode
                        )
                    }
                    if (list.isNotEmpty() || latestCached.isEmpty()) {
                        localCache.cacheReceivedInterests(cacheKey, list)
                        if (secondaryUserId.isNotBlank()) {
                            localCache.cacheReceivedInterests(secondaryUserId, list)
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeReceivedInterests poll failed: ${e.message}")
                }
                awaitRealtimeOrFallback(
                    tables = setOf("interests"),
                    disconnectedFallbackMs = INTERESTS_FALLBACK_SYNC_INTERVAL_MS,
                    extraSignal = interestsRefreshSignal
                )
            }
        }
    }

    fun observeSentInterests(
        userId: String,
        secondaryUserId: String = "",
        profileCode: String = ""
    ): Flow<List<Interest>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeSentInterests(userId)
        }
        return flow {
            if (userId.isBlank() && secondaryUserId.isBlank() && profileCode.isBlank()) {
                emit(emptyList())
                return@flow
            }
            SupabaseClient.ensureRealtimeConnected()
            val cacheKey = userId.ifBlank { secondaryUserId.ifBlank { profileCode } }
            val cached = localCache.getCachedSentInterests(cacheKey)
            if (cached.isNotEmpty()) {
                emit(cached)
            }
            while (true) {
                val latestCached = localCache.getCachedSentInterests(cacheKey)
                if (latestCached.isNotEmpty()) {
                    emit(latestCached)
                }
                try {
                    val list = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchSentInterests(
                            userId = userId,
                            secondaryUserId = secondaryUserId,
                            profileCode = profileCode
                        )
                    }
                    if (list.isNotEmpty() || latestCached.isEmpty()) {
                        localCache.cacheSentInterests(cacheKey, list)
                        if (secondaryUserId.isNotBlank()) {
                            localCache.cacheSentInterests(secondaryUserId, list)
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeSentInterests sync failed: ${e.message}")
                }
                awaitRealtimeOrFallback(
                    tables = setOf("interests"),
                    disconnectedFallbackMs = INTERESTS_FALLBACK_SYNC_INTERVAL_MS,
                    extraSignal = interestsRefreshSignal
                )
            }
        }
    }

    // --- Real-time Messaging (Supabase Realtime WebSocket + Fallback) ---

    fun observeChats(userId: String): Flow<List<ChatThread>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeChats(userId)
        }
        return flow {
            if (userId.isBlank()) {
                emit(emptyList())
                return@flow
            }
            SupabaseClient.ensureRealtimeConnected()
            val cached = localCache.getCachedChats(userId)
            if (cached.isNotEmpty()) {
                emit(cached)
            }
            while (true) {
                try {
                    val threads = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchChatsForUser(userId)
                    }
                    localCache.cacheChats(userId, threads)
                    emit(threads)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeChats sync failed: ${e.message}")
                }
                awaitRealtimeOrFallback(
                    tables = setOf("chats", "chat_messages"),
                    disconnectedFallbackMs = CHAT_THREADS_FALLBACK_SYNC_INTERVAL_MS
                )
            }
        }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.observeMessages(chatId)
        }
        return flow {
            if (chatId.isBlank()) {
                emit(emptyList())
                return@flow
            }
            SupabaseClient.ensureRealtimeConnected()
            val cached = localCache.getCachedMessages(chatId)
            if (cached.isNotEmpty()) {
                emit(cached)
            }
            while (true) {
                try {
                    val messages = withTimeout(NETWORK_TIMEOUT_MS) {
                        SupabaseClient.fetchChatMessages(chatId)
                    }
                    localCache.cacheMessages(chatId, messages)
                    emit(messages)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                        throw e
                    }
                    Log.w(TAG, "observeMessages sync failed for $chatId: ${e.message}")
                }
                awaitRealtimeOrFallback(
                    tables = setOf("chat_messages"),
                    disconnectedFallbackMs = CHAT_MESSAGES_FALLBACK_SYNC_INTERVAL_MS
                )
            }
        }
    }

    suspend fun sendMessage(
        chatId: String,
        senderProfile: UserProfile,
        otherUserId: String,
        text: String
    ): Result<ChatMessage> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.sendMessage(chatId, senderProfile, otherUserId, text)
        }
        return runCatching {
            val msgId = "msg_${System.currentTimeMillis()}"
            val participants = listOf(senderProfile.userId, otherUserId).filter { it.isNotBlank() }.sorted()
            val message = ChatMessage(
                id = msgId,
                messageId = msgId,
                senderId = senderProfile.userId,
                senderName = senderProfile.displayName,
                text = text,
                participantIds = participants,
                createdAt = Timestamp.now()
            )
            val existingCached = localCache.getCachedMessages(chatId)
            localCache.cacheMessages(chatId, existingCached + message)
            SupabaseClient.notifyLocalTableMutation("chat_messages")
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.insertChatMessage(chatId, message)
                val thread = ChatThread(
                    id = chatId,
                    chatId = chatId,
                    participantIds = participants,
                    lastMessage = text,
                    lastMessageSenderId = senderProfile.userId,
                    updatedAt = Timestamp.now()
                )
                SupabaseClient.upsertChatThread(thread)
            }
            SupabaseClient.notifyLocalTableMutation("chat_messages")
            SupabaseClient.notifyLocalTableMutation("chats")
            message
        }
    }

    suspend fun ensureChatThread(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<ChatThread> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.ensureChatThread(currentUser, otherUser)
        }
        return runCatching {
            val sortedIds = listOf(currentUser.userId, otherUser.userId).sorted()
            val chatId = "chat_${sortedIds[0]}_${sortedIds[1]}"
            val thread = ChatThread(
                id = chatId,
                chatId = chatId,
                participantIds = sortedIds,
                otherUserName = otherUser.displayName,
                otherUserNameBangla = otherUser.displayNameBangla,
                otherUserFormattedId = otherUser.getFormattedUserId(),
                otherUserPhoto = otherUser.photoUrl,
                lastMessage = "",
                lastMessageSenderId = currentUser.userId,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            withTimeout(NETWORK_TIMEOUT_MS) {
                val existing = SupabaseClient.fetchChatsForUser(currentUser.userId)
                    .firstOrNull { it.chatId == chatId || it.id == chatId }
                if (existing == null) {
                    val currentIds = setOf(currentUser.userId, currentUser.getFormattedUserId())
                        .filter { it.isNotBlank() }
                        .toSet()
                    val otherIds = setOf(otherUser.userId, otherUser.getFormattedUserId())
                        .filter { it.isNotBlank() }
                        .toSet()
                    val acceptedInterest = (
                        SupabaseClient.fetchSentInterests(
                            userId = currentUser.userId,
                            profileCode = currentUser.getFormattedUserId()
                        ) + SupabaseClient.fetchReceivedInterests(
                            userId = currentUser.userId,
                            profileCode = currentUser.getFormattedUserId()
                        )
                    ).firstOrNull { interest ->
                        interest.status.equals("accepted", ignoreCase = true) &&
                            ((interest.senderId in currentIds && interest.receiverId in otherIds) ||
                                (interest.receiverId in currentIds && interest.senderId in otherIds))
                    } ?: throw IllegalStateException("Accept this member's proposal before starting a chat.")
                    SupabaseClient.upsertChatThread(
                        thread.copy(interestId = acceptedInterest.id.ifBlank { acceptedInterest.interestId })
                    )
                }
                existing ?: thread
            }
        }
    }

    // --- Packages, Verification & Admin Actions ---

    suspend fun submitPaymentRequest(
        profile: UserProfile,
        pkg: MembershipPackage,
        paymentMethod: String,
        senderPhone: String,
        transactionId: String
    ): Result<String> {
        if (paymentMethod.equals("GOOGLE_PLAY_BILLING", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException("Google Play purchases must use server-side purchase verification.")
            )
        }
        if (!useSupabaseBackend) {
            return firestoreDataSource.submitPaymentRequest(profile, pkg, paymentMethod, senderPhone, transactionId)
        }
        return runCatching {
            val docId = profile.getFormattedUserId()
            val nowMs = System.currentTimeMillis()
            val reqId = "${docId}_${transactionId.uppercase().take(12).ifBlank { nowMs.toString() }}"
            val request = PaymentRequest(
                id = reqId,
                requestId = reqId,
                userId = profile.userId.ifBlank { docId },
                profileCode = docId,
                displayName = profile.displayName,
                packageTier = pkg.tier,
                amountBdt = pkg.priceBdt,
                paymentMethod = paymentMethod,
                senderPhone = senderPhone,
                transactionId = transactionId,
                interestsToCredit = pkg.interestsCount,
                contactsToCredit = pkg.contactsCount,
                durationDays = pkg.durationDays,
                packageActivatedAt = 0L,
                packageExpiresAt = 0L,
                status = "PENDING",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val updatedProfile = profile.copy(pendingPackageTier = pkg.tier)
            localCache.cacheResolvedProfile(profile.userId.ifBlank { docId }, updatedProfile)
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertPaymentRequest(request)
                SupabaseClient.upsertUserProfile(updatedProfile)
            }
            reqId
        }
    }

    suspend fun verifyGooglePlayPurchase(
        userId: String,
        productId: String,
        purchaseToken: String
    ): Result<UserProfile> {
        return try {
            check(useSupabaseBackend) {
                "Google Play purchases require the configured server-side verification service."
            }
            val authenticatedUid = SupabaseClient.getAuthenticatedJwtUid().orEmpty()
            require(authenticatedUid.isNotBlank() && authenticatedUid == userId) {
                "Sign in to the account that made this Google Play purchase."
            }

            val verification = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.verifyGooglePlayPurchase(purchaseToken, productId)
            }
            require(verification.optBoolean("verified")) {
                "Google Play did not confirm this purchase."
            }

            val pkg = MembershipPackages.byProductId(productId)
                ?: throw IllegalArgumentException("Google Play product is not a supported membership.")
            val verifiedTier = verification.optString("packageTier")
            require(verifiedTier.equals(pkg.tier, ignoreCase = true)) {
                "Verified Google Play product did not match the selected membership."
            }

            val current = SupabaseClient.fetchUserProfile(userId)
                ?: throw IllegalStateException("Could not reload the verified membership profile.")
            val updated = current.copy(
                packageTier = verifiedTier,
                pendingPackageTier = "",
                packageActivatedAt = verification.optLong("activatedAtMs"),
                packageExpiresAt = verification.optLong("expiresAtMs"),
                interestsRemaining = verification.optInt("interestsRemaining"),
                contactsRemaining = verification.optInt("contactsRemaining"),
                isVerified = current.isVerified || verification.optBoolean("isVerified")
            )
            localCache.cacheResolvedProfile(userId, updated)
            Result.success(updated)
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitVerificationRequest(
        userId: String,
        docType: String,
        idNumber: String,
        docUri: String = ""
    ): Result<Unit> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.submitVerificationRequest(userId, docType, idNumber, docUri)
        }
        return runCatching {
            val current = getUserProfile(userId) ?: UserProfile(userId = userId)
            val docId = current.getFormattedUserId()
            val req = VerificationRequest(
                id = docId,
                requestId = docId,
                userId = userId,
                profileCode = docId,
                displayName = current.displayName,
                gender = current.gender,
                docType = docType,
                idNumber = idNumber,
                docUrl = docUri,
                status = "PENDING",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val updated = current.copy(
                verificationStatus = "PENDING",
                verificationDocType = docType,
                verificationIdNumber = idNumber,
                verificationDocUri = docUri
            )
            localCache.cacheResolvedProfile(userId, updated)
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertVerificationRequest(req)
                SupabaseClient.upsertUserProfile(updated)
            }
        }
    }

    suspend fun purchasePackage(userId: String, pkg: MembershipPackage): Result<UserProfile> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.purchasePackage(userId, pkg)
        }
        return runCatching {
            val current = getUserProfile(userId) ?: UserProfile(userId = userId)
            val nowMs = System.currentTimeMillis()
            val activatedAtMs = if (pkg.tier == "Free") 0L else nowMs
            val expiresAtMs = if (pkg.tier == "Free") 0L else pkg.computeExpiryMillis(nowMs)
            val updated = current.copy(
                packageTier = pkg.tier,
                pendingPackageTier = "",
                packageActivatedAt = activatedAtMs,
                packageExpiresAt = expiresAtMs,
                interestsRemaining = pkg.interestsCount,
                contactsRemaining = pkg.contactsCount,
                isVerified = current.isVerified || pkg.hasVerifiedBadge
            )
            localCache.cacheResolvedProfile(userId, updated)
            withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.upsertUserProfile(updated)
            }
            updated
        }
    }

    suspend fun adminChangeUserPackage(
        targetUserIdOrCode: String,
        newTier: String,
        customInterests: Int? = null,
        customContacts: Int? = null,
        customDurationDays: Int? = null,
        adminNote: String = "Manual Package Update by Admin"
    ): Result<UserProfile> {
        if (!useSupabaseBackend) {
            return firestoreDataSource.adminChangeUserPackage(
                targetUserIdOrCode = targetUserIdOrCode,
                newTier = newTier,
                customInterests = customInterests,
                customContacts = customContacts,
                customDurationDays = customDurationDays
            )
        }
        return runCatching {
            val saved = withTimeout(NETWORK_TIMEOUT_MS) {
                SupabaseClient.adminChangeUserPackage(
                    uidOrCode = targetUserIdOrCode,
                    newTier = newTier,
                    customInterests = customInterests,
                    customContacts = customContacts,
                    customDurationDays = customDurationDays,
                    adminNote = adminNote
                ).getOrThrow()
            }
            localCache.cacheResolvedProfile(saved.userId, saved)
        }
    }

    companion object {
        private const val TAG = "MatrimonyRepository"
        const val NETWORK_TIMEOUT_MS = 15_000L
        // When Supabase Realtime WebSocket is connected, changes arrive immediately via push;
        // periodic sync runs only as a lightweight reconciliation safety net.
        private const val REALTIME_CONNECTED_FALLBACK_SYNC_MS = 60_000L
        private const val PROFILE_FALLBACK_SYNC_INTERVAL_MS = 30_000L
        private const val DIRECTORY_FALLBACK_SYNC_INTERVAL_MS = 45_000L
        private const val NOTIFICATIONS_FALLBACK_SYNC_INTERVAL_MS = 45_000L
        private const val INTERESTS_FALLBACK_SYNC_INTERVAL_MS = 25_000L
        private const val CHAT_THREADS_FALLBACK_SYNC_INTERVAL_MS = 30_000L
        private const val CHAT_MESSAGES_FALLBACK_SYNC_INTERVAL_MS = 20_000L
        private val lastLocalProfileWriteMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val interestsRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

        fun create(context: Context): MatrimonyRepository =
            MatrimonyRepository(
                db = FirebaseFirestore.getInstance(),
                appContext = context.applicationContext
            )
    }
}
