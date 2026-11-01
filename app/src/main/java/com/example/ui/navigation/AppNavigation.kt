package com.example.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SampleData
import com.example.data.model.BangladeshLocationData
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.UserProfile
import com.example.data.repository.MatrimonyRepository
import com.example.ui.chat.ChatConversationScreen
import com.example.ui.chat.ChatListScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.SavedProfilesScreen
import com.example.ui.interests.InterestsScreen
import com.example.ui.packages.PackagesScreen
import com.example.ui.profile.ProfileDetailScreen
import com.example.ui.profile.ProfileEditWizard
import com.example.ui.search.SearchFilterScreen
import com.example.ui.search.matchesOppositeGender
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.candidateMatchesSearchQuery
import com.example.ui.theme.tr
import com.example.ui.verification.VerificationDialog
import com.example.util.CloudinaryProfileUploader
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    currentUserId: String = "user_default",
    onSignOut: () -> Unit = {},
    navViewModel: MainNavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    remember(context) { BangladeshLocationData.ensureLoaded(context) }
    val repository = remember { MatrimonyRepository.create(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notifPrefs = remember(context) {
        try {
            context.getSharedPreferences("ghotok_notif_read_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    val uiState by navViewModel.uiState.collectAsState()

    val cachedInitialProfile = remember(currentUserId) {
        if (currentUserId.isNotBlank()) repository.getCachedProfileForUser(currentUserId) else null
    }
    LaunchedEffect(currentUserId) {
        navViewModel.onActiveUserChanged(
            userId = currentUserId,
            cachedProfile = cachedInitialProfile,
            notifPrefs = notifPrefs
        )
    }

    val readStorageKey = remember(currentUserId, uiState.activeUserProfile?.id) {
        uiState.activeUserProfile?.id?.ifBlank { currentUserId } ?: currentUserId.ifBlank { "guest" }
    }
    LaunchedEffect(readStorageKey) {
        if (notifPrefs != null) {
            navViewModel.syncNotificationReadPrefs(notifPrefs, readStorageKey)
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            try {
                val existing = repository.getUserProfile(currentUserId)
                if (existing != null) {
                    navViewModel.setActiveUserProfile(existing, checked = true)
                } else {
                    val draftPair = repository.getOnboardingDraft(currentUserId)
                    val currentDraft = draftPair?.first
                    if (currentDraft != null && currentDraft.displayName.isNotBlank()) {
                        val created = UserProfile(
                            id = currentUserId,
                            userId = currentUserId,
                            email = repository.currentUserEmail().orEmpty(),
                            displayName = currentDraft.displayName,
                            displayNameBangla = currentDraft.displayNameBangla,
                            gender = currentDraft.gender,
                            dateOfBirth = currentDraft.dateOfBirth,
                            maritalStatus = currentDraft.maritalStatus,
                            religion = currentDraft.religion,
                            profileCreatedBy = currentDraft.profileCreatedBy,
                            height = currentDraft.height,
                            weight = currentDraft.weightKg.toIntOrNull() ?: 0,
                            complexion = currentDraft.complexion,
                            bloodGroup = currentDraft.bloodGroup,
                            eyeColour = currentDraft.eyeColour,
                            hairColour = currentDraft.hairColour,
                            disability = currentDraft.disability,
                            education = currentDraft.educationLevel,
                            educationGroup = currentDraft.educationGroup,
                            institution = currentDraft.institution,
                            ieltsScore = currentDraft.ieltsScore,
                            employmentType = currentDraft.employmentType,
                            profession = currentDraft.profession,
                            jobDesignation = currentDraft.jobDesignation,
                            jobOrganization = currentDraft.jobOrganization,
                            jobEmploymentStatus = currentDraft.jobEmploymentStatus,
                            monthlyIncome = currentDraft.monthlyIncome,
                            workLocationDistrict = currentDraft.workLocationDistrict,
                            overseasCountry = currentDraft.overseasCountry,
                            overseasVisaStatus = currentDraft.overseasVisaStatus,
                            overseasWorkExperienceYears = currentDraft.overseasWorkExperienceYears,
                            familyType = currentDraft.familyType,
                            parentsStatus = currentDraft.parentsStatus,
                            financialCondition = currentDraft.financialCondition,
                            fatherProfession = currentDraft.fatherProfession,
                            motherProfession = currentDraft.motherProfession,
                            brotherCount = currentDraft.brotherCount,
                            sisterCount = currentDraft.sisterCount,
                            familyIntroduction = currentDraft.familyIntroduction,
                            presentDivision = currentDraft.presentDivision,
                            presentDistrict = currentDraft.presentDistrict,
                            presentUpazila = currentDraft.presentUpazila,
                            presentAddress = currentDraft.presentAddress,
                            permanentSameAsPresent = currentDraft.permanentSameAsPresent,
                            permanentDivision = currentDraft.permanentDivision,
                            permanentDistrict = currentDraft.permanentDistrict,
                            permanentUpazila = currentDraft.permanentUpazila,
                            permanentAddress = currentDraft.permanentAddress,
                            aboutMe = currentDraft.aboutMe,
                            contactPhone = currentDraft.contactPhone,
                            whatsapp = currentDraft.whatsapp,
                            imo = currentDraft.imo,
                            facebook = currentDraft.facebook,
                            instagram = currentDraft.instagram,
                            guardianPhone = currentDraft.guardianPhone,
                            partnerAgeMin = currentDraft.partnerAgeMin,
                            partnerAgeMax = currentDraft.partnerAgeMax,
                            partnerHeightMin = currentDraft.partnerHeightMin,
                            partnerHeightMax = currentDraft.partnerHeightMax,
                            partnerMaritalStatuses = currentDraft.partnerMaritalStatuses,
                            partnerEducation = currentDraft.partnerEducation,
                            partnerProfessions = currentDraft.partnerProfessions,
                            partnerLocations = currentDraft.partnerLocations,
                            partnerFinancialConditions = currentDraft.partnerFinancialConditions,
                            partnerComplexions = currentDraft.partnerComplexions,
                            partnerIelts = currentDraft.partnerIelts,
                            partnerOverseasCountries = currentDraft.partnerOverseasCountries,
                            partnerReligion = currentDraft.partnerReligion,
                            partnerDistricts = currentDraft.partnerDistricts,
                            partnerMarriageTimeline = currentDraft.partnerMarriageTimeline,
                            partnerExpectations = currentDraft.partnerExpectations,
                            accountStatus = "ACTIVE",
                            verificationStatus = "UNSUBMITTED",
                            packageTier = "Free",
                            interestsRemaining = 2,
                            contactsRemaining = 0,
                            createdAt = Timestamp(Date()),
                            updatedAt = Timestamp(Date())
                        )
                        val saved = repository.saveUserProfile(created).getOrDefault(created)
                        repository.clearOnboardingDraft(currentUserId)
                        navViewModel.setActiveUserProfile(saved, checked = true)
                    } else {
                        navViewModel.setActiveUserProfile(null, checked = true)
                    }
                }
            } catch (_: Exception) {
                navViewModel.setActiveUserProfile(uiState.activeUserProfile, checked = true)
            }
        } else {
            navViewModel.setActiveUserProfile(null, checked = true)
        }
    }

    val liveProfileFlow = remember(currentUserId) {
        repository.observeUserProfile(currentUserId)
    }
    val observedProfile by liveProfileFlow.collectAsState(initial = cachedInitialProfile)
    LaunchedEffect(observedProfile) {
        if (observedProfile != null) {
            navViewModel.setActiveUserProfile(observedProfile, checked = true)
        }
    }

    val baseUserProfile = uiState.activeUserProfile ?: observedProfile ?: cachedInitialProfile ?: remember(currentUserId) {
        UserProfile(
            id = currentUserId,
            userId = currentUserId,
            email = repository.currentUserEmail().orEmpty(),
            displayName = repository.currentUserDisplayName()?.ifBlank { "Member" } ?: "Member"
        )
    }
    val effectiveUserProfile = remember(baseUserProfile, uiState) {
        navViewModel.buildEffectiveUserProfile(baseUserProfile)
    }

    val candidatesFlow = remember(currentUserId) {
        repository.observeCandidates()
    }
    val allRemoteProfiles by candidatesFlow.collectAsState(
        initial = remember(currentUserId) { repository.getCachedCandidates() }
    )

    val profileUidKey = effectiveUserProfile.userId.ifBlank { currentUserId }
    val profileCodeKey = effectiveUserProfile.getFormattedUserId()

    val incomingInterestsFlow = remember(currentUserId, profileUidKey, profileCodeKey) {
        repository.observeReceivedInterests(
            userId = currentUserId,
            secondaryUserId = profileUidKey,
            profileCode = profileCodeKey
        )
    }
    val remoteIncoming by incomingInterestsFlow.collectAsState(
        initial = remember(currentUserId, profileUidKey) {
            repository.getCachedReceivedInterests(currentUserId).ifEmpty {
                repository.getCachedReceivedInterests(profileUidKey)
            }
        }
    )

    val sentInterestsFlow = remember(currentUserId, profileUidKey, profileCodeKey) {
        repository.observeSentInterests(
            userId = currentUserId,
            secondaryUserId = profileUidKey,
            profileCode = profileCodeKey
        )
    }
    val remoteSent by sentInterestsFlow.collectAsState(
        initial = remember(currentUserId, profileUidKey) {
            repository.getCachedSentInterests(currentUserId).ifEmpty {
                repository.getCachedSentInterests(profileUidKey)
            }
        }
    )

    val chatsFlow = remember(currentUserId) {
        repository.observeChats(currentUserId)
    }
    val remoteChats by chatsFlow.collectAsState(
        initial = remember(currentUserId) { repository.getCachedChats(currentUserId) }
    )

    val notificationsFlow = remember(currentUserId) {
        repository.observeNotificationsAndBroadcasts()
    }
    val rawAdminNotifications by notificationsFlow.collectAsState(
        initial = remember(currentUserId) { repository.getCachedNotifications() }
    )

    val matchedAdminNotifications = remember(
        rawAdminNotifications,
        remoteIncoming,
        remoteSent,
        effectiveUserProfile,
        currentUserId
    ) {
        navViewModel.computeMatchedNotifications(
            rawAdminNotifications = rawAdminNotifications,
            incomingInterests = remoteIncoming,
            sentInterests = remoteSent,
            effectiveUserProfile = effectiveUserProfile,
            currentUid = currentUserId
        )
    }

    val activeBannerAnnouncement = remember(matchedAdminNotifications, uiState.dismissedBannerIds) {
        matchedAdminNotifications.firstOrNull { notif ->
            !uiState.dismissedBannerIds.contains(notif.id) &&
                (notif.type.equals("BROADCAST", ignoreCase = true) ||
                    notif.type.equals("PACKAGE_APPROVED", ignoreCase = true) ||
                    notif.type.equals("VERIFICATION_APPROVED", ignoreCase = true))
        }
    }

    val unreadCountsByChatId = remember(
        remoteChats,
        currentUserId,
        effectiveUserProfile.id,
        uiState.lastSeenTimeByChat,
        uiState.lastSeenSigByChat,
        uiState.activeChatThread
    ) {
        navViewModel.computeUnreadCountsByChatId(
            chatThreads = remoteChats,
            currentUid = currentUserId,
            currentProfileCode = effectiveUserProfile.getFormattedUserId()
        )
    }

    val unreadThreadsList = remember(remoteChats, unreadCountsByChatId) {
        remoteChats.filter { thread ->
            val key = thread.chatId.ifBlank { thread.id }
            (unreadCountsByChatId[key] ?: 0) > 0
        }
    }

    val pendingInterests = remember(remoteIncoming) {
        remoteIncoming.filter { it.status.equals("pending", ignoreCase = true) }
    }

    val unreadNotificationCount = remember(
        matchedAdminNotifications,
        uiState.readNotificationIds,
        uiState.lastNotifReadTime,
        pendingInterests.size,
        unreadThreadsList.size
    ) {
        val unreadAdminCount = matchedAdminNotifications.count { notif ->
            !uiState.readNotificationIds.contains(notif.id) &&
                ((notif.createdAt?.toDate()?.time ?: 0L) > uiState.lastNotifReadTime || !notif.id.startsWith("synth_"))
        }
        unreadAdminCount + pendingInterests.size + unreadThreadsList.size
    }

    val isBanglaNotif = LocalLanguageController.current.isBangla
    var hasNotificationPerm by remember(context) {
        mutableStateOf(
            com.example.util.GhotokNotificationManager.hasNotificationPermission(context)
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted
    }

    LaunchedEffect(currentUserId) {
        com.example.util.GhotokNotificationManager.ensureChannelsCreated(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !com.example.util.GhotokNotificationManager.hasNotificationPermission(context)
        ) {
            runCatching {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (currentUserId.isNotBlank() && currentUserId != "user_default") {
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { fcmToken ->
                        if (!fcmToken.isNullOrBlank()) {
                            com.example.util.GhotokFirebaseMessagingService.saveLocalFcmToken(context, fcmToken)
                            scope.launch {
                                com.example.data.supabase.SupabaseClient.registerFcmToken(currentUserId, fcmToken)
                            }
                        }
                    }
            }
        }
    }

    LaunchedEffect(
        matchedAdminNotifications,
        unreadThreadsList,
        currentUserId,
        hasNotificationPerm,
        isBanglaNotif
    ) {
        if (currentUserId.isBlank() || !hasNotificationPerm) return@LaunchedEffect
        val readIds = uiState.readNotificationIds
        matchedAdminNotifications.forEach { notif ->
            if (!readIds.contains(notif.id) && !notif.id.startsWith("synth_account_active_")) {
                com.example.util.GhotokNotificationManager.notifyOnceIfNew(
                    context = context,
                    userId = currentUserId,
                    notification = notif,
                    isBangla = isBanglaNotif
                )
            }
        }
        unreadThreadsList.forEach { thread ->
            val chatKey = thread.chatId.ifBlank { thread.id }
            val senderName = thread.otherUserName.ifBlank { "Member" }
            val senderNameBn = thread.otherUserNameBangla.ifBlank { senderName }
            val msgTs = thread.updatedAt?.seconds ?: 0L
            val msgSig = "${chatKey}_${msgTs}_${thread.lastMessage.hashCode()}"
            val chatPushNotif = com.example.data.model.AdminNotification(
                id = "push_chat_$msgSig",
                notificationId = "push_chat_$msgSig",
                type = "CHAT_MESSAGE",
                targetUserId = currentUserId,
                title = "New Message from $senderName",
                titleEn = "New Message from $senderName",
                titleBn = "$senderNameBn নতুন মেসেজ পাঠিয়েছেন",
                body = thread.lastMessage,
                bodyEn = thread.lastMessage,
                bodyBn = thread.lastMessage,
                message = thread.lastMessage,
                actionRoute = "CHATS",
                pushNotification = true,
                createdAt = thread.updatedAt ?: Timestamp(Date())
            )
            com.example.util.GhotokNotificationManager.notifyOnceIfNew(
                context = context,
                userId = currentUserId,
                notification = chatPushNotif,
                isBangla = isBanglaNotif
            )
        }
    }

    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            navViewModel.setIsUploadingProfilePhoto(true)
            scope.launch {
                val uploadedUrl = CloudinaryProfileUploader.compressAndUploadToCloudinary(
                    context = context,
                    imageUri = uri
                ).getOrElse { uri.toString() }
                navViewModel.setLocalPhotoUrlOverride(uploadedUrl)
                repository.updateProfilePhoto(currentUserId, uploadedUrl)
                navViewModel.setIsUploadingProfilePhoto(false)
                navViewModel.setShowChangePhotoDialog(false)
                snackbarHostState.showSnackbar("Profile photo updated!")
            }
        }
    }

    BackHandler(
        enabled = uiState.showEditWizard ||
            uiState.showFilterScreen ||
            uiState.activeChatThread != null ||
            uiState.selectedProfile != null ||
            uiState.tabBackStack.isNotEmpty() ||
            uiState.selectedTab != BottomNavTab.HOME
    ) {
        navViewModel.popBackStack()
    }

    val candidatesWithCompatibility = remember(allRemoteProfiles, effectiveUserProfile, uiState.filterState, uiState.searchQuery) {
        val base = allRemoteProfiles.filter { candidate ->
            candidate.id != effectiveUserProfile.id &&
                candidate.userId != currentUserId &&
                matchesOppositeGender(candidate.gender, effectiveUserProfile.gender) &&
                !candidate.accountStatus.equals("HIDDEN", ignoreCase = true) &&
                !candidate.accountStatus.equals("DELETED", ignoreCase = true)
        }
        val query = uiState.searchQuery.trim()
        base.filter { candidate ->
            val matchesAge = candidate.age == 0 || (candidate.age in uiState.filterState.minAge..uiState.filterState.maxAge)
            val matchesDivision = uiState.filterState.divisions.isEmpty() ||
                uiState.filterState.divisions.any {
                    it.equals(candidate.presentDivision, ignoreCase = true) ||
                        it.equals(candidate.locationDivision, ignoreCase = true)
                }
            val matchesDistrict = uiState.filterState.districts.isEmpty() ||
                uiState.filterState.districts.any {
                    it.equals(candidate.presentDistrict, ignoreCase = true) ||
                        it.equals(candidate.locationDistrict, ignoreCase = true)
                }
            val matchesReligion = uiState.filterState.religion == "Any" ||
                candidate.religion.equals(uiState.filterState.religion, ignoreCase = true)
            val matchesMarital = uiState.filterState.maritalStatuses.isEmpty() ||
                uiState.filterState.maritalStatuses.any { it.equals(candidate.maritalStatus, ignoreCase = true) }
            val matchesProfession = uiState.filterState.professions.isEmpty() ||
                uiState.filterState.professions.any { candidate.profession.contains(it, ignoreCase = true) }
            val matchesQuery = candidateMatchesSearchQuery(candidate, query)
            matchesAge && matchesDivision && matchesDistrict && matchesReligion &&
                matchesMarital && matchesProfession && matchesQuery
        }
    }

    val savedProfilesList = remember(allRemoteProfiles, effectiveUserProfile.savedProfileIds) {
        allRemoteProfiles.filter { it.isSavedIn(effectiveUserProfile.savedProfileIds) }
    }

    if (uiState.showNotificationsSheet) {
        GhotokNotificationsDialog(
            matchedAdminNotifications = matchedAdminNotifications,
            readNotificationIds = uiState.readNotificationIds,
            lastNotifReadTime = uiState.lastNotifReadTime,
            pendingInterests = pendingInterests,
            unreadThreadsList = unreadThreadsList,
            onDismiss = { navViewModel.setShowNotificationsSheet(false) },
            onMarkNotificationRead = { notifId ->
                navViewModel.markSingleNotificationRead(notifPrefs, readStorageKey, notifId)
            },
            onNavigateToTab = { tab -> navViewModel.navigateToTab(tab) },
            onOpenVerification = { navViewModel.setShowVerificationDialog(true) },
            onOpenChatThread = { thread ->
                navViewModel.markChatThreadRead(thread)
                navViewModel.setActiveChatThread(thread)
            }
        )
    }

    if (uiState.showChangePhotoDialog) {
        ChangeProfilePhotoDialog(
            currentPhotoUrl = effectiveUserProfile.photoUrl,
            isUploading = uiState.isUploadingProfilePhoto,
            onDismiss = { navViewModel.setShowChangePhotoDialog(false) },
            onPickGalleryPhoto = {
                profilePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSavePhotoUrl = { url ->
                navViewModel.setLocalPhotoUrlOverride(url)
                navViewModel.setShowChangePhotoDialog(false)
                scope.launch {
                    repository.updateProfilePhoto(currentUserId, url)
                    snackbarHostState.showSnackbar("Profile photo saved!")
                }
            }
        )
    }

    if (uiState.showVerificationDialog) {
        VerificationDialog(
            currentDocType = effectiveUserProfile.verificationDocType,
            currentIdNumber = effectiveUserProfile.verificationIdNumber,
            currentDocUri = effectiveUserProfile.verificationDocUri,
            currentVerificationStatus = effectiveUserProfile.verificationStatus,
            onDismiss = { navViewModel.setShowVerificationDialog(false) },
            onSubmitVerification = { docType, idNumber, docUri ->
                navViewModel.setLocalVerificationOverride(
                    status = "PENDING",
                    docType = docType,
                    idNumber = idNumber,
                    docUri = docUri
                )
                navViewModel.setShowVerificationDialog(false)
                scope.launch {
                    repository.submitVerificationRequest(
                        userId = currentUserId,
                        docType = docType,
                        idNumber = idNumber,
                        docUri = docUri
                    )
                    snackbarHostState.showSnackbar("Verification submitted for review!")
                }
            }
        )
    }

    if (uiState.showEditWizard) {
        ProfileEditWizard(
            initialProfile = effectiveUserProfile,
            onSaveProfile = { updatedProfile ->
                val resolvedId = updatedProfile.id
                    .takeIf { UserProfile.isValidFormattedUserId(it, updatedProfile.gender) }
                    ?: effectiveUserProfile.id.ifBlank { currentUserId }
                val resolvedCode = updatedProfile.profileCode
                    .takeIf { UserProfile.isValidFormattedUserId(it, updatedProfile.gender) }
                    ?: resolvedId
                val profileWithId = updatedProfile.copy(
                    id = resolvedId,
                    userId = updatedProfile.userId.ifBlank { currentUserId },
                    profileCode = resolvedCode,
                    updatedAt = Timestamp(Date())
                )
                navViewModel.setActiveUserProfile(profileWithId, checked = true)
                navViewModel.setShowEditWizard(false)
                scope.launch {
                    snackbarHostState.showSnackbar("Biodata updated successfully!")
                }
            },
            onCancel = { navViewModel.setShowEditWizard(false) }
        )
        return
    }

    if (uiState.showFilterScreen) {
        SearchFilterScreen(
            currentFilter = uiState.filterState,
            onApplyFilter = { newFilter -> navViewModel.setFilterState(newFilter) },
            onBack = { navViewModel.setShowFilterScreen(false) }
        )
        return
    }

    val activeChat = uiState.activeChatThread
    if (activeChat != null) {
        val chatIdKey = activeChat.chatId.ifBlank { activeChat.id }
        val messagesFlow = remember(chatIdKey) { repository.observeMessages(chatIdKey) }
        val remoteMessages by messagesFlow.collectAsState(initial = emptyList())
        val optimisticList = uiState.optimisticMessagesByChat[chatIdKey].orEmpty()
        val combinedMessages = remember(remoteMessages, optimisticList) {
            val remoteSigs = remoteMessages.map { "${it.senderId}:${it.text}" }.toSet()
            val pendingOptimistic = optimisticList.filter { "${it.senderId}:${it.text}" !in remoteSigs }
            (remoteMessages + pendingOptimistic).sortedBy { it.createdAt?.toDate()?.time ?: 0L }
        }

        ChatConversationScreen(
            currentProfile = effectiveUserProfile,
            otherUserName = activeChat.otherUserName.ifBlank { "Member" },
            otherUserFormattedId = activeChat.otherUserFormattedId,
            otherUserPhoto = activeChat.otherUserPhoto,
            messages = combinedMessages,
            onSendMessage = { messageText ->
                val trimmed = messageText.trim()
                if (trimmed.isNotEmpty()) {
                    val otherParticipantId = activeChat.participantIds
                        .firstOrNull { it != currentUserId && it != effectiveUserProfile.id }
                        ?: activeChat.otherUserFormattedId
                    val optimisticMsg = ChatMessage(
                        id = "opt_${System.currentTimeMillis()}",
                        messageId = "opt_${System.currentTimeMillis()}",
                        senderId = currentUserId,
                        senderName = effectiveUserProfile.displayName,
                        text = trimmed,
                        participantIds = activeChat.participantIds,
                        createdAt = Timestamp(Date())
                    )
                    navViewModel.appendOptimisticMessage(chatIdKey, optimisticMsg)
                    scope.launch {
                        repository.sendMessage(
                            chatId = chatIdKey,
                            senderProfile = effectiveUserProfile,
                            otherUserId = otherParticipantId,
                            text = trimmed
                        )
                    }
                }
            },
            onBack = { navViewModel.setActiveChatThread(null) },
            onNavigateToPackages = {
                navViewModel.setActiveChatThread(null)
                navViewModel.navigateToTab(BottomNavTab.PACKAGES)
            }
        )
        return
    }

    val langController = LocalLanguageController.current

    val handleToggleSave: (UserProfile) -> Unit = { candidate ->
        val currentSaved = effectiveUserProfile.savedProfileIds
        val currentlySaved = candidate.isSavedIn(currentSaved)
        val candidateKeys = candidate.allSaveKeys()
        val nextSaved = if (currentlySaved) {
            currentSaved.filterNot { it in candidateKeys }
        } else {
            (currentSaved + candidateKeys).distinct()
        }
        navViewModel.setLocalSavedIdsOverride(nextSaved)
        scope.launch {
            repository.updateSavedProfiles(
                currentProfile = effectiveUserProfile,
                updatedSavedIds = nextSaved
            )
            snackbarHostState.showSnackbar(
                if (!currentlySaved) {
                    if (langController.isBangla) "${candidate.displayName} সংরক্ষিত তালিকায় যোগ করা হয়েছে"
                    else "${candidate.displayName} saved to shortlist"
                } else {
                    if (langController.isBangla) "${candidate.displayName} সংরক্ষিত তালিকা থেকে সরানো হয়েছে"
                    else "${candidate.displayName} removed from shortlist"
                }
            )
        }
    }

    val handleSendInterest: (UserProfile, Boolean) -> Unit = { candidate, closeDetailOnUpgrade ->
        if (effectiveUserProfile.interestsRemaining <= 0) {
            scope.launch {
                snackbarHostState.showSnackbar("No proposals remaining. Upgrade your package!")
            }
            if (closeDetailOnUpgrade) {
                navViewModel.setSelectedProfile(null)
            }
            navViewModel.navigateToTab(BottomNavTab.PACKAGES)
        } else {
            val nextRemaining = (effectiveUserProfile.interestsRemaining - 1).coerceAtLeast(0)
            navViewModel.setLocalInterestsOverride(nextRemaining)
            scope.launch {
                val res = repository.sendInterest(
                    fromProfile = effectiveUserProfile,
                    toProfile = candidate
                )
                if (res.isSuccess) {
                    snackbarHostState.showSnackbar("Proposal sent to ${candidate.displayName}!")
                } else {
                    snackbarHostState.showSnackbar(
                        res.exceptionOrNull()?.localizedMessage ?: "Failed to send proposal"
                    )
                }
            }
        }
    }

    val handleStartChat: (UserProfile, Boolean) -> Unit = { candidate, closeDetail ->
        scope.launch {
            val threadRes = repository.ensureChatThread(effectiveUserProfile, candidate)
            val thread = threadRes.getOrElse {
                val sorted = listOf(currentUserId, candidate.userId.ifBlank { candidate.id }).sorted()
                val cId = "chat_${sorted[0]}_${sorted[1]}"
                ChatThread(
                    id = cId,
                    chatId = cId,
                    participantIds = sorted,
                    otherUserName = candidate.displayName,
                    otherUserNameBangla = candidate.displayNameBangla,
                    otherUserFormattedId = candidate.getFormattedUserId(),
                    otherUserPhoto = candidate.photoUrl,
                    lastMessage = "",
                    updatedAt = Timestamp(Date())
                )
            }
            if (closeDetail) {
                navViewModel.setSelectedProfile(null)
            }
            navViewModel.markChatThreadRead(thread)
            navViewModel.setActiveChatThread(thread)
        }
    }

    val selectedCandidate = uiState.selectedProfile
    if (selectedCandidate != null) {
        val isSaved = selectedCandidate.isSavedIn(effectiveUserProfile.savedProfileIds)
        val isContactUnlocked = uiState.unlockedContactIds.contains(selectedCandidate.id) ||
            selectedCandidate.id == effectiveUserProfile.id ||
            effectiveUserProfile.hasAdminAccess()

        ProfileDetailScreen(
            currentProfile = effectiveUserProfile,
            candidate = selectedCandidate,
            onBack = { navViewModel.setSelectedProfile(null) },
            onSendInterest = { handleSendInterest(selectedCandidate, true) },
            onStartChat = { handleStartChat(selectedCandidate, true) },
            onUnlockContact = {
                if (effectiveUserProfile.contactsRemaining > 0) {
                    val nextContacts = (effectiveUserProfile.contactsRemaining - 1).coerceAtLeast(0)
                    navViewModel.markContactUnlocked(selectedCandidate.id, nextContacts)
                    scope.launch {
                        repository.unlockCandidateContact(effectiveUserProfile)
                        snackbarHostState.showSnackbar("Guardian contact unlocked!")
                    }
                } else {
                    navViewModel.setSelectedProfile(null)
                    navViewModel.navigateToTab(BottomNavTab.PACKAGES)
                }
            },
            onNavigateToPackages = {
                navViewModel.setSelectedProfile(null)
                navViewModel.navigateToTab(BottomNavTab.PACKAGES)
            },
            isContactUnlocked = isContactUnlocked,
            isSaved = isSaved,
            onToggleSave = { handleToggleSave(selectedCandidate) },
            snackbarHostState = snackbarHostState
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (uiState.selectedTab) {
                BottomNavTab.HOME -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        title = {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (uiState.searchQuery.isEmpty()) {
                                            Text(
                                                text = tr("Search ID, name, district, job...", "আইডি, নাম, জেলা বা পেশা খুঁজুন..."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = uiState.searchQuery,
                                            onValueChange = { navViewModel.setSearchQuery(it) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("home_search_input")
                                        )
                                    }
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { navViewModel.setSearchQuery("") },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        actions = {
                            Surface(
                                onClick = { langController.onToggleBangla(!langController.isBangla) },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, GoldSecondary.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("language_toggle_button")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (langController.isBangla) "EN" else "বাংলা",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    navViewModel.markAllNotificationsRead(
                                        prefs = notifPrefs,
                                        userKey = readStorageKey,
                                        notifications = matchedAdminNotifications
                                    )
                                    navViewModel.setShowNotificationsSheet(true)
                                },
                                modifier = Modifier.testTag("notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotificationCount > 0) {
                                            Badge(
                                                containerColor = GoldSecondary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(
                                                    text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString()
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { navViewModel.setShowFilterScreen(true) },
                                modifier = Modifier.testTag("open_filters_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filter Matches",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    )
                }

                BottomNavTab.SAVED -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        title = {
                            Text(
                                text = tr(
                                    "Saved Profiles (${savedProfilesList.size})",
                                    "সংরক্ষিত প্রোফাইল (${savedProfilesList.size})"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    )
                }

                BottomNavTab.PACKAGES -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        navigationIcon = {
                            IconButton(onClick = { navViewModel.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = tr("Back", "পেছনে"),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        title = {
                            Text(
                                text = tr("Membership Packages", "মেম্বারশিপ প্যাকেজ"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    )
                }

                else -> Unit
            }
        },
        bottomBar = {
            NavigationBar {
                BottomNavTab.bottomBarTabs.forEach { tab ->
                    val badgeCount = when (tab) {
                        BottomNavTab.PROPOSALS -> pendingInterests.size
                        BottomNavTab.CHATS -> unreadThreadsList.size
                        BottomNavTab.SAVED -> savedProfilesList.size
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = { navViewModel.navigateToTab(tab) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge { Text(badgeCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.titleEn
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tr(tab.titleEn, tab.titleBn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                BottomNavTab.HOME -> {
                    HomeScreen(
                        currentProfile = effectiveUserProfile,
                        candidates = candidatesWithCompatibility,
                        filterState = uiState.filterState,
                        searchQuery = uiState.searchQuery,
                        savedProfileIds = effectiveUserProfile.savedProfileIds.toSet(),
                        activeAnnouncement = activeBannerAnnouncement,
                        onClickAnnouncement = { notif ->
                            navViewModel.dismissBannerNotification(notifPrefs, readStorageKey, notif.id)
                            navViewModel.setShowNotificationsSheet(true)
                        },
                        onDismissAnnouncement = { notif ->
                            navViewModel.dismissBannerNotification(notifPrefs, readStorageKey, notif.id)
                        },
                        onOpenFilters = { navViewModel.setShowFilterScreen(true) },
                        onSelectCandidate = { candidate -> navViewModel.setSelectedProfile(candidate) },
                        onSendInterest = { candidate -> handleSendInterest(candidate, false) },
                        onToggleSave = handleToggleSave,
                        onStartChat = { candidate -> handleStartChat(candidate, false) },
                        onOpenVerification = { navViewModel.setShowVerificationDialog(true) },
                        onNavigateToPackages = { navViewModel.navigateToTab(BottomNavTab.PACKAGES) }
                    )
                }

                BottomNavTab.SAVED -> {
                    SavedProfilesScreen(
                        currentProfile = effectiveUserProfile,
                        candidates = allRemoteProfiles,
                        savedProfileIds = effectiveUserProfile.savedProfileIds.toSet(),
                        onSelectCandidate = { candidate -> navViewModel.setSelectedProfile(candidate) },
                        onSendInterest = { candidate -> handleSendInterest(candidate, false) },
                        onToggleSave = handleToggleSave,
                        onStartChat = { candidate -> handleStartChat(candidate, false) }
                    )
                }

                BottomNavTab.PROPOSALS -> {
                    InterestsScreen(
                        receivedInterests = remoteIncoming,
                        sentInterests = remoteSent,
                        currentProfile = effectiveUserProfile,
                        candidates = allRemoteProfiles,
                        onSelectCandidate = { candidate ->
                            navViewModel.setSelectedProfile(candidate)
                            val targetUid = candidate.userId.ifBlank { candidate.id }
                            if (targetUid.isNotBlank() && candidate.education.isBlank() && candidate.maritalStatus.isBlank()) {
                                scope.launch {
                                    val fullProfile = repository.getUserProfile(targetUid)
                                    if (fullProfile != null) {
                                        navViewModel.setSelectedProfile(fullProfile)
                                    }
                                }
                            }
                        },
                        onAccept = { interest ->
                            scope.launch {
                                repository.respondToInterest(interest, accept = true)
                                snackbarHostState.showSnackbar("Proposal accepted! You can now chat.")
                            }
                        },
                        onDecline = { interest ->
                            scope.launch {
                                repository.respondToInterest(interest, accept = false)
                            }
                        },
                        onOpenChat = { otherUserId ->
                            val candidate = allRemoteProfiles.firstOrNull {
                                it.id == otherUserId || it.userId == otherUserId || it.getFormattedUserId() == otherUserId
                            }
                            val existingThread = remoteChats.firstOrNull {
                                it.participantIds.contains(otherUserId) || it.otherUserFormattedId == otherUserId
                            }
                            if (existingThread != null) {
                                navViewModel.markChatThreadRead(existingThread)
                                navViewModel.setActiveChatThread(existingThread)
                            } else if (candidate != null) {
                                scope.launch {
                                    val thread = repository.ensureChatThread(effectiveUserProfile, candidate).getOrElse {
                                        val sorted = listOf(currentUserId, otherUserId).sorted()
                                        val cId = "chat_${sorted[0]}_${sorted[1]}"
                                        ChatThread(
                                            id = cId,
                                            chatId = cId,
                                            participantIds = sorted,
                                            otherUserName = candidate.displayName,
                                            otherUserFormattedId = candidate.getFormattedUserId(),
                                            otherUserPhoto = candidate.photoUrl,
                                            lastMessage = "",
                                            updatedAt = Timestamp(Date())
                                        )
                                    }
                                    navViewModel.markChatThreadRead(thread)
                                    navViewModel.setActiveChatThread(thread)
                                }
                            }
                        }
                    )
                }

                BottomNavTab.CHATS -> {
                    ChatListScreen(
                        chatThreads = remoteChats,
                        currentUserId = currentUserId,
                        candidates = allRemoteProfiles,
                        receivedInterests = remoteIncoming,
                        sentInterests = remoteSent,
                        unreadCountsByChatId = unreadCountsByChatId,
                        onSelectChat = { thread ->
                            navViewModel.markChatThreadRead(thread)
                            navViewModel.setActiveChatThread(thread)
                        }
                    )
                }

                BottomNavTab.PACKAGES -> {
                    PackagesScreen(
                        currentProfile = effectiveUserProfile,
                        onSelectPackage = { pkg ->
                            val nowMs = System.currentTimeMillis()
                            val activatedAtMs = if (pkg.tier == "Free") 0L else nowMs
                            val expiresAtMs = if (pkg.tier == "Free") 0L else pkg.computeExpiryMillis(nowMs)
                            navViewModel.setLocalPackageOverride(
                                tier = pkg.tier,
                                pendingTier = "",
                                interestsRemaining = pkg.interestsCount,
                                contactsRemaining = pkg.contactsCount,
                                packageActivatedAt = activatedAtMs,
                                packageExpiresAt = expiresAtMs
                            )
                            scope.launch {
                                repository.purchasePackage(effectiveUserProfile.userId, pkg)
                                snackbarHostState.showSnackbar("Selected ${pkg.tier} package")
                            }
                        },
                        onSubmitPaymentRequest = { pkg, paymentMethod, senderPhone, transactionId ->
                            navViewModel.setLocalPackageOverride(
                                tier = null,
                                pendingTier = pkg.tier
                            )
                            scope.launch {
                                repository.submitPaymentRequest(
                                    profile = effectiveUserProfile,
                                    pkg = pkg,
                                    paymentMethod = paymentMethod,
                                    senderPhone = senderPhone,
                                    transactionId = transactionId
                                )
                                snackbarHostState.showSnackbar("Payment request submitted for admin verification!")
                            }
                        },
                        onGooglePlayPurchaseCompleted = { pkg, _, purchaseToken, _ ->
                            if (purchaseToken.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Google Play did not provide a purchase token. Restore the purchase and try again."
                                    )
                                }
                            } else {
                                scope.launch {
                                    repository.verifyGooglePlayPurchase(
                                        userId = effectiveUserProfile.userId,
                                        productId = pkg.productId,
                                        purchaseToken = purchaseToken
                                    ).onSuccess { verifiedProfile ->
                                        navViewModel.setLocalPackageOverride(
                                            tier = verifiedProfile.packageTier,
                                            pendingTier = "",
                                            interestsRemaining = verifiedProfile.interestsRemaining,
                                            contactsRemaining = verifiedProfile.contactsRemaining,
                                            packageActivatedAt = verifiedProfile.packageActivatedAt,
                                            packageExpiresAt = verifiedProfile.packageExpiresAt
                                        )
                                        snackbarHostState.showSnackbar(
                                            "${verifiedProfile.packageTier} Google Play membership verified and activated."
                                        )
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar(
                                            error.message ?: "Google Play purchase could not be verified. Please retry."
                                        )
                                    }
                                }
                            }
                        },
                        allCandidates = allRemoteProfiles,
                        onAdminChangeUserPackage = { targetUserId, targetProfileId, newTier, customInterests, customContacts, customDurationDays, adminNote ->
                            scope.launch {
                                val targetKey = targetProfileId.ifBlank { targetUserId }
                                val res = repository.adminChangeUserPackage(
                                    targetUserIdOrCode = targetKey,
                                    newTier = newTier,
                                    customInterests = customInterests,
                                    customContacts = customContacts,
                                    customDurationDays = customDurationDays,
                                    adminNote = adminNote
                                )
                                snackbarHostState.showSnackbar(
                                    if (res.isSuccess) "Updated package to $newTier" else "Failed to update package"
                                )
                            }
                        }
                    )
                }

                BottomNavTab.ME -> {
                    MyProfileTabContent(
                        profile = effectiveUserProfile,
                        savedProfiles = savedProfilesList,
                        isUploadingPhoto = uiState.isUploadingProfilePhoto,
                        onChangePhoto = { navViewModel.setShowChangePhotoDialog(true) },
                        onEditProfile = { navViewModel.setShowEditWizard(true) },
                        onUpgradeClick = { navViewModel.navigateToTab(BottomNavTab.PACKAGES) },
                        onPreviewProfile = { navViewModel.setSelectedProfile(effectiveUserProfile) },
                        onSelectSavedProfile = { saved -> navViewModel.setSelectedProfile(saved) },
                        onOpenVerificationDialog = { navViewModel.setShowVerificationDialog(true) },
                        onToggleAccountHidden = { hidden ->
                            val newStatus = if (hidden) "HIDDEN" else "ACTIVE"
                            navViewModel.setLocalAccountStatusOverride(newStatus)
                            scope.launch {
                                repository.toggleHideAccount(currentUserId, hidden)
                            }
                        },
                        onDeleteAccountConfirmed = {
                            scope.launch {
                                repository.deleteUserAccount(currentUserId)
                                onSignOut()
                            }
                        },
                        onEmailUpdated = { newEmail ->
                            navViewModel.setLocalEmailOverride(newEmail)
                            scope.launch {
                                repository.updateUserEmail(currentUserId, newEmail)
                            }
                        },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}
