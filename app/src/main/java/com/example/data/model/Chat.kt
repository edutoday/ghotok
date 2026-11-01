package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val interestId: String = "",
    val participantIds: List<String> = emptyList(),
    val otherUserName: String = "",
    val otherUserNameBangla: String = "",
    val otherUserFormattedId: String = "",
    val otherUserPhoto: String = "",
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val participantIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
