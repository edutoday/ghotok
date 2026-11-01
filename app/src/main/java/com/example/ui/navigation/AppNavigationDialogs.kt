package com.example.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AdminNotification
import com.example.data.model.ChatThread
import com.example.data.model.Interest
import com.example.data.model.UserProfile
import com.example.ui.profile.MeTopBarMenuButton
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.OnGoldContainer
import com.example.ui.theme.ThemeToggleSwitch
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private val VerifiedBadgeBlue = Color(0xFF1D4ED8)

@Composable
fun GhotokNotificationsDialog(
    matchedAdminNotifications: List<AdminNotification>,
    readNotificationIds: Set<String>,
    lastNotifReadTime: Long,
    pendingInterests: List<Interest>,
    unreadThreadsList: List<ChatThread>,
    onDismiss: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onNavigateToTab: (BottomNavTab) -> Unit,
    onOpenVerification: () -> Unit,
    onOpenChatThread: (ChatThread) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = tr("Notifications & Updates", "নোটিফিকেশন ও আপডেট"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            val hasAnyNotifs = matchedAdminNotifications.isNotEmpty() ||
                pendingInterests.isNotEmpty() ||
                unreadThreadsList.isNotEmpty()

            if (!hasAnyNotifs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tr(
                            "You're all caught up! No new notifications right now.",
                            "এই মুহূর্তে কোনো নতুন নোটিফিকেশন নেই।"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(matchedAdminNotifications, key = { it.id.ifBlank { it.title + it.message } }) { notif ->
                        val isBroadcast = notif.type.equals("BROADCAST", ignoreCase = true)
                        val isUnreadItem = !readNotificationIds.contains(notif.id) &&
                            ((notif.createdAt?.toDate()?.time ?: 0L) > lastNotifReadTime || !notif.id.startsWith("synth_"))
                        val bgColor = when (notif.type.uppercase()) {
                            "PACKAGE_APPROVED", "PACKAGE_CHANGED_BY_ADMIN" -> GoldSecondary.copy(alpha = 0.14f)
                            "VERIFICATION_APPROVED", "KYC_APPROVED", "ACCOUNT_APPROVED" -> EmeraldTertiary.copy(alpha = 0.12f)
                            "INTEREST_RECEIVED", "INTEREST_ACCEPTED" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            "BROADCAST" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val iconTint = when (notif.type.uppercase()) {
                            "PACKAGE_APPROVED", "PACKAGE_CHANGED_BY_ADMIN" -> GoldSecondary
                            "VERIFICATION_APPROVED", "KYC_APPROVED", "ACCOUNT_APPROVED" -> EmeraldTertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMarkNotificationRead(notif.id)
                                    onDismiss()
                                    when (notif.type.uppercase()) {
                                        "PACKAGE_APPROVED", "PACKAGE_CHANGED_BY_ADMIN" -> onNavigateToTab(BottomNavTab.PACKAGES)
                                        "VERIFICATION_APPROVED", "KYC_APPROVED", "VERIFICATION_REJECTED", "KYC_REJECTED" -> onOpenVerification()
                                        "ACCOUNT_APPROVED" -> onNavigateToTab(BottomNavTab.ME)
                                        "INTEREST_RECEIVED" -> onNavigateToTab(BottomNavTab.PROPOSALS)
                                        "INTEREST_ACCEPTED" -> onNavigateToTab(BottomNavTab.CHATS)
                                        "BROADCAST" -> {
                                            if (notif.targetSegment.contains("Free", ignoreCase = true) ||
                                                notif.title.contains("Package", ignoreCase = true) ||
                                                notif.title.contains("Discount", ignoreCase = true)
                                            ) {
                                                onNavigateToTab(BottomNavTab.PACKAGES)
                                            }
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            border = if (isUnreadItem) BorderStroke(1.dp, iconTint.copy(alpha = 0.45f)) else null,
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            val isBangla = LocalLanguageController.current.isBangla
                            val displayTitle = notif.resolvedTitle(isBangla)
                            val displayBody = notif.resolvedBody(isBangla)
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when (notif.type.uppercase()) {
                                                "PACKAGE_APPROVED", "PACKAGE_CHANGED_BY_ADMIN" -> Icons.Filled.WorkspacePremium
                                                "VERIFICATION_APPROVED", "KYC_APPROVED", "ACCOUNT_APPROVED" -> Icons.Filled.Verified
                                                "INTEREST_RECEIVED", "INTEREST_ACCEPTED" -> Icons.Filled.Favorite
                                                else -> Icons.Filled.Notifications
                                            },
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = displayTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isBroadcast) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = tr("Notice", "ঘোষণা"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (displayBody.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = displayBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                NotificationTimestampRow(timestamp = notif.createdAt)
                            }
                        }
                    }

                    if (pendingInterests.isNotEmpty()) {
                        item(key = "pending_interests_header") {
                            Text(
                                text = tr(
                                    "New Proposals (${pendingInterests.size})",
                                    "নতুন বিবাহের প্রস্তাব (${trNum(pendingInterests.size)})"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(pendingInterests.take(5), key = { "interest_${it.id}" }) { interest ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onNavigateToTab(BottomNavTab.PROPOSALS)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${interest.senderName} (${interest.senderAge} yrs)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${interest.senderProfession} • ${interest.senderUpazila}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    NotificationTimestampRow(
                                        timestamp = interest.updatedAt ?: interest.createdAt
                                    )
                                }
                            }
                        }
                    }

                    if (unreadThreadsList.isNotEmpty()) {
                        item(key = "unread_chats_header") {
                            Text(
                                text = tr(
                                    "Unread Messages (${unreadThreadsList.size})",
                                    "নতুন মেসেজ (${trNum(unreadThreadsList.size)})"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(unreadThreadsList.take(5), key = { "chat_${it.chatId.ifBlank { it.id }}" }) { thread ->
                            val otherName = thread.otherUserName.ifBlank {
                                thread.otherUserFormattedId.ifBlank { "Ghotok Member" }
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onOpenChatThread(thread)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = otherName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = thread.lastMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    NotificationTimestampRow(timestamp = thread.updatedAt)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pendingInterests.isNotEmpty()) {
                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToTab(BottomNavTab.PROPOSALS)
                    }
                ) {
                    Text(tr("View Proposals", "প্রস্তাব দেখুন"))
                }
            } else if (unreadThreadsList.isNotEmpty()) {
                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToTab(BottomNavTab.CHATS)
                    }
                ) {
                    Text(tr("Open Chats", "চ্যাট খুলুন"))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(tr("Close", "বন্ধ করুন"))
                }
            }
        },
        dismissButton = {
            if (pendingInterests.isNotEmpty() || unreadThreadsList.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(tr("Close", "বন্ধ করুন"))
                }
            }
        }
    )
}

@Composable
fun ChangeProfilePhotoDialog(
    currentPhotoUrl: String,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onPickGalleryPhoto: () -> Unit,
    onSavePhotoUrl: (String) -> Unit
) {
    var photoUrlInput by rememberSaveable { mutableStateOf(currentPhotoUrl) }
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr("Update Profile Photo", "প্রোফাইল ছবি পরিবর্তন করুন"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPickGalleryPhoto,
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_gallery_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Uploading...", "আপলোড হচ্ছে..."))
                    } else {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Choose Photo from Gallery", "গ্যালারি থেকে ছবি নির্বাচন করুন"))
                    }
                }

                HorizontalDivider()

                Text(
                    text = tr("Or paste a direct image URL:", "অথবা ছবির সরাসরি লিংক দিন:"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = photoUrlInput,
                    onValueChange = { photoUrlInput = it },
                    label = { Text("https://...") },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_photo_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSavePhotoUrl(photoUrlInput.trim()) },
                enabled = !isUploading && photoUrlInput.trim().isNotBlank(),
                modifier = Modifier.testTag("save_profile_photo_url_button")
            ) {
                Text(tr("Save Photo", "সংরক্ষণ করুন"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text(tr("Cancel", "বাতিল"))
            }
        }
    )
}

@Composable
fun MyProfileTabContent(
    profile: UserProfile,
    savedProfiles: List<UserProfile> = emptyList(),
    isUploadingPhoto: Boolean = false,
    onChangePhoto: () -> Unit = {},
    onEditProfile: () -> Unit,
    onUpgradeClick: () -> Unit,
    onPreviewProfile: () -> Unit,
    onSelectSavedProfile: (UserProfile) -> Unit = {},
    onOpenVerificationDialog: () -> Unit,
    onToggleAccountHidden: (Boolean) -> Unit = {},
    onDeleteAccountConfirmed: () -> Unit = {},
    onEmailUpdated: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val isBangla = LocalLanguageController.current.isBangla
    var isIdCopied by remember { mutableStateOf(false) }
    val formattedUserId = remember(profile) { profile.getFormattedUserId() }
    val defaultAvatarRes = profile.getDefaultAvatarRes()
    val effectivePhotoUrl = profile.getEffectivePhotoUrl()
    val isAccountHidden = profile.accountStatus.equals("HIDDEN", ignoreCase = true)
    val presentUpazilaDistrictText = remember(
        profile.presentUpazila,
        profile.presentDistrict,
        profile.locationDistrict
    ) {
        listOf(
            profile.presentUpazila.trim(),
            profile.presentDistrict.trim().ifEmpty { profile.locationDistrict.trim() }
        ).filter { it.isNotBlank() }
    }.map { trOption(it) }.joinToString(", ").ifBlank { tr("Not specified", "উল্লেখ নেই") }
    val completionPercent = remember(profile) { profile.calculateCompletionPercentage() }
    val missingFields = remember(profile, isBangla) { profile.getMissingProfileFields(isBangla) }
    val animatedCompletion by animateFloatAsState(
        targetValue = (completionPercent / 100f).coerceIn(0f, 1f),
        label = "profileCompletionProgress"
    )
    val completionColor = when {
        completionPercent >= 80 -> EmeraldTertiary
        completionPercent >= 50 -> GoldSecondary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MeTopBarMenuButton(
                        profile = profile,
                        isAccountHidden = isAccountHidden,
                        onToggleAccountHidden = onToggleAccountHidden,
                        onDeleteAccountConfirmed = onDeleteAccountConfirmed,
                        onEmailUpdated = onEmailUpdated,
                        onShowMessage = onShowMessage
                    )
                }

                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clickable(onClick = onChangePhoto)
                        .testTag("change_profile_photo_avatar"),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (effectivePhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = effectivePhotoUrl,
                            contentDescription = profile.displayName,
                            placeholder = painterResource(id = defaultAvatarRes),
                            error = painterResource(id = defaultAvatarRes),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, GoldSecondary, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = defaultAvatarRes),
                            contentDescription = profile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, GoldSecondary, CircleShape)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .border(2.dp, Color.White, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile.isVerified || profile.verificationStatus == "VERIFIED") {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = VerifiedBadgeBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Ghotok ID", formattedUserId))
                        isIdCopied = true
                        onShowMessage(
                            if (isBangla) "বায়োডাটা আইডি কপি করা হয়েছে: $formattedUserId"
                            else "Biodata ID copied: $formattedUserId"
                        )
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.testTag("me_profile_id_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ID: $formattedUserId",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = if (isIdCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = tr("Copy ID", "আইডি কপি করুন"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(13.dp)
                                .testTag("copy_me_user_id_button")
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${trNum(profile.age)} ${tr("yrs", "বছর")} • ${trOption(profile.profession)} • $presentUpazilaDistrictText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                Surface(
                    onClick = {
                        if (completionPercent < 100) {
                            if (missingFields.size == 1 && profile.photoUrl.isBlank()) {
                                onChangePhoto()
                            } else {
                                onEditProfile()
                            }
                        }
                    },
                    enabled = completionPercent < 100,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, completionColor.copy(alpha = 0.30f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_completion_section")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Profile Completion", "প্রোফাইল সম্পন্ন"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = completionColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${trNum(completionPercent)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = completionColor,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                        .testTag("profile_completion_percentage_text")
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { animatedCompletion },
                            color = completionColor,
                            trackColor = completionColor.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .testTag("profile_completion_progress_bar")
                        )

                        if (completionPercent < 100 && missingFields.isNotEmpty()) {
                            val previewMissing = missingFields.take(3).joinToString(", ")
                            val moreCount = (missingFields.size - 3).coerceAtLeast(0)
                            val suffix = if (moreCount > 0) {
                                if (isBangla) " +${trNum(moreCount)}টি" else " +$moreCount more"
                            } else {
                                ""
                            }
                            Text(
                                text = "${tr("Missing: ", "বাকি আছে: ")}$previewMissing$suffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = tr(
                                        "100% Complete • Your biodata is fully completed!",
                                        "১০০% সম্পন্ন • অভিনন্দন, আপনার বায়োডাটা সম্পূর্ণ!"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldTertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        val isProfileVerified = profile.isVerified || profile.verificationStatus.equals("VERIFIED", ignoreCase = true)

        if (!isProfileVerified) {
            Spacer(Modifier.height(14.dp))

            // Verification Status Card (Hidden once verified)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verification_section_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Badge,
                                contentDescription = null,
                                tint = VerifiedBadgeBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = tr("Biodata Verification", "বায়োডাটা ভেরিফিকেশন"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tr("Verify with NID / Passport for Blue Badge", "জাতীয় পরিচয়পত্র দিয়ে ভেরিফায়েড ব্যাজ নিন"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val statusBadgeKey = when {
                            profile.verificationStatus.equals("PENDING", ignoreCase = true) -> "PENDING"
                            profile.verificationStatus.equals("REJECTED", ignoreCase = true) -> "REJECTED"
                            else -> "SUBMIT"
                        }
                        val statusBadgeLabel = when (statusBadgeKey) {
                            "PENDING" -> tr("Pending", "পেন্ডিং")
                            "REJECTED" -> tr("Rejected", "বাতিল")
                            else -> tr("Submit", "জমা দিন")
                        }
                        val statusBadgeColor = when (statusBadgeKey) {
                            "PENDING" -> GoldSecondary
                            "REJECTED" -> MaterialTheme.colorScheme.error
                            else -> VerifiedBadgeBlue
                        }
                        val canTapVerificationBadge = statusBadgeKey == "SUBMIT" || statusBadgeKey == "REJECTED"
                        Surface(
                            onClick = {
                                if (canTapVerificationBadge) {
                                    onOpenVerificationDialog()
                                }
                            },
                            enabled = canTapVerificationBadge,
                            shape = RoundedCornerShape(50),
                            color = statusBadgeColor.copy(alpha = 0.14f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = statusBadgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusBadgeColor,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    when {
                        profile.verificationStatus.equals("PENDING", ignoreCase = true) -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GoldContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.HourglassTop,
                                        contentDescription = null,
                                        tint = GoldSecondary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = tr("Verification Under Review", "ভেরিফিকেশন পর্যালোচনার যাচাই করা হচ্ছে"),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = OnGoldContainer
                                        )
                                        Text(
                                            text = tr(
                                                "Submitted: ${profile.verificationDocType} (${profile.verificationIdNumber}).",
                                                "জমা দেওয়া হয়েছে: ${profile.verificationDocType} (${profile.verificationIdNumber})।"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnGoldContainer
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = onOpenVerificationDialog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("submit_verification_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerifiedBadgeBlue)
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = tr("Submit Document for Verification Badge", "ভেরিফিকেশনের জন্য ডকুমেন্ট জমা দিন"),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Membership Section (Above Biodata Summary)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("membership_section_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onUpgradeClick,
                        shape = RoundedCornerShape(50),
                        color = GoldContainer,
                        border = BorderStroke(1.dp, GoldSecondary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = OnGoldContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${profile.packageTier} ${tr("Member", "সদস্য")}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnGoldContainer
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("me_membership_interests_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = tr("Proposals", "প্রস্তাব"),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = trNum(profile.interestsRemaining),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("me_membership_contacts_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = tr("Contacts", "কন্টাক্ট"),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = trNum(profile.contactsRemaining),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!profile.packageTier.equals("Free", ignoreCase = true)) {
                    val remainingDays = profile.getRemainingPackageDays()
                    val expiryDateStr = profile.getFormattedPackageExpiryDate()
                    if (expiryDateStr.isNotBlank() && remainingDays > 0) {
                        Text(
                            text = "${tr("Valid until", "মেয়াদ:")} ${trNum(expiryDateStr)} (${trNum(remainingDays)} ${tr("days left", "দিন বাকি")})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("me_membership_expiry_text")
                        )
                    }
                }

                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upgrade_membership_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tr("Upgrade Membership Package", "মেম্বারশিপ প্যাকেজ আপগ্রেড করুন"),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Biodata Summary Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr("Biodata Summary", "বায়োডাটা সার-সংক্ষেপ"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                ProfileSummaryRow(tr("Gender & Age", "লিঙ্গ ও বয়স"), "${trOption(profile.gender)}, ${trNum(profile.age)} ${tr("yrs", "বছর")} (${profile.getDisplayHeight()})")
                ProfileSummaryRow(tr("Marital Status", "বৈবাহিক অবস্থা"), trOption(profile.maritalStatus))
                ProfileSummaryRow(tr("Education", "শিক্ষাগত যোগ্যতা"), "${trOption(profile.education)}")
                ProfileSummaryRow(tr("Profession", "পেশা"), trOption(profile.profession))
                ProfileSummaryRow(tr("Monthly Income", "মাসিক আয়"), profile.monthlyIncome.ifBlank { tr("Not specified", "উল্লেখ নেই") })
                ProfileSummaryRow(tr("Overseas Status", "প্রবাসী তথ্য"), trOption(profile.overseasCountry))
                ProfileSummaryRow(tr("Present Address", "বর্তমান ঠিকানা"), presentUpazilaDistrictText)
                ProfileSummaryRow(tr("Contact Phone", "মোবাইল নম্বর"), profile.contactPhone.ifBlank { tr("Hidden", "লুকানো") })

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviewProfile,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("preview_biodata_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Preview", "প্রিভিউ"), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("edit_biodata_top_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Edit Biodata", "ইডিট করুন"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Log Out", "লগ আউট"))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NotificationTimestampRow(
    timestamp: Timestamp?,
    fallbackLabel: String = "Recent update • সাম্প্রতিক আপডেট"
) {
    val formatted = remember(timestamp) { formatNotificationDateTime(timestamp) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = formatted.ifBlank { fallbackLabel },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

fun formatNotificationDateTime(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return ""
    return runCatching {
        val formatter = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.ENGLISH)
        formatter.format(date)
    }.getOrDefault("")
}

@Composable
fun ProfileSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
