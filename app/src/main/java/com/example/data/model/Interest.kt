package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class Interest(
    @DocumentId val id: String = "",
    val interestId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderFormattedId: String = "",
    val receiverFormattedId: String = "",
    val senderName: String = "",
    val senderPhoto: String = "",
    val senderAge: Int = 0,
    val senderUpazila: String = "",
    val senderProfession: String = "",
    val senderVerified: Boolean = false,
    val receiverName: String = "",
    val receiverPhoto: String = "",
    val receiverAge: Int = 0,
    val receiverUpazila: String = "",
    val receiverProfession: String = "",
    val receiverVerified: Boolean = false,
    val matchScore: Int = 88,
    val status: String = "pending", // "pending", "accepted", "declined"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

