package com.example.data.repository

import android.util.Log
import com.example.data.SampleData
import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.MembershipPackage
import com.example.data.model.MembershipPackages
import com.example.data.model.OnboardingDraft
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Isolated Firestore data source used for Firestore emulator rule testing (`MatrimonyRepositoryRuleTest`)
 * and legacy fallback when Supabase is unconfigured.
 *
 * Replaces all aggressive 1.8s-3.5s `withTimeoutOrNull` wrappers with a realistic `FIRESTORE_TIMEOUT_MS = 15_000L`.
 */
class FirestoreMatrimonyDataSource(
    private val db: FirebaseFirestore,
    private val localCache: MatrimonyLocalCache
) {
    private val usersCol = db.collection("users")
    private val publicProfilesCol = db.collection("publicProfiles")
    private val draftsCol = db.collection("onboardingDrafts")
    private val interestsCol = db.collection("interests")
    private val chatsCol = db.collection("chats")
    private val paymentRequestsCol = db.collection("paymentRequests")
    private val verificationRequestsCol = db.collection("verificationRequests")
    private val notificationsCol = db.collection("notifications")
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun parseProfileSnapshot(
        doc: DocumentSnapshot,
        sequentialMap: Map<String, String> = emptyMap()
    ): UserProfile? {
        val base = runCatching { doc.toObject(UserProfile::class.java) }.getOrNull() ?: return null
        val docEmail = (doc.getString("email") ?: base.email).trim()
        val rawRole = (doc.getString("role") ?: base.role).trim()
        val isAdminFlag = (doc.getBoolean("isAdmin") == true) ||
            (doc.getBoolean("admin") == true) ||
            base.isAdmin ||
            rawRole.equals("ADMIN", ignoreCase = true) ||
            rawRole.equals("SUPER_ADMIN", ignoreCase = true) ||
            UserProfile.isConfiguredOrVerifiedAdminEmail(docEmail)
        val rawVerifStatus = (doc.getString("verificationStatus") ?: base.verificationStatus).trim()
        val isVerifiedFlag = (doc.getBoolean("isVerified") == true) ||
            (doc.getBoolean("verified") == true) ||
            base.isVerified ||
            rawVerifStatus.equals("VERIFIED", ignoreCase = true) ||
            isAdminFlag
        val resolvedVerifStatus = when {
            isVerifiedFlag -> "VERIFIED"
            rawVerifStatus.isNotBlank() -> rawVerifStatus
            else -> "UNSUBMITTED"
        }
        val resolvedRole = if (isAdminFlag) "ADMIN" else rawRole.ifBlank { "USER" }

        val resolvedGender = (doc.getString("gender") ?: base.gender).trim()
        val rawProfileCode = (doc.getString("profileCode") ?: base.profileCode).trim()
        val resolvedCode = when {
            UserProfile.isValidFormattedUserId(rawProfileCode, resolvedGender) -> rawProfileCode.uppercase()
            UserProfile.isValidFormattedUserId(doc.id, resolvedGender) -> doc.id.uppercase()
            sequentialMap[doc.id] != null -> sequentialMap[doc.id]!!
            sequentialMap[base.userId] != null -> sequentialMap[base.userId]!!
            else -> ""
        }
        val effectiveDocId = if (UserProfile.isValidFormattedUserId(resolvedCode, resolvedGender)) {
            resolvedCode
        } else {
            doc.id
        }
        val resolvedPhotoUrl = (doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
            ?: doc.getString("photoURL")?.takeIf { it.isNotBlank() }
            ?: base.photoUrl).trim()

        val resolved = base.copy(
            id = effectiveDocId,
            userId = base.userId.ifBlank { doc.id },
            profileCode = resolvedCode,
            email = docEmail,
            photoUrl = resolvedPhotoUrl,
            isVerified = isVerifiedFlag,
            verificationStatus = resolvedVerifStatus,
            isAdmin = isAdminFlag,
            role = resolvedRole
        ).withWeeklyFreeInterestReset()

        if (doc.reference.parent.id == "users") {
            migrateLegacyDocumentToFormattedIdAsync(doc, resolved)
        }
        return resolved
    }

    private fun toPublicProfileData(profile: UserProfile): MutableMap<String, Any> {
        val data = profile.toMap().toMutableMap()
        listOf(
            "dateOfBirth", "email", "phone", "phoneNumber", "contactPhone",
            "guardianPhone", "candidateMobile", "guardianMobile", "contactEmail",
            "whatsapp", "imo",
            "facebook", "instagram", "presentAddress", "permanentAddress",
            "verificationDocType", "verificationIdNumber", "verificationDocUri",
            "verificationStatus", "fcmToken", "fcm_token", "packageActivatedAt", "packageExpiresAt",
            "pendingPackageTier", "interestsRemaining", "contactsRemaining",
            "savedProfileIds", "partnerAgeMin", "partnerAgeMax", "partnerHeightMin",
            "partnerHeightMax", "partnerMaritalStatuses", "partnerEducation",
            "partnerProfessions", "partnerLocations", "partnerFinancialConditions",
            "partnerComplexions", "partnerIelts", "partnerOverseasCountries",
            "partnerReligion", "partnerProfession", "partnerDistrict",
            "partnerDistricts", "partnerMarriageTimeline", "partnerExpectations"
        ).forEach { key -> data.remove(key) }
        data["role"] = "USER"
        data["isAdmin"] = false
        return data
    }

    fun computeSequentialCodeMap(docs: List<DocumentSnapshot>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val usedSerialsByGender = mutableMapOf(
            "Male" to mutableSetOf<Int>(),
            "Female" to mutableSetOf<Int>()
        )

        val unformattedByGender = mutableMapOf(
            "Male" to mutableListOf<DocumentSnapshot>(),
            "Female" to mutableListOf<DocumentSnapshot>()
        )

        for (doc in docs) {
            val genderRaw = (doc.getString("gender") ?: "").trim()
            val genderKey = if (genderRaw.equals("Female", ignoreCase = true)) "Female" else "Male"
            val explicitCode = (doc.getString("profileCode") ?: "").trim()

            val validCode = when {
                UserProfile.isValidFormattedUserId(explicitCode, genderRaw) -> explicitCode.uppercase()
                UserProfile.isValidFormattedUserId(doc.id, genderRaw) -> doc.id.uppercase()
                else -> null
            }

            if (validCode != null) {
                result[doc.id] = validCode
                doc.getString("userId")?.takeIf { it.isNotBlank() }?.let { result[it] = validCode }
                val serial = UserProfile.extractSerialFromUserId(validCode)
                if (serial != null) {
                    usedSerialsByGender[genderKey]?.add(serial)
                }
            } else {
                unformattedByGender[genderKey]?.add(doc)
            }
        }

        for ((genderKey, list) in unformattedByGender) {
            val sorted = list.sortedWith(
                compareBy<DocumentSnapshot>(
                    { (it.getTimestamp("createdAt")?.toDate()?.time) ?: Long.MAX_VALUE },
                    { it.getString("userId") ?: it.id },
                    { it.id }
                )
            )
            val usedSet = usedSerialsByGender[genderKey] ?: mutableSetOf()
            var nextIndex = (usedSet.maxOrNull() ?: 0) + 1
            for (doc in sorted) {
                while (usedSet.contains(nextIndex)) {
                    nextIndex++
                }
                usedSet.add(nextIndex)
                val code = UserProfile.formatSerialUserId(genderKey, nextIndex)
                result[doc.id] = code
                doc.getString("userId")?.takeIf { it.isNotBlank() }?.let { result[it] = code }
                nextIndex++
            }
        }

        return result
    }

    private fun migrateLegacyDocumentToFormattedIdAsync(
        sourceDoc: DocumentSnapshot,
        resolved: UserProfile
    ) {
        val targetCode = resolved.profileCode.trim()
        if (!UserProfile.isValidFormattedUserId(targetCode, resolved.gender)) return
        val currentDocId = sourceDoc.id
        val storedCode = (sourceDoc.getString("profileCode") ?: "").trim()
        if (currentDocId.equals(targetCode, ignoreCase = true) &&
            storedCode.equals(targetCode, ignoreCase = true)
        ) {
            return
        }

        migrationScope.launch {
            runCatching {
                val payload = resolved.copy(id = targetCode, profileCode = targetCode).toMap().toMutableMap()
                payload["profileCode"] = targetCode
                payload["updatedAt"] = sourceDoc.getTimestamp("updatedAt") ?: FieldValue.serverTimestamp()
                payload["createdAt"] = sourceDoc.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()

                if (!currentDocId.equals(targetCode, ignoreCase = true)) {
                    val targetSnap = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                        usersCol.document(targetCode).get().await()
                    }
                    val targetOwner = targetSnap?.getString("userId").orEmpty()
                    if (targetSnap == null || !targetSnap.exists() ||
                        targetOwner.isBlank() || targetOwner == resolved.userId
                    ) {
                        usersCol.document(targetCode).set(payload, SetOptions.merge()).await()
                        publicProfilesCol.document(targetCode).set(
                            toPublicProfileData(resolved.copy(id = targetCode, profileCode = targetCode)),
                            SetOptions.merge()
                        ).await()
                        usersCol.document(currentDocId).delete().await()
                        publicProfilesCol.document(currentDocId).delete().await()
                    }
                } else {
                    usersCol.document(targetCode).set(
                        mapOf("profileCode" to targetCode),
                        SetOptions.merge()
                    ).await()
                    publicProfilesCol.document(targetCode).set(
                        toPublicProfileData(resolved.copy(id = targetCode, profileCode = targetCode)),
                        SetOptions.merge()
                    ).await()
                }
            }
        }
    }

    private suspend fun findUserDocumentSnapshot(userIdOrDocId: String): DocumentSnapshot? {
        if (userIdOrDocId.isBlank()) return null

        val cached = localCache.getMemoryProfile(userIdOrDocId)
        if (cached != null) {
            val candidateIds = listOf(cached.profileCode, cached.id)
                .filter { it.isNotBlank() && it != userIdOrDocId }
                .distinct()
            for (cid in candidateIds) {
                val snap = runCatching {
                    withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) { usersCol.document(cid).get().await() }
                }.getOrNull()
                if (snap != null && snap.exists()) return snap
            }
        }

        val directDoc = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                usersCol.document(userIdOrDocId).get().await()
            }
        }.getOrNull()
        if (directDoc != null && directDoc.exists()) {
            return directDoc
        }

        val querySnap = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                usersCol.whereEqualTo("userId", userIdOrDocId).limit(1).get().await()
            }
        }.getOrNull()
        val firstMatch = querySnap?.documents?.firstOrNull()
        if (firstMatch != null && firstMatch.exists()) {
            return firstMatch
        }

        val codeSnap = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                usersCol.whereEqualTo("profileCode", userIdOrDocId.uppercase()).limit(1).get().await()
            }
        }.getOrNull()
        codeSnap?.documents?.firstOrNull()?.let { return it }

        val publicDoc = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                publicProfilesCol.document(userIdOrDocId).get().await()
            }
        }.getOrNull()
        if (publicDoc != null && publicDoc.exists()) return publicDoc

        val publicByUserId = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                publicProfilesCol.whereEqualTo("userId", userIdOrDocId).limit(1).get().await()
            }
        }.getOrNull()?.documents?.firstOrNull()
        if (publicByUserId != null) return publicByUserId

        val publicByCode = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                publicProfilesCol.whereEqualTo("profileCode", userIdOrDocId.uppercase()).limit(1).get().await()
            }
        }.getOrNull()
        return publicByCode?.documents?.firstOrNull()
    }

    private suspend fun resolveDocIdForUser(userIdOrDocId: String, fallbackGender: String = "Male"): String {
        val snap = findUserDocumentSnapshot(userIdOrDocId)
        if (snap != null && snap.exists()) {
            val gender = (snap.getString("gender") ?: fallbackGender).trim()
            val storedCode = (snap.getString("profileCode") ?: "").trim()
            if (UserProfile.isValidFormattedUserId(snap.id, gender)) return snap.id
            if (UserProfile.isValidFormattedUserId(storedCode, gender)) return storedCode
            return snap.id
        }
        return if (UserProfile.isValidFormattedUserId(userIdOrDocId)) {
            userIdOrDocId.uppercase()
        } else {
            resolveSequentialProfileCode(userIdOrDocId, fallbackGender)
        }
    }

    private suspend fun resolveSequentialProfileCode(
        userId: String,
        gender: String,
        existingCode: String = ""
    ): String {
        val normalizedGender = if (gender.trim().equals("Female", ignoreCase = true)) "Female" else "Male"
        if (UserProfile.isValidFormattedUserId(existingCode, normalizedGender)) {
            return existingCode.trim().uppercase()
        }
        if (UserProfile.isValidFormattedUserId(userId, normalizedGender)) {
            return userId.trim().uppercase()
        }

        val allDocs = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                publicProfilesCol.get().await().documents
            }
        }.getOrNull().orEmpty()

        val existingDoc = allDocs.firstOrNull {
            it.id == userId || it.getString("userId") == userId
        }
        if (existingDoc != null) {
            val docCode = (existingDoc.getString("profileCode") ?: "").trim()
            if (UserProfile.isValidFormattedUserId(docCode, normalizedGender)) return docCode.uppercase()
            if (UserProfile.isValidFormattedUserId(existingDoc.id, normalizedGender)) return existingDoc.id.uppercase()
            val seqMap = computeSequentialCodeMap(allDocs)
            seqMap[existingDoc.id]?.let { if (UserProfile.isValidFormattedUserId(it, normalizedGender)) return it }
            seqMap[userId]?.let { if (UserProfile.isValidFormattedUserId(it, normalizedGender)) return it }
        }

        val usedSerials: List<Int> = allDocs.mapNotNull { doc ->
            val docGender = (doc.getString("gender") ?: "").trim()
            val isSameGender = docGender.equals(normalizedGender, ignoreCase = true)
            if (!isSameGender) return@mapNotNull null
            val code = (doc.getString("profileCode") ?: "").trim().ifBlank { doc.id }
            UserProfile.extractSerialFromUserId(code)
        }
        val maxUsedSerial = usedSerials.maxOrNull() ?: allDocs.count {
            (it.getString("gender") ?: "").trim().equals(normalizedGender, ignoreCase = true)
        }

        var candidateIndex = maxUsedSerial + 1
        val existingIds = allDocs.flatMap {
            listOf(it.id.uppercase(), (it.getString("profileCode") ?: "").trim().uppercase())
        }.toSet()
        while (existingIds.contains(UserProfile.formatSerialUserId(normalizedGender, candidateIndex).uppercase())) {
            candidateIndex++
        }
        return UserProfile.formatSerialUserId(normalizedGender, candidateIndex)
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        return try {
            val doc = findUserDocumentSnapshot(userId)
            val remoteProfile = if (doc != null && doc.exists()) {
                val gender = (doc.getString("gender") ?: "").trim()
                val rawCode = (doc.getString("profileCode") ?: "").trim()
                val seqMap = if (!UserProfile.isValidFormattedUserId(rawCode, gender) &&
                    !UserProfile.isValidFormattedUserId(doc.id, gender)
                ) {
                    val assignedCode = resolveSequentialProfileCode(
                        userId = doc.getString("userId") ?: userId,
                        gender = gender,
                        existingCode = rawCode
                    )
                    mapOf(doc.id to assignedCode, userId to assignedCode)
                } else {
                    emptyMap()
                }
                parseProfileSnapshot(doc, seqMap)
            } else {
                null
            }

            if (remoteProfile != null) {
                localCache.cacheResolvedProfile(userId, remoteProfile)
            } else {
                localCache.getCachedProfileForUser(userId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore getUserProfile failed for $userId: ${e.message}")
            localCache.getCachedProfileForUser(userId)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        localCache.getCachedProfileForUser(userId)?.let { cached ->
            trySend(cached)
        }

        val profileCollection = if (FirebaseAuth.getInstance().currentUser?.uid == userId) {
            usersCol
        } else {
            publicProfilesCol
        }
        val byFieldListener = profileCollection.whereEqualTo("userId", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val doc = snap?.documents?.firstOrNull()
                if (doc != null && doc.exists()) {
                    val parsed = parseProfileSnapshot(doc)
                    if (parsed != null) {
                        trySend(localCache.cacheResolvedProfile(userId, parsed))
                    }
                }
            }

        val byDocListener = profileCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val parsed = parseProfileSnapshot(snapshot)
                    if (parsed != null) {
                        trySend(localCache.cacheResolvedProfile(userId, parsed))
                    }
                }
            }

        awaitClose {
            byFieldListener.remove()
            byDocListener.remove()
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<UserProfile> = runCatching {
        val currentWeek = UserProfile.getCurrentWeekKey()
        val resolvedAccountStatus = profile.accountStatus.ifBlank { "ACTIVE" }
        val formattedDocId = resolveSequentialProfileCode(
            userId = profile.userId.ifBlank { profile.id },
            gender = profile.gender,
            existingCode = profile.profileCode.ifBlank { profile.id }
        )
        val normalizedProfile = profile.copy(
            id = formattedDocId,
            profileCode = formattedDocId,
            freeInterestWeekKey = profile.freeInterestWeekKey.ifBlank { currentWeek },
            accountStatus = resolvedAccountStatus
        )
        val existingSnap = findUserDocumentSnapshot(profile.userId.ifBlank { formattedDocId })
        val isExistingDoc = existingSnap != null && existingSnap.exists()

        val data = normalizedProfile.toMap().toMutableMap()
        data["profileCode"] = formattedDocId
        data["updatedAt"] = FieldValue.serverTimestamp()
        if (!isExistingDoc) {
            data["createdAt"] = FieldValue.serverTimestamp()
        } else {
            existingSnap?.getTimestamp("createdAt")?.let { data["createdAt"] = it }
        }

        localCache.cacheResolvedProfile(normalizedProfile.userId, normalizedProfile)

        withTimeout(FIRESTORE_TIMEOUT_MS) {
            usersCol.document(formattedDocId).set(data, SetOptions.merge()).await()
            publicProfilesCol.document(formattedDocId).set(
                toPublicProfileData(normalizedProfile).apply {
                    put("profileCode", formattedDocId)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (!isExistingDoc) {
                        put("createdAt", FieldValue.serverTimestamp())
                    } else {
                        existingSnap?.getTimestamp("createdAt")?.let { put("createdAt", it) }
                    }
                },
                SetOptions.merge()
            ).await()
            if (existingSnap != null && existingSnap.exists() &&
                !existingSnap.id.equals(formattedDocId, ignoreCase = true)
            ) {
                runCatching { usersCol.document(existingSnap.id).delete().await() }
                runCatching { publicProfilesCol.document(existingSnap.id).delete().await() }
            }
        }
        clearOnboardingDraft(normalizedProfile.userId)
        normalizedProfile
    }

    suspend fun saveOnboardingDraft(userId: String, draft: OnboardingDraft, step: Int): Result<Unit> = runCatching {
        if (userId.isBlank()) return@runCatching
        localCache.saveDraftToPrefs(userId, draft, step)
        val draftMap = mapOf(
            "userId" to userId,
            "draftJson" to draft.toJson(),
            "currentStep" to step.coerceIn(0, 6),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            draftsCol.document(userId).set(draftMap, SetOptions.merge()).await()
        }
    }

    suspend fun getOnboardingDraft(userId: String): Pair<OnboardingDraft, Int>? {
        if (userId.isBlank()) return null
        val localDraft = localCache.loadDraftFromPrefs(userId)
        val remoteSnap = runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                draftsCol.document(userId).get().await()
            }
        }.getOrNull()

        if (remoteSnap != null && remoteSnap.exists()) {
            val step = (remoteSnap.getLong("currentStep")?.toInt() ?: 0).coerceIn(0, 6)
            val draftJsonStr = remoteSnap.getString("draftJson").orEmpty()
            if (draftJsonStr.isNotBlank()) {
                val remoteDraft = OnboardingDraft.fromJson(draftJsonStr)
                localCache.saveDraftToPrefs(userId, remoteDraft, step)
                return remoteDraft to step
            }
        }
        return localDraft
    }

    suspend fun clearOnboardingDraft(userId: String) {
        if (userId.isBlank()) return
        localCache.clearDraftFromPrefs(userId)
        runCatching {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                draftsCol.document(userId).delete().await()
            }
        }
    }

    fun observeCandidates(): Flow<List<UserProfile>> = callbackFlow {
        val listener = publicProfilesCol.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeCandidates error: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            val rawDocs = snapshot?.documents.orEmpty()
            val seqMap = computeSequentialCodeMap(rawDocs)
            val profiles = rawDocs.mapNotNull { doc ->
                parseProfileSnapshot(doc, seqMap)
            }.filter { profile ->
                val statusActive = profile.accountStatus.equals("ACTIVE", ignoreCase = true) ||
                    profile.accountStatus.isBlank()
                statusActive && profile.displayName.isNotBlank()
            }
            profiles.forEach { p ->
                localCache.putMemoryProfile(p.userId, p)
                if (p.id.isNotBlank()) localCache.putMemoryProfile(p.id, p)
                if (p.profileCode.isNotBlank()) localCache.putMemoryProfile(p.profileCode, p)
            }
            trySend(profiles)
        }
        awaitClose { listener.remove() }
    }

    fun observeNotificationsAndBroadcasts(): Flow<List<AdminNotification>> = callbackFlow {
        val listener = notificationsCol.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                val n = runCatching { doc.toObject(AdminNotification::class.java) }.getOrNull() ?: return@mapNotNull null
                n.copy(id = doc.id)
            }?.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun toggleSaveProfile(currentUserId: String, targetUserId: String): Result<List<String>> = runCatching {
        val profile = getUserProfile(currentUserId) ?: UserProfile(userId = currentUserId)
        val currentList = profile.savedProfileIds.toMutableList()
        if (currentList.contains(targetUserId)) {
            currentList.remove(targetUserId)
        } else {
            currentList.add(targetUserId)
        }
        val updatedProfile = profile.copy(savedProfileIds = currentList)
        localCache.cacheResolvedProfile(currentUserId, updatedProfile)
        val docId = resolveDocIdForUser(currentUserId, profile.gender)
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            usersCol.document(docId).set(
                mapOf(
                    "userId" to currentUserId,
                    "profileCode" to docId,
                    "savedProfileIds" to currentList,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        currentList
    }

    suspend fun sendInterest(
        fromProfile: UserProfile,
        toProfile: UserProfile
    ): Result<Interest> = runCatching {
        val refreshedSender = fromProfile.withWeeklyFreeInterestReset()
        if (refreshedSender.interestsRemaining <= 0) {
            throw IllegalStateException("No interests remaining in your current package.")
        }

        val interestId = "int_${refreshedSender.userId}_${toProfile.userId}"
        val matchScore = SampleData.calculateCompatibility(refreshedSender, toProfile)
        val interest = Interest(
            id = interestId,
            interestId = interestId,
            senderId = refreshedSender.userId,
            receiverId = toProfile.userId,
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
        localCache.cacheResolvedProfile(refreshedSender.userId, updatedSender)

        val senderDocId = resolveDocIdForUser(refreshedSender.userId, refreshedSender.gender)
        val interestData = mapOf(
            "id" to interest.id,
            "interestId" to interest.interestId,
            "senderId" to interest.senderId,
            "receiverId" to interest.receiverId,
            "senderName" to interest.senderName,
            "senderPhoto" to interest.senderPhoto,
            "senderAge" to interest.senderAge,
            "senderUpazila" to interest.senderUpazila,
            "senderProfession" to interest.senderProfession,
            "senderVerified" to interest.senderVerified,
            "receiverName" to interest.receiverName,
            "receiverPhoto" to interest.receiverPhoto,
            "receiverAge" to interest.receiverAge,
            "receiverUpazila" to interest.receiverUpazila,
            "receiverProfession" to interest.receiverProfession,
            "receiverVerified" to interest.receiverVerified,
            "matchScore" to interest.matchScore,
            "status" to interest.status,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            val batch = db.batch()
            val interestRef = interestsCol.document(interestId)
            val userRef = usersCol.document(senderDocId)
            batch.set(interestRef, interestData)
            batch.set(
                userRef,
                mapOf(
                    "userId" to refreshedSender.userId,
                    "profileCode" to senderDocId,
                    "interestsRemaining" to newRemaining,
                    "freeInterestWeekKey" to refreshedSender.freeInterestWeekKey.ifBlank { UserProfile.getCurrentWeekKey() },
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            batch.commit().await()
        }
        interest
    }

    suspend fun respondToInterest(interest: Interest, accept: Boolean): Result<String?> = runCatching {
        val newStatus = if (accept) "accepted" else "declined"
        val sortedIds = listOf(interest.senderId, interest.receiverId).sorted()
        val chatId = if (accept) "chat_${sortedIds[0]}_${sortedIds[1]}" else null

        withTimeout(FIRESTORE_TIMEOUT_MS) {
            val docId = interest.id.ifBlank { interest.interestId }
            interestsCol.document(docId).set(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            if (accept && chatId != null) {
                val threadData = mapOf<String, Any>(
                    "id" to chatId,
                    "chatId" to chatId,
                    "interestId" to interest.id.ifBlank { interest.interestId },
                    "participantIds" to sortedIds,
                    "otherUserName" to interest.senderName,
                    "otherUserPhoto" to interest.senderPhoto,
                    "lastMessage" to "Interest Accepted! Say Assalamu Alaikum.",
                    "lastMessageSenderId" to interest.receiverId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                chatsCol.document(chatId).set(threadData, SetOptions.merge()).await()
            }
        }
        chatId
    }

    fun observeReceivedInterests(userId: String): Flow<List<Interest>> = callbackFlow {
        val listener = interestsCol
            .whereEqualTo("receiverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Interest::class.java)?.copy(id = doc.id, interestId = doc.id)
                }?.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeSentInterests(userId: String): Flow<List<Interest>> = callbackFlow {
        val listener = interestsCol
            .whereEqualTo("senderId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Interest::class.java)?.copy(id = doc.id, interestId = doc.id)
                }?.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> = callbackFlow {
        val listener = chatsCol
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val threads = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatThread::class.java)?.copy(id = doc.id, chatId = doc.id)
                }?.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L } ?: emptyList()
                trySend(threads)
            }
        awaitClose { listener.remove() }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = chatsCol.document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id, messageId = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(
        chatId: String,
        senderProfile: UserProfile,
        otherUserId: String,
        text: String
    ): Result<ChatMessage> = runCatching {
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
        val msgData = mapOf(
            "id" to msgId,
            "messageId" to msgId,
            "senderId" to senderProfile.userId,
            "senderName" to senderProfile.displayName,
            "text" to text,
            "participantIds" to participants,
            "createdAt" to FieldValue.serverTimestamp()
        )
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            val messageRef = chatsCol.document(chatId).collection("messages").document(msgId)
            val threadRef = chatsCol.document(chatId)
            val batch = db.batch()
            batch.set(messageRef, msgData)
            batch.set(
                threadRef,
                mapOf(
                    "id" to chatId,
                    "chatId" to chatId,
                    "participantIds" to participants,
                    "lastMessage" to text,
                    "lastMessageSenderId" to senderProfile.userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            batch.commit().await()
        }
        message
    }

    suspend fun ensureChatThread(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<ChatThread> = runCatching {
        val acceptedByCurrentUser = interestsCol
            .whereEqualTo("senderId", currentUser.userId)
            .limit(100)
            .get()
            .await()
            .documents
            .firstOrNull {
                it.getString("receiverId") == otherUser.userId &&
                    it.getString("status").equals("accepted", ignoreCase = true)
            }
        val acceptedByOtherUser = if (acceptedByCurrentUser == null) {
            interestsCol
                .whereEqualTo("senderId", otherUser.userId)
                .limit(100)
                .get()
                .await()
                .documents
                .firstOrNull {
                    it.getString("receiverId") == currentUser.userId &&
                        it.getString("status").equals("accepted", ignoreCase = true)
                }
        } else {
            null
        }
        val acceptedInterest = acceptedByCurrentUser ?: acceptedByOtherUser
            ?: throw IllegalStateException("Accept this member's proposal before starting a chat.")
        val sortedIds = listOf(currentUser.userId, otherUser.userId).sorted()
        val chatId = "chat_${sortedIds[0]}_${sortedIds[1]}"
        val thread = ChatThread(
            id = chatId,
            chatId = chatId,
            interestId = acceptedInterest.id,
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
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            val docRef = chatsCol.document(chatId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val threadData = mapOf(
                    "id" to chatId,
                    "chatId" to chatId,
                    "interestId" to acceptedInterest.id,
                    "participantIds" to sortedIds,
                    "otherUserName" to otherUser.displayName,
                    "otherUserNameBangla" to otherUser.displayNameBangla,
                    "otherUserFormattedId" to otherUser.getFormattedUserId(),
                    "otherUserPhoto" to otherUser.photoUrl,
                    "lastMessage" to "",
                    "lastMessageSenderId" to currentUser.userId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(threadData, SetOptions.merge()).await()
            }
        }
        thread
    }

    suspend fun submitPaymentRequest(
        profile: UserProfile,
        pkg: MembershipPackage,
        paymentMethod: String,
        senderPhone: String,
        transactionId: String
    ): Result<String> = runCatching {
        val docId = resolveDocIdForUser(profile.userId.ifBlank { profile.id }, profile.gender)
        val requestId = "${docId}_${transactionId.uppercase().take(12).ifBlank { UUID.randomUUID().toString().take(8) }}"
        val requestData = mapOf(
            "id" to requestId,
            "requestId" to requestId,
            "userId" to profile.userId.ifBlank { docId },
            "profileCode" to docId,
            "displayName" to profile.displayName,
            "packageId" to pkg.productId,
            "packageTier" to pkg.tier,
            "packageName" to pkg.nameEn,
            "amountBdt" to pkg.priceBdt,
            "paymentMethod" to paymentMethod,
            "senderPhone" to senderPhone,
            "transactionId" to transactionId,
            "interestsToCredit" to pkg.interestsCount,
            "contactsToCredit" to pkg.contactsCount,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val updatedProfile = profile.copy(pendingPackageTier = pkg.tier)
        localCache.cacheResolvedProfile(profile.userId.ifBlank { docId }, updatedProfile)

        withTimeout(FIRESTORE_TIMEOUT_MS) {
            paymentRequestsCol.document(requestId).set(requestData, SetOptions.merge()).await()
            usersCol.document(docId).set(
                mapOf(
                    "userId" to profile.userId.ifBlank { docId },
                    "profileCode" to docId,
                    "pendingPackageTier" to pkg.tier,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        requestId
    }

    suspend fun submitVerificationRequest(
        userId: String,
        docType: String,
        idNumber: String,
        docUri: String = ""
    ): Result<Unit> = runCatching {
        val current = getUserProfile(userId) ?: UserProfile(userId = userId)
        val docId = resolveDocIdForUser(userId, current.gender)
        val updated = current.copy(
            id = docId,
            profileCode = docId,
            verificationStatus = "PENDING",
            verificationDocType = docType,
            verificationIdNumber = idNumber,
            verificationDocUri = docUri
        )
        localCache.cacheResolvedProfile(userId, updated)
        val verificationRequestData = mapOf(
            "id" to docId,
            "requestId" to docId,
            "userId" to userId,
            "profileCode" to docId,
            "displayName" to updated.displayName,
            "gender" to updated.gender,
            "docType" to docType,
            "idNumber" to idNumber,
            "docUrl" to docUri,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            verificationRequestsCol.document(docId).set(verificationRequestData, SetOptions.merge()).await()
            usersCol.document(docId).set(
                mapOf(
                    "userId" to userId,
                    "profileCode" to docId,
                    "verificationStatus" to "PENDING",
                    "verificationDocType" to docType,
                    "verificationIdNumber" to idNumber,
                    "verificationDocUri" to docUri,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun purchasePackage(userId: String, pkg: MembershipPackage): Result<UserProfile> = runCatching {
        val current = getUserProfile(userId) ?: UserProfile(userId = userId)
        val docId = resolveDocIdForUser(userId, current.gender)
        val nowMs = System.currentTimeMillis()
        val activatedAtMs = if (pkg.tier == "Free") 0L else nowMs
        val expiresAtMs = if (pkg.tier == "Free") 0L else pkg.computeExpiryMillis(nowMs)
        val updated = current.copy(
            id = docId,
            profileCode = docId,
            packageTier = pkg.tier,
            pendingPackageTier = "",
            packageActivatedAt = activatedAtMs,
            packageExpiresAt = expiresAtMs,
            interestsRemaining = pkg.interestsCount,
            contactsRemaining = pkg.contactsCount,
            isVerified = current.isVerified || pkg.hasVerifiedBadge
        )
        localCache.cacheResolvedProfile(userId, updated)
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            usersCol.document(docId).set(
                mapOf(
                    "userId" to userId,
                    "profileCode" to docId,
                    "packageTier" to pkg.tier,
                    "pendingPackageTier" to "",
                    "packageActivatedAt" to activatedAtMs,
                    "packageExpiresAt" to expiresAtMs,
                    "interestsRemaining" to pkg.interestsCount,
                    "contactsRemaining" to pkg.contactsCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        updated
    }

    suspend fun adminChangeUserPackage(
        targetUserIdOrCode: String,
        newTier: String,
        customInterests: Int? = null,
        customContacts: Int? = null,
        customDurationDays: Int? = null
    ): Result<UserProfile> = runCatching {
        val pkg = MembershipPackages.byTier(newTier)
        val current = getUserProfile(targetUserIdOrCode) ?: UserProfile(userId = targetUserIdOrCode)
        val docId = resolveDocIdForUser(targetUserIdOrCode, current.gender)
        val resolvedInterests = customInterests ?: pkg.interestsCount
        val resolvedContacts = customContacts ?: pkg.contactsCount
        val nowMs = System.currentTimeMillis()
        val resolvedDays = if (pkg.tier == "Free") 0 else (customDurationDays ?: pkg.durationDays)
        val activatedAtMs = if (pkg.tier == "Free") 0L else nowMs
        val expiresAtMs = if (pkg.tier == "Free") 0L else pkg.computeExpiryMillis(nowMs, resolvedDays)
        val updated = current.copy(
            id = docId,
            profileCode = docId,
            packageTier = pkg.tier,
            pendingPackageTier = "",
            packageActivatedAt = activatedAtMs,
            packageExpiresAt = expiresAtMs,
            interestsRemaining = resolvedInterests,
            contactsRemaining = resolvedContacts,
            isVerified = current.isVerified || pkg.hasVerifiedBadge
        )
        localCache.cacheResolvedProfile(updated.userId.ifBlank { targetUserIdOrCode }, updated)
        withTimeout(FIRESTORE_TIMEOUT_MS) {
            usersCol.document(docId).set(
                mapOf(
                    "packageTier" to pkg.tier,
                    "pendingPackageTier" to "",
                    "packageActivatedAt" to activatedAtMs,
                    "packageExpiresAt" to expiresAtMs,
                    "interestsRemaining" to resolvedInterests,
                    "contactsRemaining" to resolvedContacts,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            publicProfilesCol.document(docId).set(
                mapOf(
                    "packageTier" to pkg.tier,
                    "isVerified" to updated.isVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        updated
    }

    companion object {
        private const val TAG = "FirestoreMatrimonyDS"
        const val FIRESTORE_TIMEOUT_MS = 15_000L
    }
}
