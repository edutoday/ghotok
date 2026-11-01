package com.example.data.model

import android.content.Context
import org.json.JSONObject

data class BdDivision(
    val id: String,
    val name: String,
    val bnName: String
)

data class BdDistrict(
    val id: String,
    val divisionId: String,
    val name: String,
    val bnName: String
)

data class BdUpazila(
    val id: String,
    val districtId: String,
    val name: String,
    val bnName: String
)

object BangladeshLocationData {

    val DIVISIONS = listOf(
        BdDivision("1", "Barishal", "বরিশাল"),
        BdDivision("2", "Chattogram", "চট্টগ্রাম"),
        BdDivision("3", "Dhaka", "ঢাকা"),
        BdDivision("4", "Khulna", "খুলনা"),
        BdDivision("5", "Rajshahi", "রাজশাহী"),
        BdDivision("6", "Rangpur", "রংপুর"),
        BdDivision("7", "Sylhet", "সিলেট"),
        BdDivision("8", "Mymensingh", "ময়মনসিংহ")
    )

    private val bnNameByEnglish = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        DIVISIONS.forEach { div ->
            bnNameByEnglish[div.name.trim().lowercase()] = div.bnName
        }
    }

    private var cachedDistricts: List<BdDistrict>? = null
    private var cachedUpazilas: List<BdUpazila>? = null

    fun ensureLoaded(context: Context) {
        loadDistricts(context)
        loadUpazilas(context)
    }

    fun getBanglaName(englishName: String): String? {
        if (englishName.isBlank()) return null
        return bnNameByEnglish[englishName.trim().lowercase()]
    }

    fun loadDistricts(context: Context): List<BdDistrict> {
        if (cachedDistricts != null) return cachedDistricts!!
        val list = mutableListOf<BdDistrict>()
        try {
            val jsonString = context.assets.open("bd-districts.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("districts")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BdDistrict(
                        id = obj.getString("id"),
                        divisionId = obj.getString("division_id"),
                        name = obj.getString("name"),
                        bnName = obj.optString("bn_name", obj.getString("name"))
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback to essential districts if asset loading fails
            list.addAll(
                listOf(
                    BdDistrict("1", "3", "Dhaka", "ঢাকা"),
                    BdDistrict("3", "3", "Gazipur", "গাজীপুর"),
                    BdDistrict("11", "3", "Narayanganj", "নারায়াণগঞ্জ"),
                    BdDistrict("43", "2", "Chattogram", "চট্টগ্রাম"),
                    BdDistrict("54", "7", "Sylhet", "সিলেট"),
                    BdDistrict("24", "5", "Rajshahi", "রাজশাহী"),
                    BdDistrict("59", "4", "Khulna", "খুলনা"),
                    BdDistrict("35", "1", "Barishal", "বরিশাল"),
                    BdDistrict("32", "6", "Rangpur", "রংপুর"),
                    BdDistrict("10", "8", "Mymensingh", "ময়মনসিংহ")
                )
            )
        }
        list.forEach { dist ->
            bnNameByEnglish[dist.name.trim().lowercase()] = dist.bnName
        }
        cachedDistricts = list
        return list
    }

    fun loadUpazilas(context: Context): List<BdUpazila> {
        if (cachedUpazilas != null) return cachedUpazilas!!
        val list = mutableListOf<BdUpazila>()
        try {
            val jsonString = context.assets.open("bd-upazilas.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("upazilas")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BdUpazila(
                        id = obj.getString("id"),
                        districtId = obj.getString("district_id"),
                        name = obj.getString("name"),
                        bnName = obj.optString("bn_name", obj.getString("name"))
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback
            list.add(BdUpazila("1", "1", "Dhanmondi", "ধানমন্ডি"))
            list.add(BdUpazila("2", "1", "Gulshan", "গুলশান"))
            list.add(BdUpazila("3", "1", "Uttara", "উত্তরা"))
        }
        list.forEach { upz ->
            bnNameByEnglish[upz.name.trim().lowercase()] = upz.bnName
        }
        DHAKA_METRO_THANAS.forEach { thana ->
            bnNameByEnglish[thana.name.trim().lowercase()] = thana.bnName
        }
        cachedUpazilas = list
        return list
    }

    fun getDistrictsForDivision(context: Context, divisionName: String): List<BdDistrict> {
        val div = DIVISIONS.find { it.name.equals(divisionName, ignoreCase = true) } ?: return emptyList()
        return loadDistricts(context).filter { it.divisionId == div.id }
    }

    private val DHAKA_METRO_THANAS = listOf(
        BdUpazila("dhaka_m_1", "1", "Dhanmondi", "ধানমন্ডি"),
        BdUpazila("dhaka_m_2", "1", "Gulshan", "গুলশান"),
        BdUpazila("dhaka_m_3", "1", "Banani", "বনানী"),
        BdUpazila("dhaka_m_4", "1", "Uttara", "উত্তরা"),
        BdUpazila("dhaka_m_5", "1", "Mirpur", "মিরপুর"),
        BdUpazila("dhaka_m_6", "1", "Mohammadpur", "মোহাম্মদপুর"),
        BdUpazila("dhaka_m_7", "1", "Motijheel", "মতিঝিল"),
        BdUpazila("dhaka_m_8", "1", "Tejgaon", "তেজগাঁও"),
        BdUpazila("dhaka_m_9", "1", "Badda", "বাড্ডা"),
        BdUpazila("dhaka_m_10", "1", "Khilgaon", "খিলগাঁও"),
        BdUpazila("dhaka_m_11", "1", "Lalbagh", "লালবাগ"),
        BdUpazila("dhaka_m_12", "1", "Paltan", "পল্টন"),
        BdUpazila("dhaka_m_13", "1", "Ramna", "রমনা"),
        BdUpazila("dhaka_m_14", "1", "Shahbagh", "শাহবাগ"),
        BdUpazila("dhaka_m_15", "1", "Jatrabari", "যাত্রাবাড়ী"),
        BdUpazila("dhaka_m_16", "1", "Rampura", "রামপুরা"),
        BdUpazila("dhaka_m_17", "1", "Bashundhara R/A", "বসুন্ধরা"),
        BdUpazila("dhaka_m_18", "1", "Cantonment", "ক্যান্টনমেন্ট"),
        BdUpazila("dhaka_m_19", "1", "Malibagh", "মালিবাগ"),
        BdUpazila("dhaka_m_20", "1", "Old Dhaka / Kotwali", "পুরান ঢাকা / কোতোয়ালি")
    )

    fun getUpazilasForDistrict(context: Context, districtName: String): List<BdUpazila> {
        val dist = loadDistricts(context).find { it.name.equals(districtName, ignoreCase = true) } ?: return emptyList()
        val list = loadUpazilas(context).filter { it.districtId == dist.id }.toMutableList()
        if (dist.name.equals("Dhaka", ignoreCase = true)) {
            for (thana in DHAKA_METRO_THANAS) {
                if (list.none { it.name.equals(thana.name, ignoreCase = true) }) {
                    list.add(thana)
                }
            }
        }
        if (list.isEmpty()) {
            val fallbackUpz = BdUpazila("${dist.id}_sadar", dist.id, "${dist.name} Sadar", "${dist.bnName} সদর")
            bnNameByEnglish[fallbackUpz.name.trim().lowercase()] = fallbackUpz.bnName
            list.add(fallbackUpz)
        }
        return list.sortedBy { it.name }
    }

    fun getAllDistrictNames(context: Context): List<String> {
        return loadDistricts(context).map { it.name }.sorted()
    }
}
