package com.example.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BangladeshLocationData
import com.example.data.model.BiodataOptions
import com.example.data.model.OnboardingDraft
import com.example.data.model.UserProfile
import com.example.data.repository.MatrimonyRepository
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditWizard(
    initialProfile: UserProfile?,
    onSaveProfile: (UserProfile) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { MatrimonyRepository.create(context) }
    val currentUserId = remember { repo.currentUserId() ?: initialProfile?.userId ?: "user_default" }

    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 7
    val stepTitles = listOf(
        tr("Basic", "মৌলিক"),
        tr("Physical", "শারীরিক"),
        tr("Career", "ক্যারিয়ার"),
        tr("Family", "পরিবার"),
        tr("Location", "ঠিকানা"),
        tr("Bio", "পরিচিতি"),
        tr("Partner", "সঙ্গী")
    )
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isNewProfile = remember(initialProfile) {
        initialProfile == null || initialProfile.displayName.isEmpty() || initialProfile.gender.isEmpty()
    }

    // Step 1: Basic details
    var displayName by remember { mutableStateOf(initialProfile?.displayName?.ifBlank { null } ?: repo.currentUserDisplayName() ?: "") }
    var displayNameBangla by remember { mutableStateOf(initialProfile?.displayNameBangla ?: "") }
    var gender by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.gender ?: "")) }

    val initialDobParts = remember(initialProfile?.dateOfBirth, isNewProfile) {
        val rawDob = if (isNewProfile) "" else (initialProfile?.dateOfBirth ?: "")
        val parts = rawDob.split("-")
        if (parts.size == 3) {
            Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
        } else {
            Triple(null, null, null)
        }
    }
    var birthYear by remember { mutableStateOf<Int?>(initialDobParts.first) }
    var birthMonth by remember { mutableStateOf<Int?>(initialDobParts.second) }
    var birthDay by remember { mutableStateOf<Int?>(initialDobParts.third) }

    val calculatedAge = remember(birthYear, birthMonth, birthDay) {
        val y = birthYear
        val m = birthMonth
        val d = birthDay
        if (y != null && m != null && d != null) {
            UserProfile.calculateAgeFromDob(y, m, d)
        } else {
            0
        }
    }
    val formattedDob = remember(birthYear, birthMonth, birthDay) {
        val y = birthYear
        val m = birthMonth
        val d = birthDay
        if (y != null && m != null && d != null) {
            String.format("%04d-%02d-%02d", y, m, d)
        } else {
            ""
        }
    }

    var maritalStatus by remember { mutableStateOf(initialProfile?.maritalStatus ?: BiodataOptions.MARITAL_STATUSES.first()) }
    var religion by remember { mutableStateOf(initialProfile?.religion ?: BiodataOptions.RELIGIONS.first()) }
    var profileCreatedBy by remember { mutableStateOf(initialProfile?.profileCreatedBy ?: BiodataOptions.CREATED_BY.first()) }

    // Step 2: Physical attributes
    var height by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.height ?: "")) }
    var weightKg by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.weight?.takeIf { it > 0 }?.toString() ?: "")) }
    var complexion by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.complexion ?: "")) }
    var bloodGroup by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.bloodGroup ?: "")) }
    var eyeColour by remember { mutableStateOf(initialProfile?.eyeColour ?: "Black") }
    var hairColour by remember { mutableStateOf(initialProfile?.hairColour ?: "Black") }
    var disability by remember { mutableStateOf(initialProfile?.disability ?: "None") }

    // Step 3: Education & Work
    var educationLevel by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.education ?: "")) }
    var educationGroup by remember { mutableStateOf(initialProfile?.educationGroup ?: "") }
    var institution by remember { mutableStateOf(initialProfile?.institution ?: "") }
    var ieltsScore by remember { mutableStateOf(initialProfile?.ieltsScore ?: BiodataOptions.IELTS_SCORES.first()) }
    var employmentType by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.employmentType ?: "")) }
    var profession by remember { mutableStateOf(initialProfile?.profession ?: "") }
    var jobDesignation by remember { mutableStateOf(initialProfile?.jobDesignation ?: "") }
    var jobOrganization by remember { mutableStateOf(initialProfile?.jobOrganization ?: "") }
    var jobEmploymentStatus by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.jobEmploymentStatus ?: "")) }
    var monthlyIncome by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.monthlyIncome ?: "")) }
    var workLocationDistrict by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.workLocationDistrict ?: "")) }

    // Overseas Experience
    var overseasCountry by remember { mutableStateOf(initialProfile?.overseasCountry ?: "") }
    var overseasVisaStatus by remember { mutableStateOf(initialProfile?.overseasVisaStatus ?: "") }
    var overseasWorkExperienceYears by remember { mutableStateOf(initialProfile?.overseasWorkExperienceYears ?: "") }

    // Step 4: Family Details & Location
    var familyType by remember { mutableStateOf(initialProfile?.familyType ?: BiodataOptions.FAMILY_TYPES.first()) }
    var parentsStatus by remember { mutableStateOf(initialProfile?.parentsStatus ?: BiodataOptions.PARENTS_STATUSES.first()) }
    var financialCondition by remember { mutableStateOf(initialProfile?.financialCondition ?: BiodataOptions.FINANCIAL_CONDITIONS[2]) }
    var fatherProfession by remember { mutableStateOf(initialProfile?.fatherProfession ?: "") }
    var motherProfession by remember { mutableStateOf(initialProfile?.motherProfession ?: "") }
    var brotherCount by remember { mutableIntStateOf(if (isNewProfile) 0 else (initialProfile?.brotherCount ?: 0)) }
    var sisterCount by remember { mutableIntStateOf(if (isNewProfile) 0 else (initialProfile?.sisterCount ?: 0)) }
    var familyIntroduction by remember { mutableStateOf(initialProfile?.familyIntroduction ?: "") }

    // Location & Addresses
    var presentDivision by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.presentDivision ?: "")) }
    var presentDistrict by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.presentDistrict ?: "")) }
    var presentUpazila by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.presentUpazila ?: "")) }
    var presentAddress by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.presentAddress ?: "")) }

    var permanentSameAsPresent by remember { mutableStateOf(initialProfile?.permanentSameAsPresent ?: false) }
    var permanentDivision by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.permanentDivision ?: "")) }
    var permanentDistrict by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.permanentDistrict ?: "")) }
    var permanentUpazila by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.permanentUpazila ?: "")) }
    var permanentAddress by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.permanentAddress ?: "")) }

    // Step 5: Bio, Contacts & Partner Preferences
    var aboutMe by remember { mutableStateOf(initialProfile?.aboutMe ?: "") }
    var contactPhone by remember { mutableStateOf(initialProfile?.contactPhone ?: "") }
    var whatsapp by remember { mutableStateOf(initialProfile?.whatsapp ?: "") }
    var imo by remember { mutableStateOf(initialProfile?.imo ?: "") }
    var facebook by remember { mutableStateOf(initialProfile?.facebook ?: "") }
    var instagram by remember { mutableStateOf(initialProfile?.instagram ?: "") }
    var guardianPhone by remember { mutableStateOf(initialProfile?.guardianPhone ?: "") }

    // Partner preferences (All optional, start empty for new profiles)
    var partnerAgeMin by remember { mutableIntStateOf(initialProfile?.partnerAgeMin ?: 18) }
    var partnerAgeMax by remember { mutableIntStateOf(initialProfile?.partnerAgeMax ?: 28) }
    var partnerHeightMin by remember { mutableStateOf(initialProfile?.partnerHeightMin ?: BiodataOptions.HEIGHTS[12]) }
    var partnerHeightMax by remember { mutableStateOf(initialProfile?.partnerHeightMax ?: BiodataOptions.HEIGHTS[22]) }
    var partnerMaritalStatuses by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerMaritalStatuses ?: emptyList())) }
    var partnerEducation by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.partnerEducation ?: "")) }
    var partnerProfessions by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerProfessions ?: emptyList())) }
    var partnerLocations by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerLocations ?: emptyList())) }
    var partnerDistricts by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerDistricts ?: emptyList())) }
    var partnerFinancialConditions by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerFinancialConditions ?: emptyList())) }
    var partnerComplexions by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerComplexions ?: emptyList())) }
    var partnerIelts by remember { mutableStateOf(initialProfile?.partnerIelts ?: "None / N/A") }
    var partnerOverseasCountries by remember { mutableStateOf(if (isNewProfile) emptyList() else (initialProfile?.partnerOverseasCountries ?: emptyList())) }
    var partnerReligion by remember { mutableStateOf(initialProfile?.partnerReligion ?: "Muslim") }
    var partnerMarriageTimeline by remember { mutableStateOf(initialProfile?.partnerMarriageTimeline ?: BiodataOptions.MARRIAGE_TIMELINES[1]) }
    var partnerExpectations by remember { mutableStateOf(if (isNewProfile) "" else (initialProfile?.partnerExpectations ?: "")) }

    // Check for draft restoration on initial launch if initialProfile is empty
    LaunchedEffect(currentUserId) {
        if (initialProfile == null || initialProfile.displayName.isEmpty() || initialProfile.gender.isEmpty()) {
            val draftPair = repo.getOnboardingDraft(currentUserId)
            if (draftPair != null) {
                val draft = draftPair.first
                step = draftPair.second.coerceIn(0, totalSteps - 1)
                if (draft.displayName.isNotEmpty()) displayName = draft.displayName
                if (draft.displayNameBangla.isNotEmpty()) displayNameBangla = draft.displayNameBangla
                gender = draft.gender
                val draftDobParts = draft.dateOfBirth.split("-")
                if (draftDobParts.size == 3) {
                    birthYear = draftDobParts[0].toIntOrNull()
                    birthMonth = draftDobParts[1].toIntOrNull()
                    birthDay = draftDobParts[2].toIntOrNull()
                }
                maritalStatus = draft.maritalStatus
                religion = draft.religion
                profileCreatedBy = draft.profileCreatedBy
                height = draft.height
                weightKg = draft.weightKg
                complexion = draft.complexion
                bloodGroup = draft.bloodGroup
                eyeColour = draft.eyeColour
                hairColour = draft.hairColour
                disability = draft.disability
                educationLevel = draft.educationLevel
                educationGroup = draft.educationGroup
                institution = draft.institution
                ieltsScore = draft.ieltsScore
                employmentType = draft.employmentType
                profession = draft.profession
                jobDesignation = draft.jobDesignation
                jobOrganization = draft.jobOrganization
                jobEmploymentStatus = draft.jobEmploymentStatus
                monthlyIncome = draft.monthlyIncome
                workLocationDistrict = draft.workLocationDistrict
                overseasCountry = draft.overseasCountry
                overseasVisaStatus = draft.overseasVisaStatus
                overseasWorkExperienceYears = draft.overseasWorkExperienceYears
                familyType = draft.familyType
                parentsStatus = draft.parentsStatus
                financialCondition = draft.financialCondition
                fatherProfession = draft.fatherProfession
                motherProfession = draft.motherProfession
                brotherCount = draft.brotherCount
                sisterCount = draft.sisterCount
                familyIntroduction = draft.familyIntroduction
                presentDivision = draft.presentDivision
                presentDistrict = draft.presentDistrict
                presentUpazila = draft.presentUpazila
                presentAddress = draft.presentAddress
                permanentSameAsPresent = draft.permanentSameAsPresent
                permanentDivision = draft.permanentDivision
                permanentDistrict = draft.permanentDistrict
                permanentUpazila = draft.permanentUpazila
                permanentAddress = draft.permanentAddress
                aboutMe = draft.aboutMe
                contactPhone = draft.contactPhone
                whatsapp = draft.whatsapp
                imo = draft.imo
                facebook = draft.facebook
                instagram = draft.instagram
                guardianPhone = draft.guardianPhone
                partnerAgeMin = draft.partnerAgeMin
                partnerAgeMax = draft.partnerAgeMax
                partnerHeightMin = draft.partnerHeightMin
                partnerHeightMax = draft.partnerHeightMax
                partnerMaritalStatuses = draft.partnerMaritalStatuses
                partnerEducation = draft.partnerEducation
                partnerProfessions = draft.partnerProfessions
                partnerLocations = draft.partnerLocations
                partnerDistricts = draft.partnerDistricts
                partnerFinancialConditions = draft.partnerFinancialConditions
                partnerComplexions = draft.partnerComplexions
                partnerIelts = draft.partnerIelts
                partnerOverseasCountries = draft.partnerOverseasCountries
                partnerReligion = draft.partnerReligion
                partnerMarriageTimeline = draft.partnerMarriageTimeline
                partnerExpectations = draft.partnerExpectations
            }
        }
    }

    // Auto-save draft on step transition
    fun persistCurrentDraft(targetStep: Int) {
        val draft = OnboardingDraft(
            displayName = displayName,
            displayNameBangla = displayNameBangla,
            gender = gender,
            dateOfBirth = formattedDob,
            maritalStatus = maritalStatus,
            religion = religion,
            profileCreatedBy = profileCreatedBy,
            height = height,
            weightKg = weightKg,
            complexion = complexion,
            bloodGroup = bloodGroup,
            eyeColour = eyeColour,
            hairColour = hairColour,
            disability = disability,
            educationLevel = educationLevel,
            educationGroup = educationGroup,
            institution = institution,
            ieltsScore = ieltsScore,
            employmentType = employmentType,
            profession = profession,
            jobDesignation = jobDesignation,
            jobOrganization = jobOrganization,
            jobEmploymentStatus = jobEmploymentStatus,
            monthlyIncome = monthlyIncome,
            workLocationDistrict = workLocationDistrict,
            overseasCountry = overseasCountry,
            overseasVisaStatus = overseasVisaStatus,
            overseasWorkExperienceYears = overseasWorkExperienceYears,
            familyType = familyType,
            parentsStatus = parentsStatus,
            financialCondition = financialCondition,
            fatherProfession = fatherProfession,
            motherProfession = motherProfession,
            brotherCount = brotherCount,
            sisterCount = sisterCount,
            familyIntroduction = familyIntroduction,
            presentDivision = presentDivision,
            presentDistrict = presentDistrict,
            presentUpazila = presentUpazila,
            presentAddress = presentAddress,
            permanentSameAsPresent = permanentSameAsPresent,
            permanentDivision = if (permanentSameAsPresent) presentDivision else permanentDivision,
            permanentDistrict = if (permanentSameAsPresent) presentDistrict else permanentDistrict,
            permanentUpazila = if (permanentSameAsPresent) presentUpazila else permanentUpazila,
            permanentAddress = if (permanentSameAsPresent) presentAddress else permanentAddress,
            aboutMe = aboutMe,
            contactPhone = contactPhone,
            whatsapp = whatsapp,
            imo = imo,
            facebook = facebook,
            instagram = instagram,
            guardianPhone = guardianPhone,
            partnerAgeMin = partnerAgeMin,
            partnerAgeMax = partnerAgeMax,
            partnerHeightMin = partnerHeightMin,
            partnerHeightMax = partnerHeightMax,
            partnerMaritalStatuses = partnerMaritalStatuses,
            partnerEducation = partnerEducation,
            partnerProfessions = partnerProfessions,
            partnerLocations = partnerLocations,
            partnerDistricts = partnerDistricts,
            partnerFinancialConditions = partnerFinancialConditions,
            partnerComplexions = partnerComplexions,
            partnerIelts = partnerIelts,
            partnerOverseasCountries = partnerOverseasCountries,
            partnerReligion = partnerReligion,
            partnerMarriageTimeline = partnerMarriageTimeline,
            partnerExpectations = partnerExpectations
        )
        coroutineScope.launch {
            repo.saveOnboardingDraft(currentUserId, draft, targetStep)
        }
    }

    var lastWizardBackPressTime by remember { mutableStateOf(0L) }
    val isBanglaWizard = com.example.ui.theme.LocalLanguageController.current.isBangla
    androidx.activity.compose.BackHandler {
        when {
            step > 0 -> {
                val prevStep = step - 1
                step = prevStep
                persistCurrentDraft(prevStep)
            }
            onCancel != null -> {
                onCancel()
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastWizardBackPressTime <= 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastWizardBackPressTime = now
                    android.widget.Toast.makeText(
                        context,
                        if (isBanglaWizard) "ঘটক অ্যাপ বন্ধ করতে পরপর দুইবার ব্যাক চাপুন (Double tap to close Ghotok App)"
                        else "Double tap to close Ghotok",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val saveAndExitProfile: () -> Unit = {
        if (!isSaving) {
            isSaving = true
            errorMessage = null
            coroutineScope.launch {
                val initialCode = initialProfile?.profileCode.orEmpty().ifBlank { initialProfile?.id.orEmpty() }
                val resolvedInitialCode = if (UserProfile.isValidFormattedUserId(initialCode, gender)) {
                    initialCode.trim()
                } else {
                    ""
                }
                val finalProfile = (initialProfile ?: UserProfile()).copy(
                    id = resolvedInitialCode,
                    userId = currentUserId,
                    profileCode = resolvedInitialCode,
                    displayName = if (displayName.isNotBlank()) displayName.trim() else "User",
                    displayNameBangla = displayNameBangla.trim(),
                    email = initialProfile?.email ?: "",
                    photoUrl = initialProfile?.photoUrl ?: "",
                    gender = gender,
                    dateOfBirth = formattedDob,
                    age = calculatedAge,
                    maritalStatus = maritalStatus,
                    religion = religion,
                    profileCreatedBy = profileCreatedBy,
                    height = height,
                    weight = weightKg.toIntOrNull() ?: 0,
                    complexion = complexion,
                    bloodGroup = bloodGroup,
                    eyeColour = eyeColour,
                    hairColour = hairColour,
                    disability = disability,
                    education = educationLevel,
                    educationGroup = educationGroup,
                    institution = institution,
                    ieltsScore = ieltsScore,
                    employmentType = employmentType,
                    profession = profession,
                    jobDesignation = jobDesignation,
                    jobOrganization = jobOrganization,
                    jobEmploymentStatus = jobEmploymentStatus,
                    monthlyIncome = monthlyIncome,
                    workLocationDistrict = workLocationDistrict,
                    overseasCountry = if (employmentType == "Overseas Employed") overseasCountry else "None (Living in BD)",
                    citizenshipStatus = if (employmentType == "Overseas Employed" && overseasVisaStatus.isNotEmpty()) overseasVisaStatus else "Bangladeshi Citizen",
                    overseasVisaStatus = overseasVisaStatus,
                    overseasWorkExperienceYears = overseasWorkExperienceYears,
                    familyType = familyType,
                    parentsStatus = parentsStatus,
                    financialCondition = financialCondition,
                    fatherProfession = fatherProfession,
                    motherProfession = motherProfession,
                    brotherCount = brotherCount,
                    sisterCount = sisterCount,
                    siblingsCount = "$brotherCount Brother(s), $sisterCount Sister(s)",
                    familyIntroduction = familyIntroduction,
                    locationDivision = presentDivision,
                    locationDistrict = presentDistrict,
                    presentDivision = presentDivision,
                    presentDistrict = presentDistrict,
                    presentUpazila = presentUpazila,
                    presentAddress = presentAddress,
                    permanentSameAsPresent = permanentSameAsPresent,
                    permanentDivision = if (permanentSameAsPresent) presentDivision else permanentDivision,
                    permanentDistrict = if (permanentSameAsPresent) presentDistrict else permanentDistrict,
                    permanentUpazila = if (permanentSameAsPresent) presentUpazila else permanentUpazila,
                    permanentAddress = if (permanentSameAsPresent) presentAddress else permanentAddress,
                    aboutMe = aboutMe,
                    contactPhone = contactPhone,
                    whatsapp = whatsapp,
                    imo = imo,
                    facebook = facebook,
                    instagram = instagram,
                    guardianPhone = guardianPhone,
                    partnerAgeMin = partnerAgeMin,
                    partnerAgeMax = partnerAgeMax,
                    partnerHeightMin = partnerHeightMin,
                    partnerHeightMax = partnerHeightMax,
                    partnerMaritalStatuses = partnerMaritalStatuses,
                    partnerEducation = partnerEducation,
                    partnerProfession = partnerProfessions.joinToString(", "),
                    partnerProfessions = partnerProfessions,
                    partnerLocations = partnerLocations,
                    partnerDistrict = partnerDistricts.joinToString(", "),
                    partnerDistricts = partnerDistricts,
                    partnerFinancialConditions = partnerFinancialConditions,
                    partnerComplexions = partnerComplexions,
                    partnerIelts = partnerIelts,
                    partnerOverseasCountries = partnerOverseasCountries,
                    partnerReligion = partnerReligion,
                    partnerMarriageTimeline = partnerMarriageTimeline,
                    partnerExpectations = partnerExpectations,
                    packageTier = initialProfile?.packageTier ?: "Free",
                    pendingPackageTier = initialProfile?.pendingPackageTier ?: "",
                    packageActivatedAt = initialProfile?.packageActivatedAt ?: 0L,
                    packageExpiresAt = initialProfile?.packageExpiresAt ?: 0L,
                    isVerified = initialProfile?.isVerified ?: false,
                    verificationStatus = initialProfile?.verificationStatus ?: "UNSUBMITTED",
                    verificationDocType = initialProfile?.verificationDocType ?: "NID Card",
                    verificationIdNumber = initialProfile?.verificationIdNumber ?: "",
                    verificationDocUri = initialProfile?.verificationDocUri ?: "",
                    accountStatus = initialProfile?.accountStatus ?: "ACTIVE",
                    interestsRemaining = initialProfile?.interestsRemaining ?: 2,
                    freeInterestWeekKey = initialProfile?.freeInterestWeekKey ?: "",
                    contactsRemaining = initialProfile?.contactsRemaining ?: 0,
                    savedProfileIds = initialProfile?.savedProfileIds ?: emptyList()
                )
                val saveResult = repo.saveUserProfile(finalProfile)
                if (saveResult.isSuccess) {
                    isSaving = false
                    onSaveProfile(saveResult.getOrNull() ?: finalProfile)
                } else {
                    isSaving = false
                    val err = saveResult.exceptionOrNull()?.localizedMessage ?: "Failed to save biodata. Please try again."
                    errorMessage = err
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (initialProfile != null && initialProfile.displayName.isNotEmpty())
                                tr("Edit Biodata Profile", "বায়োডাটা ইডিট করুন")
                            else
                                tr("Create Matrimonial Biodata", "বায়োডাটা তৈরি করুন"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${tr("Step", "ধাপ")} ${trNum(step + 1)} / ${trNum(totalSteps)}: ${stepTitles[step]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = tr("Close", "বন্ধ করুন"))
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = saveAndExitProfile,
                        enabled = !isSaving,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("wizard_top_done_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tr("Done", "সম্পন্ন"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = tr("Error", "ত্রুটি"),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { errorMessage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = tr("Dismiss", "বাতিল করুন"), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = {
                                    val prevStep = step - 1
                                    step = prevStep
                                    persistCurrentDraft(prevStep)
                                },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("wizard_prev_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Previous", "পূর্ববর্তী"), maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                if (step < totalSteps - 1) {
                                    val nextStep = step + 1
                                    step = nextStep
                                    persistCurrentDraft(nextStep)
                                } else {
                                    saveAndExitProfile()
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("wizard_next_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isSaving && step == totalSteps - 1) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Saving...", "সেভ হচ্ছে..."), maxLines = 1)
                            } else {
                                Text(
                                    text = if (step < totalSteps - 1)
                                        tr("Next", "পরবর্তী")
                                    else
                                        tr("Submit", "সাবমিট"),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    if (step < totalSteps - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Interactive Step Progress Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stepTitles.forEachIndexed { index, title ->
                    val isCurrent = index == step
                    val isCompleted = index < step
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // Allow jumping directly between steps in edit profile or when completed
                                step = index
                                persistCurrentDraft(index)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isCompleted -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = trNum(index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Step Content
            when (step) {
                0 -> {
                    // STEP 1: Basic Details
                    Text(tr("Basic Details", "মৌলিক তথ্য"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Bride/Groome identity", "পাত্র/পাত্রীর পরিচয়"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(tr("Name in English *", " নাম (ইংরেজিতে) *")) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("wizard_display_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = displayNameBangla,
                        onValueChange = { displayNameBangla = it },
                        label = { Text(tr("Name in Bangla", "নাম: (বাংলায়)")) },
                        placeholder = { Text(tr("Write Name in Bengali", "বাংলায় পূর্ণ নাম লিখুন")) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("wizard_display_name_bangla_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Gender *", "লিঙ্গ *"),
                        selectedValue = gender,
                        options = listOf("Male", "Female"),
                        onSelect = { gender = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date of Birth Dropdown Pickers & Real-Time Age
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tr("Date of Birth *", "জন্ম তারিখ *"), fontWeight = FontWeight.SemiBold)
                                }
                                if (calculatedAge > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${tr("Age:", "বয়স:")} ${trNum(calculatedAge)} ${tr("yrs", "বছর")}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Day
                                Box(modifier = Modifier.weight(1f)) {
                                    DropdownSelector(
                                        label = tr("Day", "দিন"),
                                        selectedValue = birthDay,
                                        options = (1..31).toList(),
                                        displayText = { it?.toString() ?: "" },
                                        onSelect = { birthDay = it }
                                    )
                                }
                                // Month
                                Box(modifier = Modifier.weight(1.2f)) {
                                    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                    DropdownSelector(
                                        label = tr("Month", "মাস"),
                                        selectedValue = birthMonth,
                                        options = (1..12).toList(),
                                        displayText = { m -> if (m != null && m in 1..12) monthNames[m - 1] else "" },
                                        onSelect = { birthMonth = it }
                                    )
                                }
                                // Year
                                Box(modifier = Modifier.weight(1.2f)) {
                                    val currentYr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                                    val years = ((currentYr - 70)..(currentYr - 16)).reversed().toList()
                                    DropdownSelector(
                                        label = tr("Year", "বছর"),
                                        selectedValue = birthYear,
                                        options = years,
                                        displayText = { it?.toString() ?: "" },
                                        onSelect = { birthYear = it }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Marital Status *", "বৈবাহিক অবস্থা *"),
                        selectedValue = maritalStatus,
                        options = BiodataOptions.MARITAL_STATUSES,
                        onSelect = { maritalStatus = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Religion *", "ধর্ম *"),
                        selectedValue = religion,
                        options = BiodataOptions.RELIGIONS,
                        onSelect = { religion = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Who is creating this profile? *", "প্রোফাইলটি কে তৈরি করছেন? *"),
                        selectedValue = profileCreatedBy,
                        options = BiodataOptions.CREATED_BY,
                        onSelect = { profileCreatedBy = it }
                    )
                }

                1 -> {
                    // STEP 2: Physical Attributes
                    Text(tr("Physical Attributes", "শারীরিক বৈশিষ্ট্য"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Accurate physical characteristics for matrimonial evaluation", "বিবাহের মূল্যায়নের জন্য সঠিক শারীরিক বৈশিষ্ট্য"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Height (Feet & Inches) *", "উচ্চতা (ফুট ও ইঞ্চি) *"),
                        selectedValue = height,
                        options = BiodataOptions.HEIGHTS,
                        onSelect = { height = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { if (it.all { char -> char.isDigit() }) weightKg = it },
                        label = { Text(tr("Weight (in kg) *", "ওজন (কেজি) *")) },
                        placeholder = { Text(tr("e.g. 65", "যেমন ৬৫")) },
                        leadingIcon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Complexion *", "গাত্রবর্ণ *"),
                        selectedValue = complexion,
                        options = BiodataOptions.COMPLEXIONS,
                        onSelect = { complexion = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Blood Group *", "রক্তের গ্রুপ *"),
                        selectedValue = bloodGroup,
                        options = BiodataOptions.BLOOD_GROUPS,
                        onSelect = { bloodGroup = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Eye Colour", "চোখের রঙ"),
                        selectedValue = eyeColour,
                        options = BiodataOptions.EYE_COLOURS,
                        onSelect = { eyeColour = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Hair Colour", "চুলের রঙ"),
                        selectedValue = hairColour,
                        options = BiodataOptions.HAIR_COLOURS,
                        onSelect = { hairColour = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Disability", "শারীরিক প্রতিবন্ধকতা"),
                        selectedValue = disability,
                        options = BiodataOptions.DISABILITIES,
                        onSelect = { disability = it }
                    )
                }

                2 -> {
                    // STEP 3: Education & Work
                    Text(tr("Education & Career", "শিক্ষা ও ক্যারিয়ার"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Academic background, professional occupation, and overseas career details", "শিক্ষাগত যোগ্যতা, পেশা এবং প্রবাস জীবনের বিবরণ"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Education Level *", "শিক্ষাগত যোগ্যতা *"),
                        selectedValue = educationLevel,
                        options = BiodataOptions.EDUCATION_LEVELS,
                        onSelect = { educationLevel = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = educationGroup,
                        onValueChange = { educationGroup = it },
                        label = { Text(tr("Group / Major Subject", "বিভাগ / বিষয়")) },
                        placeholder = { Text(tr("e.g. Computer Science, Finance, English, Science", "যেমন কম্পিউটার সায়েন্স, ফিন্যান্স, ইংরেজি")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text(tr("Educational Institute / University", "শিক্ষা প্রতিষ্ঠান / বিশ্ববিদ্যালয়")) },
                        placeholder = { Text(tr("e.g. BUET, Dhaka University, NSU, Medical College", "যেমন বুয়েট, ঢাকা বিশ্ববিদ্যালয়, এনএসইউ")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("IELTS Score", "আইইএলটিএস স্কোর"),
                        selectedValue = ieltsScore,
                        options = BiodataOptions.IELTS_SCORES,
                        onSelect = { ieltsScore = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Employment Type *", "কর্মসংস্থানের ধরন *"),
                        selectedValue = employmentType,
                        options = BiodataOptions.EMPLOYMENT_TYPES,
                        onSelect = { employmentType = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Comprehensive Searchable Profession Dropdown (~120 items)
                    SearchableDropdownSelector(
                        label = tr("Profession *", "পেশা *"),
                        selectedValue = profession,
                        options = BiodataOptions.PROFESSIONS,
                        onSelect = { profession = it }
                    )

                    val hideJobDetails = profession.equals("Student", ignoreCase = true) ||
                        profession.equals("Unemployed", ignoreCase = true) ||
                        employmentType.equals("Unemployed", ignoreCase = true)

                    // Job Details (Hidden if Student or Unemployed)
                    AnimatedVisibility(visible = !hideJobDetails) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(tr("Job & Organization Details", "চাকরি ও প্রতিষ্ঠানের বিবরণ"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = jobDesignation,
                                onValueChange = { jobDesignation = it },
                                label = { Text(tr("Job Designation", "পদবী")) },
                                placeholder = { Text(tr("e.g. Senior Officer, Assistant Manager, Lead Engineer", "যেমন সিনিয়র অফিসার, সহকারী ব্যবস্থাপক")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = jobOrganization,
                                onValueChange = { jobOrganization = it },
                                label = { Text(tr("Institute / Organization / Company", "প্রতিষ্ঠান / কোম্পানি")) },
                                placeholder = { Text(tr("e.g. Bangladesh Bank, Grameenphone, BEXIMCO", "যেমন বাংলাদেশ ব্যাংক, গ্রামীণফোন")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DropdownSelector(
                                label = tr("Job Status", "চাকরির ধরন"),
                                selectedValue = jobEmploymentStatus,
                                options = BiodataOptions.JOB_EMPLOYMENT_STATUSES,
                                onSelect = { jobEmploymentStatus = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DropdownSelector(
                                label = tr("Monthly Income", "মাসিক আয়"),
                                selectedValue = monthlyIncome,
                                options = BiodataOptions.MONTHLY_INCOMES,
                                onSelect = { monthlyIncome = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // District dropdown
                            val allDistricts = remember { BangladeshLocationData.getAllDistrictNames(context) }
                            SearchableDropdownSelector(
                                label = tr("Work Location/District", "কর্মস্থলের ঠিকানা/জেলা"),
                                selectedValue = workLocationDistrict,
                                options = allDistricts,
                                onSelect = { workLocationDistrict = it }
                            )
                        }
                    }
                    }

                    // Conditional Overseas Section
                    AnimatedVisibility(visible = employmentType == "Overseas Employed") {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(tr("Overseas Details", "বিদেশ/প্রবাসের বিবরণ"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    DropdownSelector(
                                        label = tr("Overseas Country *", "প্রবাসের দেশ *"),
                                        selectedValue = if (overseasCountry.isNotEmpty()) overseasCountry else BiodataOptions.OVERSEAS_COUNTRIES.first(),
                                        options = BiodataOptions.OVERSEAS_COUNTRIES,
                                        onSelect = { overseasCountry = it }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    DropdownSelector(
                                        label = tr("Visa Status *", "ভিসা স্ট্যাটাস *"),
                                        selectedValue = if (overseasVisaStatus.isNotEmpty()) overseasVisaStatus else BiodataOptions.VISA_STATUSES.first(),
                                        options = BiodataOptions.VISA_STATUSES,
                                        onSelect = { overseasVisaStatus = it }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = overseasWorkExperienceYears,
                                        onValueChange = { overseasWorkExperienceYears = it },
                                        label = { Text(tr("Overseas Work Experience (Years)", "প্রবাসে কাজের অভিজ্ঞতা (বছর)")) },
                                        placeholder = { Text(tr("e.g. 4", "যেমন ৪")) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // STEP 4: Family Details
                    Text(tr("Family Details", "পারিবারিক তথ্য"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Family background, parents' status, and siblings", "পারিবারিক পটভূমি, পিতা-মাতার অবস্থা এবং ভাইবোন"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Family Type *", "পরিবারের ধরন *"),
                        selectedValue = familyType,
                        options = BiodataOptions.FAMILY_TYPES,
                        onSelect = { familyType = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Parents' Status *", "পিতা-মাতার অবস্থা *"),
                        selectedValue = parentsStatus,
                        options = BiodataOptions.PARENTS_STATUSES,
                        onSelect = { parentsStatus = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DropdownSelector(
                        label = tr("Financial Condition *", "আর্থিক অবস্থা *"),
                        selectedValue = financialCondition,
                        options = BiodataOptions.FINANCIAL_CONDITIONS,
                        onSelect = { financialCondition = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = fatherProfession,
                        onValueChange = { fatherProfession = it },
                        label = { Text(tr("Father's Profession", "পিতার পেশা")) },
                        placeholder = { Text(tr("e.g. Business, Retired Govt. Officer, Teacher", "যেমন ব্যবসা, অবসরপ্রাপ্ত সরকারি কর্মকর্তা, শিক্ষক")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = motherProfession,
                        onValueChange = { motherProfession = it },
                        label = { Text(tr("Mother's Profession", "মাতার পেশা")) },
                        placeholder = { Text(tr("e.g. Homemaker, Teacher, Doctor", "যেমন গৃহিণী, শিক্ষক, ডাক্তার")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Siblings Counter Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(tr("Sibling Counts", "ভাইবোনের সংখ্যা"), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${tr("Brothers:", "ভাই:")} ${trNum(brotherCount)}", style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    FilledTonalIconButton(onClick = { if (brotherCount > 0) brotherCount-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = tr("Decrease brothers", "ভাই কমান"))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalIconButton(onClick = { brotherCount++ }) {
                                        Icon(Icons.Default.Add, contentDescription = tr("Increase brothers", "ভাই বাড়ান"))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${tr("Sisters:", "বোন:")} ${trNum(sisterCount)}", style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    FilledTonalIconButton(onClick = { if (sisterCount > 0) sisterCount-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = tr("Decrease sisters", "বোন কমান"))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalIconButton(onClick = { sisterCount++ }) {
                                        Icon(Icons.Default.Add, contentDescription = tr("Increase sisters", "বোন বাড়ান"))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = familyIntroduction,
                        onValueChange = { familyIntroduction = it },
                        label = { Text(tr("Short Family Introduction", "সংক্ষিপ্ত পারিবারিক পরিচিতি")) },
                        placeholder = { Text(tr("Briefly describe your family environment, traditions, values, etc.", "আপনার পরিবারের পরিবেশ, ঐতিহ্য, মূল্যবোধ সংক্ষেপে লিখুন")) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                4 -> {
                    // STEP 5: Location & Address
                    Text(tr("Location & Address", "অবস্থান ও ঠিকানা"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val currentlyInDisplay = if (employmentType == "Overseas Employed" && overseasCountry.isNotBlank() && overseasCountry != "None (Living in BD)") {
                        trOption(overseasCountry)
                    } else {
                        tr("Bangladesh", "বাংলাদেশ")
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${tr("Currently in:", "বর্তমানে আছেন:")} $currentlyInDisplay",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Present Address Hierarchy
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(tr("Present Address *", "বর্তমান ঠিকানা *"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(tr("Bangladeshi present address (for both local and overseas)", "বাংলাদেশের বর্তমান ঠিকানা (দেশি ও প্রবাসী উভয়ের জন্য)"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Division dropdown
                            val divisionNames = remember { BangladeshLocationData.DIVISIONS.map { it.name } }
                            SearchableDropdownSelector(
                                label = tr("Division *", "বিভাগ *"),
                                selectedValue = presentDivision,
                                options = divisionNames,
                                onSelect = { div ->
                                    presentDivision = div
                                    val dists = BangladeshLocationData.getDistrictsForDivision(context, div).map { it.name }
                                    if (presentDistrict !in dists) {
                                        presentDistrict = ""
                                        presentUpazila = ""
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // District dropdown
                            val currentPresentDistricts = remember(presentDivision) {
                                val d = BangladeshLocationData.getDistrictsForDivision(context, presentDivision).map { it.name }
                                if (d.isNotEmpty()) d else BangladeshLocationData.getAllDistrictNames(context)
                            }
                            SearchableDropdownSelector(
                                label = tr("District *", "জেলা *"),
                                selectedValue = presentDistrict,
                                options = currentPresentDistricts,
                                onSelect = { dist ->
                                    presentDistrict = dist
                                    val upz = BangladeshLocationData.getUpazilasForDistrict(context, dist).map { it.name }
                                    if (presentUpazila !in upz) {
                                        presentUpazila = ""
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Upazila dropdown
                            val currentPresentUpazilas = remember(presentDistrict) {
                                val u = BangladeshLocationData.getUpazilasForDistrict(context, presentDistrict).map { it.name }
                                if (u.isNotEmpty()) u else listOf("Sadar", "Other")
                            }
                            SearchableDropdownSelector(
                                label = tr("Upazila / Thana *", "উপজেলা / থানা *"),
                                selectedValue = presentUpazila,
                                options = currentPresentUpazilas,
                                onSelect = { presentUpazila = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = presentAddress,
                                onValueChange = { presentAddress = it },
                                label = { Text(tr("Area / Road / Flat Details", "এলাকা / রোড / বাসা নম্বর")) },
                                placeholder = { Text(tr("e.g. House 12, Road 4, Sector 7", "যেমন বাসা ১২, রোড ৪, সেক্টর ৭")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Permanent Address with Autofill Checkbox
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tr("Permanent Address *", "স্থায়ী ঠিকানা *"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        permanentSameAsPresent = !permanentSameAsPresent
                                        if (permanentSameAsPresent) {
                                            permanentDivision = presentDivision
                                            permanentDistrict = presentDistrict
                                            permanentUpazila = presentUpazila
                                            permanentAddress = presentAddress
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = permanentSameAsPresent,
                                    onCheckedChange = { isChecked ->
                                        permanentSameAsPresent = isChecked
                                        if (isChecked) {
                                            permanentDivision = presentDivision
                                            permanentDistrict = presentDistrict
                                            permanentUpazila = presentUpazila
                                            permanentAddress = presentAddress
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tr("Permanent address is same as present address", "স্থায়ী ঠিকানা এবং বর্তমান ঠিকানা একই"), style = MaterialTheme.typography.bodyMedium)
                            }

                            if (!permanentSameAsPresent) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val permDivisions = remember { BangladeshLocationData.DIVISIONS.map { it.name } }
                                SearchableDropdownSelector(
                                    label = tr("Permanent Division", "স্থায়ী বিভাগ"),
                                    selectedValue = permanentDivision,
                                    options = permDivisions,
                                    onSelect = { div ->
                                        permanentDivision = div
                                        val dists = BangladeshLocationData.getDistrictsForDivision(context, div).map { it.name }
                                        if (permanentDistrict !in dists) {
                                            permanentDistrict = ""
                                            permanentUpazila = ""
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                val currentPermDistricts = remember(permanentDivision) {
                                    val d = BangladeshLocationData.getDistrictsForDivision(context, permanentDivision).map { it.name }
                                    if (d.isNotEmpty()) d else BangladeshLocationData.getAllDistrictNames(context)
                                }
                                SearchableDropdownSelector(
                                    label = tr("Permanent District", "স্থায়ী জেলা"),
                                    selectedValue = permanentDistrict,
                                    options = currentPermDistricts,
                                    onSelect = { dist ->
                                        permanentDistrict = dist
                                        val upz = BangladeshLocationData.getUpazilasForDistrict(context, dist).map { it.name }
                                        if (permanentUpazila !in upz) {
                                            permanentUpazila = ""
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                val currentPermUpazilas = remember(permanentDistrict) {
                                    val u = BangladeshLocationData.getUpazilasForDistrict(context, permanentDistrict).map { it.name }
                                    if (u.isNotEmpty()) u else listOf("Sadar", "Other")
                                }
                                SearchableDropdownSelector(
                                    label = tr("Permanent Upazila", "স্থায়ী উপজেলা"),
                                    selectedValue = permanentUpazila,
                                    options = currentPermUpazilas,
                                    onSelect = { permanentUpazila = it }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = permanentAddress,
                                    onValueChange = { permanentAddress = it },
                                    label = { Text(tr("Permanent Village / Area Details", "স্থায়ী গ্রাম / এলাকার বিবরণ")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                5 -> {
                    // STEP 6: Bio & Contacts
                    Text(tr("Bio & Contacts", "পরিচিতি ও যোগাযোগ"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Candidate introduction and verified direct contact details", "প্রার্থীর সংক্ষিপ্ত পরিচিতি এবং সরাসরি যোগাযোগের তথ্য"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Bio (Max 5000 chars)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tr("About Me (Bio) *", "আমার সম্পর্কে (বায়ো) *"), fontWeight = FontWeight.Bold)
                                Text("${trNum(aboutMe.length)}/${trNum(5000)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = aboutMe,
                                onValueChange = { if (it.length <= 5000) aboutMe = it },
                                placeholder = { Text(tr("Write about your personality, religious values, hobbies, worldview, and expectations from marriage...", "আপনার ব্যক্তিত্ব, ধর্মীয় মূল্যবোধ, শখ, জীবনদর্শন এবং বিবাহ সম্পর্কে প্রত্যাশা লিখুন...")) },
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                maxLines = 8,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contacts Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tr("Contact Details (Private & Verified)", "যোগাযোগের তথ্য (গোপন ও যাচাইকৃত)"), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it },
                                label = { Text(tr("Personal Phone Number *", "ব্যক্তিগত ফোন নম্বর *")) },
                                placeholder = { Text("+880 17XXXXXXXX") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = whatsapp,
                                onValueChange = { whatsapp = it },
                                label = { Text(tr("WhatsApp Number", "হোয়াটসঅ্যাপ নম্বর")) },
                                placeholder = { Text("+880 1XXXXXXXXX") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = imo,
                                onValueChange = { imo = it },
                                label = { Text(tr("IMO Number", "ইমো নম্বর")) },
                                placeholder = { Text("+880 1XXXXXXXXX") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = facebook,
                                onValueChange = { facebook = it },
                                label = { Text(tr("Facebook Profile Link", "ফেসবুক প্রোফাইল লিংক")) },
                                placeholder = { Text("https://facebook.com/...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = instagram,
                                onValueChange = { instagram = it },
                                label = { Text(tr("Instagram Handle", "ইনস্টাগ্রাম হ্যান্ডেল")) },
                                placeholder = { Text("@username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = guardianPhone,
                                onValueChange = { guardianPhone = it },
                                label = { Text(tr("Guardian's Contact Phone *", "অভিভাবকের ফোন নম্বর *")) },
                                placeholder = { Text("+880 18XXXXXXXX") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                6 -> {
                    // STEP 7: Partner Preferences
                    Text(tr("Partner Preferences", "সঙ্গীর মধ্যে যা প্রত্যাশা করি"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Specify ideal candidate criteria (all fields optional)", "আপনার কাঙ্ক্ষিত জীবনসঙ্গীর বৈশিষ্ট্য উল্লেখ করুন (সব ঘর ঐচ্ছিক)"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Partner Preferences", "সঙ্গীর পছন্দ"),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            // Age Range
                            Text(
                                text = "${tr("Preferred Age Range:", "প্রত্যাশিত বয়স:")} ${trNum(partnerAgeMin)} ${tr("to", "থেকে")} ${trNum(partnerAgeMax)} ${tr("years", "বছর")}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = partnerAgeMin.toString(),
                                    onValueChange = { partnerAgeMin = it.toIntOrNull() ?: 18 },
                                    label = { Text(tr("Min Age", "সর্বনিম্ন বয়স")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = partnerAgeMax.toString(),
                                    onValueChange = { partnerAgeMax = it.toIntOrNull() ?: 35 },
                                    label = { Text(tr("Max Age", "সর্বোচ্চ বয়স")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            // Height Range
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DropdownSelector(
                                        label = tr("Min Height", "সর্বনিম্ন উচ্চতা"),
                                        selectedValue = partnerHeightMin,
                                        options = BiodataOptions.HEIGHTS,
                                        onSelect = { partnerHeightMin = it }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DropdownSelector(
                                        label = tr("Max Height", "সর্বোচ্চ উচ্চতা"),
                                        selectedValue = partnerHeightMax,
                                        options = BiodataOptions.HEIGHTS,
                                        onSelect = { partnerHeightMax = it }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            MultiSelectChips(
                                title = tr("Preferred Marital Status", "বৈবাহিক অবস্থা"),
                                options = BiodataOptions.MARITAL_STATUSES,
                                selectedItems = partnerMaritalStatuses,
                                onToggle = { opt ->
                                    partnerMaritalStatuses = if (partnerMaritalStatuses.contains(opt)) partnerMaritalStatuses - opt else partnerMaritalStatuses + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            DropdownSelector(
                                label = tr("Preferred Education Level", "প্রত্যাশিত শিক্ষাগত যোগ্যতা"),
                                selectedValue = partnerEducation,
                                options = BiodataOptions.EDUCATION_LEVELS,
                                onSelect = { partnerEducation = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            MultiSelectChips(
                                title = tr("Preferred Professions", "প্রত্যাশিত পেশাসমূহ"),
                                options = listOf("Doctor", "Engineer", "Software Engineer", "Banker", "Teacher", "Government Employee", "Businessperson", "Accountant", "Lawyer", "Civil Service (BCS)", "Lecturer / Professor", "Student", "Others"),
                                selectedItems = partnerProfessions,
                                onToggle = { opt ->
                                    partnerProfessions = if (partnerProfessions.contains(opt)) partnerProfessions - opt else partnerProfessions + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SearchableMultiSelectDropdown(
                                label = tr("All Preferred Professions (Search & Select Multiple)", "সকল প্রত্যাশিত পেশা (খুঁজুন ও একাধিক নির্বাচন করুন)"),
                                selectedItems = partnerProfessions,
                                options = BiodataOptions.PROFESSIONS,
                                onToggle = { prof ->
                                    partnerProfessions = if (partnerProfessions.contains(prof)) partnerProfessions - prof else partnerProfessions + prof
                                },
                                onClearAll = { partnerProfessions = emptyList() },
                                placeholder = tr("Any Profession (or tap to search all)", "যেকোনো পেশা (অথবা সব পেশা খুঁজুন)")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            MultiSelectChips(
                                title = tr("Preferred Divisions", "প্রত্যাশিত বিভাগ"),
                                options = BangladeshLocationData.DIVISIONS.map { it.name },
                                selectedItems = partnerLocations,
                                onToggle = { opt ->
                                    partnerLocations = if (partnerLocations.contains(opt)) partnerLocations - opt else partnerLocations + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))



                            // Searchable Multiple Districts Selection
                            
                            val allDistrictNames = remember { BangladeshLocationData.getAllDistrictNames(context) }
                            SearchableMultiSelectDropdown(
                                label = tr("Preferred Districts", "প্রত্যাশিত জেলাসমূহ"),
                                selectedItems = partnerDistricts,
                                options = allDistrictNames,
                                onToggle = { dist ->
                                    partnerDistricts = if (partnerDistricts.contains(dist)) partnerDistricts - dist else partnerDistricts + dist
                                },
                                onClearAll = { partnerDistricts = emptyList() },
                                placeholder = tr("Any District (or tap to select multiple)", "যেকোনো জেলা (একাধিক নির্বাচন করতে ট্যাপ করুন)")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            MultiSelectChips(
                                title = tr("Preferred Financial Conditions", "প্রত্যাশিত আর্থিক অবস্থা"),
                                options = BiodataOptions.FINANCIAL_CONDITIONS,
                                selectedItems = partnerFinancialConditions,
                                onToggle = { opt ->
                                    partnerFinancialConditions = if (partnerFinancialConditions.contains(opt)) partnerFinancialConditions - opt else partnerFinancialConditions + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            MultiSelectChips(
                                title = tr("Preferred Complexions", "প্রত্যাশিত গাত্রবর্ণ"),
                                options = BiodataOptions.COMPLEXIONS,
                                selectedItems = partnerComplexions,
                                onToggle = { opt ->
                                    partnerComplexions = if (partnerComplexions.contains(opt)) partnerComplexions - opt else partnerComplexions + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                          
                            DropdownSelector(
                                label = tr("Preferred IELTS Score", "প্রত্যাশিত আইইএলটিএস স্কোর"),
                                selectedValue = partnerIelts,
                                options = BiodataOptions.IELTS_SCORES,
                                onSelect = { partnerIelts = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            MultiSelectChips(
                                title = tr("Preferred Overseas Countries", "প্রত্যাশিত প্রবাসের দেশসমূহ"),
                                options = BiodataOptions.OVERSEAS_COUNTRIES,
                                selectedItems = partnerOverseasCountries,
                                onToggle = { opt ->
                                    partnerOverseasCountries = if (partnerOverseasCountries.contains(opt)) partnerOverseasCountries - opt else partnerOverseasCountries + opt
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            DropdownSelector(
                                label = tr("Religion", "ধর্ম"),
                                selectedValue = partnerReligion,
                                options = BiodataOptions.RELIGIONS,
                                onSelect = { partnerReligion = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            
                            DropdownSelector(
                                label = tr("Marriage Timeline *", "বিবাহের সময়সীমা *"),
                                selectedValue = partnerMarriageTimeline,
                                options = BiodataOptions.MARRIAGE_TIMELINES,
                                onSelect = { partnerMarriageTimeline = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("Additional Expectations (Max 5000 char)", "অন্যান্য প্রত্যাশা (সর্বোচ্চ ৫০০০ অক্ষর)"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("${trNum(partnerExpectations.length)}/${trNum(5000)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = partnerExpectations,
                                onValueChange = { if (it.length <= 5000) partnerExpectations = it },
                                placeholder = { Text(tr("Specify family type preference, lifestyle, religious commitment, relocation preferences, etc.", "পরিবারের ধরন, জীবনযাত্রা, ধর্মীয় অনুশাসন ইত্যাদি সম্পর্কে আপনার প্রত্যাশা লিখুন...")) },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
