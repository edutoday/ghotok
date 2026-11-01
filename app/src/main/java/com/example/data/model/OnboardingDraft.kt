package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class OnboardingDraft(
    // Step 1: Basic details
    val displayName: String = "",
    val displayNameBangla: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val maritalStatus: String = "Never Married",
    val religion: String = "Muslim",
    val profileCreatedBy: String = "Self",

    // Step 2: Physical attributes
    val height: String = "",
    val weightKg: String = "",
    val complexion: String = "",
    val bloodGroup: String = "",
    val eyeColour: String = "Black",
    val hairColour: String = "Black",
    val disability: String = "None",

    // Step 3: Education and work
    val educationLevel: String = "",
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
    val overseasCountry: String = "",
    val overseasVisaStatus: String = "",
    val overseasWorkExperienceYears: String = "",

    // Step 4: Family Details and Location
    val familyType: String = "Nuclear",
    val parentsStatus: String = "Both Alive",
    val financialCondition: String = "Middle Class",
    val fatherProfession: String = "",
    val motherProfession: String = "",
    val brotherCount: Int = 0,
    val sisterCount: Int = 0,
    val familyIntroduction: String = "",
    val presentDivision: String = "",
    val presentDistrict: String = "",
    val presentUpazila: String = "",
    val presentAddress: String = "",
    val permanentSameAsPresent: Boolean = false,
    val permanentDivision: String = "",
    val permanentDistrict: String = "",
    val permanentUpazila: String = "",
    val permanentAddress: String = "",

    // Step 5: Bio and contacts
    val aboutMe: String = "",
    val contactPhone: String = "",
    val whatsapp: String = "",
    val imo: String = "",
    val facebook: String = "",
    val instagram: String = "",
    val guardianPhone: String = "",

    // Step 6: Partner preferences (all optional)
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
    val partnerDistricts: List<String> = emptyList(),
    val partnerMarriageTimeline: String = "Within 6 months",
    val partnerExpectations: String = ""
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("displayName", displayName)
        json.put("displayNameBangla", displayNameBangla)
        json.put("gender", gender)
        json.put("dateOfBirth", dateOfBirth)
        json.put("maritalStatus", maritalStatus)
        json.put("religion", religion)
        json.put("profileCreatedBy", profileCreatedBy)
        json.put("height", height)
        json.put("weightKg", weightKg)
        json.put("complexion", complexion)
        json.put("bloodGroup", bloodGroup)
        json.put("eyeColour", eyeColour)
        json.put("hairColour", hairColour)
        json.put("disability", disability)
        json.put("educationLevel", educationLevel)
        json.put("educationGroup", educationGroup)
        json.put("institution", institution)
        json.put("ieltsScore", ieltsScore)
        json.put("employmentType", employmentType)
        json.put("profession", profession)
        json.put("jobDesignation", jobDesignation)
        json.put("jobOrganization", jobOrganization)
        json.put("jobEmploymentStatus", jobEmploymentStatus)
        json.put("monthlyIncome", monthlyIncome)
        json.put("workLocationDistrict", workLocationDistrict)
        json.put("overseasCountry", overseasCountry)
        json.put("overseasVisaStatus", overseasVisaStatus)
        json.put("overseasWorkExperienceYears", overseasWorkExperienceYears)
        json.put("familyType", familyType)
        json.put("parentsStatus", parentsStatus)
        json.put("financialCondition", financialCondition)
        json.put("fatherProfession", fatherProfession)
        json.put("motherProfession", motherProfession)
        json.put("brotherCount", brotherCount)
        json.put("sisterCount", sisterCount)
        json.put("familyIntroduction", familyIntroduction)
        json.put("presentDivision", presentDivision)
        json.put("presentDistrict", presentDistrict)
        json.put("presentUpazila", presentUpazila)
        json.put("presentAddress", presentAddress)
        json.put("permanentSameAsPresent", permanentSameAsPresent)
        json.put("permanentDivision", permanentDivision)
        json.put("permanentDistrict", permanentDistrict)
        json.put("permanentUpazila", permanentUpazila)
        json.put("permanentAddress", permanentAddress)
        json.put("aboutMe", aboutMe)
        json.put("contactPhone", contactPhone)
        json.put("whatsapp", whatsapp)
        json.put("imo", imo)
        json.put("facebook", facebook)
        json.put("instagram", instagram)
        json.put("guardianPhone", guardianPhone)
        json.put("partnerAgeMin", partnerAgeMin)
        json.put("partnerAgeMax", partnerAgeMax)
        json.put("partnerHeightMin", partnerHeightMin)
        json.put("partnerHeightMax", partnerHeightMax)
        json.put("partnerMaritalStatuses", JSONArray(partnerMaritalStatuses))
        json.put("partnerEducation", partnerEducation)
        json.put("partnerProfessions", JSONArray(partnerProfessions))
        json.put("partnerLocations", JSONArray(partnerLocations))
        json.put("partnerFinancialConditions", JSONArray(partnerFinancialConditions))
        json.put("partnerComplexions", JSONArray(partnerComplexions))
        json.put("partnerIelts", partnerIelts)
        json.put("partnerOverseasCountries", JSONArray(partnerOverseasCountries))
        json.put("partnerReligion", partnerReligion)
        json.put("partnerDistricts", JSONArray(partnerDistricts))
        json.put("partnerMarriageTimeline", partnerMarriageTimeline)
        json.put("partnerExpectations", partnerExpectations)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): OnboardingDraft {
            return try {
                val json = JSONObject(jsonStr)
                fun jsonArrayToList(key: String): List<String> {
                    val arr = json.optJSONArray(key) ?: return emptyList()
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    return list
                }

                OnboardingDraft(
                    displayName = json.optString("displayName", ""),
                    displayNameBangla = json.optString("displayNameBangla", ""),
                    gender = json.optString("gender", ""),
                    dateOfBirth = json.optString("dateOfBirth", ""),
                    maritalStatus = json.optString("maritalStatus", "Never Married"),
                    religion = json.optString("religion", "Muslim"),
                    profileCreatedBy = json.optString("profileCreatedBy", "Self"),
                    height = json.optString("height", ""),
                    weightKg = json.optString("weightKg", ""),
                    complexion = json.optString("complexion", ""),
                    bloodGroup = json.optString("bloodGroup", ""),
                    eyeColour = json.optString("eyeColour", "Black"),
                    hairColour = json.optString("hairColour", "Black"),
                    disability = json.optString("disability", "None"),
                    educationLevel = json.optString("educationLevel", ""),
                    educationGroup = json.optString("educationGroup", ""),
                    institution = json.optString("institution", ""),
                    ieltsScore = json.optString("ieltsScore", "None / N/A"),
                    employmentType = json.optString("employmentType", ""),
                    profession = json.optString("profession", ""),
                    jobDesignation = json.optString("jobDesignation", ""),
                    jobOrganization = json.optString("jobOrganization", ""),
                    jobEmploymentStatus = json.optString("jobEmploymentStatus", ""),
                    monthlyIncome = json.optString("monthlyIncome", ""),
                    workLocationDistrict = json.optString("workLocationDistrict", ""),
                    overseasCountry = json.optString("overseasCountry", ""),
                    overseasVisaStatus = json.optString("overseasVisaStatus", ""),
                    overseasWorkExperienceYears = json.optString("overseasWorkExperienceYears", ""),
                    familyType = json.optString("familyType", "Nuclear"),
                    parentsStatus = json.optString("parentsStatus", "Both Alive"),
                    financialCondition = json.optString("financialCondition", "Middle Class"),
                    fatherProfession = json.optString("fatherProfession", ""),
                    motherProfession = json.optString("motherProfession", ""),
                    brotherCount = json.optInt("brotherCount", 0),
                    sisterCount = json.optInt("sisterCount", 0),
                    familyIntroduction = json.optString("familyIntroduction", ""),
                    presentDivision = json.optString("presentDivision", ""),
                    presentDistrict = json.optString("presentDistrict", ""),
                    presentUpazila = json.optString("presentUpazila", ""),
                    presentAddress = json.optString("presentAddress", ""),
                    permanentSameAsPresent = json.optBoolean("permanentSameAsPresent", false),
                    permanentDivision = json.optString("permanentDivision", ""),
                    permanentDistrict = json.optString("permanentDistrict", ""),
                    permanentUpazila = json.optString("permanentUpazila", ""),
                    permanentAddress = json.optString("permanentAddress", ""),
                    aboutMe = json.optString("aboutMe", ""),
                    contactPhone = json.optString("contactPhone", ""),
                    whatsapp = json.optString("whatsapp", ""),
                    imo = json.optString("imo", ""),
                    facebook = json.optString("facebook", ""),
                    instagram = json.optString("instagram", ""),
                    guardianPhone = json.optString("guardianPhone", ""),
                    partnerAgeMin = json.optInt("partnerAgeMin", 22),
                    partnerAgeMax = json.optInt("partnerAgeMax", 28),
                    partnerHeightMin = json.optString("partnerHeightMin", "5'0''"),
                    partnerHeightMax = json.optString("partnerHeightMax", "5'8''"),
                    partnerMaritalStatuses = jsonArrayToList("partnerMaritalStatuses"),
                    partnerEducation = json.optString("partnerEducation", ""),
                    partnerProfessions = jsonArrayToList("partnerProfessions"),
                    partnerLocations = jsonArrayToList("partnerLocations"),
                    partnerFinancialConditions = jsonArrayToList("partnerFinancialConditions"),
                    partnerComplexions = jsonArrayToList("partnerComplexions"),
                    partnerIelts = json.optString("partnerIelts", "None / N/A"),
                    partnerOverseasCountries = jsonArrayToList("partnerOverseasCountries"),
                    partnerReligion = json.optString("partnerReligion", "Muslim"),
                    partnerDistricts = jsonArrayToList("partnerDistricts"),
                    partnerMarriageTimeline = json.optString("partnerMarriageTimeline", "Within 6 months"),
                    partnerExpectations = json.optString("partnerExpectations", "")
                )
            } catch (e: Exception) {
                OnboardingDraft()
            }
        }
    }
}
