package com.example.ui.navigation

import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.example.data.model.AdminNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.UserProfile
import com.example.ui.search.SearchFilterState
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BottomNavTab(
    val titleEn: String,
    val titleBn: String,
    val icon: ImageVector,
    val tag: String
) {
    HOME("Matches", "ম্যাচ", Icons.Default.Home, "nav_home"),
    PROPOSALS("Proposals", "প্রস্তাব", Icons.Default.Favorite, "nav_proposals"),
    CHATS("Chats", "চ্যাট", Icons.AutoMirrored.Filled.Chat, "nav_chats"),
    SAVED("Saved", "সেভ", Icons.Default.Bookmark, "nav_saved"),
    ME("Me", "প্রোফাইল", Icons.Default.Person, "nav_me"),
    PACKAGES("Packages", "প্যাকেজ", Icons.Default.WorkspacePremium, "nav_packages");

    companion object {
        val bottomBarTabs: List<BottomNavTab> = listOf(HOME, PROPOSALS, CHATS, SAVED, ME)
    }
}

data class MainNavigationUiState(
    val activeUserId: String = "",
    val activeUserProfile: UserProfile? = null,
    val checkedProfileLoad: Boolean = false,
    val selectedTab: BottomNavTab = BottomNavTab.HOME,
    val tabBackStack: List<BottomNavTab> = emptyList(),
    val selectedProfile: UserProfile? = null,
    val activeChatThread: ChatThread? = null,
    val showFilterScreen: Boolean = false,
    val showVerificationDialog: Boolean = false,
    val showNotificationsSheet: Boolean = false,
    val showEditWizard: Boolean = false,
    val showChangePhotoDialog: Boolean = false,
    val isUploadingProfilePhoto: Boolean = false,
    val searchQuery: String = "",
    val filterState: SearchFilterState = SearchFilterState(),
    val unlockedContactIds: Set<String> = emptySet(),
    val optimisticMessagesByChat: Map<String, List<ChatMessage>> = emptyMap(),
    val lastSeenTimeByChat: Map<String, Long> = emptyMap(),
    val lastSeenSigByChat: Map<String, String> = emptyMap(),
    val notificationsMarkedRead: Boolean = false,
    val lastNotifReadTime: Long = 0L,
    val readNotificationIds: Set<String> = emptySet(),
    val dismissedBannerIds: Set<String> = emptySet(),
    val localSavedIdsOverride: List<String>? = null,
    val localInterestsOverride: Int? = null,
    val localContactsOverride: Int? = null,
    val localPackageTierOverride: String? = null,
    val localPendingPackageTierOverride: String? = null,
    val localPackageActivatedAtOverride: Long? = null,
    val localPackageExpiresAtOverride: Long? = null,
    val localVerificationStatusOverride: String? = null,
    val localVerificationDocTypeOverride: String? = null,
    val localVerificationIdNumberOverride: String? = null,
    val localVerificationDocUriOverride: String? = null,
    val localPhotoUrlOverride: String? = null,
    val localAccountStatusOverride: String? = null,
    val localEmailOverride: String? = null
)

/**
 * ViewModel + StateFlow managing root navigation, filter state, optimistic UI overrides,
 * chat read receipts, and notification read/banner dismissal state.
 * Extracted from AppNavigation to eliminate God-composable state sprawl.
 */
class MainNavigationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainNavigationUiState())
    val uiState: StateFlow<MainNavigationUiState> = _uiState.asStateFlow()

    fun resetForSignOut() {
        _uiState.value = MainNavigationUiState()
    }

    fun onActiveUserChanged(
        userId: String,
        cachedProfile: UserProfile?,
        notifPrefs: SharedPreferences? = null
    ) {
        val current = _uiState.value
        if (current.activeUserId == userId) return
        val readKey = userId.ifBlank { "guest" }
        val lastReadTime = notifPrefs?.getLong("last_read_ts_$readKey", 0L) ?: 0L
        val readIds = notifPrefs?.getStringSet("read_ids_$readKey", emptySet())?.toSet() ?: emptySet()
        val dismissedIds = notifPrefs?.getStringSet("dismissed_banner_ids_$readKey", emptySet())?.toSet() ?: emptySet()

        _uiState.value = MainNavigationUiState(
            activeUserId = userId,
            activeUserProfile = cachedProfile,
            checkedProfileLoad = cachedProfile != null || userId.isBlank(),
            lastNotifReadTime = lastReadTime,
            readNotificationIds = readIds,
            dismissedBannerIds = dismissedIds
        )
    }

    fun setActiveUserProfile(profile: UserProfile?, checked: Boolean = true) {
        _uiState.update { state ->
            val prevProfile = state.activeUserProfile
            val verifConfirmedOrChangedByAdmin = profile != null && state.localVerificationStatusOverride != null && (
                profile.verificationStatus.equals(state.localVerificationStatusOverride, ignoreCase = true) ||
                    profile.isVerified ||
                    profile.verificationStatus.equals("VERIFIED", ignoreCase = true) ||
                    profile.verificationStatus.equals("REJECTED", ignoreCase = true) ||
                    (prevProfile != null && !profile.verificationStatus.equals(prevProfile.verificationStatus, ignoreCase = true))
                )
            val tierConfirmedOrChanged = profile != null && state.localPackageTierOverride != null && (
                profile.packageTier.equals(state.localPackageTierOverride, ignoreCase = true) ||
                    (prevProfile != null && !profile.packageTier.equals(prevProfile.packageTier, ignoreCase = true))
                )
            val pendingTierConfirmedOrClearedByAdmin = profile != null && state.localPendingPackageTierOverride != null && (
                profile.pendingPackageTier.equals(state.localPendingPackageTierOverride, ignoreCase = true) ||
                    profile.packageTier.equals(state.localPendingPackageTierOverride, ignoreCase = true) ||
                    (prevProfile != null && profile.pendingPackageTier != prevProfile.pendingPackageTier)
                )
            val interestsConfirmedOrChanged = profile != null && state.localInterestsOverride != null && (
                profile.interestsRemaining == state.localInterestsOverride ||
                    (prevProfile != null && profile.interestsRemaining != prevProfile.interestsRemaining)
                )
            val contactsConfirmedOrChanged = profile != null && state.localContactsOverride != null && (
                profile.contactsRemaining == state.localContactsOverride ||
                    (prevProfile != null && profile.contactsRemaining != prevProfile.contactsRemaining)
                )
            val photoConfirmedOrChanged = profile != null && state.localPhotoUrlOverride != null && (
                profile.photoUrl == state.localPhotoUrlOverride ||
                    (prevProfile != null && profile.photoUrl != prevProfile.photoUrl && profile.photoUrl.isNotBlank())
                )
            val accountStatusConfirmedOrChanged = profile != null && state.localAccountStatusOverride != null && (
                profile.accountStatus.equals(state.localAccountStatusOverride, ignoreCase = true) ||
                    (prevProfile != null && !profile.accountStatus.equals(prevProfile.accountStatus, ignoreCase = true))
                )
            val emailConfirmedOrChanged = profile != null && state.localEmailOverride != null && (
                profile.email.equals(state.localEmailOverride, ignoreCase = true) ||
                    (prevProfile != null && !profile.email.equals(prevProfile.email, ignoreCase = true) && profile.email.isNotBlank())
                )
            val savedIdsConfirmed = profile != null && state.localSavedIdsOverride != null &&
                profile.savedProfileIds == state.localSavedIdsOverride

            state.copy(
                activeUserProfile = profile,
                checkedProfileLoad = checked,
                localPhotoUrlOverride = if (photoConfirmedOrChanged) null else state.localPhotoUrlOverride,
                localPackageTierOverride = if (tierConfirmedOrChanged) null else state.localPackageTierOverride,
                localPendingPackageTierOverride = if (pendingTierConfirmedOrClearedByAdmin) null else state.localPendingPackageTierOverride,
                localPackageActivatedAtOverride = if (tierConfirmedOrChanged) null else state.localPackageActivatedAtOverride,
                localPackageExpiresAtOverride = if (tierConfirmedOrChanged) null else state.localPackageExpiresAtOverride,
                localInterestsOverride = if (interestsConfirmedOrChanged) null else state.localInterestsOverride,
                localContactsOverride = if (contactsConfirmedOrChanged) null else state.localContactsOverride,
                localVerificationStatusOverride = if (verifConfirmedOrChangedByAdmin) null else state.localVerificationStatusOverride,
                localVerificationDocTypeOverride = if (verifConfirmedOrChangedByAdmin) null else state.localVerificationDocTypeOverride,
                localVerificationIdNumberOverride = if (verifConfirmedOrChangedByAdmin) null else state.localVerificationIdNumberOverride,
                localVerificationDocUriOverride = if (verifConfirmedOrChangedByAdmin) null else state.localVerificationDocUriOverride,
                localAccountStatusOverride = if (accountStatusConfirmedOrChanged) null else state.localAccountStatusOverride,
                localEmailOverride = if (emailConfirmedOrChanged) null else state.localEmailOverride,
                localSavedIdsOverride = if (savedIdsConfirmed) null else state.localSavedIdsOverride
            )
        }
    }

    fun navigateToTab(target: BottomNavTab) {
        _uiState.update { state ->
            val updatedStack = if (target != state.selectedTab) {
                (state.tabBackStack + state.selectedTab).takeLast(10)
            } else {
                state.tabBackStack
            }
            state.copy(
                selectedTab = target,
                tabBackStack = updatedStack,
                selectedProfile = null,
                activeChatThread = null,
                showFilterScreen = false
            )
        }
    }

    fun handlePushNotificationAction(type: String, actionRoute: String) {
        val route = actionRoute.trim().uppercase()
        val notifType = type.trim().uppercase()
        when {
            route == "INTERESTS" || route == "PROPOSALS" ||
                notifType == "INTEREST_RECEIVED" || notifType == "INTEREST_ACCEPTED" -> {
                navigateToTab(BottomNavTab.PROPOSALS)
            }
            route == "CHATS" || route == "CHAT" || notifType == "CHAT_MESSAGE" -> {
                navigateToTab(BottomNavTab.CHATS)
            }
            route == "PACKAGES" || notifType == "PACKAGE_APPROVED" ||
                notifType == "PACKAGE_REJECTED" || notifType == "DISCOUNT" -> {
                navigateToTab(BottomNavTab.PACKAGES)
            }
            route == "VERIFY" || notifType == "KYC_REJECTED" -> {
                navigateToTab(BottomNavTab.ME)
                setShowVerificationDialog(true)
            }
            route == "PROFILE" || notifType == "VERIFICATION_APPROVED" || notifType == "KYC_APPROVED" -> {
                navigateToTab(BottomNavTab.ME)
            }
            route == "MATCHES" || notifType == "MATCH_FOUND" -> {
                navigateToTab(BottomNavTab.HOME)
            }
            else -> {
                setShowNotificationsSheet(true)
            }
        }
    }

    fun popBackStack(): Boolean {
        val state = _uiState.value
        return when {
            state.showEditWizard -> {
                _uiState.update { it.copy(showEditWizard = false) }
                true
            }
            state.showFilterScreen -> {
                _uiState.update { it.copy(showFilterScreen = false) }
                true
            }
            state.activeChatThread != null -> {
                _uiState.update { it.copy(activeChatThread = null) }
                true
            }
            state.selectedProfile != null -> {
                _uiState.update { it.copy(selectedProfile = null) }
                true
            }
            state.tabBackStack.isNotEmpty() -> {
                val previous = state.tabBackStack.last()
                _uiState.update {
                    it.copy(
                        selectedTab = previous,
                        tabBackStack = it.tabBackStack.dropLast(1)
                    )
                }
                true
            }
            state.selectedTab != BottomNavTab.HOME -> {
                _uiState.update { it.copy(selectedTab = BottomNavTab.HOME) }
                true
            }
            else -> false
        }
    }

    fun setSelectedProfile(profile: UserProfile?) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    fun setActiveChatThread(thread: ChatThread?) {
        _uiState.update { it.copy(activeChatThread = thread) }
    }

    fun setShowFilterScreen(show: Boolean) {
        _uiState.update { it.copy(showFilterScreen = show) }
    }

    fun setShowVerificationDialog(show: Boolean) {
        _uiState.update { it.copy(showVerificationDialog = show) }
    }

    fun setShowNotificationsSheet(show: Boolean) {
        _uiState.update { it.copy(showNotificationsSheet = show) }
    }

    fun setShowEditWizard(show: Boolean) {
        _uiState.update { it.copy(showEditWizard = show) }
    }

    fun setShowChangePhotoDialog(show: Boolean) {
        _uiState.update { it.copy(showChangePhotoDialog = show) }
    }

    fun setIsUploadingProfilePhoto(uploading: Boolean) {
        _uiState.update { it.copy(isUploadingProfilePhoto = uploading) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterState(filter: SearchFilterState) {
        _uiState.update { it.copy(filterState = filter, showFilterScreen = false) }
    }

    fun markContactUnlocked(candidateId: String, remainingContacts: Int) {
        _uiState.update {
            it.copy(
                unlockedContactIds = it.unlockedContactIds + candidateId,
                localContactsOverride = remainingContacts
            )
        }
    }

    // --- Optimistic Profile Overrides ---

    fun setLocalSavedIdsOverride(ids: List<String>?) {
        _uiState.update { it.copy(localSavedIdsOverride = ids) }
    }

    fun setLocalInterestsOverride(remaining: Int?) {
        _uiState.update { it.copy(localInterestsOverride = remaining) }
    }

    fun setLocalPackageOverride(
        tier: String?,
        pendingTier: String?,
        interestsRemaining: Int? = null,
        contactsRemaining: Int? = null,
        packageActivatedAt: Long? = null,
        packageExpiresAt: Long? = null
    ) {
        _uiState.update {
            it.copy(
                localPackageTierOverride = tier ?: it.localPackageTierOverride,
                localPendingPackageTierOverride = pendingTier,
                localInterestsOverride = interestsRemaining ?: it.localInterestsOverride,
                localContactsOverride = contactsRemaining ?: it.localContactsOverride,
                localPackageActivatedAtOverride = packageActivatedAt ?: it.localPackageActivatedAtOverride,
                localPackageExpiresAtOverride = packageExpiresAt ?: it.localPackageExpiresAtOverride
            )
        }
    }

    fun setLocalVerificationOverride(status: String, docType: String, idNumber: String, docUri: String) {
        _uiState.update {
            it.copy(
                localVerificationStatusOverride = status,
                localVerificationDocTypeOverride = docType,
                localVerificationIdNumberOverride = idNumber,
                localVerificationDocUriOverride = docUri
            )
        }
    }

    fun setLocalPhotoUrlOverride(photoUrl: String?) {
        _uiState.update { it.copy(localPhotoUrlOverride = photoUrl) }
    }

    fun setLocalAccountStatusOverride(accountStatus: String?) {
        _uiState.update { it.copy(localAccountStatusOverride = accountStatus) }
    }

    fun setLocalEmailOverride(email: String?) {
        _uiState.update { it.copy(localEmailOverride = email) }
    }

    fun buildEffectiveUserProfile(baseProfile: UserProfile): UserProfile {
        val state = _uiState.value
        val activeTier = state.localPackageTierOverride ?: baseProfile.packageTier
        return baseProfile.copy(
            email = state.localEmailOverride ?: baseProfile.email,
            photoUrl = state.localPhotoUrlOverride ?: baseProfile.photoUrl,
            savedProfileIds = state.localSavedIdsOverride ?: baseProfile.savedProfileIds,
            interestsRemaining = state.localInterestsOverride ?: baseProfile.interestsRemaining,
            contactsRemaining = state.localContactsOverride ?: baseProfile.contactsRemaining,
            packageTier = activeTier,
            pendingPackageTier = state.localPendingPackageTierOverride ?: baseProfile.pendingPackageTier,
            packageActivatedAt = state.localPackageActivatedAtOverride ?: baseProfile.packageActivatedAt,
            packageExpiresAt = state.localPackageExpiresAtOverride ?: baseProfile.packageExpiresAt,
            verificationStatus = state.localVerificationStatusOverride ?: baseProfile.verificationStatus,
            verificationDocType = state.localVerificationDocTypeOverride ?: baseProfile.verificationDocType,
            verificationIdNumber = state.localVerificationIdNumberOverride ?: baseProfile.verificationIdNumber,
            verificationDocUri = state.localVerificationDocUriOverride ?: baseProfile.verificationDocUri,
            accountStatus = state.localAccountStatusOverride ?: baseProfile.accountStatus
        ).withWeeklyFreeInterestReset()
    }

    // --- Chat Read Receipts & Optimistic Messages ---

    fun markChatThreadRead(thread: ChatThread) {
        val chatKey = thread.chatId.ifBlank { thread.id }
        val msgTime = thread.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val sig = "${thread.lastMessageSenderId}:${thread.lastMessage}"
        _uiState.update { state ->
            state.copy(
                lastSeenTimeByChat = state.lastSeenTimeByChat + (chatKey to maxOf(msgTime, System.currentTimeMillis())),
                lastSeenSigByChat = state.lastSeenSigByChat + (chatKey to sig)
            )
        }
    }

    fun appendOptimisticMessage(chatId: String, message: ChatMessage) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val currentList = state.optimisticMessagesByChat[chatId].orEmpty()
            state.copy(
                optimisticMessagesByChat = state.optimisticMessagesByChat + (chatId to (currentList + message)),
                lastSeenTimeByChat = state.lastSeenTimeByChat + (chatId to now),
                lastSeenSigByChat = state.lastSeenSigByChat + (chatId to "${message.senderId}:${message.text}")
            )
        }
    }

    fun computeUnreadCountsByChatId(
        chatThreads: List<ChatThread>,
        currentUid: String,
        currentProfileCode: String
    ): Map<String, Int> {
        val state = _uiState.value
        val activeChatKey = state.activeChatThread?.let { it.chatId.ifBlank { it.id } }
        return chatThreads.associate { thread ->
            val chatKey = thread.chatId.ifBlank { thread.id }
            val isFromOther = thread.lastMessageSenderId.isNotBlank() &&
                thread.lastMessageSenderId != currentUid &&
                !thread.lastMessageSenderId.equals(currentProfileCode, ignoreCase = true)
            val msgTime = thread.updatedAt?.toDate()?.time ?: 0L
            val seenTime = state.lastSeenTimeByChat[chatKey] ?: 0L
            val currentSig = "${thread.lastMessageSenderId}:${thread.lastMessage}"
            val seenSig = state.lastSeenSigByChat[chatKey]
            val isActivelyViewing = activeChatKey == chatKey
            val isUnread = isFromOther &&
                !isActivelyViewing &&
                thread.lastMessage.isNotBlank() &&
                currentSig != seenSig &&
                (seenTime == 0L || msgTime > seenTime)
            chatKey to if (isUnread) 1 else 0
        }
    }

    // --- Notifications Read Tracking & Aggregation ---

    fun syncNotificationReadPrefs(prefs: SharedPreferences, userKey: String) {
        val lastRead = prefs.getLong("last_read_ts_$userKey", 0L)
        val readIds = prefs.getStringSet("read_ids_$userKey", emptySet())?.toSet() ?: emptySet()
        val dismissedIds = prefs.getStringSet("dismissed_banner_ids_$userKey", emptySet())?.toSet() ?: emptySet()
        _uiState.update {
            it.copy(
                lastNotifReadTime = lastRead,
                readNotificationIds = readIds,
                dismissedBannerIds = dismissedIds
            )
        }
    }

    fun markAllNotificationsRead(
        prefs: SharedPreferences?,
        userKey: String,
        notifications: List<AdminNotification>
    ) {
        val latestTs = notifications.maxOfOrNull { it.createdAt?.toDate()?.time ?: 0L }
            ?: System.currentTimeMillis()
        val newTs = maxOf(System.currentTimeMillis(), latestTs)
        val allIds = _uiState.value.readNotificationIds + notifications.map { it.id }
        _uiState.update {
            it.copy(
                notificationsMarkedRead = true,
                lastNotifReadTime = newTs,
                readNotificationIds = allIds
            )
        }
        prefs?.edit()
            ?.putLong("last_read_ts_$userKey", newTs)
            ?.putStringSet("read_ids_$userKey", allIds)
            ?.apply()
    }

    fun markSingleNotificationRead(
        prefs: SharedPreferences?,
        userKey: String,
        notifId: String
    ) {
        val updated = _uiState.value.readNotificationIds + notifId
        _uiState.update { it.copy(readNotificationIds = updated) }
        prefs?.edit()?.putStringSet("read_ids_$userKey", updated)?.apply()
    }

    fun dismissBannerNotification(
        prefs: SharedPreferences?,
        userKey: String,
        notifId: String
    ) {
        val updatedDismissed = _uiState.value.dismissedBannerIds + notifId
        val updatedRead = _uiState.value.readNotificationIds + notifId
        _uiState.update {
            it.copy(
                dismissedBannerIds = updatedDismissed,
                readNotificationIds = updatedRead
            )
        }
        prefs?.edit()
            ?.putStringSet("dismissed_banner_ids_$userKey", updatedDismissed)
            ?.putStringSet("read_ids_$userKey", updatedRead)
            ?.apply()
    }

    fun computeMatchedNotifications(
        rawAdminNotifications: List<AdminNotification>,
        incomingInterests: List<Interest>,
        sentInterests: List<Interest>,
        effectiveUserProfile: UserProfile,
        currentUid: String
    ): List<AdminNotification> {
        val fromCollection = rawAdminNotifications.filter {
            it.matchesUser(effectiveUserProfile, currentUid)
        }
        val syntheticNotifs = mutableListOf<AdminNotification>()

        if (effectiveUserProfile.accountStatus.equals("ACTIVE", ignoreCase = true) &&
            effectiveUserProfile.displayName.isNotBlank() &&
            fromCollection.none { it.type == "ACCOUNT_APPROVED" }
        ) {
            syntheticNotifs.add(
                AdminNotification(
                    id = "synth_account_active_${effectiveUserProfile.getFormattedUserId()}",
                    type = "ACCOUNT_APPROVED",
                    targetUserId = currentUid,
                    targetProfileCode = effectiveUserProfile.getFormattedUserId(),
                    title = "Biodata Approved & Live!",
                    titleBn = "আপনার বায়োডাটা অনুমোদিত ও লাইভ হয়েছে!",
                    message = "Congratulations! Your biodata (${effectiveUserProfile.getFormattedUserId()}) is active and visible to matching candidates.",
                    bodyBn = "অভিনন্দন! আপনার বায়োডাটা (${effectiveUserProfile.getFormattedUserId()}) সক্রিয় রয়েছে এবং ম্যাচিং প্রার্থীদের কাছে দৃশ্যমান।",
                    createdAt = effectiveUserProfile.updatedAt ?: effectiveUserProfile.createdAt
                )
            )
        }

        if ((effectiveUserProfile.isVerified ||
                effectiveUserProfile.verificationStatus.equals("VERIFIED", ignoreCase = true)) &&
            fromCollection.none { it.type == "VERIFICATION_APPROVED" || it.type == "KYC_APPROVED" }
        ) {
            syntheticNotifs.add(
                AdminNotification(
                    id = "synth_verified_${effectiveUserProfile.getFormattedUserId()}",
                    type = "VERIFICATION_APPROVED",
                    targetUserId = currentUid,
                    targetProfileCode = effectiveUserProfile.getFormattedUserId(),
                    title = "Verified Blue Badge Active",
                    titleBn = "ভেরিফায়েড ব্লু ব্যাজ সক্রিয় রয়েছে!",
                    message = "Your NID/Passport verification is approved and the Verified Badge is active on your profile.",
                    bodyBn = "আপনার পরিচয়পত্র যাচাই অনুমোদিত হয়েছে এবং প্রোফাইলে ভেরিফায়েড ব্যাজ যুক্ত রয়েছে।",
                    createdAt = effectiveUserProfile.updatedAt ?: effectiveUserProfile.createdAt
                )
            )
        }

        if (!effectiveUserProfile.packageTier.equals("Free", ignoreCase = true) &&
            effectiveUserProfile.packageTier.isNotBlank() &&
            fromCollection.none {
                (it.type == "PACKAGE_APPROVED" || it.type == "PACKAGE_CHANGED_BY_ADMIN") &&
                    it.packageTier.equals(effectiveUserProfile.packageTier, ignoreCase = true)
            }
        ) {
            syntheticNotifs.add(
                AdminNotification(
                    id = "synth_pkg_${effectiveUserProfile.getFormattedUserId()}_${effectiveUserProfile.packageTier}",
                    type = "PACKAGE_APPROVED",
                    targetUserId = currentUid,
                    targetProfileCode = effectiveUserProfile.getFormattedUserId(),
                    packageTier = effectiveUserProfile.packageTier,
                    title = "${effectiveUserProfile.packageTier} Membership Active!",
                    titleBn = "${effectiveUserProfile.packageTier} মেম্বারশিপ প্যাকেজ চালু রয়েছে!",
                    message = "Your ${effectiveUserProfile.packageTier} membership tier is active with ${effectiveUserProfile.interestsRemaining} interests remaining.",
                    bodyBn = "আপনার ${effectiveUserProfile.packageTier} প্যাকেজটি সক্রিয় রয়েছে (${effectiveUserProfile.interestsRemaining}টি প্রস্তাব অবশিষ্ট)।",
                    createdAt = effectiveUserProfile.updatedAt ?: effectiveUserProfile.createdAt
                )
            )
        }

        incomingInterests.forEach { interest ->
            val senderName = interest.senderName.ifBlank { "A member" }
            val hasMatchingRemote = fromCollection.any {
                it.type == "INTEREST_RECEIVED" && it.title.contains(senderName, ignoreCase = true)
            }
            if (!hasMatchingRemote) {
                val statusLabel = when (interest.status.lowercase()) {
                    "pending" -> "Pending your response"
                    "accepted" -> "You accepted this interest"
                    "declined" -> "Declined"
                    else -> interest.status
                }
                syntheticNotifs.add(
                    AdminNotification(
                        id = "synth_interest_in_${interest.id.ifBlank { interest.senderId }}",
                        type = "INTEREST_RECEIVED",
                        targetUserId = currentUid,
                        targetProfileCode = effectiveUserProfile.getFormattedUserId(),
                        title = "Proposal from $senderName (${interest.senderAge} yrs)",
                        titleBn = "$senderName (${interest.senderAge} বছর) আপনার প্রোফাইলে আগ্রহ প্রকাশ করেছেন!",
                        message = "${interest.senderProfession.ifBlank { "Member" }} • ${interest.senderUpazila.ifBlank { "Bangladesh" }} — $statusLabel.",
                        bodyBn = "${interest.senderProfession.ifBlank { "সদস্য" }} • ${interest.senderUpazila.ifBlank { "বাংলাদেশ" }} — প্রস্তাবটি দেখতে ট্যাপ করুন।",
                        createdAt = interest.updatedAt ?: interest.createdAt ?: Timestamp(Date())
                    )
                )
            }
        }

        sentInterests.filter { it.status.equals("accepted", ignoreCase = true) }.forEach { interest ->
            val receiverName = interest.receiverName.ifBlank { "A member" }
            val hasMatchingRemote = fromCollection.any {
                it.type == "INTEREST_ACCEPTED" && it.title.contains(receiverName, ignoreCase = true)
            }
            if (!hasMatchingRemote) {
                syntheticNotifs.add(
                    AdminNotification(
                        id = "synth_interest_acc_${interest.id.ifBlank { interest.receiverId }}",
                        type = "INTEREST_ACCEPTED",
                        targetUserId = currentUid,
                        targetProfileCode = effectiveUserProfile.getFormattedUserId(),
                        title = "$receiverName Accepted Your Proposal!",
                        titleBn = "$receiverName আপনার প্রস্তাব গ্রহণ করেছেন!",
                        message = "Congratulations! Your proposal to $receiverName was accepted. Tap to open Chats.",
                        bodyBn = "অভিনন্দন! $receiverName আপনার প্রস্তাব গ্রহণ করেছেন। মেসেজ পাঠাতে ট্যাপ করুন।",
                        createdAt = interest.updatedAt ?: interest.createdAt ?: Timestamp(Date())
                    )
                )
            }
        }

        return (fromCollection + syntheticNotifs).sortedByDescending {
            it.createdAt?.toDate()?.time ?: 0L
        }
    }
}
