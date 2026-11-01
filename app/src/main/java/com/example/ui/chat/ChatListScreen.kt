package com.example.ui.chat

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.UserProfile
import com.example.data.repository.MatrimonyRepository
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.tr
import com.example.ui.theme.trNum

internal fun isPlaceholderChatName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isEmpty() ||
        trimmed.equals("Member", ignoreCase = true) ||
        trimmed.equals("সদস্য", ignoreCase = true) ||
        trimmed.equals("Candidate", ignoreCase = true) ||
        trimmed.equals("Candidate Match", ignoreCase = true) ||
        trimmed.startsWith("Member (", ignoreCase = true) ||
        trimmed.startsWith("সদস্য (")
}

@Composable
fun ChatListScreen(
    chatThreads: List<ChatThread>,
    currentUserId: String,
    candidates: List<UserProfile> = emptyList(),
    receivedInterests: List<Interest> = emptyList(),
    sentInterests: List<Interest> = emptyList(),
    unreadCountsByChatId: Map<String, Int> = emptyMap(),
    onSelectChat: (ChatThread) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MatrimonyRepository.create(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("chat_list_screen")
    ) {
        if (chatThreads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = tr("No private chats yet", "এখনো কোনো প্রাইভেট চ্যাট নেই"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            "When another member accepts your proposal or you accept theirs, you can chat privately here.",
                            "যখন অন্য কোনো সদস্য আপনার আগ্রহ প্রস্তাব গ্রহণ করবেন অথবা আপনি তাদের আগ্রহ প্রস্তাব গ্রহণ করবেন, তখন এখানে প্রাইভেট চ্যাট করতে পারবেন।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatThreads, key = { it.chatId.ifBlank { it.id } }) { thread ->
                    val chatKey = thread.chatId.ifBlank { thread.id }
                    ChatThreadRowItem(
                        thread = thread,
                        currentUserId = currentUserId,
                        candidates = candidates,
                        receivedInterests = receivedInterests,
                        sentInterests = sentInterests,
                        unreadCount = unreadCountsByChatId[chatKey] ?: 0,
                        repo = repo,
                        onSelectChat = onSelectChat
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatThreadRowItem(
    thread: ChatThread,
    currentUserId: String,
    candidates: List<UserProfile>,
    receivedInterests: List<Interest>,
    sentInterests: List<Interest>,
    unreadCount: Int,
    repo: MatrimonyRepository,
    onSelectChat: (ChatThread) -> Unit
) {
    val isBangla = LocalLanguageController.current.isBangla
    val otherId = remember(thread.participantIds, currentUserId) {
        thread.participantIds.firstOrNull { it != currentUserId }.orEmpty()
    }

    val candidateFromList = remember(otherId, candidates) {
        if (otherId.isBlank()) null
        else candidates.find { it.userId == otherId || it.id == otherId || it.profileCode == otherId }
    }

    val interestMatch = remember(otherId, receivedInterests, sentInterests) {
        if (otherId.isBlank()) null
        else (receivedInterests + sentInterests).firstOrNull {
            it.senderId == otherId || it.receiverId == otherId
        }
    }

    val context = LocalContext.current
    var fetchedProfile by remember(otherId) {
        mutableStateOf(repo.getMemoryCachedProfile(otherId))
    }
    var fetchedMessageSenderName by remember(thread.chatId, otherId) { mutableStateOf("") }

    LaunchedEffect(otherId, candidateFromList, thread.otherUserName) {
        if (otherId.isNotBlank() && candidateFromList == null && fetchedProfile == null) {
            val profile = repo.getUserProfileById(otherId)
            if (profile != null) {
                fetchedProfile = profile
            }
            if (profile == null && isPlaceholderChatName(thread.otherUserName) && thread.chatId.isNotBlank()) {
                runCatching {
                    val messages = SupabaseClient.fetchChatMessages(thread.chatId)
                    val senderMsg = messages.firstOrNull { msg ->
                        msg.senderId == otherId && !isPlaceholderChatName(msg.senderName)
                    }
                    senderMsg?.senderName?.let {
                        fetchedMessageSenderName = it
                    }
                }
            }
        }
    }

    val resolvedProfile = candidateFromList ?: fetchedProfile

    val interestOtherName = remember(interestMatch, otherId) {
        if (interestMatch == null) ""
        else if (interestMatch.senderId == otherId) interestMatch.senderName
        else interestMatch.receiverName
    }
    val interestOtherPhoto = remember(interestMatch, otherId) {
        if (interestMatch == null) ""
        else if (interestMatch.senderId == otherId) interestMatch.senderPhoto
        else interestMatch.receiverPhoto
    }

    val resolvedEnglishName = when {
        ! resolvedProfile?.displayName.isNullOrBlank() -> resolvedProfile?.displayName.orEmpty()
        !isPlaceholderChatName(thread.otherUserName) -> thread.otherUserName
        !isPlaceholderChatName(interestOtherName) -> interestOtherName
        !isPlaceholderChatName(fetchedMessageSenderName) -> fetchedMessageSenderName
        else -> ""
    }

    val resolvedBanglaName = when {
        !resolvedProfile?.displayNameBangla.isNullOrBlank() -> resolvedProfile?.displayNameBangla.orEmpty()
        thread.otherUserNameBangla.isNotBlank() -> thread.otherUserNameBangla
        else -> ""
    }

    val resolvedFormattedId = when {
        resolvedProfile != null && UserProfile.isValidFormattedUserId(resolvedProfile.getFormattedUserId()) ->
            resolvedProfile.getFormattedUserId()
        UserProfile.isValidFormattedUserId(thread.otherUserFormattedId) ->
            thread.otherUserFormattedId
        UserProfile.isValidFormattedUserId(otherId) ->
            otherId
        else -> ""
    }

    val resolvedPhoto = when {
        !resolvedProfile?.photoUrl.isNullOrBlank() -> resolvedProfile?.photoUrl.orEmpty()
        thread.otherUserPhoto.isNotBlank() -> thread.otherUserPhoto
        interestOtherPhoto.isNotBlank() -> interestOtherPhoto
        else -> ""
    }

    val displayName = when {
        isBangla && resolvedBanglaName.isNotBlank() -> resolvedBanglaName
        resolvedEnglishName.isNotBlank() -> resolvedEnglishName
        resolvedFormattedId.isNotBlank() -> resolvedFormattedId
        else -> tr("Ghotok Member", "ঘটক সদস্য")
    }

    val enrichedThread = remember(
        thread,
        resolvedEnglishName,
        resolvedBanglaName,
        resolvedFormattedId,
        resolvedPhoto
    ) {
        thread.copy(
            otherUserName = resolvedEnglishName.ifBlank { resolvedFormattedId.ifBlank { thread.otherUserName } },
            otherUserNameBangla = resolvedBanglaName,
            otherUserFormattedId = resolvedFormattedId,
            otherUserPhoto = resolvedPhoto
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectChat(enrichedThread) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val defaultAvatarRes = UserProfile.getDefaultAvatarRes(
                gender = resolvedProfile?.gender.orEmpty().ifBlank { candidates.firstOrNull()?.gender.orEmpty() },
                formattedId = resolvedFormattedId.ifBlank { otherId }
            )
            val optimizedResolvedPhoto = UserProfile.optimizePhotoUrl(resolvedPhoto)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (optimizedResolvedPhoto.isNotEmpty()) {
                    AsyncImage(
                        model = optimizedResolvedPhoto,
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
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (resolvedFormattedId.isNotBlank() && displayName != resolvedFormattedId) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = resolvedFormattedId,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = thread.lastMessage.ifEmpty { tr("Start conversation...", "কথোপকথন শুরু করুন...") },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (unreadCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = Color(0xFFD32F2F),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("chat_thread_unread_badge")
                ) {
                    Text(
                        text = trNum(unreadCount),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
