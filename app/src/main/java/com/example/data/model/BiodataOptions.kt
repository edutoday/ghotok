package com.example.data.model

object BiodataOptions {

    val RELIGIONS = listOf(
        "Islam",
        "Hindu",
        "Christian",
        "Buddhist",
        "Other"
    )

    val CREATED_BY = listOf(
        "Self",
        "Parent",
        "Sibling",
        "Guardian",
        "Other"
    )

    val MARITAL_STATUSES = listOf(
        "Never Married",
        "Divorced",
        "Widowed",
        "Separated"
    )

    val HEIGHTS = (48..77).map { inches ->
        val ft = inches / 12
        val remainderInches = inches % 12
        "$ft'${remainderInches}''"
    }

    val COMPLEXIONS = listOf(
        "Very Fair",
        "Fair",
        "Light Brown / Bright Shamla",
        "Brown / Shamla",
        "Deep Brown / Sham Borno"
    )

    val BLOOD_GROUPS = listOf(
        "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"
    )

    val EYE_COLOURS = listOf(
        "Black",
        "Dark Brown",
        "Brown",
        "Hazel",
        "Green",
        "Blue",
        "Other"
    )

    val HAIR_COLOURS = listOf(
        "Black",
        "Dark Brown",
        "Brown",
        "Gray",
        "Other"
    )

    val DISABILITIES = listOf(
        "None",
        "Intellectual",
        "Physical Disability",
        "Visual Impairment",
        "Hearing Impairment",
        "Speech Impairment",
        "Other"
    )

    val EDUCATION_LEVELS = listOf(
        "Primary",
        "Junior School",
        "SSC/Dakhil",
        "HSC/Alim",
        "Bachelor’s",
        "Master’s",
        "MBBS",
        "MPhil",
        "PhD",
        "Diploma",
        "Qawmi Madrasa",
        "Other",
        "Uneducated"
    )

    val IELTS_SCORES = listOf(
        "None / N/A",
        "5.0",
        "5.5",
        "6.0",
        "6.5",
        "7.0",
        "7.5",
        "8.0",
        "8.5",
        "9.0"
    )

    val EMPLOYMENT_TYPES = listOf(
        "Govt job",
        "Private job",
        "Overseas Employed",
        "Self Employed",
        "Unemployed"
    )

    val PROFESSIONS = listOf(
        "Accountant", "Actor", "Adviser", "App Developer", "Architect", "Army", "Artisan",
        "Craftsperson", "Artist", "Autonomous", "Bachelor", "Baker", "Banker", "Barber",
        "Barrister", "Beautician / Makeup Artist", "Blacksmith", "Blogger", "Boatman, Launch Operator",
        "Brick Kiln Worker", "Businessperson", "Butcher", "Call Center Agent", "Carpenter",
        "Chef / Cook", "Cleaner", "CNG / Auto-rickshaw Driver", "Cobbler / Shoe Repairer",
        "Construction Worker / Mason", "Caregiver", "Contractor", "Cricketer", "Dancer",
        "Data Scientist", "Day Laborer", "Deceased", "Dentist", "Staff in Foreign Mission",
        "Dependent on Diplomat", "Digital Marketer", "Doctor", "Domestic Worker / House Maid",
        "Driver", "Electrician", "Engineer", "Essayist", "Farmer", "Firefighter", "Fisherman",
        "Flight Attendant", "Footballer", "Freelancer", "Garment / RMG Worker, Sewing Machine Operator",
        "Factory Worker", "Government Employee", "Graphic Designer", "Security Guard", "Hawker, Street Vendor",
        "House Wife", "Imam, Moulana, Religious Teacher", "Religious Worker", "Instrumentalist",
        "Insurance Agent", "IT Support Assistant", "Journalist", "Judge / Justice", "Jute Worker",
        "Labor", "Lawyer", "Librarian", "Madrasah Teacher", "Mechanic", "Medical Assistant, Paramedic",
        "Medical Technologist", "Member of Local Government", "Merchant Marine Officer", "Midwife",
        "Mobile Phone Servicing Technician", "Movie Director", "Network Engineer", "NGO Worker",
        "Novelist", "Nurse", "Others", "Painter", "Pharmacist", "Photographer", "Physiotherapist",
        "Pilot", "Playwright", "Plumber / Pipe Fitter", "Poet", "Police, Ansar, BGB", "Politician",
        "Porter", "Postman", "Potter", "Private-Sector Employee", "Private Service", "Project Employee",
        "Public Speaker", "Real Estate Agent", "Researcher", "Restaurant Worker, Waiter, Hotel Staff",
        "Retired (Government Service)", "Retired (Private Service)", "Runner", "Salesman",
        "School Administrator", "Scientist", "Sculptor", "Ship-breaking Worker", "Shopkeeper",
        "Singer", "Software Engineer", "Steel Fixer / Rod Binder", "Student", "Sweeper", "Swimmer",
        "Tailor", "Teacher", "Tea Garden Worker", "Textile Worker / Weaver", "Tour Guide",
        "Translator", "Unemployed", "Unknown", "UX/UI Designer", "Veterinarian", "Video Editor",
        "Washerman", "Web Developer", "Welder", "YouTuber"
    )

    val JOB_EMPLOYMENT_STATUSES = listOf(
        "Permanent",
        "Contractual",
        "Part-time",
        "Owner",
        "Share Holder"
    )

    val MONTHLY_INCOMES = listOf(
        "Prefer not to say",

        "Under ৳20,000",
        "৳20,000 - ৳30,000",
        "৳30,000 - ৳50,000",
        "৳50,000 - ৳1,00,000",
        "৳1,00,000 - ৳2,00,000",
        "৳2,00,000 - ৳5,00,000",
        "৳5,00,000+",
        "No Income"
    )

    val OVERSEAS_COUNTRIES = listOf(
        "United Kingdom (UK)",
        "US (America)",
        "Canada",
        "Australia",
        "New Zealand",
        "Germany",
        "France",
        "Italy",
        "Spain",
        "Portugal",
        "Ireland",
        "Europe",
        "Saudi Arabia",
        "UAE (Dubai)",
        "Qatar",
        "Kuwait",
        "Oman",
        "Bahrain",
        "Malaysia",
        "Singapore",
        "Japan",
        "South Korea",
        "Other Country"
    )

    val VISA_STATUSES = listOf(
        "Citizen",
        "Permanent Resident (PR)",
        "Work Permit / Visa",
        "Student Visa",
        "Dependent Visa",
        "Other"
    )

    val FAMILY_TYPES = listOf(
        "Joint",
        "Nuclear",
        "Other"
    )

    val PARENTS_STATUSES = listOf(
        "Both Alive",
        "Only Father Alive",
        "Only Mother Alive",
        "Neither Alive"
    )

    val FINANCIAL_CONDITIONS = listOf(
        "Upper Class",
        "Upper Middle Class",
        "Middle Class",
        "Lower Middle Class",
        "Lower Class"
    )

    val MARRIAGE_TIMELINES = listOf(
        "Immediately",
        "Within 1-3 months",
        "Within 6 months",
        "Within 1 year",
        "Within 2 years",
        "Flexible / Not Fixed"
    )

    data class DistrictData(
        val name: String,
        val upazilas: List<String>
    )

    data class DivisionData(
        val name: String,
        val districts: List<DistrictData>
    )

    val BANGLADESH_DIVISIONS: List<DivisionData> = listOf(
        DivisionData(
            name = "Dhaka",
            districts = listOf(
                DistrictData("Dhaka", listOf("Dhanmondi", "Gulshan", "Banani", "Uttara", "Mirpur", "Mohammadpur", "Motijheel", "Savar", "Keraniganj", "Dhamrai")),
                DistrictData("Gazipur", listOf("Gazipur Sadar", "Kaliakair", "Kapasia", "Sreepur", "Kaliganj")),
                DistrictData("Narayanganj", listOf("Narayanganj Sadar", "Bandar", "Rupganj", "Sonargaon", "Araihazar")),
                DistrictData("Tangail", listOf("Tangail Sadar", "Mirzapur", "Madhupur", "Ghatail", "Kalihati", "Sakhipur")),
                DistrictData("Faridpur", listOf("Faridpur Sadar", "Boalmari", "Bhanga", "Nagarkanda", "Madhukhali")),
                DistrictData("Manikganj", listOf("Manikganj Sadar", "Singair", "Saturia", "Shivalaya", "Harirampur")),
                DistrictData("Munshiganj", listOf("Munshiganj Sadar", "Sreenagar", "Sirajdikhan", "Tongibari", "Lohajang")),
                DistrictData("Narsingdi", listOf("Narsingdi Sadar", "Palash", "Shibpur", "Raipura", "Belabo")),
                DistrictData("Gopalganj", listOf("Gopalganj Sadar", "Kashiani", "Kotalipara", "Muksudpur", "Tungipara")),
                DistrictData("Kishoreganj", listOf("Kishoreganj Sadar", "Bhairab", "Bajitpur", "Katiadi", "Pakundia")),
                DistrictData("Madaripur", listOf("Madaripur Sadar", "Shibchar", "Kalkini", "Rajoir")),
                DistrictData("Rajbari", listOf("Rajbari Sadar", "Goalanda", "Pangsha", "Baliakandi")),
                DistrictData("Shariatpur", listOf("Shariatpur Sadar", "Naria", "Zajira", "Bhedarganj", "Damudya"))
            )
        ),
        DivisionData(
            name = "Chattogram",
            districts = listOf(
                DistrictData("Chattogram", listOf("Panchlaish", "Kotwali", "Double Mooring", "Halishahar", "Hathazari", "Raozan", "Fatikchhari", "Patiya", "Sitakunda", "Mirsharai", "Anwara", "Boalkhali")),
                DistrictData("Cox's Bazar", listOf("Cox's Bazar Sadar", "Ramu", "Chakaria", "Teknaf", "Ukhia", "Maheshkhali", "Pekua")),
                DistrictData("Cumilla", listOf("Cumilla Adarsha Sadar", "Laksam", "Debidwar", "Burichang", "Chandina", "Muradnagar", "Daudkandi", "Chauddagram")),
                DistrictData("Noakhali", listOf("Noakhali Sadar", "Begumganj", "Chatkhil", "Senbagh", "Companyganj", "Hatiya")),
                DistrictData("Feni", listOf("Feni Sadar", "Daganbhuiyan", "Chhagalnaiya", "Parshuram", "Fulgazi", "Sonagazi")),
                DistrictData("Brahmanbaria", listOf("Brahmanbaria Sadar", "Ashuganj", "Sarail", "Kasba", "Nabinagar", "Bancharampur", "Akhaura")),
                DistrictData("Chandpur", listOf("Chandpur Sadar", "Hajiganj", "Matlab Dakshin", "Matlab Uttar", "Shahrasti", "Faridganj")),
                DistrictData("Lakshmipur", listOf("Lakshmipur Sadar", "Raipur", "Ramganj", "Ramgati", "Kamalnagar")),
                DistrictData("Bandarban", listOf("Bandarban Sadar", "Ruma", "Thanchi", "Lama", "Ali Kadam")),
                DistrictData("Rangamati", listOf("Rangamati Sadar", "Kaptai", "Baghaichhari", "Barkal", "Langadu")),
                DistrictData("Khagrachhari", listOf("Khagrachhari Sadar", "Dighinala", "Panchhari", "Mahalchhari", "Matiranga"))
            )
        ),
        DivisionData(
            name = "Sylhet",
            districts = listOf(
                DistrictData("Sylhet", listOf("Sylhet Sadar", "Beanibazar", "Golapganj", "Zakiganj", "Kanaighat", "Fenchuganj", "Balaganj", "Bishwanath", "Companiganj")),
                DistrictData("Moulvibazar", listOf("Moulvibazar Sadar", "Sreemangal", "Kulaura", "Kamalganj", "Barlekha", "Rajnagar", "Juri")),
                DistrictData("Habiganj", listOf("Habiganj Sadar", "Madhabpur", "Chunarughat", "Nabiganj", "Bahubal", "Baniachong")),
                DistrictData("Sunamganj", listOf("Sunamganj Sadar", "Chhatak", "Jagannathpur", "Derai", "Tahirpur", "Dowarabazar"))
            )
        ),
        DivisionData(
            name = "Rajshahi",
            districts = listOf(
                DistrictData("Rajshahi", listOf("Boalia", "Rajpara", "Motihar", "Paba", "Godagari", "Tanore", "Bagmara", "Durgapur", "Charghat", "Puthia")),
                DistrictData("Bogura", listOf("Bogura Sadar", "Shajahanpur", "Sherpur", "Shibganj", "Gabtali", "Dhunat", "Sariakandi")),
                DistrictData("Pabna", listOf("Pabna Sadar", "Ishwardi", "Santhia", "Chatmohar", "Bera", "Sujanagar")),
                DistrictData("Sirajganj", listOf("Sirajganj Sadar", "Shahjadpur", "Ullapara", "Belkuchi", "Kamarkhanda")),
                DistrictData("Naogaon", listOf("Naogaon Sadar", "Patnitala", "Dhamoirhat", "Manda", "Mohadevpur")),
                DistrictData("Natore", listOf("Natore Sadar", "Baraigram", "Singra", "Bagatipara", "Lalpur")),
                DistrictData("Chapai Nawabganj", listOf("Chapai Nawabganj Sadar", "Shibganj", "Gomastapur", "Nachole", "Bholahat")),
                DistrictData("Joypurhat", listOf("Joypurhat Sadar", "Panchbibi", "Kalai", "Khetlal", "Akkelpur"))
            )
        ),
        DivisionData(
            name = "Khulna",
            districts = listOf(
                DistrictData("Khulna", listOf("Khulna Sadar", "Sonadanga", "Khalishpur", "Daulatpur", "Dighalia", "Dumuria", "Rupsha", "Paikgachha")),
                DistrictData("Jashore", listOf("Jashore Sadar", "Jhikargachha", "Manirampur", "Keshabpur", "Bagherpara", "Abhaynagar")),
                DistrictData("Kushtia", listOf("Kushtia Sadar", "Kumarkhali", "Mirpur", "Bheramara", "Daulatpur")),
                DistrictData("Bagerhat", listOf("Bagerhat Sadar", "Mongla", "Fakirhat", "Rampal", "Morrelganj")),
                DistrictData("Satkhira", listOf("Satkhira Sadar", "Tala", "Kaliganj", "Shyamnagar", "Kalaroa")),
                DistrictData("Chuadanga", listOf("Chuadanga Sadar", "Alamdanga", "Damurhuda", "Jibannagar")),
                DistrictData("Jhenaidah", listOf("Jhenaidah Sadar", "Kaliganj", "Kotchandpur", "Shailkupa", "Maheshpur")),
                DistrictData("Magura", listOf("Magura Sadar", "Sreepur", "Mohammadpur", "Shalikha")),
                DistrictData("Meherpur", listOf("Meherpur Sadar", "Gangni", "Mujibnagar")),
                DistrictData("Narail", listOf("Narail Sadar", "Lohagara", "Kalia"))
            )
        ),
        DivisionData(
            name = "Barishal",
            districts = listOf(
                DistrictData("Barishal", listOf("Barishal Sadar", "Bakerganj", "Babuganj", "Wazirpur", "Banaripara", "Gournadi", "Muladi", "Mehendiganj")),
                DistrictData("Bhola", listOf("Bhola Sadar", "Borhanuddin", "Char Fasson", "Lalmohan", "Daulatkhan", "Tazumuddin")),
                DistrictData("Patuakhali", listOf("Patuakhali Sadar", "Galachipa", "Bauphal", "Kalapara", "Mirzaganj")),
                DistrictData("Pirojpur", listOf("Pirojpur Sadar", "Mathbaria", "Bhandaria", "Nazirpur", "Nesarabad")),
                DistrictData("Barguna", listOf("Barguna Sadar", "Amtali", "Patharghata", "Betagi", "Bamna")),
                DistrictData("Jhalokati", listOf("Jhalokati Sadar", "Nalchity", "Rajapur", "Kathalia"))
            )
        ),
        DivisionData(
            name = "Rangpur",
            districts = listOf(
                DistrictData("Rangpur", listOf("Rangpur Sadar", "Pirgachha", "Badarganj", "Mithapukur", "Pirganj", "Kaunia", "Gangachhara")),
                DistrictData("Dinajpur", listOf("Dinajpur Sadar", "Birganj", "Parbatipur", "Fulbari", "Birol", "Biral")),
                DistrictData("Gaibandha", listOf("Gaibandha Sadar", "Gobindaganj", "Palashbari", "Sundarganj", "Sadullapur")),
                DistrictData("Kurigram", listOf("Kurigram Sadar", "Nageshwari", "Bhurungamari", "Ulipur", "Chilmari")),
                DistrictData("Nilphamari", listOf("Nilphamari Sadar", "Saidpur", "Domar", "Jaldhaka", "Kishoreganj")),
                DistrictData("Lalmonirhat", listOf("Lalmonirhat Sadar", "Aditmari", "Kaliganj", "Hatibandha", "Patgram")),
                DistrictData("Thakurgaon", listOf("Thakurgaon Sadar", "Pirganj", "Ranisankail", "Haripur", "Baliadangi")),
                DistrictData("Panchagarh", listOf("Panchagarh Sadar", "Boda", "Debiganj", "Tetulia", "Atwari"))
            )
        ),
        DivisionData(
            name = "Mymensingh",
            districts = listOf(
                DistrictData("Mymensingh", listOf("Mymensingh Sadar", "Muktagachha", "Fulbaria", "Trishal", "Bhaluka", "Gafargaon", "Ishwarganj", "Nandail", "Haluaghat")),
                DistrictData("Jamalpur", listOf("Jamalpur Sadar", "Sarishabari", "Melandaha", "Dewanganj", "Islampur", "Madarganj")),
                DistrictData("Netrokona", listOf("Netrokona Sadar", "Kendua", "Mohanganj", "Durgapur", "Purbadhala", "Kalmakanda")),
                DistrictData("Sherpur", listOf("Sherpur Sadar", "Nalitabari", "Nakla", "Jhenaigati", "Sreebardi"))
            )
        )
    )
}
