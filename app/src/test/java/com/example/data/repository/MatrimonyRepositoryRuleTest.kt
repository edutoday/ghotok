package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdminNotification
import com.example.data.model.MembershipPackages
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrimonyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveProfileAndSubmitVerificationAndPaymentQueues_authenticatedUser_succeeds() = runBlocking {
    val uid = signInTestUser("user_${UUID.randomUUID().toString().take(8)}@test.com")
    val repository = MatrimonyRepository(firestore)

    val initialProfile = UserProfile(
      userId = uid,
      displayName = "Tanvir Ahmed",
      gender = "Male",
      age = 28,
      presentDivision = "Dhaka"
    )
    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.saveUserProfile(initialProfile) }
    assertTrue("Expected saveUserProfile to succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

    val savedProfile = checkNotNull(saveResult.getOrNull())
    assertEquals("UNSUBMITTED", savedProfile.verificationStatus)
    assertEquals("ACTIVE", savedProfile.accountStatus)
    assertEquals("Free", savedProfile.packageTier)

    // Submit Verification Request to Admin Queue
    val verifyResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.submitVerificationRequest(
        userId = uid,
        docType = "National ID (NID)",
        idNumber = "1998123456789",
        docUri = "https://res.cloudinary.com/demo/image/upload/nid.jpg"
      )
    }
    assertTrue("Expected submitVerificationRequest to succeed: ${verifyResult.exceptionOrNull()}", verifyResult.isSuccess)

    val verifyDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
      firestore.collection("verificationRequests").document(savedProfile.getFormattedUserId()).get().await()
    }
    assertTrue(verifyDoc.exists())
    assertEquals("PENDING", verifyDoc.getString("status"))

    // Submit Payment Request to Admin Queue
    val goldPkg = MembershipPackages.ALL.first { it.tier == "Gold" }
    val paymentResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.submitPaymentRequest(
        profile = savedProfile,
        pkg = goldPkg,
        paymentMethod = "bKash",
        senderPhone = "01711223344",
        transactionId = "TRX99887766"
      )
    }
    assertTrue("Expected submitPaymentRequest to succeed: ${paymentResult.exceptionOrNull()}", paymentResult.isSuccess)
    val requestId = paymentResult.getOrThrow()

    val paymentDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
      firestore.collection("paymentRequests").document(requestId).get().await()
    }
    assertTrue(paymentDoc.exists())
    assertEquals("PENDING", paymentDoc.getString("status"))
    assertEquals("Gold", paymentDoc.getString("packageTier"))

    val updatedUserDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
      firestore.collection("users").document(savedProfile.getFormattedUserId()).get().await()
    }
    assertEquals("PENDING", updatedUserDoc.getString("verificationStatus"))
    assertEquals("Gold", updatedUserDoc.getString("pendingPackageTier"))
    assertEquals("Free", updatedUserDoc.getString("packageTier"))
  }

  @Test
  fun selfGrantVerifiedOrPaidPackage_nonAdminUser_failsWithPermissionDenied() = runBlocking {
    val uid = signInTestUser("escalate_${UUID.randomUUID().toString().take(8)}@test.com")
    val docId = "G699999"

    var thrown: FirebaseFirestoreException? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(docId).set(
          mapOf(
            "userId" to uid,
            "profileCode" to docId,
            "displayName" to "Spoof User",
            "gender" to "Male",
            "isVerified" to true,
            "verificationStatus" to "VERIFIED",
            "packageTier" to "Diamond"
          )
        ).await()
      }
    } catch (e: Exception) {
      thrown = e.unwrapFirestoreException()
    }
    assertNotNull("Expected FirebaseFirestoreException when self-granting verified/paid tier", thrown)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, thrown?.code)
  }

  @Test
  fun nonAdminUser_writeNotificationOrBroadcast_failsWithPermissionDeniedAndSegmentMatchingWorks() = runBlocking {
    val uid = signInTestUser("member_${UUID.randomUUID().toString().take(8)}@test.com")

    var notifThrown: FirebaseFirestoreException? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("notifications").document("spoof_notif").set(
          mapOf(
            "type" to "PACKAGE_APPROVED",
            "targetUserId" to uid,
            "title" to "Spoofed",
            "createdAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
    } catch (e: Exception) {
      notifThrown = e.unwrapFirestoreException()
    }
    assertNotNull("Expected PERMISSION_DENIED when non-admin writes to notifications", notifThrown)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, notifThrown?.code)

    val unverifiedDhakaProfile = UserProfile(
      userId = uid,
      profileCode = "G600001",
      displayName = "Rahim",
      isVerified = false,
      packageTier = "Free",
      presentDivision = "Dhaka"
    )
    val broadcastFree = AdminNotification(
      id = "b1",
      type = "BROADCAST",
      targetSegment = "Free-Tier Members (Discount & Upgrade campaign)",
      titleBn = "গোল্ডেন নিকাহ ও ডায়মন্ড প্যাকেজে বিশেষ ২৫% ছাড়!"
    )
    val broadcastDiamond = AdminNotification(
      id = "b2",
      type = "BROADCAST",
      targetSegment = "Diamond VIP Members (Exclusive matchmaker invites)"
    )
    val directApproval = AdminNotification(
      id = "n1",
      type = "ACCOUNT_APPROVED",
      targetProfileCode = "G600001"
    )
    assertTrue(broadcastFree.matchesUser(unverifiedDhakaProfile, uid))
    assertFalse(broadcastDiamond.matchesUser(unverifiedDhakaProfile, uid))
    assertTrue(directApproval.matchesUser(unverifiedDhakaProfile, uid))
  }

  @Test
  fun unauthenticatedUser_queryUsers_failsWithPermissionDenied() = runBlocking {
    auth.signOut()

    var thrown: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore.collection("users").get().await()
      }
    } catch (e: Exception) {
      thrown = e.unwrapFirestoreException()
    }
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated users query", thrown)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, thrown?.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
