package com.example

import com.example.data.SampleData
import com.example.data.model.MembershipPackages
import com.example.data.model.UserProfile
import com.example.data.supabase.SupabaseRowMappers
import com.example.ui.navigation.BottomNavTab
import com.example.ui.navigation.MainNavigationViewModel
import com.example.ui.search.SearchFilterState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering core domain models, row mappers, compatibility scoring,
 * and MainNavigationViewModel state transitions.
 */
class ExampleUnitTest {

    @Test
    fun formatAndParseSerialUserId_validatesMaleAndFemaleBiodataCodes() {
        val maleCode = UserProfile.formatSerialUserId(gender = "Male", serial = 42, yearDigitOverride = 6)
        val femaleCode = UserProfile.formatSerialUserId(gender = "Female", serial = 7, yearDigitOverride = 6)

        assertEquals("G600042", maleCode)
        assertEquals("B600007", femaleCode)
        assertTrue(UserProfile.isValidFormattedUserId(maleCode, "Male"))
        assertTrue(UserProfile.isValidFormattedUserId(femaleCode, "Female"))
        assertFalse(UserProfile.isValidFormattedUserId("invalid_id"))
        assertEquals(42, UserProfile.extractSerialFromUserId(maleCode))
        assertEquals(7, UserProfile.extractSerialFromUserId(femaleCode))
    }

    @Test
    fun userProfile_adminVerificationReliesOnVerifiedAdminRegistryOrRole() {
        val regularProfile = UserProfile(
            id = "G600101",
            email = "member@example.com",
            role = "USER"
        )
        assertFalse(regularProfile.hasAdminAccess())

        val roleAdminProfile = UserProfile(
            id = "G600102",
            email = "moderator@example.com",
            role = "ADMIN"
        )
        assertTrue(roleAdminProfile.hasAdminAccess())

        UserProfile.registerVerifiedAdminEmail("verified.admin@example.com")
        val dynamicAdminProfile = UserProfile(
            id = "G600103",
            email = "VERIFIED.ADMIN@EXAMPLE.COM",
            role = "USER"
        )
        assertTrue(dynamicAdminProfile.hasAdminAccess())
    }

    @Test
    fun mainNavigationViewModel_buildEffectiveUserProfile_appliesOptimisticOverrides() {
        val viewModel = MainNavigationViewModel()
        val baseProfile = UserProfile(
            id = "G600015",
            userId = "uid_15",
            displayName = "Tanvir Ahmed",
            packageTier = "Free",
            interestsRemaining = 2,
            contactsRemaining = 0,
            verificationStatus = "UNSUBMITTED"
        )

        viewModel.setLocalPackageOverride(
            tier = "Silver",
            pendingTier = "",
            interestsRemaining = 20,
            contactsRemaining = 10
        )
        viewModel.setLocalVerificationOverride(
            status = "PENDING",
            docType = "NID Card",
            idNumber = "1996123456",
            docUri = "https://example.com/nid.jpg"
        )

        val effective = viewModel.buildEffectiveUserProfile(baseProfile)
        assertEquals("Silver", effective.packageTier)
        assertEquals(20, effective.interestsRemaining)
        assertEquals(10, effective.contactsRemaining)
        assertEquals("PENDING", effective.verificationStatus)
        assertEquals("1996123456", effective.verificationIdNumber)
    }

    @Test
    fun mainNavigationViewModel_managesTabNavigationAndBackStackCleanly() {
        val viewModel = MainNavigationViewModel()
        assertEquals(BottomNavTab.HOME, viewModel.uiState.value.selectedTab)

        viewModel.navigateToTab(BottomNavTab.PROPOSALS)
        assertEquals(BottomNavTab.PROPOSALS, viewModel.uiState.value.selectedTab)
        assertEquals(listOf(BottomNavTab.HOME), viewModel.uiState.value.tabBackStack)

        viewModel.navigateToTab(BottomNavTab.PACKAGES)
        assertEquals(BottomNavTab.PACKAGES, viewModel.uiState.value.selectedTab)
        assertEquals(listOf(BottomNavTab.HOME, BottomNavTab.PROPOSALS), viewModel.uiState.value.tabBackStack)

        val poppedFirst = viewModel.popBackStack()
        assertTrue(poppedFirst)
        assertEquals(BottomNavTab.PROPOSALS, viewModel.uiState.value.selectedTab)

        val poppedSecond = viewModel.popBackStack()
        assertTrue(poppedSecond)
        assertEquals(BottomNavTab.HOME, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun mainNavigationViewModel_handlesFilterAndDetailScreensInBackStack() {
        val viewModel = MainNavigationViewModel()
        val candidate = UserProfile(id = "B600010", displayName = "Nusrat Jahan", gender = "Female")

        viewModel.setSelectedProfile(candidate)
        assertNotNull(viewModel.uiState.value.selectedProfile)

        assertTrue(viewModel.popBackStack())
        assertNull(viewModel.uiState.value.selectedProfile)

        val customFilter = SearchFilterState(divisions = listOf("Dhaka"), minAge = 22, maxAge = 30)
        viewModel.setShowFilterScreen(true)
        assertTrue(viewModel.uiState.value.showFilterScreen)

        viewModel.setFilterState(customFilter)
        assertFalse(viewModel.uiState.value.showFilterScreen)
        assertEquals(listOf("Dhaka"), viewModel.uiState.value.filterState.divisions)
    }

    @Test
    fun membershipPackages_haveValidPricingAndQuotas() {
        val packages = MembershipPackages.ALL
        assertTrue(packages.size >= 3)
        packages.forEach { pkg ->
            assertTrue(pkg.tier.isNotBlank())
            assertTrue(pkg.productId.isNotBlank())
            assertTrue(pkg.interestsCount > 0)
        }
    }

    @Test
    fun calculateCompatibility_scoresMatchingAttributesHigher() {
        val seeker = UserProfile(
            id = "G600001",
            gender = "Male",
            age = 28,
            locationDistrict = "Dhaka",
            partnerDistrict = "Dhaka",
            religion = "Muslim",
            partnerReligion = "Muslim",
            partnerAgeMin = 22,
            partnerAgeMax = 27
        )
        val highMatch = UserProfile(
            id = "B600001",
            gender = "Female",
            age = 24,
            locationDistrict = "Dhaka",
            partnerDistrict = "Dhaka",
            religion = "Muslim",
            profession = "Doctor",
            isVerified = true
        )
        val lowMatch = UserProfile(
            id = "B600002",
            gender = "Female",
            age = 40,
            locationDistrict = "Sylhet",
            partnerDistrict = "Sylhet",
            religion = "Hindu",
            profession = "",
            isVerified = false
        )

        val highScore = SampleData.calculateCompatibility(seeker, highMatch)
        val lowScore = SampleData.calculateCompatibility(seeker, lowMatch)
        assertTrue("Expected highScore ($highScore) > lowScore ($lowScore)", highScore > lowScore)
    }
}
