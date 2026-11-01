package com.example.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
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
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
private fun formatMessageSentTime(msg: ChatMessage, isBangla: Boolean): String {
    val millis = msg.createdAt?.toDate()?.time
        ?: msg.messageId.removePrefix("msg_").toLongOrNull()
        ?: return if (isBangla) "এইমাত্র" else "Just now"

    val msgCal = Calendar.getInstance().apply { timeInMillis = millis }
    val nowCal = Calendar.getInstance()
    val isSameDay = msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

    val pattern = if (isSameDay) "hh:mm a" else "dd MMM, hh:mm a"
    val formatted = SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(millis))
    return if (isBangla) {
        trNum(formatted)
            .replace("AM", "পূর্বাহ্ন")
            .replace("PM", "অপরাহ্ন")
    } else {
        formatted
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    currentProfile: UserProfile,
    otherUserName: String,
    otherUserFormattedId: String = "",
    otherUserPhoto: String = "",
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToPackages: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val canChat = !currentProfile.packageTier.equals("Free", ignoreCase = true) && !currentProfile.isPackageExpired()
    val isBangla = LocalLanguageController.current.isBangla
    val listState = rememberLazyListState()

    val effectiveHeaderName = remember(otherUserName, otherUserFormattedId, messages, currentProfile.userId) {
        val msgSenderName = messages.firstOrNull {
            it.senderId != currentProfile.userId && !isPlaceholderChatName(it.senderName)
        }?.senderName.orEmpty()
        when {
            !isPlaceholderChatName(otherUserName) -> otherUserName
            msgSenderName.isNotBlank() -> msgSenderName
            otherUserFormattedId.isNotBlank() -> otherUserFormattedId
            else -> ""
        }
    }

    var hasInitiallyScrolled by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (!hasInitiallyScrolled) {
                listState.scrollToItem(messages.size - 1)
                hasInitiallyScrolled = true
                previousMessageCount = messages.size
            } else if (messages.size > previousMessageCount) {
                listState.animateScrollToItem(messages.size - 1)
                previousMessageCount = messages.size
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val defaultAvatarRes = UserProfile.getDefaultAvatarRes(
                        formattedId = otherUserFormattedId,
                        oppositeOfGender = currentProfile.gender
                    )
                    val optimizedOtherPhoto = UserProfile.optimizePhotoUrl(otherUserPhoto)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (optimizedOtherPhoto.isNotEmpty()) {
                            AsyncImage(
                                model = optimizedOtherPhoto,
                                contentDescription = effectiveHeaderName,
                                placeholder = painterResource(id = defaultAvatarRes),
                                error = painterResource(id = defaultAvatarRes),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = defaultAvatarRes),
                                contentDescription = effectiveHeaderName.ifBlank { tr("Profile Photo", "প্রোফাইল ছবি") },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = effectiveHeaderName.ifBlank {
                                    if (otherUserFormattedId.isNotBlank()) otherUserFormattedId
                                    else tr("Ghotok Member", "ঘটক সদস্য")
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (otherUserFormattedId.isNotBlank() && effectiveHeaderName != otherUserFormattedId) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = otherUserFormattedId,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = tr("Private Chat", "প্রাইভেট চ্যাট"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "পেছনে"))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr(
                            "No messages yet. Send respectful matrimonial greetings to introduce family background.",
                            "এখনো কোনো বার্তা নেই। পারিবারিক পরিচয় দিতে মার্জিত শুভেচ্ছা বার্তা পাঠান।"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.messageId.ifBlank { it.id.ifBlank { "${it.senderId}_${it.text.hashCode()}" } } }
                    ) { msg ->
                        val isMe = msg.senderId == currentProfile.userId
                        val sentTimeText = formatMessageSentTime(msg, isBangla)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                ),
                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    if (!isMe) {
                                        val senderLabel = when {
                                            !isPlaceholderChatName(effectiveHeaderName) -> effectiveHeaderName
                                            !isPlaceholderChatName(msg.senderName) -> msg.senderName
                                            otherUserFormattedId.isNotBlank() -> otherUserFormattedId
                                            else -> tr("Ghotok Member", "ঘটক সদস্য")
                                        }
                                        Text(
                                            text = senderLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = sentTimeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .testTag("chat_message_timestamp")
                            )
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!canChat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            tr(
                                "Direct messaging requires a Silver, Gold, or Diamond package.",
                                "সরাসরি মেসেজিংয়ের জন্য সিলভার, গোল্ড বা ডায়মন্ড প্যাকেজ প্রয়োজন।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToPackages,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(tr("Upgrade Package to Chat", "চ্যাট করতে প্যাকেজ আপগ্রেড করুন"))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text(tr("Write a message...", "মেসেজ লিখুন...")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (textInput.trim().isNotEmpty()) {
                                onSendMessage(textInput.trim())
                                textInput = ""
                            }
                        },
                        enabled = textInput.trim().isNotEmpty(),
                        modifier = Modifier
                            .background(
                                color = if (textInput.trim().isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .testTag("send_chat_message_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = tr("Send", "পাঠান"),
                            tint = if (textInput.trim().isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
