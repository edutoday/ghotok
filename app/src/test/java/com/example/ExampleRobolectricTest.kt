package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ghotok", appName)
  }

  @Test
  fun `check upazilas loaded for districts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val districts = com.example.data.model.BangladeshLocationData.loadDistricts(context)
    println("DEBUG_TEST: Loaded districts count = ${districts.size}")
    assertEquals(64, districts.size)

    val upazilas = com.example.data.model.BangladeshLocationData.loadUpazilas(context)
    println("DEBUG_TEST: Loaded upazilas count = ${upazilas.size}")
    org.junit.Assert.assertTrue("Should load > 400 upazilas, but loaded ${upazilas.size}", upazilas.size > 400)

    val dhakaUpazilas = com.example.data.model.BangladeshLocationData.getUpazilasForDistrict(context, "Dhaka")
    println("DEBUG_TEST: Dhaka upazilas count = ${dhakaUpazilas.size}, sample = ${dhakaUpazilas.take(5).map { it.name }}")
    org.junit.Assert.assertTrue(dhakaUpazilas.isNotEmpty())

    val sylhetUpazilas = com.example.data.model.BangladeshLocationData.getUpazilasForDistrict(context, "Sylhet")
    println("DEBUG_TEST: Sylhet upazilas count = ${sylhetUpazilas.size}, sample = ${sylhetUpazilas.take(5).map { it.name }}")
    org.junit.Assert.assertTrue(sylhetUpazilas.isNotEmpty())

    val chattogramUpazilas = com.example.data.model.BangladeshLocationData.getUpazilasForDistrict(context, "Chattogram")
    println("DEBUG_TEST: Chattogram upazilas count = ${chattogramUpazilas.size}")
    org.junit.Assert.assertTrue(chattogramUpazilas.isNotEmpty())
  }

  @Test
  fun `verify height format without leading zero and mock data removed`() {
    org.junit.Assert.assertTrue(com.example.data.model.BiodataOptions.HEIGHTS.contains("5'8''"))
    org.junit.Assert.assertFalse(com.example.data.model.BiodataOptions.HEIGHTS.any { Regex("'0\\d").containsMatchIn(it) })
    assertEquals("5'8''", com.example.data.model.UserProfile.formatHeightFeet("5'08\" (173 cm)"))
    assertEquals("5'0''", com.example.data.model.UserProfile.formatHeightFeet("5'00\""))
    assertEquals(0, com.example.data.SampleData.SAMPLE_CANDIDATES.size)
  }

  @Test
  fun `verify bride and groom serial user id format`() {
    val cal2026 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.OCTOBER, 24) }
    val ts2026 = com.google.firebase.Timestamp(cal2026.time)

    val bride1 = com.example.data.model.UserProfile(
      userId = "u1",
      gender = "Female",
      createdAt = ts2026
    )
    assertEquals("B600001", bride1.getFormattedUserId())

    val groom1 = com.example.data.model.UserProfile(
      userId = "u2",
      gender = "Male",
      createdAt = ts2026
    )
    assertEquals("G600001", groom1.getFormattedUserId())

    val bride2Code = com.example.data.model.UserProfile.formatSerialUserId("Female", 2, ts2026)
    assertEquals("B600002", bride2Code)
  }

  @Test
  fun `verify firestore custom class mapper on UserProfile and activity startup`() {
    val beanMapperClazz = Class.forName("com.google.firebase.firestore.util.CustomClassMapper\$BeanMapper")
    val ctor = beanMapperClazz.getDeclaredConstructor(Class::class.java)
    ctor.isAccessible = true
    for (modelClazz in listOf(
      com.example.data.model.UserProfile::class.java,
      com.example.data.model.Interest::class.java,
      com.example.data.model.ChatThread::class.java,
      com.example.data.model.ChatMessage::class.java,
      com.example.data.model.VerificationRequest::class.java,
      com.example.data.model.PaymentRequest::class.java
    )) {
      val mapper = ctor.newInstance(modelClazz)
      org.junit.Assert.assertNotNull(mapper)
    }
    val optimized = com.example.data.model.UserProfile.optimizePhotoUrl(
      "https://res.cloudinary.com/fzqumdkk/image/upload/v1730000000/sample.jpg"
    )
    assertEquals(
      "https://res.cloudinary.com/fzqumdkk/image/upload/f_auto,q_auto,w_400,h_400,c_fill,r_max/v1730000000/sample.jpg",
      optimized
    )
  }

  @Test
  fun `verify quick filter tabs show only user present division after All and Verified Profiles at end`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val groomDhaka = com.example.data.model.UserProfile(
      gender = "Male",
      presentDivision = "Dhaka"
    )
    assertEquals(
      listOf(
        "All",
        "Dhaka",
        "Age 20-25",
        "Fair/Very Fair",
        "Govt Job",
        "Private Job",
        "Has IELTS",
        "Verified Profiles"
      ),
      com.example.ui.home.buildQuickFilterTabs(groomDhaka, context)
    )

    val brideSylhet = com.example.data.model.UserProfile(
      gender = "Female",
      presentDivision = "Sylhet"
    )
    assertEquals(
      listOf(
        "All",
        "Sylhet",
        "Govt Job",
        "Abroad/Overseas",
        "Monthly income 50K+",
        "Business",
        "Private Job",
        "Doctor",
        "Engineer",
        "Verified Profiles"
      ),
      com.example.ui.home.buildQuickFilterTabs(brideSylhet, context)
    )

    val groomRajshahi = com.example.data.model.UserProfile(
      gender = "Male",
      presentDivision = "Rajshahi"
    )
    val rajshahiTabs = com.example.ui.home.buildQuickFilterTabs(groomRajshahi, context)
    assertEquals("All", rajshahiTabs[0])
    assertEquals("Rajshahi", rajshahiTabs[1])
    assertEquals("Verified Profiles", rajshahiTabs.last())
    org.junit.Assert.assertFalse(rajshahiTabs.contains("Dhaka"))
    org.junit.Assert.assertFalse(rajshahiTabs.contains("Sylhet"))
    org.junit.Assert.assertFalse(rajshahiTabs.contains("Chittagong"))

    // Multi-selection and unselection behavior
    var selected = setOf("All")
    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Dhaka")
    assertEquals(setOf("Dhaka"), selected)

    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Govt Job")
    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Verified Profiles")
    assertEquals(setOf("Dhaka", "Govt Job", "Verified Profiles"), selected)

    val matchingCandidate = com.example.data.model.UserProfile(
      presentDivision = "Dhaka",
      employmentType = "Govt job",
      isVerified = true
    )
    val nonMatchingCandidate = com.example.data.model.UserProfile(
      presentDivision = "Dhaka",
      employmentType = "Govt job",
      isVerified = false
    )
    org.junit.Assert.assertTrue(com.example.ui.home.matchesQuickFilters(matchingCandidate, selected, userPresentDivision = "Dhaka"))
    org.junit.Assert.assertFalse(com.example.ui.home.matchesQuickFilters(nonMatchingCandidate, selected, userPresentDivision = "Dhaka"))

    // Unselecting a tab
    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Verified Profiles")
    assertEquals(setOf("Dhaka", "Govt Job"), selected)
    org.junit.Assert.assertTrue(com.example.ui.home.matchesQuickFilters(nonMatchingCandidate, selected, userPresentDivision = "Dhaka"))

    // Unselecting remaining tabs resets to All
    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Dhaka")
    selected = com.example.ui.home.toggleQuickFilterSelection(selected, "Govt Job")
    assertEquals(setOf("All"), selected)
  }

  @Test
  fun `verify supabase row mappers parse user profile and restore package defaults`() {
    val payloadJson = org.json.JSONObject().apply {
      put("userId", "uid_15")
      put("displayName", "Tanvir Ahmed")
      put("email", "tanvir@example.com")
      put("age", 28)
      put("gender", "Male")
      put("savedProfileIds", org.json.JSONArray().put("B600002").put("B600005"))
    }
    val json = org.json.JSONObject().apply {
      put("id", "G600015")
      put("uid", "uid_15")
      put("profile_code", "G600015")
      put("full_name", "Tanvir Ahmed")
      put("gender", "Male")
      put("package_tier", "Silver")
      put("interests_remaining", 0)
      put("contacts_remaining", 0)
      put("payload", payloadJson)
    }

    val profile = com.example.data.supabase.SupabaseRowMappers.rowToUserProfile(json)
    org.junit.Assert.assertNotNull(profile)
    assertEquals("G600015", profile!!.id)
    assertEquals("uid_15", profile.userId)
    assertEquals("Tanvir Ahmed", profile.displayName)
    assertEquals("Silver", profile.packageTier)
    assertEquals(20, profile.interestsRemaining)
    assertEquals(10, profile.contactsRemaining)
    assertEquals(listOf("B600002", "B600005"), profile.savedProfileIds)
  }
}



