package com.example.ui.home

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.SampleData
import com.example.data.model.AdminNotification
import com.example.data.model.BangladeshLocationData
import com.example.data.model.UserProfile
import com.example.ui.search.SearchFilterState
import com.example.ui.search.matchesOppositeGender
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.candidateMatchesSearchQuery
import com.example.ui.theme.matchesSearchQuery
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentProfile: UserProfile,
    candidates: List<UserProfile>,
    filterState: SearchFilterState,
    searchQuery: String = "",
    savedProfileIds: Set<String> = emptySet(),
    activeAnnouncement: AdminNotification? = null,
    onClickAnnouncement: (AdminNotification) -> Unit = {},
    onDismissAnnouncement: (AdminNotification) -> Unit = {},
    onOpenFilters: () -> Unit,
    onSelectCandidate: (UserProfile) -> Unit,
    onSendInterest: (UserProfile) -> Unit,
    onToggleSave: (UserProfile) -> Unit = {},
    onStartChat: (UserProfile) -> Unit,
    onOpenVerification: () -> Unit,
    onNavigateToPackages: () -> Unit
) {
    val context = LocalContext.current
    remember(context) { BangladeshLocationData.ensureLoaded(context) }
    var selectedQuickFilters by remember { mutableStateOf(setOf("All")) }
    val lastUnselectedTimeByTab = remember { mutableStateMapOf<String, Long>() }

    val userPresentDivision = remember(
        currentProfile.presentDivision,
        currentProfile.locationDivision,
        currentProfile.presentDistrict,
        currentProfile.locationDistrict,
        context
    ) {
        resolveUserPresentDivision(currentProfile, context)
    }

    val quickFilters = remember(
        currentProfile.gender,
        userPresentDivision
    ) {
        buildQuickFilterTabs(currentProfile, context)
    }

    LaunchedEffect(quickFilters) {
        val validSelections = selectedQuickFilters.filter { it in quickFilters }.toSet()
        selectedQuickFilters = if (validSelections.isEmpty()) setOf("All") else validSelections
    }

    // Apply combined filters
    val filteredCandidates = remember(candidates, filterState, searchQuery, selectedQuickFilters, currentProfile, userPresentDivision) {
        candidates.filter { candidate ->
            val isUserIdMatch = searchQuery.isNotBlank() && candidate.matchesUserIdQuery(searchQuery)
            if (isUserIdMatch) {
                return@filter true
            }

            val matchesGender = matchesOppositeGender(candidate.gender, currentProfile.gender)

            // Search query (supports both English and Bangla terms across all fields)
            val matchesQuery = candidateMatchesSearchQuery(candidate, searchQuery)

            // 1. Age range filter
            val matchesAge = candidate.age == 0 || candidate.age in filterState.minAge..filterState.maxAge

            // 2. Home district / location filter (divisions & districts)
            val candidateDivisions = listOf(candidate.presentDivision, candidate.permanentDivision, candidate.locationDivision)
            val matchesDivision = filterState.divisions.isEmpty() ||
                    filterState.divisions.any { div -> candidateDivisions.any { it.equals(div, ignoreCase = true) } }

            val candidateDistricts = listOf(candidate.presentDistrict, candidate.permanentDistrict, candidate.locationDistrict)
            val matchesDistrict = filterState.districts.isEmpty() ||
                    filterState.districts.any { dist -> candidateDistricts.any { it.contains(dist, ignoreCase = true) } }

            // 3. Religion filter
            val matchesReligion = filterState.religion == "Any" ||
                    candidate.religion.contains(filterState.religion, ignoreCase = true)

            // 4. Employment type filter
            val matchesEmploymentType = filterState.employmentTypes.isEmpty() ||
                    filterState.employmentTypes.any { candidate.employmentType.equals(it, ignoreCase = true) }

            // 5. Profession filter
            val matchesProfession = filterState.professions.isEmpty() ||
                    filterState.professions.any { candidate.profession.contains(it, ignoreCase = true) }

            // 6. Overseas country / citizenship filter
            val matchesOverseasCountry = filterState.overseasCountries.isEmpty() ||
                    filterState.overseasCountries.any { candidate.overseasCountry.contains(it, ignoreCase = true) }

            val matchesCitizenship = filterState.citizenshipStatuses.isEmpty() ||
                    filterState.citizenshipStatuses.any { status ->
                        candidate.overseasVisaStatus.contains(status, ignoreCase = true) ||
                                candidate.citizenshipStatus.contains(status, ignoreCase = true)
                    }

            // 7. Education level filter
            val matchesEducation = filterState.educationLevel == "Any" ||
                    candidate.education.contains(filterState.educationLevel, ignoreCase = true)

            // 8. IELTS score filter
            val matchesIelts = filterState.minIelts == "Any" ||
                    candidate.ieltsScore.contains(filterState.minIelts, ignoreCase = true)

            // 9. Marital status filter
            val matchesMaritalStatus = filterState.maritalStatuses.isEmpty() ||
                    filterState.maritalStatuses.any { candidate.maritalStatus.equals(it, ignoreCase = true) }

            // 10. Complexion filter
            val matchesComplexion = filterState.complexions.isEmpty() ||
                    filterState.complexions.any { candidate.complexion.contains(it, ignoreCase = true) }

            // 11. Financial Condition filter
            val matchesFinancialCondition = filterState.financialConditions.isEmpty() ||
                    filterState.financialConditions.any { candidate.financialCondition.equals(it, ignoreCase = true) }

            // Quick filters (all selected quick filter tabs must match)
            val matchesQuick = matchesQuickFilters(
                candidate = candidate,
                selectedFilters = selectedQuickFilters,
                candidateDivisions = candidateDivisions,
                userPresentDivision = userPresentDivision
            )

            matchesGender && matchesQuery && matchesAge && matchesDivision && matchesDistrict && matchesReligion &&
                    matchesEmploymentType && matchesProfession && matchesOverseasCountry && matchesCitizenship &&
                    matchesEducation && matchesIelts && matchesMaritalStatus && matchesComplexion &&
                    matchesFinancialCondition && matchesQuick
        }.map { cand ->
            cand to SampleData.calculateCompatibility(currentProfile, cand)
        }.sortedByDescending { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen")
    ) {
        // Verification Callout Banner (if current user is not verified yet)
        if (!currentProfile.isVerified) {
            val isPendingVerify = currentProfile.verificationStatus.equals("PENDING", ignoreCase = true)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenVerification() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPendingVerify) Icons.Default.HourglassTop else Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPendingVerify) {
                                tr(
                                    "Your NID/Passport is under Admin review for Verified Badge",
                                    "আপনার এনআইডি/পাসপোর্ট ভেরিফাইড ব্যাজের জন্য অ্যাডমিন যাচাইয়ের অপেক্ষায় আছে"
                                )
                            } else {
                                tr(
                                    "Get Verified Badge with NID/Passport for authentic responses",
                                    "বেশি বেশি প্রস্তাব পেতে এনআইডি/পাসপোর্ট দিয়ে ভেরিফাইড ব্যাজ নিন"
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    TextButton(
                        onClick = onOpenVerification,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (isPendingVerify) tr("Status", "স্ট্যাটাস") else tr("Verify", "ভেরিফাই"),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // In-App Announcement Card (dispatched from Admin Dashboard Broadcasts or Account/Package/Match Approvals)
        if (activeAnnouncement != null) {
            val isBangla = LocalLanguageController.current.isBangla
            val annTitle = activeAnnouncement.resolvedTitle(isBangla)
            val annBody = activeAnnouncement.resolvedBody(isBangla)
            if (annTitle.isNotBlank() || annBody.isNotBlank()) {
                val badgeLabel = when (activeAnnouncement.type.uppercase()) {
                    "ACCOUNT_APPROVED", "KYC_APPROVED" -> tr("Account Verified", "প্রোফাইল ভেরিফাইড")
                    "PACKAGE_APPROVED" -> tr("Package Activated", "প্যাকেজ চালু হয়েছে")
                    "MATCH_FOUND" -> tr("Match Found", "নতুন ম্যাচ পাওয়া গেছে")
                    else -> tr("In-App Announcement Card", "বিশেষ ঘোষণা ও অফার")
                }
                val badgeIcon = when (activeAnnouncement.type.uppercase()) {
                    "ACCOUNT_APPROVED", "KYC_APPROVED" -> Icons.Default.Verified
                    "PACKAGE_APPROVED" -> Icons.Default.WorkspacePremium
                    "MATCH_FOUND" -> Icons.Default.Favorite
                    else -> Icons.Default.NotificationsActive
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onClickAnnouncement(activeAnnouncement) }
                        .testTag("in_app_announcement_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tr("View Details", "বিস্তারিত দেখুন"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onClickAnnouncement(activeAnnouncement) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDismissAnnouncement(activeAnnouncement) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("dismiss_announcement_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = tr("Dismiss", "বন্ধ করুন"),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        if (annTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = annTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (annBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = annBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }

        // Selection Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("quick_filter_tabs"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(quickFilters, key = { it }) { filterName ->
                val isSelected = filterName in selectedQuickFilters
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (filterName == "All") {
                            selectedQuickFilters = setOf("All")
                        } else {
                            val now = System.currentTimeMillis()
                            if (isSelected) {
                                lastUnselectedTimeByTab[filterName] = now
                                selectedQuickFilters = toggleQuickFilterSelection(selectedQuickFilters, filterName)
                            } else {
                                val lastUnselect = lastUnselectedTimeByTab[filterName] ?: 0L
                                if (now - lastUnselect >= 350L) {
                                    selectedQuickFilters = toggleQuickFilterSelection(selectedQuickFilters, filterName)
                                }
                            }
                        }
                    },
                    label = { Text(trOption(filterName), style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Candidates List
        if (filteredCandidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = tr("No compatible matches found", "উপযুক্ত কোনো ম্যাচ পাওয়া যায়নি"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = tr("Try adjusting your age, profession, or location search filters.", "আপনার বয়স, পেশা বা এলাকার সার্চ ফিল্টার পরিবর্তন করে চেষ্টা করুন।"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = filteredCandidates,
                    key = { (cand, _) -> cand.userId.ifBlank { cand.id } },
                    contentType = { "candidate_card" }
                ) { (candidate, compatibilityScore) ->
                    CandidateCard(
                        currentProfile = currentProfile,
                        candidate = candidate,
                        precomputedCompatibility = compatibilityScore,
                        isSaved = candidate.isSavedIn(savedProfileIds),
                        onViewBiodata = { onSelectCandidate(candidate) },
                        onSendInterest = { onSendInterest(candidate) },
                        onToggleSave = { onToggleSave(candidate) },
                        onStartChat = { onStartChat(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedProfilesScreen(
    currentProfile: UserProfile,
    candidates: List<UserProfile>,
    savedProfileIds: Set<String>,
    onSelectCandidate: (UserProfile) -> Unit,
    onSendInterest: (UserProfile) -> Unit,
    onToggleSave: (UserProfile) -> Unit,
    onStartChat: (UserProfile) -> Unit
) {
    val savedCandidates = remember(candidates, savedProfileIds, currentProfile) {
        candidates
            .filter { it.isSavedIn(savedProfileIds) }
            .map { cand -> cand to SampleData.calculateCompatibility(currentProfile, cand) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("saved_profiles_screen")
    ) {
        if (savedCandidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = tr("No saved profiles yet", "কোনো সংরক্ষিত প্রোফাইল নেই"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = tr(
                            "Tap the bookmark icon beside the interest button on any profile to save it here.",
                            "যেকোনো প্রোফাইলে আগ্রহ বাটনের পাশের বুকমার্ক আইকনে ট্যাপ করে এখানে সেভ করুন।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                items(
                    items = savedCandidates,
                    key = { (cand, _) -> cand.userId.ifBlank { cand.id } },
                    contentType = { "candidate_card" }
                ) { (candidate, compatibilityScore) ->
                    CandidateCard(
                        currentProfile = currentProfile,
                        candidate = candidate,
                        precomputedCompatibility = compatibilityScore,
                        isSaved = candidate.isSavedIn(savedProfileIds),
                        onViewBiodata = { onSelectCandidate(candidate) },
                        onSendInterest = { onSendInterest(candidate) },
                        onToggleSave = { onToggleSave(candidate) },
                        onStartChat = { onStartChat(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CandidateCard(
    currentProfile: UserProfile,
    candidate: UserProfile,
    precomputedCompatibility: Int? = null,
    isSaved: Boolean = false,
    onViewBiodata: () -> Unit,
    onSendInterest: () -> Unit,
    onToggleSave: () -> Unit = {},
    onStartChat: () -> Unit
) {
    val isBangla = com.example.ui.theme.LocalLanguageController.current.isBangla
    val compatibility = remember(currentProfile, candidate, precomputedCompatibility) {
        precomputedCompatibility ?: SampleData.calculateCompatibility(currentProfile, candidate)
    }
    val defaultAvatarRes = remember(candidate.gender, candidate.profileCode, candidate.id) {
        candidate.getDefaultAvatarRes()
    }
    val effectiveCandidatePhoto = remember(candidate.photoUrl) {
        candidate.getEffectivePhotoUrl()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewBiodata() }
            .testTag("candidate_card_${candidate.userId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Photo/Avatar
                val avatarPainter = painterResource(id = defaultAvatarRes)
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (effectiveCandidatePhoto.isNotEmpty()) {
                        AsyncImage(
                            model = effectiveCandidatePhoto,
                            contentDescription = candidate.displayName,
                            placeholder = avatarPainter,
                            error = avatarPainter,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = avatarPainter,
                            contentDescription = candidate.displayName.ifBlank { tr("Profile Photo", "প্রোফাইল ছবি") },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBangla && candidate.displayNameBangla.isNotBlank()) candidate.displayNameBangla else candidate.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (candidate.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Identity",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Compact Match Compatibility Score Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${trNum(compatibility)}% ${tr("Match", "মিল")}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val ageHeightMaritalText = "${trNum(candidate.age)} ${tr("yrs", "বছর")} • ${trNum(candidate.getDisplayHeight())} • ${trOption(candidate.complexion)} • ${trOption(candidate.maritalStatus)}"
                    Text(
                        text = ageHeightMaritalText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.15).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Text(
                        text = trOption(candidate.profession),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    val locationDisplay = trOption(candidate.getDisplayLocation())
                    Text(
                        text = if (candidate.employmentType == "Overseas Employed" || (candidate.overseasCountry.isNotBlank() && candidate.overseasCountry != "None (Living in BD)")) "✈️ $locationDisplay" else "🏠 $locationDisplay",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            letterSpacing = (-0.15).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Family & Education Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val eduLabel = tr("Education", "শিক্ষা")
                    val eduVal = trOption(candidate.education)
                    val educationDisplay = if (candidate.institution.isNotBlank()) {
                        "$eduLabel: $eduVal (${candidate.institution})"
                    } else {
                        "$eduLabel: $eduVal"
                    }
                    Text(
                        text = educationDisplay,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val siblingsDisplay = if (candidate.siblingsCount.isNotEmpty() &&
                        !candidate.siblingsCount.contains("Brother", ignoreCase = true) &&
                        !candidate.siblingsCount.contains("Sister", ignoreCase = true)
                    ) {
                        trOption(candidate.siblingsCount)
                    } else {
                        "${trNum(candidate.brotherCount)} ${tr("Brother(s)", "ভাই")}, ${trNum(candidate.sisterCount)} ${tr("Sister(s)", "বোন")}"
                    }
                    Text(
                        text = "${tr("Family", "পরিবার")}: ${trOption(candidate.financialCondition)} • $siblingsDisplay",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewBiodata,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text(
                        tr("View Biodata", "বায়োডাটা দেখুন"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Button(
                    onClick = onSendInterest,
                    modifier = Modifier.weight(1.2f).testTag("card_send_interest_${candidate.userId}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tr("Send Interest", "আগ্রহ জানান"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("card_save_profile_${candidate.userId}"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isSaved) tr("Remove from Saved", "সংরক্ষিত থেকে সরান") else tr("Save Profile", "প্রোফাইল সংরক্ষণ করুন")
                    )
                }
            }
        }
    }
}

private val INCOME_NUMBER_REGEX = Regex("""\d[\d,]*""")

private fun isMonthlyIncome50kPlus(income: String): Boolean {
    if (income.isBlank()) return false
    val normalized = UserProfile.normalizeDigitsToAscii(income).trim()
    val lower = normalized.lowercase()
    if (lower.contains("no income") || lower.contains("prefer not") || lower.contains("under") || lower.contains("below")) {
        return false
    }
    // Extract numeric chunks (ignoring commas inside numbers like 50,000 or 1,00,000)
    val numberStrings = INCOME_NUMBER_REGEX.findAll(normalized)
        .mapNotNull { it.value.replace(",", "").toLongOrNull() }
        .toList()
    if (numberStrings.isNotEmpty()) {
        // Use the lower bound of a range (e.g., 50,000 in "50,000 - 1,00,000" vs 30,000 in "30,000 - 50,000")
        return numberStrings.first() >= 50000L
    }
    return false
}

fun resolveUserPresentDivision(profile: UserProfile, context: Context? = null): String {
    val directDivision = profile.presentDivision.trim().ifBlank { profile.locationDivision.trim() }
    if (
        directDivision.isNotBlank() &&
        !directDivision.equals("Any", ignoreCase = true) &&
        !directDivision.equals("Not specified", ignoreCase = true)
    ) {
        return directDivision
    }
    val districtName = profile.presentDistrict.trim().ifBlank { profile.locationDistrict.trim() }
    if (districtName.isNotBlank() && context != null) {
        val dist = BangladeshLocationData.loadDistricts(context).find { it.name.equals(districtName, ignoreCase = true) }
        if (dist != null) {
            val div = BangladeshLocationData.DIVISIONS.find { it.id == dist.divisionId }
            if (div != null) return div.name
        }
    }
    return ""
}

fun buildQuickFilterTabs(profile: UserProfile, context: Context? = null): List<String> {
    val presentDivision = resolveUserPresentDivision(profile, context)
    val otherTabs = if (profile.gender.equals("Male", ignoreCase = true)) {
        listOf(
            "Age 20-25",
            "Fair/Very Fair",
            "Govt Job",
            "Private Job",
            "Has IELTS"
        )
    } else {
        listOf(
            "Govt Job",
            "Abroad/Overseas",
            "Monthly income 50K+",
            "Business",
            "Private Job",
            "Doctor",
            "Engineer"
        )
    }
    return buildList {
        add("All")
        if (presentDivision.isNotBlank()) {
            add(presentDivision)
        }
        addAll(otherTabs)
        add("Verified Profiles")
    }
}

internal fun matchesDivisionName(candidateDivision: String, targetDivision: String): Boolean {
    val c = candidateDivision.trim()
    val t = targetDivision.trim()
    if (c.isBlank() || t.isBlank()) return false
    if (c.equals(t, ignoreCase = true)) return true
    val isChattogram = { s: String -> s.equals("Chittagong", ignoreCase = true) || s.equals("Chattogram", ignoreCase = true) }
    if (isChattogram(c) && isChattogram(t)) return true
    val isBarishal = { s: String -> s.equals("Barisal", ignoreCase = true) || s.equals("Barishal", ignoreCase = true) }
    if (isBarishal(c) && isBarishal(t)) return true
    return false
}

fun toggleQuickFilterSelection(
    currentSelection: Set<String>,
    clickedFilter: String
): Set<String> {
    if (clickedFilter == "All") {
        return setOf("All")
    }
    val withoutAll = currentSelection - "All"
    val updated = if (clickedFilter in withoutAll) {
        withoutAll - clickedFilter
    } else {
        withoutAll + clickedFilter
    }
    return if (updated.isEmpty()) setOf("All") else updated
}

fun matchesQuickFilters(
    candidate: UserProfile,
    selectedFilters: Set<String>,
    candidateDivisions: List<String> = listOf(candidate.presentDivision, candidate.permanentDivision, candidate.locationDivision),
    userPresentDivision: String = ""
): Boolean {
    if (selectedFilters.isEmpty() || "All" in selectedFilters) return true
    return selectedFilters.all { filter ->
        matchesSingleQuickFilter(
            candidate = candidate,
            selectedQuickFilter = filter,
            candidateDivisions = candidateDivisions,
            userPresentDivision = userPresentDivision
        )
    }
}

internal fun matchesSingleQuickFilter(
    candidate: UserProfile,
    selectedQuickFilter: String,
    candidateDivisions: List<String>,
    userPresentDivision: String = ""
): Boolean {
    return when (selectedQuickFilter) {
        "All" -> true
        "Verified Profiles" -> candidate.isVerified
        "Age 20-25" -> candidate.age in 20..25
        "Fair/Very Fair" -> candidate.complexion.equals("Fair", ignoreCase = true) ||
                candidate.complexion.equals("Very Fair", ignoreCase = true)
        "Govt Job" -> candidate.employmentType.equals("Govt job", ignoreCase = true) ||
                candidate.employmentType.contains("Gov", ignoreCase = true) ||
                candidate.profession.contains("Government", ignoreCase = true) ||
                candidate.profession.contains("BCS", ignoreCase = true) ||
                candidate.profession.contains("Army", ignoreCase = true) ||
                candidate.profession.contains("Police", ignoreCase = true) ||
                candidate.profession.contains("Judge", ignoreCase = true) ||
                candidate.profession.contains("Magistrate", ignoreCase = true)
        "Private Job" -> candidate.employmentType.equals("Private job", ignoreCase = true) ||
                candidate.employmentType.contains("Private", ignoreCase = true) ||
                candidate.profession.contains("Private", ignoreCase = true)
        "Has IELTS" -> candidate.ieltsScore.isNotBlank() &&
                !candidate.ieltsScore.contains("None", ignoreCase = true) &&
                !candidate.ieltsScore.equals("N/A", ignoreCase = true) &&
                !candidate.ieltsScore.equals("No", ignoreCase = true)
        "Abroad/Overseas" -> candidate.employmentType.contains("Overseas", ignoreCase = true) ||
                (candidate.overseasCountry.isNotBlank() && !candidate.overseasCountry.contains("None", ignoreCase = true)) ||
                (candidate.citizenshipStatus.isNotBlank() && !candidate.citizenshipStatus.equals("Bangladeshi Citizen", ignoreCase = true)) ||
                candidate.overseasVisaStatus.isNotBlank()
        "Monthly income 50K+" -> isMonthlyIncome50kPlus(candidate.monthlyIncome)
        "Business" -> candidate.profession.contains("Business", ignoreCase = true) ||
                candidate.employmentType.contains("Business", ignoreCase = true) ||
                candidate.employmentType.equals("Self Employed", ignoreCase = true) ||
                candidate.jobEmploymentStatus.equals("Owner", ignoreCase = true) ||
                candidate.jobEmploymentStatus.equals("Shareholder", ignoreCase = true) ||
                candidate.profession.contains("Contractor", ignoreCase = true) ||
                candidate.profession.contains("Shopkeeper", ignoreCase = true)
        "Doctor" -> candidate.profession.contains("Doctor", ignoreCase = true) ||
                candidate.profession.contains("Dentist", ignoreCase = true) ||
                candidate.profession.contains("Surgeon", ignoreCase = true) ||
                candidate.profession.contains("Physician", ignoreCase = true) ||
                candidate.education.contains("MBBS", ignoreCase = true)
        "Engineer" -> candidate.profession.contains("Engineer", ignoreCase = true) ||
                candidate.profession.contains("Developer", ignoreCase = true) ||
                candidate.profession.contains("Architect", ignoreCase = true) ||
                candidate.education.contains("Engineering", ignoreCase = true) ||
                candidate.education.contains("B.Sc", ignoreCase = true)
        "Dhaka", "Sylhet", "Chittagong", "Chattogram", "Rajshahi", "Khulna", "Barisal", "Barishal", "Rangpur", "Mymensingh" ->
            candidateDivisions.any { matchesDivisionName(it, selectedQuickFilter) }
        else -> {
            if (userPresentDivision.isNotBlank() && selectedQuickFilter.equals(userPresentDivision, ignoreCase = true)) {
                candidateDivisions.any { matchesDivisionName(it, selectedQuickFilter) }
            } else {
                true
            }
        }
    }
}
