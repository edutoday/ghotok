package com.example.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.data.model.UserProfile
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    currentProfile: UserProfile,
    candidate: UserProfile,
    onBack: () -> Unit,
    onSendInterest: () -> Unit,
    onStartChat: () -> Unit,
    onUnlockContact: () -> Unit,
    onNavigateToPackages: () -> Unit,
    isContactUnlocked: Boolean,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    isPreview: Boolean = (currentProfile.id.isNotBlank() && currentProfile.id == candidate.id) ||
        (currentProfile.userId.isNotBlank() && currentProfile.userId == candidate.userId) ||
        (currentProfile.getFormattedUserId().isNotBlank() && currentProfile.getFormattedUserId() == candidate.getFormattedUserId()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isBangla = com.example.ui.theme.LocalLanguageController.current.isBangla
    var isIdCopied by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }
    val candidateFormattedId = remember(candidate) { candidate.getFormattedUserId() }
    val candidateDisplayId = candidateFormattedId

    val compatibility = remember(currentProfile, candidate) {
        SampleData.calculateCompatibility(currentProfile, candidate)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(candidate.displayName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "পেছনে"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val actionAlpha = if (isPreview) 0.40f else 1f
                    OutlinedButton(
                        onClick = onStartChat,
                        enabled = !isPreview,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .alpha(actionAlpha)
                            .testTag("chat_candidate_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Chat", "চ্যাট"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = onSendInterest,
                        enabled = !isPreview,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .alpha(actionAlpha)
                            .testTag("send_interest_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Interest", "আগ্রহ"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(
                        onClick = onToggleSave,
                        enabled = !isPreview,
                        modifier = Modifier
                            .size(44.dp)
                            .alpha(actionAlpha)
                            .testTag("detail_save_profile_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) tr("Remove from Saved", "সেভ থেকে সরান") else tr("Save Profile", "প্রোফাইল সেভ করুন")
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            // Profile Header Card
            item(key = "header", contentType = "header") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Compatibility Score Chip (top right corner)
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${trNum(compatibility)}% ${tr("Match", "ম্যাচ")}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val defaultAvatarRes = remember(candidate.gender, candidate.profileCode, candidate.id) {
                                candidate.getDefaultAvatarRes()
                            }
                            val effectiveCandidatePhoto = remember(candidate.photoUrl) {
                                candidate.getEffectivePhotoUrl()
                            }
                            val avatarPainter = painterResource(id = defaultAvatarRes)
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
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

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isBangla && candidate.displayNameBangla.isNotBlank()) candidate.displayNameBangla else candidate.displayName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                if (candidate.isVerified) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = tr("Verified Identity", "ভেরিফাইড আইডি"),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${trNum(candidate.age)} ${tr("yrs", "বছর")} • ${trNum(candidate.getDisplayHeight())} • ${trOption(candidate.maritalStatus)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = "${trOption(candidate.profession)} • ${trOption(candidate.getDisplayLocation())}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(5.dp))

                            // Candidate User ID with Copy Icon
                            Surface(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("User ID", candidateFormattedId))
                                    isIdCopied = true
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (isBangla)
                                                "ইউজার আইডি কপি করা হয়েছে: $candidateDisplayId"
                                            else
                                                "User ID copied: $candidateFormattedId"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                modifier = Modifier.testTag("candidate_user_id_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ID: $candidateDisplayId",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        imageVector = if (isIdCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = tr("Copy User ID", "ইউজার আইডি কপি করুন"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .testTag("copy_candidate_user_id_button")
                                    )
                                }
                            }

                            if (candidate.isVerified) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // About Me / Bio Section
            if (candidate.aboutMe.isNotEmpty()) {
                item(key = "about_me", contentType = "biodata_section") {
                    BiodataSection(title = tr("About Me", "আমার সম্পর্কে")) {
                        Text(
                            text = candidate.aboutMe,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Section 1: Personal & Physical Details
            item(key = "personal", contentType = "biodata_section") {
                BiodataSection(title = tr("Personal & Physical Attributes", "ব্যক্তিগত ও শারীরিক বিবরণ")) {
                    BiodataRow(tr("Profile Created By", "প্রোফাইল তৈরি করেছেন"), trOption(candidate.profileCreatedBy))
                    BiodataRow(tr("Gender", "লিঙ্গ"), trOption(candidate.gender))
                    BiodataRow(tr("Age", "বয়স"), "${trNum(candidate.age)} ${tr("Years", "বছর")}")
                    BiodataRow(tr("Marital Status", "বৈবাহিক অবস্থা"), trOption(candidate.maritalStatus))
                    BiodataRow(tr("Blood Group", "রক্তের গ্রুপ"), candidate.bloodGroup)
                    BiodataRow(tr("Complexion", "গাত্রবর্ণ"), trOption(candidate.complexion))
                    BiodataRow(tr("Height & Weight", "উচ্চতা ও ওজন"), "${trNum(candidate.getDisplayHeight())}, ${trNum(candidate.weight)} ${tr("kg", "কেজি")}")
                    BiodataRow(tr("Religion & Community", "ধর্ম ও সম্প্রদায়"), trOption(candidate.religion))
                    BiodataRow(tr("Eye & Hair Colour", "চোখ ও চুলের রঙ"), "${trOption(candidate.eyeColour)} • ${trOption(candidate.hairColour)}")
                    if (candidate.disability != "None") {
                        BiodataRow(tr("Disability", "শারীরিক প্রতিবন্ধকতা"), trOption(candidate.disability))
                    }
                }
            }

            // Section 2: Education & Profession
            item(key = "education", contentType = "biodata_section") {
                BiodataSection(title = tr("Education & Profession", "শিক্ষা ও পেশা")) {
                    BiodataRow(tr("Highest Degree", "সর্বোচ্চ ডিগ্রি"), trOption(candidate.education))
                    if (candidate.educationGroup.isNotEmpty()) {
                        BiodataRow(tr("Group / Major", "বিভাগ / বিষয়"), candidate.educationGroup)
                    }
                    if (candidate.institution.isNotEmpty()) {
                        BiodataRow(tr("Institute", "শিক্ষা প্রতিষ্ঠান"), candidate.institution)
                    }
                    if (candidate.ieltsScore.isNotEmpty() && candidate.ieltsScore != "None / N/A") {
                        BiodataRow(tr("IELTS Band Score", "আইইএলটিএস স্কোর"), trNum(candidate.ieltsScore))
                    }
                    BiodataRow(tr("Employment Type", "কর্মসংস্থানের ধরন"), trOption(candidate.employmentType))
                    BiodataRow(tr("Current Profession", "বর্তমান পেশা"), trOption(candidate.profession))
                    val hideJobDetails = candidate.profession.equals("Student", ignoreCase = true) ||
                        candidate.profession.equals("Unemployed", ignoreCase = true) ||
                        candidate.employmentType.equals("Unemployed", ignoreCase = true)
                    if (!hideJobDetails) {
                        if (candidate.jobDesignation.isNotEmpty()) {
                            BiodataRow(tr("Designation", "পদবী"), candidate.jobDesignation)
                        }
                        if (candidate.jobOrganization.isNotEmpty()) {
                            BiodataRow(tr("Organization", "প্রতিষ্ঠান / কোম্পানি"), candidate.jobOrganization)
                        }
                        BiodataRow(tr("Monthly Income", "মাসিক আয়"), trOption(candidate.monthlyIncome))
                        val workLoc = if (candidate.employmentType == "Overseas Employed" || (candidate.overseasCountry.isNotEmpty() && candidate.overseasCountry != "None (Living in BD)")) {
                            candidate.overseasCountry
                        } else {
                            candidate.workLocationDistrict.ifEmpty { candidate.locationDistrict }
                        }
                        BiodataRow(tr("Work Location", "কর্মস্থল"), trOption(workLoc))
                    }
                    if (candidate.employmentType == "Overseas Employed" || (candidate.overseasCountry.isNotEmpty() && candidate.overseasCountry != "None (Living in BD)")) {
                        BiodataRow(tr("Overseas Country", "প্রবাসী দেশ"), trOption(candidate.overseasCountry))
                        if (candidate.overseasVisaStatus.isNotEmpty()) {
                            BiodataRow(tr("Visa / Citizenship", "ভিসা / নাগরিকত্ব"), trOption(candidate.overseasVisaStatus))
                        }
                        if (candidate.overseasWorkExperienceYears.isNotEmpty()) {
                            BiodataRow(tr("Work Experience Abroad", "বিদেশে কাজের অভিজ্ঞতা"), "${trNum(candidate.overseasWorkExperienceYears)} ${tr("years", "বছর")}")
                        }
                    }
                }
            }

            // Section 3: Family Background & Heritage
            item(key = "family", contentType = "biodata_section") {
                BiodataSection(title = tr("Family Background", "পারিবারিক তথ্য")) {
                    BiodataRow(tr("Family Type", "পরিবারের ধরন"), trOption(candidate.familyType))
                    BiodataRow(tr("Parents' Status", "পিতা-মাতার অবস্থা"), trOption(candidate.parentsStatus))
                    BiodataRow(tr("Father's Profession", "পিতার পেশা"), trOption(candidate.fatherProfession.ifEmpty { "Not specified" }))
                    BiodataRow(tr("Mother's Profession", "মাতার পেশা"), trOption(candidate.motherProfession.ifEmpty { "Homemaker" }))
                    val siblingsDisplay = if (candidate.siblingsCount.isNotEmpty() &&
                        !candidate.siblingsCount.contains("Brother", ignoreCase = true) &&
                        !candidate.siblingsCount.contains("Sister", ignoreCase = true)
                    ) {
                        trOption(candidate.siblingsCount)
                    } else {
                        "${trNum(candidate.brotherCount)} ${tr("Brother(s)", "ভাই")}, ${trNum(candidate.sisterCount)} ${tr("Sister(s)", "বোন")}"
                    }
                    BiodataRow(tr("Siblings Details", "ভাই-বোনের বিবরণ"), siblingsDisplay)
                    BiodataRow(tr("Financial Status", "আর্থিক অবস্থা"), trOption(candidate.financialCondition))
                    if (candidate.familyIntroduction.isNotEmpty()) {
                        BiodataRow(tr("Family Intro", "পারিবারিক পরিচয়"), candidate.familyIntroduction)
                    }
                }
            }

            // Section 4: Location & Addresses (Public / Visible)
            item(key = "location", contentType = "biodata_section") {
                BiodataSection(title = tr("Location & Addresses", "অবস্থান ও ঠিকানা")) {
                    val isOverseas = candidate.employmentType == "Overseas Employed" || (candidate.overseasCountry.isNotEmpty() && candidate.overseasCountry != "None (Living in BD)")
                    val currentlyIn = if (isOverseas && candidate.overseasCountry.isNotBlank()) {
                        candidate.overseasCountry
                    } else {
                        "Bangladesh"
                    }

                    // Bangladeshi present address for both local and overseas
                    val presentAddrStr = listOf(
                        candidate.presentAddress,
                        trOption(candidate.presentUpazila),
                        trOption(candidate.presentDistrict.ifEmpty { candidate.locationDistrict })
                    ).filter { it.isNotBlank() }.joinToString(", ")

                    val permParts = listOf(
                        candidate.permanentAddress,
                        trOption(candidate.permanentUpazila),
                        trOption(candidate.permanentDistrict.ifEmpty { candidate.locationDistrict })
                    ).filter { it.isNotBlank() }.joinToString(", ")

                    val permAddrStr = if (candidate.permanentSameAsPresent && permParts.isEmpty()) {
                        presentAddrStr.ifEmpty { tr("Same as Present Address", "বর্তমান ঠিকানার অনুরূপ") }
                    } else {
                        permParts.ifEmpty { trOption(candidate.permanentDistrict.ifEmpty { candidate.locationDistrict }) }
                    }

                    BiodataRow(tr("Currently in", "বর্তমানে আছেন"), trOption(currentlyIn))
                    BiodataRow(tr("Present Address", "বর্তমান ঠিকানা"), presentAddrStr.ifEmpty { tr("Bangladesh", "বাংলাদেশ") })
                    BiodataRow(tr("Permanent Address", "স্থায়ী ঠিকানা"), permAddrStr.ifEmpty { tr("Bangladesh", "বাংলাদেশ") })
                }
            }

            // Section 5: Partner Expectations & Preferences
            item(key = "partner", contentType = "biodata_section") {
                BiodataSection(title = tr("Partner Expectations & Preferences", "প্রত্যাশিত জীবনসঙ্গীর বিবরণ")) {
                    BiodataRow(tr("Preferred Age Range", "প্রত্যাশিত বয়স"), "${trNum(candidate.partnerAgeMin)} - ${trNum(candidate.partnerAgeMax)} ${tr("Years", "বছর")}")
                    if (candidate.partnerHeightMin.isNotEmpty() && candidate.partnerHeightMax.isNotEmpty()) {
                        BiodataRow(tr("Preferred Height Range", "প্রত্যাশিত উচ্চতা"), "${trNum(candidate.getDisplayPartnerHeightMin())} - ${trNum(candidate.getDisplayPartnerHeightMax())}")
                    }
                    BiodataRow(tr("Preferred Religion", "ধর্ম"), trOption(candidate.partnerReligion))
                    BiodataRow(tr("Preferred Education", "প্রত্যাশিত শিক্ষাগত যোগ্যতা"), trOption(candidate.partnerEducation))
                    if (candidate.partnerMaritalStatuses.isNotEmpty()) {
                        BiodataRow(tr("Preferred Marital Status", "বৈবাহিক অবস্থা"), trOption(candidate.partnerMaritalStatuses.joinToString(", ")))
                    }
                    if (candidate.partnerProfessions.isNotEmpty()) {
                        BiodataRow(tr("Preferred Professions", "প্রত্যাশিত পেশা"), trOption(candidate.partnerProfessions.joinToString(", ")))
                    } else if (candidate.partnerProfession.isNotEmpty()) {
                        BiodataRow(tr("Preferred Profession", "প্রত্যাশিত পেশা"), trOption(candidate.partnerProfession))
                    }
                    if (candidate.partnerDistricts.isNotEmpty()) {
                        BiodataRow(tr("Preferred Districts", "প্রত্যাশিত জেলা"), trOption(candidate.partnerDistricts.joinToString(", ")))
                    } else if (candidate.partnerDistrict.isNotEmpty()) {
                        BiodataRow(tr("Preferred District", "প্রত্যাশিত জেলা"), trOption(candidate.partnerDistrict))
                    }
                    if (candidate.partnerFinancialConditions.isNotEmpty()) {
                        BiodataRow(tr("Preferred Financial Status", "আর্থিক অবস্থা"), trOption(candidate.partnerFinancialConditions.joinToString(", ")))
                    }
                    if (candidate.partnerComplexions.isNotEmpty()) {
                        BiodataRow(tr("Preferred Complexion", "প্রত্যাশিত গাত্রবর্ণ"), trOption(candidate.partnerComplexions.joinToString(", ")))
                    }
                    if (candidate.partnerIelts.isNotEmpty() && candidate.partnerIelts != "None / N/A") {
                        BiodataRow(tr("IELTS Requirement", "IELTS স্কোর"), trOption(candidate.partnerIelts))
                    }
                    if (candidate.partnerOverseasCountries.isNotEmpty()) {
                        BiodataRow(tr("Preferred Overseas Countries", "প্রত্যাশিত দেশ"), trOption(candidate.partnerOverseasCountries.joinToString(", ")))
                    }
                    BiodataRow(tr("Marriage Timeline", "বিয়ের সম্ভাব্য সময়"), trOption(candidate.partnerMarriageTimeline.ifEmpty { "Within 6 months" }))
                    if (candidate.partnerExpectations.isNotEmpty()) {
                        BiodataRow(tr("Additional Expectations", "অন্যান্য প্রত্যাশা"), candidate.partnerExpectations)
                    }
                }
            }

            // Section 6: Direct Contact Details (Moved after Partner Preferences, Protected / Locked)
            item(key = "contact", contentType = "biodata_section") {
                BiodataSection(title = tr("Direct Contact Details", "সরাসরি যোগাযোগের তথ্য")) {
                    if (isContactUnlocked || currentProfile.packageTier == "Diamond") {
                        BiodataRow(tr("Contact Phone", "ফোন নম্বর"), candidate.contactPhone)
                        if (candidate.whatsapp.isNotEmpty()) BiodataRow("WhatsApp", candidate.whatsapp)
                        if (candidate.imo.isNotEmpty()) BiodataRow("IMO", candidate.imo)
                        if (candidate.facebook.isNotEmpty()) BiodataRow("Facebook", candidate.facebook)
                        if (candidate.instagram.isNotEmpty()) BiodataRow("Instagram", candidate.instagram)
                        if (candidate.guardianPhone.isNotEmpty()) BiodataRow(tr("Guardian Phone", "অভিভাবকের ফোন নম্বর"), candidate.guardianPhone)
                        if (candidate.email.isNotEmpty()) BiodataRow(tr("Email Address", "ইমেইল"), candidate.email)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tr("Direct Contact Details are Locked", "সরাসরি যোগাযোগের তথ্য লক করা আছে"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = tr(
                                    "To safeguard privacy, members must use contact credits from their package to view phone numbers, social handles, and guardian contacts.",
                                    "গোপনীয়তা রক্ষার স্বার্থে ফোন নম্বর, সোশ্যাল মিডিয়া ও অভিভাবকের নম্বর দেখতে প্যাকেজের কন্টাক্ট ক্রেডিট ব্যবহার করুন।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (currentProfile.contactsRemaining > 0) {
                                Button(
                                    onClick = { showUnlockConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.testTag("unlock_contact_button")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        tr(
                                            "Unlock Contact (1 of ${currentProfile.contactsRemaining} left)",
                                            "কন্টাক্ট আনলক করলে (${trNum(currentProfile.contactsRemaining)}টির মধ্যে ১টি কন্টাক্ট খরচ হবে)"
                                        )
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onNavigateToPackages,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("upgrade_for_contacts_button")
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(tr("Upgrade Package to Unlock Contacts", "কন্টাক্ট আনলক করতে প্যাকেজ আপগ্রেড করুন"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = tr("Unlock Contact Details?", "যোগাযোগের তথ্য আনলক করবেন?"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Are you sure to unlock contact details? It will cost you 1 contact from remaining ${currentProfile.contactsRemaining} contact view.",
                        "আপনি কি যোগাযোগের তথ্য আনলক করতে নিশ্চিত? আপনার অবশিষ্ট ${trNum(currentProfile.contactsRemaining)}টি কন্টাক্ট ভিউ থেকে ১টি কন্টাক্ট খরচ হবে।"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlockConfirmDialog = false
                        onUnlockContact()
                    },
                    modifier = Modifier.testTag("confirm_unlock_contact_yes_button")
                ) {
                    Text(tr("Yes", "হ্যাঁ"))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUnlockConfirmDialog = false },
                    modifier = Modifier.testTag("confirm_unlock_contact_no_button")
                ) {
                    Text(tr("No", "না"))
                }
            }
        )
    }
}

@Composable
private fun BiodataSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun BiodataRow(label: String, value: String) {
    val displayValue = value.ifEmpty { tr("Not specified", "উল্লেখ নেই") }
    // Give the left label more room when the right value is short so labels like
    // "Religion & Community" or "Eye & Hair Colour" don't wrap while space sits empty on the right.
    val labelWeight = if (displayValue.length <= 18) 1.15f else 0.95f
    val valueWeight = if (displayValue.length <= 18) 1.0f else 1.25f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(labelWeight)
                .padding(end = 10.dp)
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(valueWeight)
        )
    }
}
