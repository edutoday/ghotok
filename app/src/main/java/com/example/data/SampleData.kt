package com.example.data

import com.example.data.model.UserProfile

object SampleData {
    val SAMPLE_CANDIDATES: List<UserProfile> = emptyList()


    fun calculateCompatibility(viewer: UserProfile, candidate: UserProfile): Int {
        var score = 40 // baseline
        // 1. Religion match
        if (viewer.partnerReligion.contains(candidate.religion, ignoreCase = true) ||
            candidate.partnerReligion.contains(viewer.religion, ignoreCase = true) ||
            viewer.religion.equals(candidate.religion, ignoreCase = true)) {
            score += 25
        }
        // 2. Age preference
        if (candidate.age in viewer.partnerAgeMin..viewer.partnerAgeMax) {
            score += 15
        }
        // 3. Location / District
        if (viewer.partnerDistrict.contains(candidate.locationDistrict, ignoreCase = true) ||
            candidate.partnerDistrict.contains(viewer.locationDistrict, ignoreCase = true)) {
            score += 10
        }
        // 4. Education / Profession
        if (candidate.profession.isNotEmpty() && viewer.partnerProfession.isNotEmpty()) {
            score += 10
        }
        return score.coerceIn(45, 98)
    }
}
