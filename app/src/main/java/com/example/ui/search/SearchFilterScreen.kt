package com.example.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BangladeshLocationData
import com.example.data.model.BiodataOptions
import com.example.data.model.UserProfile
import com.example.ui.profile.DropdownSelector
import com.example.ui.profile.MultiSelectChips
import com.example.ui.profile.SearchableMultiSelectDropdown
import com.example.ui.theme.ThemeToggleSwitch
import com.example.ui.theme.tr
import com.example.ui.theme.trNum

@Immutable
data class SearchFilterState(
    val minAge: Int = 16,
    val maxAge: Int = 50,
    val divisions: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val religion: String = "Any",
    val employmentTypes: List<String> = emptyList(),
    val professions: List<String> = emptyList(),
    val overseasCountries: List<String> = emptyList(),
    val citizenshipStatuses: List<String> = emptyList(),
    val educationLevel: String = "Any",
    val minIelts: String = "Any",
    val maritalStatuses: List<String> = emptyList(),
    val complexions: List<String> = emptyList(),
    val financialConditions: List<String> = emptyList()
)

fun matchesHeightRange(height: String, minHeight: String, maxHeight: String): Boolean {
    fun toInches(value: String): Int? {
        val normalized = UserProfile.formatHeightFeet(value)
        val match = Regex("""^\s*(\d+)\s*'\s*(\d+)""").find(normalized) ?: return null
        val feet = match.groupValues[1].toIntOrNull() ?: return null
        val inches = match.groupValues[2].toIntOrNull() ?: return null
        return if (inches in 0..11) feet * 12 + inches else null
    }

    val candidateInches = toInches(height) ?: return true
    val minInches = toInches(minHeight) ?: return true
    val maxInches = toInches(maxHeight) ?: return true
    return candidateInches in minOf(minInches, maxInches)..maxOf(minInches, maxInches)
}

fun matchesOppositeGender(candidateGender: String, currentGender: String): Boolean {
    if (candidateGender.isBlank() || currentGender.isBlank()) return true
    return UserProfile.isBrideGender(candidateGender) != UserProfile.isBrideGender(currentGender)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterScreen(
    currentFilter: SearchFilterState,
    onApplyFilter: (SearchFilterState) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var minAge by remember { mutableIntStateOf(currentFilter.minAge) }
    var maxAge by remember { mutableIntStateOf(currentFilter.maxAge) }
    var divisions by remember { mutableStateOf(currentFilter.divisions) }
    var districts by remember { mutableStateOf(currentFilter.districts) }
    var religion by remember { mutableStateOf(currentFilter.religion) }
    var employmentTypes by remember { mutableStateOf(currentFilter.employmentTypes) }
    var professions by remember { mutableStateOf(currentFilter.professions) }
    var overseasCountries by remember { mutableStateOf(currentFilter.overseasCountries) }
    var citizenshipStatuses by remember { mutableStateOf(currentFilter.citizenshipStatuses) }
    var educationLevel by remember { mutableStateOf(currentFilter.educationLevel) }
    var minIelts by remember { mutableStateOf(currentFilter.minIelts) }
    var maritalStatuses by remember { mutableStateOf(currentFilter.maritalStatuses) }
    var complexions by remember { mutableStateOf(currentFilter.complexions) }
    var financialConditions by remember { mutableStateOf(currentFilter.financialConditions) }

    val allDistrictNames = remember { BangladeshLocationData.getAllDistrictNames(context) }
    val allDivisionNames = remember { BangladeshLocationData.DIVISIONS.map { it.name } }

    val religionOptions = remember { listOf("Any") + BiodataOptions.RELIGIONS }
    val educationOptions = remember { listOf("Any") + BiodataOptions.EDUCATION_LEVELS }
    val ieltsOptions = remember { listOf("Any") + BiodataOptions.IELTS_SCORES }
    val quickProfessionOptions = remember {
        listOf(
            "Doctor",
            "Engineer",
            "Software Engineer",
            "Banker",
            "Teacher",
            "Government Employee",
            "Businessperson",
            "Accountant",
            "Lawyer",
            "Civil Service (BCS)",
            "Lecturer / Professor",
            "Student",
            "Unemployed",
            "Others"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Search Filters", "সার্চ ফিল্টার"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "পেছনে"))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            minAge = 16
                            maxAge = 50
                            divisions = emptyList()
                            districts = emptyList()
                            religion = "Any"
                            employmentTypes = emptyList()
                            professions = emptyList()
                            overseasCountries = emptyList()
                            citizenshipStatuses = emptyList()
                            educationLevel = "Any"
                            minIelts = "Any"
                            maritalStatuses = emptyList()
                            complexions = emptyList()
                            financialConditions = emptyList()
                        }
                    ) {
                        Text(tr("Reset", "রিসেট"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Button(
                    onClick = {
                        onApplyFilter(
                            SearchFilterState(
                                minAge = minAge.coerceIn(16, 80),
                                maxAge = maxAge.coerceIn(16, 80),
                                divisions = divisions,
                                districts = districts,
                                religion = religion,
                                employmentTypes = employmentTypes,
                                professions = professions,
                                overseasCountries = overseasCountries,
                                citizenshipStatuses = citizenshipStatuses,
                                educationLevel = educationLevel,
                                minIelts = minIelts,
                                maritalStatuses = maritalStatuses,
                                complexions = complexions,
                                financialConditions = financialConditions
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .testTag("apply_search_filters_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("Apply Search Filters", "সার্চ ফিল্টার প্রয়োগ করুন"))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Filter Candidates by Partner Preferences", "পাত্র/পাত্রীর পছন্দ অনুযায়ী ফিল্টার করুন"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Age Range
                    Text(
                        text = tr(
                            "Age Range: $minAge to $maxAge years",
                            "বয়স: ${trNum(minAge)} থেকে ${trNum(maxAge)} বছর"
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minAge.toString(),
                            onValueChange = { minAge = it.toIntOrNull() ?: 16 },
                            label = { Text(tr("Min Age", "সর্বনিম্ন বয়স")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = maxAge.toString(),
                            onValueChange = { maxAge = it.toIntOrNull() ?: 50 },
                            label = { Text(tr("Max Age", "সর্বোচ্চ বয়স")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Home District / Location
                    MultiSelectChips(
                        title = tr("Home Division / Location", "বিভাগ / এলাকা"),
                        options = allDivisionNames,
                        selectedItems = divisions,
                        onToggle = { opt ->
                            divisions = if (divisions.contains(opt)) divisions - opt else divisions + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SearchableMultiSelectDropdown(
                        label = tr("Home District / Location (Multiple Selection)", "নিজ জেলা / অবস্থান"),
                        selectedItems = districts,
                        options = allDistrictNames,
                        onToggle = { dist ->
                            districts = if (districts.contains(dist)) districts - dist else districts + dist
                        },
                        onClearAll = { districts = emptyList() },
                        placeholder = tr("Any District (or tap to select multiple)", "যেকোনো জেলা (অথবা একাধিক নির্বাচন করুন)")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Religion
                    DropdownSelector(
                        label = tr("Religion", "ধর্ম"),
                        selectedValue = religion,
                        options = religionOptions,
                        onSelect = { religion = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Employment Type
                    MultiSelectChips(
                        title = tr("Employment Type", "কর্মসংস্থানের ধরন"),
                        options = BiodataOptions.EMPLOYMENT_TYPES,
                        selectedItems = employmentTypes,
                        onToggle = { opt ->
                            employmentTypes = if (employmentTypes.contains(opt)) employmentTypes - opt else employmentTypes + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Profession
                    MultiSelectChips(
                        title = tr("Profession", "পেশা"),
                        options = quickProfessionOptions,
                        selectedItems = professions,
                        onToggle = { opt ->
                            professions = if (professions.contains(opt)) professions - opt else professions + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SearchableMultiSelectDropdown(
                        label = tr("All Professions (Search & Select Multiple)", "সকল পেশা (খুঁজুন ও একাধিক নির্বাচন করুন)"),
                        selectedItems = professions,
                        options = BiodataOptions.PROFESSIONS,
                        onToggle = { prof ->
                            professions = if (professions.contains(prof)) professions - prof else professions + prof
                        },
                        onClearAll = { professions = emptyList() },
                        placeholder = tr("Any Profession (or tap to search all)", "যেকোনো পেশা (অথবা সব পেশা খুঁজুন)")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6. Overseas Country / Citizenship
                    MultiSelectChips(
                        title = tr("Overseas Country", "প্রবাসী দেশ"),
                        options = BiodataOptions.OVERSEAS_COUNTRIES,
                        selectedItems = overseasCountries,
                        onToggle = { opt ->
                            overseasCountries = if (overseasCountries.contains(opt)) overseasCountries - opt else overseasCountries + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MultiSelectChips(
                        title = tr("Overseas Citizenship / Visa Status", "প্রবাসী নাগরিকত্ব / ভিসা স্ট্যাটাস"),
                        options = BiodataOptions.VISA_STATUSES,
                        selectedItems = citizenshipStatuses,
                        onToggle = { opt ->
                            citizenshipStatuses = if (citizenshipStatuses.contains(opt)) citizenshipStatuses - opt else citizenshipStatuses + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 7. Education Level
                    DropdownSelector(
                        label = tr("Education Level", "শিক্ষাগত যোগ্যতা"),
                        selectedValue = educationLevel,
                        options = educationOptions,
                        onSelect = { educationLevel = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 8. IELTS Score
                    DropdownSelector(
                        label = tr("IELTS Score", "আইইএলটিএস স্কোর"),
                        selectedValue = minIelts,
                        options = ieltsOptions,
                        onSelect = { minIelts = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 9. Marital Status
                    MultiSelectChips(
                        title = tr("Marital Status", "বৈবাহিক অবস্থা"),
                        options = BiodataOptions.MARITAL_STATUSES,
                        selectedItems = maritalStatuses,
                        onToggle = { opt ->
                            maritalStatuses = if (maritalStatuses.contains(opt)) maritalStatuses - opt else maritalStatuses + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 10. Complexion
                    MultiSelectChips(
                        title = tr("Complexion", "গাত্রবর্ণ"),
                        options = BiodataOptions.COMPLEXIONS,
                        selectedItems = complexions,
                        onToggle = { opt ->
                            complexions = if (complexions.contains(opt)) complexions - opt else complexions + opt
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 11. Financial Condition
                    MultiSelectChips(
                        title = tr("Financial Condition", "পারিবারিক আর্থিক অবস্থা"),
                        options = BiodataOptions.FINANCIAL_CONDITIONS,
                        selectedItems = financialConditions,
                        onToggle = { opt ->
                            financialConditions = if (financialConditions.contains(opt)) financialConditions - opt else financialConditions + opt
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
