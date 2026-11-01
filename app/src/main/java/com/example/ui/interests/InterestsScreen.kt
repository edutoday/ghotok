package com.example.ui.interests

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.SampleData
import com.example.data.model.Interest
import com.example.data.model.UserProfile
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.toBanglaDigits
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import com.google.firebase.Timestamp

@Composable
fun InterestsScreen(
    receivedInterests: List<Interest>,
    sentInterests: List<Interest>,
    currentProfile: UserProfile? = null,
    candidates: List<UserProfile> = emptyList(),
    onSelectCandidate: (UserProfile) -> Unit = {},
    onAccept: (Interest) -> Unit,
    onDecline: (Interest) -> Unit,
    onOpenChat: (otherUserId: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val receivedList = remember(receivedInterests) {
        receivedInterests.filter { it.status == "pending" }
    }
    val sentList = remember(sentInterests) {
        sentInterests.filter { it.status != "accepted" }
    }
    val acceptedList = remember(receivedInterests, sentInterests) {
        (receivedInterests + sentInterests).filter { it.status == "accepted" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("interests_screen")
    ) {
        // Heading at top of the page matching Home screen header size
        Text(
            text = tr("Proposal", "প্রস্তাব"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("proposal_heading")
        )

        // 3 Tabs: Received, Sent, Accepted
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "${tr("Received", "প্রাপ্ত")} (${trNum(receivedList.size)})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                modifier = Modifier.testTag("tab_received")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "${tr("Sent", "প্রেরিত")} (${trNum(sentList.size)})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                modifier = Modifier.testTag("tab_sent")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        text = "${tr("Accepted", "গৃহীত")} (${trNum(acceptedList.size)})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                modifier = Modifier.testTag("tab_accepted")
            )
        }

        val currentList = when (selectedTab) {
            0 -> receivedList
            1 -> sentList
            else -> acceptedList
        }

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = when (selectedTab) {
                            0 -> tr("No received requests yet.", "এখনো কোনো প্রাপ্ত প্রস্তাব নেই।")
                            1 -> tr("No sent requests yet.", "এখনো কোনো প্রেরিত প্রস্তাব নেই।")
                            else -> tr("No accepted connections yet.", "এখনো কোনো গৃহীত প্রস্তাব নেই।")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val receivedIds = remember(receivedInterests) {
                receivedInterests.mapNotNull { it.interestId.ifBlank { it.id }.takeIf { k -> k.isNotBlank() } }.toSet()
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = currentList,
                    key = { it.interestId.ifBlank { it.id.ifBlank { "${it.senderId}_${it.receiverId}" } } },
                    contentType = { "interest_card" }
                ) { interest ->
                    val interestKey = interest.interestId.ifBlank { interest.id }
                    val isReceivedOrigin = if (interestKey.isNotEmpty()) {
                        receivedIds.contains(interestKey)
                    } else {
                        receivedInterests.contains(interest)
                    }
                    val otherUserId = if (isReceivedOrigin) interest.senderId else interest.receiverId

                    InterestCard(
                        interest = interest,
                        tabIndex = selectedTab,
                        isReceivedOrigin = isReceivedOrigin,
                        otherUserId = otherUserId,
                        currentProfile = currentProfile,
                        candidates = candidates,
                        onSelectCandidate = onSelectCandidate,
                        onAccept = { onAccept(interest) },
                        onDecline = { onDecline(interest) },
                        onOpenChat = { onOpenChat(otherUserId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InterestCard(
    interest: Interest,
    tabIndex: Int,
    isReceivedOrigin: Boolean,
    otherUserId: String,
    currentProfile: UserProfile?,
    candidates: List<UserProfile>,
    onSelectCandidate: (UserProfile) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenChat: () -> Unit
) {
    val otherFormattedId = if (isReceivedOrigin) interest.senderFormattedId else interest.receiverFormattedId
    val candidateFromList = remember(otherUserId, otherFormattedId, candidates) {
        candidates.find {
            it.userId == otherUserId ||
                it.id == otherUserId ||
                it.getFormattedUserId().equals(otherUserId, ignoreCase = true) ||
                it.profileCode.equals(otherUserId, ignoreCase = true) ||
                (otherFormattedId.isNotBlank() && (
                    it.id.equals(otherFormattedId, ignoreCase = true) ||
                        it.getFormattedUserId().equals(otherFormattedId, ignoreCase = true) ||
                        it.profileCode.equals(otherFormattedId, ignoreCase = true)
                    ))
        }
    }
    var fetchedProfile by remember(otherUserId, otherFormattedId) { mutableStateOf<UserProfile?>(null) }

    val rawAge = if (isReceivedOrigin) interest.senderAge else interest.receiverAge
    val rawUpazila = if (isReceivedOrigin) interest.senderUpazila else interest.receiverUpazila
    val rawProfession = if (isReceivedOrigin) interest.senderProfession else interest.receiverProfession

    LaunchedEffect(otherUserId, candidateFromList) {
        if (candidateFromList == null && otherUserId.isNotBlank()) {
            try {
                fetchedProfile = SupabaseClient.fetchUserProfile(otherUserId)
            } catch (_: Exception) {
            }
        }
    }

    val resolvedProfile = candidateFromList ?: fetchedProfile

    val displayName = (if (isReceivedOrigin) interest.senderName else interest.receiverName)
        .ifEmpty { resolvedProfile?.displayName ?: "Candidate" }
    val displayPhoto = (if (isReceivedOrigin) interest.senderPhoto else interest.receiverPhoto)
        .ifEmpty { resolvedProfile?.photoUrl ?: "" }
    val displayAge = rawAge.takeIf { it > 0 } ?: resolvedProfile?.age ?: 0
    val displayUpazila = rawUpazila.ifEmpty {
        resolvedProfile?.presentUpazila?.ifEmpty {
            resolvedProfile.presentDistrict.ifEmpty { resolvedProfile.locationDistrict }
        } ?: ""
    }
    val displayProfession = rawProfession.ifEmpty {
        resolvedProfile?.profession ?: ""
    }

    val isBangla = LocalLanguageController.current.isBangla

    // Line 1: Name, Age (e.g., Sayma Akter, 22)
    val lineOneText = if (displayAge > 0) {
        "$displayName, ${trNum(displayAge)}"
    } else {
        displayName
    }

    // Line 2: Upazila, Profession (e.g., Banani, Student)
    val lineTwoText = listOf(trOption(displayUpazila), trOption(displayProfession))
        .filter { it.isNotBlank() }
        .joinToString(", ")

    // Line 3: time, Match (e.g., Accepted 16 hours ago, 88% Match)
    val timePrefix = when (tabIndex) {
        0 -> tr("Received", "প্রাপ্ত")
        1 -> tr("Sent", "প্রেরিত")
        else -> tr("Accepted", "গৃহীত")
    }
    val relativeTime = formatRelativeTime(
        if (tabIndex == 2) (interest.updatedAt ?: interest.createdAt) else interest.createdAt,
        isBangla = isBangla
    )
    val matchScore = when {
        currentProfile != null && resolvedProfile != null ->
            SampleData.calculateCompatibility(currentProfile, resolvedProfile)
        interest.matchScore > 0 -> interest.matchScore
        else -> 88
    }
    val lineThreeText = "$timePrefix $relativeTime, ${trNum(matchScore)}% ${tr("Match", "মিল")}"

    val targetCandidateProfile = remember(
        resolvedProfile,
        otherUserId,
        displayName,
        displayPhoto,
        displayAge,
        displayUpazila,
        displayProfession
    ) {
        resolvedProfile ?: UserProfile(
            id = otherUserId,
            userId = otherUserId,
            displayName = displayName,
            photoUrl = displayPhoto,
            age = displayAge,
            presentUpazila = displayUpazila,
            profession = displayProfession
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCandidate(targetCandidateProfile) }
            .testTag("interest_card_${interest.interestId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultAvatarRes = UserProfile.getDefaultAvatarRes(
                    gender = resolvedProfile?.gender.orEmpty(),
                    formattedId = resolvedProfile?.getFormattedUserId().orEmpty().ifBlank { otherUserId },
                    oppositeOfGender = currentProfile?.gender.orEmpty()
                )
                val optimizedDisplayPhoto = UserProfile.optimizePhotoUrl(displayPhoto)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (optimizedDisplayPhoto.isNotEmpty()) {
                        AsyncImage(
                            model = optimizedDisplayPhoto,
                            contentDescription = displayName,
                            placeholder = painterResource(id = defaultAvatarRes),
                            error = painterResource(id = defaultAvatarRes),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = defaultAvatarRes),
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Line 1: Name, Age
                    Text(
                        text = lineOneText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (lineTwoText.isNotEmpty()) {
                        Text(
                            text = lineTwoText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = lineThreeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (tabIndex == 0 && interest.status == "pending") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("decline_button_${interest.interestId}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("Decline", "প্রত্যাখ্যান"), style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("accept_button_${interest.interestId}"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("Accept", "গ্রহণ করুন"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (tabIndex == 2 || interest.status == "accepted") {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Open Private Chat", "প্রাইভেট চ্যাট খুলুন"), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Timestamp?, isBangla: Boolean = false): String {
    if (timestamp == null) return if (isBangla) "এইমাত্র" else "just now"
    val diffMillis = (System.currentTimeMillis() - timestamp.toDate().time).coerceAtLeast(0L)
    val minutes = diffMillis / (1000 * 60)
    val hours = diffMillis / (1000 * 60 * 60)
    val days = diffMillis / (1000 * 60 * 60 * 24)
    return if (isBangla) {
        when {
            minutes < 2 -> "এইমাত্র"
            minutes < 60 -> "${toBanglaDigits(minutes.toString())} মিনিট আগে"
            hours == 1L -> "১ ঘণ্টা আগে"
            hours < 24 -> "${toBanglaDigits(hours.toString())} ঘণ্টা আগে"
            days == 1L -> "গতকাল"
            days < 7 -> "${toBanglaDigits(days.toString())} দিন আগে"
            else -> "${toBanglaDigits((days / 7).toString())} সপ্তাহ আগে"
        }
    } else {
        when {
            minutes < 2 -> "just now"
            minutes < 60 -> "$minutes minutes ago"
            hours == 1L -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }
}
