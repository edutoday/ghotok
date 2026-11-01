package com.example.data.supabase

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseRowMappersTest {

    @Test
    fun parseJsonArray_returnsParsedObjectsAndHandlesBlankOrInvalidInput() {
        assertTrue(SupabaseRowMappers.parseJsonArray("").isEmpty())
        assertTrue(SupabaseRowMappers.parseJsonArray("not json").isEmpty())

        val raw = """[{"id":"G600001","full_name":"Tanvir"},{"id":"B600002","full_name":"Nusrat"}]"""
        val parsed = SupabaseRowMappers.parseJsonArray(raw)
        assertEquals(2, parsed.size)
        assertEquals("G600001", parsed[0].optString("id"))
        assertEquals("Nusrat", parsed[1].optString("full_name"))
    }

    @Test
    fun extractPayloadJson_handlesObjectAndStringPayloads() {
        val nestedObjRow = JSONObject().apply {
            put("payload", JSONObject().put("fullName", "Arif"))
        }
        assertEquals("Arif", SupabaseRowMappers.extractPayloadJson(nestedObjRow).optString("fullName"))

        val stringPayloadRow = JSONObject().apply {
            put("payload", """{"fullName":"Sumaiya","age":25}""")
        }
        val extracted = SupabaseRowMappers.extractPayloadJson(stringPayloadRow)
        assertEquals("Sumaiya", extracted.optString("fullName"))
        assertEquals(25, extracted.optInt("age"))
    }

    @Test
    fun rowToUserProfile_mapsTopLevelAndPayloadFieldsCorrectly() {
        val futureExpiry = System.currentTimeMillis() + 15L * 24L * 60L * 60L * 1000L
        val activatedAt = System.currentTimeMillis() - 5L * 24L * 60L * 60L * 1000L
        val row = JSONObject().apply {
            put("id", "G600012")
            put("uid", "uid_12345")
            put("profile_code", "G600012")
            put("full_name", "Mahmudul Hasan")
            put("email", "mahmud@example.com")
            put("gender", "Male")
            put("religion", "Islam")
            put("marital_status", "Never Married")
            put("present_division", "Dhaka")
            put("present_district", "Dhaka")
            put("present_upazila", "Dhanmondi")
            put("highest_degree", "B.Sc in CSE")
            put("occupation", "Software Engineer")
            put("package_tier", "Gold")
            put("interests_remaining", 48)
            put("contacts_remaining", 22)
            put("is_verified", true)
            put(
                "payload",
                JSONObject().apply {
                    put("age", 28)
                    put("packageActivatedAt", activatedAt)
                    put("packageExpiresAt", futureExpiry)
                    put("savedProfileIds", JSONArray().put("B600001").put("B600005"))
                }
            )
        }

        val profile = SupabaseRowMappers.rowToUserProfile(row)
        assertNotNull(profile)
        profile!!
        assertEquals("G600012", profile.id)
        assertEquals("uid_12345", profile.userId)
        assertEquals("G600012", profile.profileCode)
        assertEquals("Mahmudul Hasan", profile.displayName)
        assertEquals("mahmud@example.com", profile.email)
        assertEquals("Male", profile.gender)
        assertEquals(28, profile.age)
        assertEquals("Dhaka", profile.presentDivision)
        assertEquals("Dhanmondi", profile.presentUpazila)
        assertEquals("B.Sc in CSE", profile.education)
        assertEquals("Software Engineer", profile.profession)
        assertEquals("Gold", profile.packageTier)
        assertEquals(48, profile.interestsRemaining)
        assertEquals(22, profile.contactsRemaining)
        assertFalse(profile.isPackageExpired())
        assertEquals(listOf("B600001", "B600005"), profile.savedProfileIds)
    }

    @Test
    fun rowToUserProfile_usesAgeFromPrivacySafeMatchmakingView() {
        val row = JSONObject().apply {
            put("id", "B600013")
            put("uid", "uid_12346")
            put("profile_code", "B600013")
            put("full_name", "Nusrat Jahan")
            put("date_of_birth", "")
            put("age", 29)
            put("present_district", "Dhaka")
            put("present_address", "")
            put("payload", JSONObject().put("fullName", "Nusrat Jahan"))
        }

        val profile = SupabaseRowMappers.rowToUserProfile(row)

        assertNotNull(profile)
        assertEquals(29, profile!!.age)
        assertEquals("", profile.dateOfBirth)
        assertEquals("", profile.presentAddress)
    }

    @Test
    fun rowToUserProfile_doesNotTreatProfileUpdatedAtAsPackageActivation() {
        val row = JSONObject().apply {
            put("id", "G600014")
            put("uid", "uid_12347")
            put("profile_code", "G600014")
            put("package_tier", "Gold")
            put("updated_at", "2026-10-31T12:00:00Z")
            put("payload", JSONObject().put("updatedAt", System.currentTimeMillis()))
        }

        val profile = SupabaseRowMappers.rowToUserProfile(row)

        assertNotNull(profile)
        assertEquals(0L, profile!!.packageActivatedAt)
        assertEquals(0L, profile.packageExpiresAt)
    }

    @Test
    fun rowToUserProfile_automaticallyDowngradesExpiredPaidPackageToFree() {
        val pastActivated = System.currentTimeMillis() - 40L * 24L * 60L * 60L * 1000L
        val pastExpiry = System.currentTimeMillis() - 10L * 24L * 60L * 60L * 1000L
        val row = JSONObject().apply {
            put("id", "B600099")
            put("uid", "uid_expired_99")
            put("full_name", "Farzana Rahman")
            put("gender", "Female")
            put("package_tier", "Silver")
            put("interests_remaining", 19)
            put("contacts_remaining", 8)
            put(
                "payload",
                JSONObject().apply {
                    put("packageActivatedAt", pastActivated)
                    put("packageExpiresAt", pastExpiry)
                }
            )
        }

        val profile = SupabaseRowMappers.rowToUserProfile(row)
        assertNotNull(profile)
        profile!!
        assertEquals("Free", profile.packageTier)
        assertEquals(2, profile.interestsRemaining)
        assertEquals(0, profile.contactsRemaining)
        assertEquals(0L, profile.packageExpiresAt)
    }

    @Test
    fun rowToInterest_mapsSenderAndReceiverMetadata() {
        val row = JSONObject().apply {
            put("id", "int_u1_u2")
            put("sender_id", "u1")
            put("receiver_id", "u2")
            put("status", "ACCEPTED")
            put(
                "payload",
                JSONObject().apply {
                    put("senderName", "Rafi")
                    put("receiverName", "Mim")
                    put("senderFormattedId", "G600001")
                    put("receiverFormattedId", "B600001")
                    put("matchScore", 92)
                }
            )
        }

        val interest = SupabaseRowMappers.rowToInterest(row)
        assertNotNull(interest)
        interest!!
        assertEquals("int_u1_u2", interest.id)
        assertEquals("u1", interest.senderId)
        assertEquals("u2", interest.receiverId)
        assertEquals("accepted", interest.status)
        assertEquals("Rafi", interest.senderName)
        assertEquals("Mim", interest.receiverName)
        assertEquals("G600001", interest.senderFormattedId)
        assertEquals("B600001", interest.receiverFormattedId)
        assertEquals(92, interest.matchScore)
    }

    @Test
    fun rowToChatThread_and_rowToChatMessage_mapCorrectly() {
        val threadRow = JSONObject().apply {
            put("id", "chat_u1_u2")
            put("participant_a", "u1")
            put("participant_b", "u2")
            put("last_message", "Assalamu Alaikum")
            put("last_message_sender_id", "u1")
        }
        val thread = SupabaseRowMappers.rowToChatThread(threadRow)
        assertNotNull(thread)
        assertEquals("chat_u1_u2", thread!!.chatId)
        assertEquals(listOf("u1", "u2"), thread.participantIds)
        assertEquals("Assalamu Alaikum", thread.lastMessage)

        val msgRow = JSONObject().apply {
            put("id", "msg_100")
            put("chat_id", "chat_u1_u2")
            put("sender_id", "u1")
            put("text", "Wa Alaikum Assalam")
            put("payload", JSONObject().put("senderName", "Rafi"))
        }
        val message = SupabaseRowMappers.rowToChatMessage(msgRow)
        assertNotNull(message)
        assertEquals("msg_100", message!!.id)
        assertEquals("u1", message.senderId)
        assertEquals("Rafi", message.senderName)
        assertEquals("Wa Alaikum Assalam", message.text)
    }

    @Test
    fun rowToAdminNotification_and_reflectiveJsonRoundTrip_mapCorrectly() {
        val notifRow = JSONObject().apply {
            put("id", "notif_501")
            put("title", "Eid Mubarak Offer")
            put("message", "Upgrade to Gold with 20% discount")
            put("target_segment", "FREE")
            put("type", "BROADCAST")
            put("action_route", "packages")
        }
        val notif = SupabaseRowMappers.rowToAdminNotification(notifRow)
        assertNotNull(notif)
        assertEquals("notif_501", notif!!.id)
        assertEquals("Eid Mubarak Offer", notif.title)
        assertEquals("Upgrade to Gold with 20% discount", notif.message)
        assertEquals("FREE", notif.targetSegment)
        assertEquals("packages", notif.actionRoute)

        val paymentOriginal = com.example.data.model.PaymentRequest(
            id = "G600001_TRX999",
            requestId = "G600001_TRX999",
            userId = "u1",
            packageTier = "Gold",
            amountBdt = 999,
            durationDays = 90,
            status = "PENDING"
        )
        val paymentJson = SupabaseRowMappers.instanceToJsonObject(paymentOriginal)
        val paymentHydrated = SupabaseRowMappers.jsonToInstance(paymentJson, com.example.data.model.PaymentRequest())
        assertEquals("Gold", paymentHydrated.packageTier)
        assertEquals(999, paymentHydrated.amountBdt)
        assertEquals(90, paymentHydrated.durationDays)
        assertEquals("PENDING", paymentHydrated.status)

        assertNull(SupabaseRowMappers.rowToInterest(JSONObject()))
        assertEquals("", SupabaseRowMappers.rowToUserProfile(JSONObject())?.displayName)
    }
}
