package com.example.ui.packages

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Verified
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
import com.example.data.model.MembershipPackage
import com.example.data.model.MembershipPackages
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.tr
import com.example.ui.theme.trNum
import com.example.ui.theme.trOption
import com.example.util.PlayBillingManager

@Composable
fun PackagesScreen(
    currentProfile: UserProfile,
    onSelectPackage: (MembershipPackage) -> Unit,
    onSubmitPaymentRequest: (
        pkg: MembershipPackage,
        paymentMethod: String,
        senderPhone: String,
        transactionId: String
    ) -> Unit = { pkg, _, _, _ -> onSelectPackage(pkg) },
    onGooglePlayPurchaseCompleted: (
        pkg: MembershipPackage,
        paymentMethod: String,
        purchaseToken: String,
        orderId: String
    ) -> Unit,
    allCandidates: List<UserProfile> = emptyList(),
    onAdminChangeUserPackage: (
        targetUserId: String,
        targetProfileId: String,
        newTier: String,
        customInterests: Int?,
        customContacts: Int?,
        customDurationDays: Int?,
        adminNote: String
    ) -> Unit = { _, _, newTier, _, _, _, _ ->
        val matchedPkg = MembershipPackages.byTier(newTier)
        if (matchedPkg != null) {
            onSelectPackage(matchedPkg)
        }
    }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isBangla = LocalLanguageController.current.isBangla

    var selectedPackageForConfirmation by remember { mutableStateOf<MembershipPackage?>(null) }
    var showAdminPackageModal by remember { mutableStateOf(false) }
    var billingStatusBanner by remember { mutableStateOf<String?>(null) }
    var checkoutErrorMessage by remember { mutableStateOf<String?>(null) }

    val billingManager = remember(context) {
        PlayBillingManager(
            context = context,
            onPurchaseCompleted = { pkg, orderId, purchaseToken ->
                checkoutErrorMessage = null
                selectedPackageForConfirmation = null
                billingStatusBanner = if (isBangla) {
                    "Google Play purchase পাওয়া গেছে। ${pkg.nameBn} যাচাই করা হচ্ছে..."
                } else {
                    "Google Play purchase received. Verifying ${pkg.nameEn}..."
                }
                onGooglePlayPurchaseCompleted(
                    pkg,
                    "GOOGLE_PLAY_BILLING",
                    purchaseToken,
                    orderId
                )
            },
            onBillingError = { msg ->
                checkoutErrorMessage = msg
                billingStatusBanner = msg
            }
        )
    }

    val billingConnected by billingManager.isPlayStoreConnected.collectAsState()
    val productDetailsMap by billingManager.productDetailsById.collectAsState()

    DisposableEffect(billingManager) {
        billingManager.startBillingConnection()
        onDispose {
            billingManager.endConnection()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("packages_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Ghotok Membership Packages", "ঘটক মেম্বারশিপ প্যাকেজসমূহ"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    Text(
                        text = tr(
                            "Instant activation with Google Play Billing & Google Pay. Unlock Direct Phone Numbers, Guardian Contacts & Proposal Interests.",
                            "Google Play Billing ও Google Pay-এর মাধ্যমে তাৎক্ষণিক অ্যাক্টিভেশন। সরাসরি ফোন নম্বর, অভিভাবকের যোগাযোগ ও বিবাহের প্রস্তাব আনলক করুন।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Current Package: ", "বর্তমান প্যাকেজ: "),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${trOption(currentProfile.packageTier)} ${tr("Plan", "প্ল্যান")}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (currentProfile.packageTier == "Free") {
                                    "(${trNum(currentProfile.interestsRemaining)}/${trNum(2)} ${tr("Interests", "আগ্রহ")} • ${trNum(currentProfile.contactsRemaining)} ${tr("Contacts", "কন্টাক্ট")})"
                                } else {
                                    "(${if (currentProfile.interestsRemaining >= 999) tr("Unlimited", "আনলিমিটেড") else trNum(currentProfile.interestsRemaining)} ${tr("Interests", "আগ্রহ")} • ${if (currentProfile.contactsRemaining >= 999) tr("Unlimited", "আনলিমিটেড") else trNum(currentProfile.contactsRemaining)} ${tr("Contacts", "কন্টাক্ট")})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!currentProfile.packageTier.equals("Free", ignoreCase = true)) {
                        val remainingDays = currentProfile.getRemainingPackageDays()
                        val expiryDateStr = currentProfile.getFormattedPackageExpiryDate()
                        if (expiryDateStr.isNotBlank() && remainingDays > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${tr("Expires on:", "মেয়াদ শেষ:")} ${trNum(expiryDateStr)} • ${trNum(remainingDays)} ${tr("days remaining", "দিন বাকি")}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("active_package_expiry_label")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (currentProfile.hasAdminAccess()) Arrangement.SpaceBetween else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                billingManager.restorePurchases { restoredPkg ->
                                    billingStatusBanner = if (restoredPkg != null) {
                                        if (isBangla) "Google Play-এ ${restoredPkg.nameBn} পাওয়া গেছে, যাচাই চলছে..."
                                        else "Found ${restoredPkg.nameEn} in Google Play. Verifying it..."
                                    } else {
                                        if (isBangla) "কোনো সক্রিয় Google Play সাবস্ক্রিপশন পাওয়া যায়নি।"
                                        else "No active Google Play purchases found to restore."
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("restore_google_play_purchases_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tr("Restore Play Purchases", "পার্চেজ রিস্টোর করুন"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        if (currentProfile.hasAdminAccess()) {
                            FilledTonalButton(
                                onClick = { showAdminPackageModal = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("open_admin_package_control_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tr("Admin Package Control", "অ্যাডমিন প্যাকেজ পরিবর্তন"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }

                    if (billingStatusBanner != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = billingStatusBanner!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (currentProfile.pendingPackageTier.isNotBlank() &&
                        !currentProfile.pendingPackageTier.equals(currentProfile.packageTier, ignoreCase = true)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("pending_package_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr(
                                        "Upgrade to ${currentProfile.pendingPackageTier} Plan is pending Admin verification",
                                        "${trOption(currentProfile.pendingPackageTier)} প্ল্যানে আপগ্রেড অ্যাডমিন অনুমোদনের অপেক্ষায় রয়েছে"
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        items(MembershipPackages.ALL) { pkg ->
            val formattedPrice = remember(pkg, productDetailsMap) {
                billingManager.getFormattedPlayPrice(pkg)
            }
            PackageCard(
                pkg = pkg,
                currentTier = currentProfile.packageTier,
                isCurrent = currentProfile.packageTier.equals(pkg.tier, ignoreCase = true),
                isPending = currentProfile.pendingPackageTier.equals(pkg.tier, ignoreCase = true) &&
                    !currentProfile.packageTier.equals(pkg.tier, ignoreCase = true),
                remainingDays = if (currentProfile.packageTier.equals(pkg.tier, ignoreCase = true)) currentProfile.getRemainingPackageDays() else 0,
                expiryDateText = if (currentProfile.packageTier.equals(pkg.tier, ignoreCase = true)) currentProfile.getFormattedPackageExpiryDate() else "",
                googlePlayFormattedPrice = formattedPrice,
                onBuy = {
                    checkoutErrorMessage = null
                    selectedPackageForConfirmation = pkg
                }
            )
        }
    }

    // Google Play Billing / Google Pay Checkout Dialog
    if (selectedPackageForConfirmation != null) {
        val pkg = selectedPackageForConfirmation!!
        val pkgDisplayName = if (isBangla) pkg.nameBn else pkg.nameEn
        val playFormattedPrice = billingManager.getFormattedPlayPrice(pkg)
        val isDowngradeOrSwitchToFree = pkg.priceBdt == 0

        val googlePayMethods = listOf(
            "Google Pay (GPay)",
            "Google Play Balance",
            "Visa / Mastercard (Google Play)"
        )
        var selectedGooglePayInstrument by remember(pkg) { mutableStateOf(googlePayMethods.first()) }

        AlertDialog(
            onDismissRequest = {
                selectedPackageForConfirmation = null
            },
            icon = {
                Icon(
                    imageVector = if (isDowngradeOrSwitchToFree) Icons.Default.SwapVert else Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = if (isDowngradeOrSwitchToFree) {
                        tr("Switch to Free Package", "ফ্রি প্যাকেজে পরিবর্তন করুন")
                    } else {
                        tr("Google Play & Google Pay Checkout", "Google Play ও Google Pay চেকআউট")
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Package Summary Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pkgDisplayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = playFormattedPrice ?: if (pkg.priceBdt == 0) {
                                        tr("Free", "ফ্রি")
                                    } else {
                                        "৳${trNum(pkg.priceBdt)}"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tr(
                                    "Duration: ${pkg.durationText} • Play SKU: ${pkg.productId}",
                                    "মেয়াদ: ${trOption(pkg.durationText)} • Play SKU: ${pkg.productId}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (pkg.tier == "Free") {
                                    tr(
                                        "• ${pkg.interestsCount} Free Marriage Proposal Interests every week\n• Basic Biodata Browsing",
                                        "• প্রতি সপ্তাহে ${trNum(pkg.interestsCount)}টি ফ্রি বিবাহের প্রস্তাব\n• বেসিক বায়োডাটা ব্রাউজিং"
                                    )
                                } else {
                                    tr(
                                        "• ${if (pkg.interestsCount >= 999) "Unlimited" else pkg.interestsCount} Proposal Interests\n• ${if (pkg.contactsCount >= 999) "Unlimited" else pkg.contactsCount} Direct Guardian & Candidate Contact Unlocks",
                                        "• ${if (pkg.interestsCount >= 999) "আনলিমিটেড" else trNum(pkg.interestsCount)}টি বিবাহের প্রস্তাব\n• ${if (pkg.contactsCount >= 999) "আনলিমিটেড" else trNum(pkg.contactsCount)}টি অভিভাবক ও পাত্র/পাত্রীর সরাসরি কন্টাক্ট আনলক"
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!isDowngradeOrSwitchToFree) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = tr(
                                "Select Google Play Payment Method:",
                                "Google Play পেমেন্ট মাধ্যম নির্বাচন করুন:"
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        googlePayMethods.forEach { instrument ->
                            val isSelected = selectedGooglePayInstrument == instrument
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedGooglePayInstrument = instrument }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedGooglePayInstrument = instrument }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = instrument,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = tr(
                                                "Instant activation via Google Play Billing",
                                                "Google Play Billing-এর মাধ্যমে তাৎক্ষণিক অ্যাক্টিভেশন"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr(
                                        "Secured by Google Play Billing. Your membership package & contact quotas unlock only after Google Play verifies your payment.",
                                        "Google Play Billing দ্বারা সুরক্ষিত। Google Play পেমেন্ট যাচাই সম্পন্ন হওয়ার পরেই আপনার প্যাকেজ ও কন্টাক্ট কোটা সক্রিয় হবে।"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (checkoutErrorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = checkoutErrorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        checkoutErrorMessage = null
                        if (isDowngradeOrSwitchToFree) {
                            onSelectPackage(pkg)
                            selectedPackageForConfirmation = null
                        } else {
                            billingManager.launchPackagePurchase(
                                activity = activity,
                                pkg = pkg
                            )
                        }
                    },
                    modifier = Modifier.testTag("confirm_package_activation_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        if (isDowngradeOrSwitchToFree) {
                            tr("Switch to Free Plan", "ফ্রি প্ল্যানে পরিবর্তন করুন")
                        } else {
                            tr("Pay with Google Play / GPay", "Google Play / GPay দিয়ে পেমেন্ট করুন")
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        checkoutErrorMessage = null
                        selectedPackageForConfirmation = null
                    }
                ) {
                    Text(tr("Cancel", "বাতিল"))
                }
            }
        )
    }

    // Admin Manual Package Control (Upgrade / Downgrade) Modal
    if (showAdminPackageModal) {
        AdminManualPackageDialog(
            currentProfile = currentProfile,
            allCandidates = allCandidates,
            onDismiss = { showAdminPackageModal = false },
            onApplyPackageChange = { targetUserId, targetProfileId, newTier, customInterests, customContacts, customDurationDays, adminNote ->
                onAdminChangeUserPackage(
                    targetUserId,
                    targetProfileId,
                    newTier,
                    customInterests,
                    customContacts,
                    customDurationDays,
                    adminNote
                )
                showAdminPackageModal = false
            }
        )
    }
}

@Composable
private fun AdminManualPackageDialog(
    currentProfile: UserProfile,
    allCandidates: List<UserProfile>,
    onDismiss: () -> Unit,
    onApplyPackageChange: (
        targetUserId: String,
        targetProfileId: String,
        newTier: String,
        customInterests: Int?,
        customContacts: Int?,
        customDurationDays: Int?,
        adminNote: String
    ) -> Unit
) {
    val allMembers = remember(currentProfile, allCandidates) {
        val list = mutableListOf<UserProfile>()
        if (currentProfile.userId.isNotBlank()) {
            list.add(currentProfile)
        }
        allCandidates.forEach { candidate ->
            if (candidate.userId.isNotBlank() && list.none { it.userId == candidate.userId }) {
                list.add(candidate)
            }
        }
        list
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf(currentProfile) }
    var manualUserIdOrProfileId by remember {
        mutableStateOf(currentProfile.getFormattedUserId().ifBlank { currentProfile.userId })
    }
    var selectedTier by remember { mutableStateOf(currentProfile.packageTier.ifBlank { "Gold" }) }
    var customInterestsText by remember {
        val defaultPkg = MembershipPackages.byTier(selectedTier)
        mutableStateOf((defaultPkg?.interestsCount ?: currentProfile.interestsRemaining).toString())
    }
    var customContactsText by remember {
        val defaultPkg = MembershipPackages.byTier(selectedTier)
        mutableStateOf((defaultPkg?.contactsCount ?: currentProfile.contactsRemaining).toString())
    }
    var customDurationDaysText by remember {
        val defaultPkg = MembershipPackages.byTier(selectedTier)
        mutableStateOf(defaultPkg.durationDays.toString())
    }
    var adminNote by remember { mutableStateOf("Manual package update from Admin Panel") }

    val filteredMembers = remember(allMembers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allMembers.take(8)
        } else {
            allMembers.filter {
                it.getFormattedUserId().lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q) ||
                    it.email.lowercase().contains(q) ||
                    it.userId.lowercase().contains(q)
            }.take(10)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr(
                    "Admin Manual Package Change (Upgrade / Downgrade)",
                    "অ্যাডমিন ম্যানুয়াল প্যাকেজ পরিবর্তন (আপগ্রেড / ডাউনগ্রেড)"
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = tr(
                        "Select any member or enter their Profile ID (e.g. G600001 / B600001) to manually upgrade or downgrade their package in Supabase.",
                        "যেকোনো সদস্য নির্বাচন করুন অথবা তার প্রোফাইল আইডি (যেমন G600001 / B600001) দিয়ে ম্যানুয়ালি প্যাকেজ আপগ্রেড বা ডাউনগ্রেড করুন।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(tr("Search Member (Name / G600001 / Email)", "সদস্য খুঁজুন (নাম / আইডি / ইমেইল)")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_package_search_input")
                )

                if (filteredMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            filteredMembers.take(5).forEach { member ->
                                val isPicked = selectedUser.userId == member.userId
                                val memberCode = member.getFormattedUserId().ifBlank { member.userId.take(8) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isPicked) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                        )
                                        .clickable {
                                            selectedUser = member
                                            manualUserIdOrProfileId = member.getFormattedUserId().ifBlank { member.userId }
                                            selectedTier = member.packageTier.ifBlank { "Free" }
                                            val pkg = MembershipPackages.byTier(selectedTier)
                                            customInterestsText = (pkg?.interestsCount ?: member.interestsRemaining).toString()
                                            customContactsText = (pkg?.contactsCount ?: member.contactsRemaining).toString()
                                            customDurationDaysText = pkg.durationDays.toString()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${member.displayName.ifBlank { "Member" }} ($memberCode)",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Current: ${member.packageTier} • ${member.email}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isPicked) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = manualUserIdOrProfileId,
                    onValueChange = { manualUserIdOrProfileId = it },
                    label = { Text(tr("Target Profile ID or User UID *", "টার্গেট প্রোফাইল আইডি বা ইউজার আইডি *")) },
                    placeholder = { Text("e.g. G600001 or B600001") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_target_profile_id_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = tr("Select Target Package (Upgrade / Downgrade):", "নতুন প্যাকেজ নির্বাচন করুন (আপগ্রেড / ডাউনগ্রেড):"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MembershipPackages.ALL.forEach { pkg ->
                        val selected = selectedTier.equals(pkg.tier, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTier = pkg.tier
                                customInterestsText = pkg.interestsCount.toString()
                                customContactsText = pkg.contactsCount.toString()
                                customDurationDaysText = pkg.durationDays.toString()
                            },
                            label = {
                                Text(
                                    text = pkg.tier,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier.testTag("admin_select_tier_${pkg.tier.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val interestsLabel = tr("Interests Quota", "প্রস্তাব কোটা")
                val contactsLabel = tr("Contacts Quota", "কন্টাক্ট কোটা")
                val durationLabel = tr("Validity (Days)", "মেয়াদ (দিন)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customInterestsText,
                        onValueChange = { newVal -> customInterestsText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text(interestsLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customContactsText,
                        onValueChange = { newVal -> customContactsText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text(contactsLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customDurationDaysText,
                        onValueChange = { newVal -> customDurationDaysText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text(durationLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = adminNote,
                    onValueChange = { adminNote = it },
                    label = { Text(tr("Admin Note", "অ্যাডমিন নোট")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedInput = manualUserIdOrProfileId.trim()
                    val matchedMember = allMembers.find {
                        it.getFormattedUserId().equals(trimmedInput, ignoreCase = true) ||
                            it.userId.equals(trimmedInput, ignoreCase = true)
                    }
                    val resolvedUserId = matchedMember?.userId ?: if (
                        trimmedInput.equals(currentProfile.getFormattedUserId(), ignoreCase = true) ||
                        trimmedInput.isBlank()
                    ) {
                        currentProfile.userId
                    } else {
                        trimmedInput
                    }
                    val resolvedProfileId = matchedMember?.getFormattedUserId() ?: if (
                        trimmedInput.startsWith("B", ignoreCase = true) ||
                        trimmedInput.startsWith("G", ignoreCase = true)
                    ) {
                        trimmedInput.uppercase()
                    } else {
                        selectedUser.getFormattedUserId()
                    }

                    onApplyPackageChange(
                        resolvedUserId,
                        resolvedProfileId,
                        selectedTier,
                        customInterestsText.toIntOrNull(),
                        customContactsText.toIntOrNull(),
                        customDurationDaysText.toIntOrNull(),
                        adminNote.trim()
                    )
                },
                modifier = Modifier.testTag("admin_apply_package_change_button")
            ) {
                Text(tr("Apply Package Change", "প্যাকেজ পরিবর্তন কার্যকর করুন"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "বাতিল"))
            }
        }
    )
}

@Composable
private fun PackageCard(
    pkg: MembershipPackage,
    currentTier: String,
    isCurrent: Boolean,
    isPending: Boolean,
    remainingDays: Int = 0,
    expiryDateText: String = "",
    googlePlayFormattedPrice: String?,
    onBuy: () -> Unit
) {
    val tierOrder = mapOf("Free" to 0, "Silver" to 1, "Gold" to 2, "Diamond" to 3)
    val currentRank = tierOrder[MembershipPackages.normalizeTierName(currentTier)] ?: 0
    val pkgRank = tierOrder[pkg.tier] ?: 0
    val isDowngrade = pkgRank < currentRank

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pkg.isPopular) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = if (pkg.isPopular) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (pkg.isPopular) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val isBangla = LocalLanguageController.current.isBangla
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isBangla) pkg.nameBn else pkg.nameEn,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${if (isBangla) pkg.nameEn else pkg.nameBn})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = trOption(pkg.durationText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pkg.isPopular) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ) {
                        Text(tr("MOST POPULAR", "সবচেয়ে জনপ্রিয়"), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price Tag
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = googlePlayFormattedPrice ?: if (pkg.priceBdt == 0) tr("Free", "ফ্রি") else "৳${trNum(pkg.priceBdt)}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                if (pkg.priceBdt > 0) {
                    Text(
                        text = " ${if (googlePlayFormattedPrice == null) tr("BDT", "টাকা") else ""} / ${trOption(pkg.durationText)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            pkg.perks.forEach { perk ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trOption(perk),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isCurrent -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val activeLabel = if (pkg.tier != "Free" && remainingDays > 0) {
                            "${tr("Current Active Plan", "বর্তমান চালু প্ল্যান")} • ${trNum(remainingDays)} ${tr("days left", "দিন বাকি")}"
                        } else {
                            tr("Current Active Plan", "বর্তমান চালু প্ল্যান")
                        }
                        Text(activeLabel)
                    }
                }
                isPending -> {
                    OutlinedButton(
                        onClick = onBuy,
                        modifier = Modifier.fillMaxWidth().testTag("buy_package_${pkg.tier.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("Pending Activation (Complete with Google Pay)", "পেন্ডিং (Google Pay দিয়ে তাৎক্ষণিক চালু করুন)"))
                    }
                }
                else -> {
                    Button(
                        onClick = onBuy,
                        modifier = Modifier.fillMaxWidth().testTag("buy_package_${pkg.tier.lowercase()}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pkg.isPopular) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = if (pkg.priceBdt == 0 || isDowngrade) Icons.Default.SwapVert else Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                pkg.priceBdt == 0 -> tr("Switch to Free Plan", "ফ্রি প্ল্যানে পরিবর্তন করুন")
                                isDowngrade -> tr("Downgrade to ${pkg.nameEn} (Google Play)", "${pkg.nameBn}-এ পরিবর্তন করুন (Google Play)")
                                else -> tr("Buy ${pkg.nameEn} with Google Pay", "Google Pay দিয়ে ${pkg.nameBn} কিনুন")
                            }
                        )
                    }
                }
            }
        }
    }
}
