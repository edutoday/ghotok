package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val displayNameBangla: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val gender: String = "", // "Male" or "Female"
    val dateOfBirth: String = "",
    val age: Int = 0,
    val maritalStatus: String = "Never Married",
    val religion: String = "Muslim",
    val profileCreatedBy: String = "Self",

    // Physical attributes
    val height: String = "",
    val weight: Int = 0,
    val complexion: String = "",
    val bloodGroup: String = "",
    val eyeColour: String = "Black",
    val hairColour: String = "Black",
    val disability: String = "None",

    // Education & Work
    val education: String = "",
    val educationGroup: String = "",
    val institution: String = "",
    val ieltsScore: String = "None / N/A",
    val employmentType: String = "",
    val profession: String = "",
    val jobDesignation: String = "",
    val jobOrganization: String = "",
    val jobEmploymentStatus: String = "",
    val monthlyIncome: String = "",
    val workLocationDistrict: String = "",
    val overseasCountry: String = "None (Living in BD)",
    val citizenshipStatus: String = "Bangladeshi Citizen",
    val overseasVisaStatus: String = "",
    val overseasWorkExperienceYears: String = "",

    // Family details
    val familyType: String = "Nuclear",
    val parentsStatus: String = "Both Alive",
    val financialCondition: String = "Middle Class",
    val fatherProfession: String = "",
    val motherProfession: String = "",
    val brotherCount: Int = 0,
    val sisterCount: Int = 0,
    val siblingsCount: String = "",
    val familyIntroduction: String = "",

    // Address
    val locationDivision: String = "",
    val locationDistrict: String = "",
    val presentDivision: String = "",
    val presentDistrict: String = "",
    val presentUpazila: String = "",
    val presentAddress: String = "",
    val permanentSameAsPresent: Boolean = false,
    val permanentDivision: String = "",
    val permanentDistrict: String = "",
    val permanentUpazila: String = "",
    val permanentAddress: String = "",

    // Bio & Contacts
    val aboutMe: String = "",
    val contactPhone: String = "",
    val guardianPhone: String = "",
    val whatsapp: String = "",
    val imo: String = "",
    val facebook: String = "",
    val instagram: String = "",

    // Partner preferences
    val partnerAgeMin: Int = 22,
    val partnerAgeMax: Int = 28,
    val partnerHeightMin: String = "5'0''",
    val partnerHeightMax: String = "5'8''",
    val partnerMaritalStatuses: List<String> = emptyList(),
    val partnerEducation: String = "",
    val partnerProfessions: List<String> = emptyList(),
    val partnerLocations: List<String> = emptyList(),
    val partnerFinancialConditions: List<String> = emptyList(),
    val partnerComplexions: List<String> = emptyList(),
    val partnerIelts: String = "None / N/A",
    val partnerOverseasCountries: List<String> = emptyList(),
    val partnerReligion: String = "Muslim",
    val partnerProfession: String = "Any Reputed Profession",
    val partnerDistrict: String = "",
    val partnerDistricts: List<String> = emptyList(),
    val partnerMarriageTimeline: String = "Within 6 months",
    val partnerExpectations: String = "",

    // System & status
    @get:PropertyName("isVerified")
    val isVerified: Boolean = false,
    val verificationStatus: String = "UNSUBMITTED", // "UNSUBMITTED", "PENDING", "VERIFIED", "REJECTED"
    val verificationDocType: String = "NID Card",
    val verificationIdNumber: String = "",
    val verificationDocUri: String = "",
    val accountStatus: String = "ACTIVE", // "ACTIVE", "SUSPENDED", "HIDDEN"
    val packageTier: String = "Free", // "Free", "Silver", "Gold", "Diamond"
    val pendingPackageTier: String = "",
    val packageActivatedAt: Long = 0L,
    val packageExpiresAt: Long = 0L,
    val interestsRemaining: Int = 2,
    val freeInterestWeekKey: String = "",
    val contactsRemaining: Int = 0,
    val savedProfileIds: List<String> = emptyList(),
    val role: String = "USER", // "USER" or "ADMIN"
    @get:PropertyName("isAdmin")
    val isAdmin: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasAdminAccess(): Boolean {
        return isAdmin ||
            role.equals("ADMIN", ignoreCase = true) ||
            role.equals("SUPER_ADMIN", ignoreCase = true) ||
            isConfiguredOrVerifiedAdminEmail(email)
    }

    fun getEffectivePackageExpiresAt(): Long {
        if (packageTier.equals("Free", ignoreCase = true)) return 0L
        if (packageExpiresAt > 0L) return packageExpiresAt
        if (packageActivatedAt > 0L) {
            return MembershipPackages.byTier(packageTier).computeExpiryMillis(packageActivatedAt)
        }
        return 0L
    }

    fun isPackageExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (packageTier.equals("Free", ignoreCase = true)) return false
        val expiryMs = getEffectivePackageExpiresAt()
        return expiryMs > 0L && nowMs >= expiryMs
    }

    fun getRemainingPackageDays(nowMs: Long = System.currentTimeMillis()): Int {
        val expiryMs = getEffectivePackageExpiresAt()
        if (packageTier.equals("Free", ignoreCase = true) || expiryMs <= 0L || nowMs >= expiryMs) return 0
        val remainingMs = expiryMs - nowMs
        return kotlin.math.ceil(remainingMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toInt().coerceAtLeast(1)
    }

    fun getFormattedPackageExpiryDate(): String {
        val expiryMs = getEffectivePackageExpiresAt()
        if (packageTier.equals("Free", ignoreCase = true) || expiryMs <= 0L) return ""
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
        return sdf.format(java.util.Date(expiryMs))
    }

    fun withWeeklyFreeInterestReset(
        currentWeek: String = getCurrentWeekKey(),
        nowMs: Long = System.currentTimeMillis()
    ): UserProfile {
        if (!packageTier.equals("Free", ignoreCase = true)) {
            if (isPackageExpired(nowMs)) {
                return copy(
                    packageTier = "Free",
                    packageActivatedAt = 0L,
                    packageExpiresAt = 0L,
                    interestsRemaining = 2,
                    contactsRemaining = 0,
                    freeInterestWeekKey = currentWeek
                )
            }
            val effectiveActivated = packageActivatedAt
            val effectiveExpiry = if (packageExpiresAt > 0L) {
                packageExpiresAt
            } else if (effectiveActivated > 0L) {
                MembershipPackages.byTier(packageTier).computeExpiryMillis(effectiveActivated)
            } else {
                0L
            }
            return if (effectiveActivated != packageActivatedAt || effectiveExpiry != packageExpiresAt) {
                copy(
                    packageActivatedAt = effectiveActivated,
                    packageExpiresAt = effectiveExpiry
                )
            } else {
                this
            }
        }
        return if (freeInterestWeekKey.isNotBlank() && freeInterestWeekKey != currentWeek) {
            copy(
                packageActivatedAt = 0L,
                packageExpiresAt = 0L,
                interestsRemaining = 2,
                contactsRemaining = 0,
                freeInterestWeekKey = currentWeek
            )
        } else if (freeInterestWeekKey.isBlank() || packageActivatedAt != 0L || packageExpiresAt != 0L) {
            copy(
                packageActivatedAt = 0L,
                packageExpiresAt = 0L,
                freeInterestWeekKey = currentWeek
            )
        } else {
            this
        }
    }
    fun toMap(): Map<String, Any> {
        val assignedCode = when {
            isValidFormattedUserId(profileCode, gender) -> profileCode.trim()
            isValidFormattedUserId(id, gender) -> id.trim()
            else -> ""
        }
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "profileCode" to assignedCode,
            "displayName" to displayName,
            "displayNameBangla" to displayNameBangla,
            "email" to email,
            "photoUrl" to photoUrl,
            "photoURL" to photoUrl,
            "gender" to gender,
            "dateOfBirth" to dateOfBirth,
            "age" to age,
            "maritalStatus" to maritalStatus,
            "religion" to religion,
            "profileCreatedBy" to profileCreatedBy,
            "height" to height,
            "weight" to weight,
            "complexion" to complexion,
            "bloodGroup" to bloodGroup,
            "eyeColour" to eyeColour,
            "hairColour" to hairColour,
            "disability" to disability,
            "education" to education,
            "educationGroup" to educationGroup,
            "institution" to institution,
            "ieltsScore" to ieltsScore,
            "employmentType" to employmentType,
            "profession" to profession,
            "jobDesignation" to jobDesignation,
            "jobOrganization" to jobOrganization,
            "jobEmploymentStatus" to jobEmploymentStatus,
            "monthlyIncome" to monthlyIncome,
            "workLocationDistrict" to workLocationDistrict,
            "overseasCountry" to overseasCountry,
            "citizenshipStatus" to citizenshipStatus,
            "overseasVisaStatus" to overseasVisaStatus,
            "overseasWorkExperienceYears" to overseasWorkExperienceYears,
            "familyType" to familyType,
            "parentsStatus" to parentsStatus,
            "financialCondition" to financialCondition,
            "fatherProfession" to fatherProfession,
            "motherProfession" to motherProfession,
            "brotherCount" to brotherCount,
            "sisterCount" to sisterCount,
            "siblingsCount" to (if (siblingsCount.isNotEmpty()) siblingsCount else "$brotherCount Brother(s), $sisterCount Sister(s)"),
            "familyIntroduction" to familyIntroduction,
            "locationDivision" to locationDivision,
            "locationDistrict" to locationDistrict,
            "presentDivision" to presentDivision,
            "presentDistrict" to presentDistrict,
            "presentUpazila" to presentUpazila,
            "presentAddress" to presentAddress,
            "permanentSameAsPresent" to permanentSameAsPresent,
            "permanentDivision" to permanentDivision,
            "permanentDistrict" to permanentDistrict,
            "permanentUpazila" to permanentUpazila,
            "permanentAddress" to permanentAddress,
            "aboutMe" to aboutMe,
            "contactPhone" to contactPhone,
            "guardianPhone" to guardianPhone,
            "whatsapp" to whatsapp,
            "imo" to imo,
            "facebook" to facebook,
            "instagram" to instagram,
            "partnerAgeMin" to partnerAgeMin,
            "partnerAgeMax" to partnerAgeMax,
            "partnerHeightMin" to partnerHeightMin,
            "partnerHeightMax" to partnerHeightMax,
            "partnerMaritalStatuses" to partnerMaritalStatuses,
            "partnerEducation" to partnerEducation,
            "partnerProfessions" to partnerProfessions,
            "partnerLocations" to partnerLocations,
            "partnerFinancialConditions" to partnerFinancialConditions,
            "partnerComplexions" to partnerComplexions,
            "partnerIelts" to partnerIelts,
            "partnerOverseasCountries" to partnerOverseasCountries,
            "partnerReligion" to partnerReligion,
            "partnerProfession" to partnerProfession,
            "partnerDistrict" to partnerDistrict,
            "partnerDistricts" to partnerDistricts,
            "partnerMarriageTimeline" to partnerMarriageTimeline,
            "partnerExpectations" to partnerExpectations,
            "isVerified" to isVerified,
            "verificationStatus" to (if (isVerified) "VERIFIED" else verificationStatus.ifBlank { "UNSUBMITTED" }),
            "verificationDocType" to verificationDocType,
            "verificationIdNumber" to verificationIdNumber,
            "verificationDocUri" to verificationDocUri,
            "accountStatus" to accountStatus.ifBlank { "ACTIVE" },
            "packageTier" to packageTier,
            "pendingPackageTier" to pendingPackageTier,
            "packageActivatedAt" to packageActivatedAt,
            "packageExpiresAt" to packageExpiresAt,
            "interestsRemaining" to interestsRemaining,
            "freeInterestWeekKey" to freeInterestWeekKey.ifBlank { getCurrentWeekKey() },
            "contactsRemaining" to contactsRemaining,
            "savedProfileIds" to savedProfileIds,
            "role" to (if (hasAdminAccess()) "ADMIN" else role.ifBlank { "USER" }),
            "isAdmin" to hasAdminAccess()
        )
        return map
    }

    fun getFormattedUserId(): String {
        if (isValidFormattedUserId(profileCode, gender)) {
            return profileCode.trim()
        }
        if (isValidFormattedUserId(id, gender)) {
            return id.trim()
        }
        val existingSerial = extractSerialFromUserId(profileCode)
            ?: extractSerialFromUserId(id)
            ?: 1
        val existingYearDigit = extractYearDigitFromUserId(profileCode)
            ?: extractYearDigitFromUserId(id)
        return formatSerialUserId(
            gender = gender,
            serial = existingSerial,
            createdAt = createdAt,
            yearDigitOverride = existingYearDigit
        )
    }

    fun matchesUserIdQuery(rawQuery: String): Boolean {
        val q = normalizeDigitsToAscii(rawQuery.trim())
            .replace("বি", "B")
            .replace("জি", "G")
            .replace("ব", "B")
            .replace("জ", "G")
            .replace(" ", "")
            .replace("-", "")
        if (q.isEmpty()) return false
        val formatted = getFormattedUserId() // e.g., "B600001" or "G600001"
        val digitsOnly = formatted.dropWhile { it.isLetter() } // e.g., "600001"
        val serialOnly = if (digitsOnly.length > 1) digitsOnly.substring(1) else digitsOnly // e.g., "00001"
        val strippedSerial = serialOnly.trimStart('0').ifEmpty { "0" }
        val strippedQueryDigits = q.dropWhile { it.isLetter() }.trimStart('0')
        return formatted.contains(q, ignoreCase = true) ||
                digitsOnly.contains(q, ignoreCase = true) ||
                serialOnly.equals(q, ignoreCase = true) ||
                (q.all { it.isDigit() } && q.length >= 2 && digitsOnly.endsWith(q)) ||
                (strippedQueryDigits.isNotEmpty() && q.first().uppercaseChar() in setOf('B', 'G') &&
                    formatted.startsWith(q.first(), ignoreCase = true) && strippedSerial == strippedQueryDigits) ||
                (userId.isNotBlank() && userId.equals(q, ignoreCase = true))
    }

    fun isSavedIn(savedIds: Collection<String>): Boolean {
        if (savedIds.isEmpty()) return false
        val formatted = getFormattedUserId()
        return (userId.isNotBlank() && savedIds.contains(userId)) ||
            (id.isNotBlank() && savedIds.contains(id)) ||
            (profileCode.isNotBlank() && savedIds.contains(profileCode)) ||
            (formatted.isNotBlank() && savedIds.contains(formatted))
    }

    fun allSaveKeys(): Set<String> {
        return listOf(userId, id, profileCode, getFormattedUserId())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun getDisplayHeight(): String = formatHeightFeet(height)
    fun getDisplayPartnerHeightMin(): String = formatHeightFeet(partnerHeightMin)
    fun getDisplayPartnerHeightMax(): String = formatHeightFeet(partnerHeightMax)

    fun getPresentUpazilaDistrict(): String {
        val upz = presentUpazila.trim()
        val dist = presentDistrict.trim().ifEmpty { locationDistrict.trim() }
        return listOf(upz, dist).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun getDisplayLocation(): String {
        if (employmentType == "Overseas Employed" || (overseasCountry.isNotBlank() && overseasCountry != "None (Living in BD)")) {
            return overseasCountry
        }
        return getPresentUpazilaDistrict()
    }

    fun getDefaultAvatarRes(): Int = getDefaultAvatarRes(
        gender = gender,
        formattedId = getFormattedUserId()
    )

    fun getEffectivePhotoUrl(): String = optimizePhotoUrl(photoUrl)

    private fun completionChecks(isBangla: Boolean = false): List<Pair<String, Boolean>> {
        val hasPresentAddress = (presentDistrict.isNotBlank() || locationDistrict.isNotBlank()) &&
            presentUpazila.isNotBlank()
        val hasPermanentAddress = permanentSameAsPresent ||
            permanentDistrict.isNotBlank() ||
            permanentAddress.isNotBlank()
        val hasPartnerPrefs = partnerExpectations.isNotBlank() ||
            partnerEducation.isNotBlank() ||
            partnerProfessions.isNotEmpty() ||
            partnerMaritalStatuses.isNotEmpty() ||
            partnerDistricts.isNotEmpty()

        return listOf(
            (if (isBangla) "প্রোফাইল ছবি" else "Profile Photo") to photoUrl.isNotBlank(),
            (if (isBangla) "পূর্ণ নাম" else "Full Name") to (displayName.isNotBlank() && !displayName.equals("User", ignoreCase = true)),
            (if (isBangla) "লিঙ্গ" else "Gender") to gender.isNotBlank(),
            (if (isBangla) "জন্ম তারিখ" else "Date of Birth") to (dateOfBirth.isNotBlank() || age > 0),
            (if (isBangla) "বৈবাহিক অবস্থা ও ধর্ম" else "Marital Status & Religion") to (maritalStatus.isNotBlank() && religion.isNotBlank()),
            (if (isBangla) "উচ্চতা" else "Height") to height.isNotBlank(),
            (if (isBangla) "ওজন" else "Weight") to (weight > 0),
            (if (isBangla) "গাত্রবর্ণ" else "Complexion") to complexion.isNotBlank(),
            (if (isBangla) "রক্তের গ্রুপ" else "Blood Group") to bloodGroup.isNotBlank(),
            (if (isBangla) "শিক্ষাগত যোগ্যতা" else "Education") to education.isNotBlank(),
            (if (isBangla) "শিক্ষা প্রতিষ্ঠান" else "Institution") to institution.isNotBlank(),
            (if (isBangla) "পেশা" else "Profession") to (profession.isNotBlank() || employmentType.isNotBlank()),
            (if (isBangla) "পিতা-মাতার পেশা" else "Parents' Profession") to (fatherProfession.isNotBlank() && motherProfession.isNotBlank()),
            (if (isBangla) "পারিবারিক পরিচিতি" else "Family Introduction") to familyIntroduction.isNotBlank(),
            (if (isBangla) "বর্তমান ঠিকানা" else "Present Address") to hasPresentAddress,
            (if (isBangla) "স্থায়ী ঠিকানা" else "Permanent Address") to hasPermanentAddress,
            (if (isBangla) "নিজের সম্পর্কে" else "About Me") to aboutMe.isNotBlank(),
            (if (isBangla) "মোবাইল নম্বর" else "Contact Phone") to contactPhone.isNotBlank(),
            (if (isBangla) "অভিভাবকের নম্বর" else "Guardian Phone") to guardianPhone.isNotBlank(),
            (if (isBangla) "কাঙ্ক্ষিত জীবনসঙ্গীর বিবরণ" else "Partner Preferences") to hasPartnerPrefs
        )
    }

    fun calculateCompletionPercentage(): Int {
        val checks = completionChecks()
        if (checks.isEmpty()) return 0
        val completedCount = checks.count { it.second }
        return ((completedCount * 100f) / checks.size).toInt().coerceIn(0, 100)
    }

    fun getMissingProfileFields(isBangla: Boolean = false): List<String> {
        return completionChecks(isBangla)
            .filterNot { it.second }
            .map { it.first }
    }

    fun toJson(): String {
        val obj = org.json.JSONObject()
        for ((k, v) in toMap()) {
            when (v) {
                is List<*> -> {
                    val arr = org.json.JSONArray()
                    v.forEach { item -> if (item != null) arr.put(item) }
                    obj.put(k, arr)
                }
                else -> obj.put(k, v)
            }
        }
        createdAt?.let { obj.put("createdAtSeconds", it.seconds) }
        return obj.toString()
    }

    companion object {
        private val NEW_USER_ID_REGEX = Regex("^[BG]\\d\\d{5,}$")

        fun optimizePhotoUrl(rawUrl: String): String =
            com.example.util.CloudinaryProfileUploader.getOptimizedProfileUrl(rawUrl)

        fun getDefaultAvatarRes(
            gender: String = "",
            formattedId: String = "",
            oppositeOfGender: String = ""
        ): Int {
            val isFemale = when {
                gender.isNotBlank() -> isBrideGender(gender)
                formattedId.trim().startsWith("B", ignoreCase = true) -> true
                formattedId.trim().startsWith("G", ignoreCase = true) -> false
                oppositeOfGender.isNotBlank() -> !isBrideGender(oppositeOfGender)
                else -> false
            }
            return if (isFemale) com.example.R.drawable.default_woman_dp else com.example.R.drawable.default_man_dp
        }

        fun getCurrentWeekKey(nowMillis: Long = System.currentTimeMillis()): String {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = nowMillis
            cal.firstDayOfWeek = java.util.Calendar.SATURDAY
            cal.minimalDaysInFirstWeek = 4
            val year = cal.get(java.util.Calendar.YEAR)
            val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
            return String.format(java.util.Locale.US, "%04d-W%02d", year, week)
        }

        fun isBrideGender(gender: String): Boolean {
            val g = gender.trim()
            return g.equals("Female", ignoreCase = true) ||
                    g.equals("Bride", ignoreCase = true) ||
                    g.contains("কনে") ||
                    g.contains("মহিলা") ||
                    g.contains("নারী")
        }

        fun genderPrefix(gender: String): Char {
            return if (isBrideGender(gender)) 'B' else 'G'
        }

        fun accountYearLastDigit(createdAt: Timestamp? = null): Int {
            val cal = java.util.Calendar.getInstance()
            if (createdAt != null) {
                cal.time = createdAt.toDate()
            }
            return cal.get(java.util.Calendar.YEAR) % 10
        }

        fun isValidFormattedUserId(code: String, gender: String? = null): Boolean {
            val trimmed = code.trim()
            if (!NEW_USER_ID_REGEX.matches(trimmed)) return false
            if (gender != null && trimmed.first() != genderPrefix(gender)) return false
            val serial = trimmed.substring(2).toIntOrNull() ?: return false
            return serial >= 1
        }

        fun extractSerialFromUserId(code: String): Int? {
            val trimmed = code.trim()
            if (!NEW_USER_ID_REGEX.matches(trimmed)) return null
            return trimmed.substring(2).toIntOrNull()?.takeIf { it >= 1 }
        }

        fun extractYearDigitFromUserId(code: String): Int? {
            val trimmed = code.trim()
            if (!NEW_USER_ID_REGEX.matches(trimmed)) return null
            return trimmed[1].digitToIntOrNull()
        }

        fun formatSerialUserId(
            gender: String,
            serial: Int,
            createdAt: Timestamp? = null,
            yearDigitOverride: Int? = null
        ): String {
            val prefix = genderPrefix(gender)
            val yearDigit = yearDigitOverride ?: accountYearLastDigit(createdAt)
            val safeSerial = serial.coerceAtLeast(1)
            return String.format(java.util.Locale.US, "%c%d%05d", prefix, yearDigit, safeSerial)
        }

        fun generateUserIdFromUid(
            uid: String,
            gender: String = "Male",
            createdAt: Timestamp? = null,
            serial: Int = 1
        ): String {
            return formatSerialUserId(
                gender = gender,
                serial = serial,
                createdAt = createdAt
            )
        }

        fun fromJson(jsonStr: String): UserProfile? {
            if (jsonStr.isBlank()) return null
            return try {
                val obj = org.json.JSONObject(jsonStr)
                fun optStringList(key: String): List<String> {
                    val arr = obj.optJSONArray(key) ?: return emptyList()
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i)
                        if (v.isNotEmpty()) list.add(v)
                    }
                    return list
                }
                val uid = obj.optString("userId", "")
                val genderVal = obj.optString("gender", "")
                val createdSecs = obj.optLong("createdAtSeconds", 0L)
                val restoredCreatedAt = if (createdSecs > 0L) Timestamp(createdSecs, 0) else null
                val rawCode = obj.optString("profileCode", "").trim()
                val validCode = if (isValidFormattedUserId(rawCode, genderVal)) {
                    rawCode
                } else {
                    ""
                }
                UserProfile(
                    id = validCode.ifBlank { uid },
                    userId = uid,
                    profileCode = validCode,
                    displayName = obj.optString("displayName", ""),
                    displayNameBangla = obj.optString("displayNameBangla", ""),
                    email = obj.optString("email", ""),
                    photoUrl = obj.optString("photoURL", "").ifBlank { obj.optString("photoUrl", "") },
                    gender = genderVal,
                    dateOfBirth = obj.optString("dateOfBirth", ""),
                    age = obj.optInt("age", 0),
                    maritalStatus = obj.optString("maritalStatus", "Never Married"),
                    religion = obj.optString("religion", "Muslim"),
                    profileCreatedBy = obj.optString("profileCreatedBy", "Self"),
                    height = obj.optString("height", ""),
                    weight = obj.optInt("weight", 0),
                    complexion = obj.optString("complexion", ""),
                    bloodGroup = obj.optString("bloodGroup", ""),
                    eyeColour = obj.optString("eyeColour", "Black"),
                    hairColour = obj.optString("hairColour", "Black"),
                    disability = obj.optString("disability", "None"),
                    education = obj.optString("education", ""),
                    educationGroup = obj.optString("educationGroup", ""),
                    institution = obj.optString("institution", ""),
                    ieltsScore = obj.optString("ieltsScore", "None / N/A"),
                    employmentType = obj.optString("employmentType", ""),
                    profession = obj.optString("profession", ""),
                    jobDesignation = obj.optString("jobDesignation", ""),
                    jobOrganization = obj.optString("jobOrganization", ""),
                    jobEmploymentStatus = obj.optString("jobEmploymentStatus", ""),
                    monthlyIncome = obj.optString("monthlyIncome", ""),
                    workLocationDistrict = obj.optString("workLocationDistrict", ""),
                    overseasCountry = obj.optString("overseasCountry", "None (Living in BD)"),
                    citizenshipStatus = obj.optString("citizenshipStatus", "Bangladeshi Citizen"),
                    overseasVisaStatus = obj.optString("overseasVisaStatus", ""),
                    overseasWorkExperienceYears = obj.optString("overseasWorkExperienceYears", ""),
                    familyType = obj.optString("familyType", "Nuclear"),
                    parentsStatus = obj.optString("parentsStatus", "Both Alive"),
                    financialCondition = obj.optString("financialCondition", "Middle Class"),
                    fatherProfession = obj.optString("fatherProfession", ""),
                    motherProfession = obj.optString("motherProfession", ""),
                    brotherCount = obj.optInt("brotherCount", 0),
                    sisterCount = obj.optInt("sisterCount", 0),
                    siblingsCount = obj.optString("siblingsCount", ""),
                    familyIntroduction = obj.optString("familyIntroduction", ""),
                    locationDivision = obj.optString("locationDivision", ""),
                    locationDistrict = obj.optString("locationDistrict", ""),
                    presentDivision = obj.optString("presentDivision", ""),
                    presentDistrict = obj.optString("presentDistrict", ""),
                    presentUpazila = obj.optString("presentUpazila", ""),
                    presentAddress = obj.optString("presentAddress", ""),
                    permanentSameAsPresent = obj.optBoolean("permanentSameAsPresent", false),
                    permanentDivision = obj.optString("permanentDivision", ""),
                    permanentDistrict = obj.optString("permanentDistrict", ""),
                    permanentUpazila = obj.optString("permanentUpazila", ""),
                    permanentAddress = obj.optString("permanentAddress", ""),
                    aboutMe = obj.optString("aboutMe", ""),
                    contactPhone = obj.optString("contactPhone", ""),
                    guardianPhone = obj.optString("guardianPhone", ""),
                    whatsapp = obj.optString("whatsapp", ""),
                    imo = obj.optString("imo", ""),
                    facebook = obj.optString("facebook", ""),
                    instagram = obj.optString("instagram", ""),
                    partnerAgeMin = obj.optInt("partnerAgeMin", 22),
                    partnerAgeMax = obj.optInt("partnerAgeMax", 28),
                    partnerHeightMin = obj.optString("partnerHeightMin", "5'0''"),
                    partnerHeightMax = obj.optString("partnerHeightMax", "5'8''"),
                    partnerMaritalStatuses = optStringList("partnerMaritalStatuses"),
                    partnerEducation = obj.optString("partnerEducation", ""),
                    partnerProfessions = optStringList("partnerProfessions"),
                    partnerLocations = optStringList("partnerLocations"),
                    partnerFinancialConditions = optStringList("partnerFinancialConditions"),
                    partnerComplexions = optStringList("partnerComplexions"),
                    partnerIelts = obj.optString("partnerIelts", "None / N/A"),
                    partnerOverseasCountries = optStringList("partnerOverseasCountries"),
                    partnerReligion = obj.optString("partnerReligion", "Muslim"),
                    partnerProfession = obj.optString("partnerProfession", "Any Reputed Profession"),
                    partnerDistrict = obj.optString("partnerDistrict", "Dhaka / Sylhet"),
                    partnerDistricts = optStringList("partnerDistricts"),
                    partnerMarriageTimeline = obj.optString("partnerMarriageTimeline", "Within 6 months"),
                    partnerExpectations = obj.optString("partnerExpectations", ""),
                    isVerified = obj.optBoolean("isVerified", false),
                    verificationStatus = obj.optString(
                        "verificationStatus",
                        if (obj.optBoolean("isVerified", false)) "VERIFIED" else "UNSUBMITTED"
                    ),
                    verificationDocType = obj.optString("verificationDocType", "NID Card"),
                    verificationIdNumber = obj.optString("verificationIdNumber", ""),
                    verificationDocUri = obj.optString("verificationDocUri", ""),
                    accountStatus = obj.optString("accountStatus", "ACTIVE"),
                    packageTier = obj.optString("packageTier", "Free"),
                    pendingPackageTier = obj.optString("pendingPackageTier", ""),
                    packageActivatedAt = obj.optLong("packageActivatedAt", 0L),
                    packageExpiresAt = obj.optLong("packageExpiresAt", 0L),
                    interestsRemaining = obj.optInt("interestsRemaining", 2),
                    freeInterestWeekKey = obj.optString("freeInterestWeekKey", ""),
                    contactsRemaining = obj.optInt("contactsRemaining", 0),
                    savedProfileIds = optStringList("savedProfileIds"),
                    role = obj.optString("role", if (isConfiguredOrVerifiedAdminEmail(obj.optString("email", ""))) "ADMIN" else "USER"),
                    isAdmin = obj.optBoolean("isAdmin", obj.optBoolean("is_admin", isConfiguredOrVerifiedAdminEmail(obj.optString("email", "")))),
                    createdAt = restoredCreatedAt
                ).withWeeklyFreeInterestReset()
            } catch (e: Exception) {
                null
            }
        }

        private val verifiedAdminEmailsFromDb: MutableSet<String> =
            java.util.Collections.synchronizedSet(mutableSetOf<String>())

        fun registerVerifiedAdminEmail(email: String) {
            val normalized = email.trim().lowercase(java.util.Locale.US)
            if (normalized.isNotBlank() && normalized.contains("@")) {
                verifiedAdminEmailsFromDb.add(normalized)
            }
        }

        fun getConfiguredAdminEmails(): Set<String> {
            // SECURITY: the ADMIN_EMAILS build-config fallback is a debug-only escape hatch.
            // In a release build the binary is shipped to users and BuildConfig.ADMIN_EMAILS
            // (if ever set) would embed admin account emails in an APK anyone can decompile.
            // Production authorization must come from the server-side `admins` table only,
            // which is what `checkAndCacheAdminStatus` populates into verifiedAdminEmailsFromDb.
            if (!com.example.BuildConfig.DEBUG) return emptySet()
            val raw = runCatching { com.example.BuildConfig.ADMIN_EMAILS }.getOrDefault("")
            return raw.split(",")
                .map { it.trim().lowercase(java.util.Locale.US) }
                .filter { it.isNotBlank() && it != "your_admin_email" && it.contains("@") }
                .toSet()
        }

        fun isConfiguredOrVerifiedAdminEmail(email: String): Boolean {
            val normalized = email.trim().lowercase(java.util.Locale.US)
            if (normalized.isBlank()) return false
            return verifiedAdminEmailsFromDb.contains(normalized) ||
                getConfiguredAdminEmails().contains(normalized)
        }

        fun normalizeDigitsToAscii(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                if (ch in '০'..'৯') {
                    sb.append(('0'.code + (ch.code - '০'.code)).toChar())
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun formatHeightFeet(heightStr: String): String {
            if (heightStr.isBlank()) return ""
            var cleaned = heightStr.replace(Regex("""\s*\(\d+\s*cm\)"""), "").trim()
            cleaned = cleaned.replace("\"", "''")
            cleaned = cleaned.replace(Regex("""(\d+)'0(\d)"""), "$1'$2")
            if (Regex("""\d+'\d+$""").matches(cleaned)) {
                cleaned = "$cleaned''"
            }
            return cleaned
        }

        fun calculateAgeFromDob(year: Int, month: Int, day: Int): Int {
            val today = java.util.Calendar.getInstance()
            var calculatedAge = today.get(java.util.Calendar.YEAR) - year
            val currentMonth = today.get(java.util.Calendar.MONTH) + 1
            val currentDay = today.get(java.util.Calendar.DAY_OF_MONTH)
            if (currentMonth < month || (currentMonth == month && currentDay < day)) {
                calculatedAge--
            }
            return calculatedAge.coerceIn(16, 99)
        }
    }
}
