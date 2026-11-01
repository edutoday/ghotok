package com.example.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.LocalLanguageController
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.tr
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class MeMenuDialogType {
    NONE,
    CHANGE_EMAIL,
    CHANGE_PASSWORD,
    HIDE_ACCOUNT,
    DELETE_ACCOUNT,
    PRIVACY_POLICY,
    USER_AGREEMENT,
    SUBSCRIPTION_AGREEMENT,
    COMMUNITY_GUIDELINE,
    CONTACT_US,
    CHECK_VERSION_UPDATE
}

@Composable
fun MeTopBarMenuButton(
    profile: UserProfile,
    isAccountHidden: Boolean,
    onToggleAccountHidden: (Boolean) -> Unit,
    onDeleteAccountConfirmed: () -> Unit,
    onEmailUpdated: (String) -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isBangla = LocalLanguageController.current.isBangla
    val themeController = LocalThemeController.current
    val securityPrefs = remember(context) {
        context.getSharedPreferences("ghotok_security_prefs", Context.MODE_PRIVATE)
    }
    val userKey = remember(profile.userId) {
        profile.userId.ifBlank { "default" }
    }
    val passwordKey = remember(userKey) {
        "account_password_$userKey"
    }
    val emailOverrideKey = remember(userKey) {
        "account_email_$userKey"
    }
    val unverifiedEmailKey = remember(userKey) {
        "unverified_email_$userKey"
    }
    var savedPassword by remember(passwordKey) {
        mutableStateOf(securityPrefs.getString(passwordKey, "") ?: "")
    }
    val emailVerifiedTick by SupabaseClient.emailVerifiedTick.collectAsState()
    var unverifiedEmail by remember(unverifiedEmailKey, emailVerifiedTick) {
        mutableStateOf(securityPrefs.getString(unverifiedEmailKey, "")?.trim().orEmpty())
    }
    var currentEmail by remember(emailOverrideKey, profile.email, emailVerifiedTick) {
        val pending = securityPrefs.getString(unverifiedEmailKey, "")?.trim().orEmpty()
        val stored = securityPrefs.getString(emailOverrideKey, "")?.trim().orEmpty()
            .takeIf { it.isNotBlank() && !it.equals(pending, ignoreCase = true) }
            .orEmpty()
        val resolved = stored.ifBlank {
            profile.email.trim()
                .takeIf { it.isNotBlank() && !it.equals(pending, ignoreCase = true) }
                ?: SupabaseClient.getSessionEmail(context).orEmpty().trim()
                    .takeIf { it.isNotBlank() && !it.equals(pending, ignoreCase = true) }
                ?: profile.email.trim()
        }
        mutableStateOf(resolved)
    }
    LaunchedEffect(emailVerifiedTick) {
        if (emailVerifiedTick > 0L) {
            unverifiedEmail = securityPrefs.getString(unverifiedEmailKey, "")?.trim().orEmpty()
            val stored = securityPrefs.getString(emailOverrideKey, "")?.trim().orEmpty()
            if (stored.isNotBlank() && unverifiedEmail.isBlank()) {
                currentEmail = stored
                onEmailUpdated(stored)
            }
        }
    }
    LaunchedEffect(unverifiedEmail) {
        if (unverifiedEmail.isNotBlank()) {
            val verifiedNow = SupabaseClient.checkIsEmailVerifiedRemotely(
                context = context,
                targetEmail = unverifiedEmail
            )
            if (verifiedNow) {
                val newlyVerified = unverifiedEmail
                unverifiedEmail = ""
                currentEmail = newlyVerified
                securityPrefs.edit()
                    .putString(emailOverrideKey, newlyVerified)
                    .remove(unverifiedEmailKey)
                    .apply()
                onEmailUpdated(newlyVerified)
            }
        }
    }
    val isEmailVerified = currentEmail.isNotBlank() && unverifiedEmail.isBlank()

    var menuExpanded by remember { mutableStateOf(false) }
    var accountSecurityExpanded by remember { mutableStateOf(false) }
    var aboutGhotokExpanded by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf(MeMenuDialogType.NONE) }

    Box {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("me_top_bar_menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = tr("Menu", "মেনু"),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .width(290.dp)
                .testTag("me_top_bar_dropdown_menu")
        ) {
            // 0. Dark Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { themeController.onToggleTheme(!themeController.isDarkTheme) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("me_menu_dark_mode"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (themeController.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Dark Mode", "ডার্ক মোড"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (themeController.isDarkTheme) tr("Enabled", "চালু আছে") else tr("Disabled", "বন্ধ আছে"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = themeController.isDarkTheme,
                    onCheckedChange = { isDark -> themeController.onToggleTheme(isDark) },
                    modifier = Modifier
                        .scale(0.82f)
                        .testTag("me_menu_dark_mode_switch")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 1. Account Security (Expandable)
            MeMenuMainItem(
                icon = Icons.Default.Security,
                title = tr("Account Security", "অ্যাকাউন্ট নিরাপত্তা"),
                isExpandable = true,
                isExpanded = accountSecurityExpanded,
                testTag = "me_menu_account_security",
                onClick = { accountSecurityExpanded = !accountSecurityExpanded }
            )

            AnimatedVisibility(visible = accountSecurityExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    MeMenuSubItem(
                        icon = Icons.Default.Email,
                        title = tr("Change Email", "ইমেইল পরিবর্তন করুন"),
                        badgeText = if (currentEmail.isNotBlank() && !isEmailVerified) {
                            tr("Unverified", "অযাচাইকৃত")
                        } else {
                            null
                        },
                        testTag = "me_menu_change_email",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.CHANGE_EMAIL
                        }
                    )
                    MeMenuSubItem(
                        icon = Icons.Default.LockReset,
                        title = tr("Change Password", "পাসওয়ার্ড পরিবর্তন করুন"),
                        testTag = "me_menu_change_password",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.CHANGE_PASSWORD
                        }
                    )
                    MeMenuSubItem(
                        icon = if (isAccountHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        title = tr("Hide Account", "অ্যাকাউন্ট লুকান"),
                        badgeText = if (isAccountHidden) tr("Hidden", "লুকানো") else null,
                        testTag = "me_menu_hide_account",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.HIDE_ACCOUNT
                        }
                    )
                    MeMenuSubItem(
                        icon = Icons.Default.DeleteForever,
                        title = tr("Delete Account", "অ্যাকাউন্ট মুছে ফেলুন"),
                        isDestructive = true,
                        testTag = "me_menu_delete_account",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.DELETE_ACCOUNT
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 2. Invite friends
            MeMenuMainItem(
                icon = Icons.Default.PersonAdd,
                title = tr("Invite friends", "বন্ধুদের আমন্ত্রণ জানান"),
                testTag = "me_menu_invite_friends",
                onClick = {
                    menuExpanded = false
                    shareText(
                        context = context,
                        subject = if (isBangla) "ঘটক - বাংলাদেশী ম্যাট্রিমনি অ্যাপে যোগ দিন" else "Join Ghotok - Bangladeshi Matrimony",
                        body = if (isBangla) {
                            "আসসালামু আলাইকুম! বিশ্বস্ত বাংলাদেশী ম্যাট্রিমনি অ্যাপ 'ঘটক' (Ghotok)-এ আপনার বা আপনার পরিবারের জন্য উপযুক্ত জীবনসঙ্গী খুঁজুন। আমার ঘটক আইডি: ${profile.getFormattedUserId()}"
                        } else {
                            "Assalamu Alaikum! Find your ideal life partner on Ghotok — trusted Bangladeshi Matrimony app. Connect with my Ghotok ID: ${profile.getFormattedUserId()}"
                        },
                        chooserTitle = if (isBangla) "আমন্ত্রণ জানান" else "Invite Friends via"
                    )
                }
            )

            // 3. Share your id
            MeMenuMainItem(
                icon = Icons.Default.Share,
                title = tr("Share your id", "আপনার আইডি শেয়ার করুন"),
                subtitle = profile.getFormattedUserId(),
                testTag = "me_menu_share_id",
                onClick = {
                    menuExpanded = false
                    val formattedId = profile.getFormattedUserId()
                    copyToClipboard(context, "Ghotok ID", formattedId)
                    onShowMessage(
                        if (isBangla) "আপনার ঘটক আইডি ($formattedId) কপি করা হয়েছে"
                        else "Your Ghotok ID ($formattedId) has been copied"
                    )
                    shareText(
                        context = context,
                        subject = if (isBangla) "আমার ঘটক ম্যাট্রিমনি আইডি" else "My Ghotok Matrimony ID",
                        body = if (isBangla) {
                            "ঘটক (Ghotok) ম্যাট্রিমনিতে আমার বায়োডাটা দেখুন।\nনাম: ${profile.displayName}\nঘটক আইডি: $formattedId"
                        } else {
                            "View my biodata profile on Ghotok Matrimony.\nName: ${profile.displayName}\nGhotok ID: $formattedId"
                        },
                        chooserTitle = if (isBangla) "আইডি শেয়ার করুন" else "Share Ghotok ID"
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 4. About Ghotok (Expandable)
            MeMenuMainItem(
                icon = Icons.Default.Info,
                title = tr("About Ghotok", "ঘটক সম্পর্কে"),
                isExpandable = true,
                isExpanded = aboutGhotokExpanded,
                testTag = "me_menu_about_ghotok",
                onClick = { aboutGhotokExpanded = !aboutGhotokExpanded }
            )

            AnimatedVisibility(visible = aboutGhotokExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    MeMenuSubItem(
                        icon = Icons.Default.Policy,
                        title = tr("Privacy policy", "গোপনীয়তা নীতি"),
                        testTag = "me_menu_privacy_policy",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.PRIVACY_POLICY
                        }
                    )
                    MeMenuSubItem(
                        icon = Icons.Default.Gavel,
                        title = tr("User Agreement", "ব্যবহারকারীর চুক্তি"),
                        testTag = "me_menu_user_agreement",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.USER_AGREEMENT
                        }
                    )
                    MeMenuSubItem(
                        icon = Icons.Default.WorkspacePremium,
                        title = tr("Subscription agreement", "সাবস্ক্রিপশন চুক্তি"),
                        testTag = "me_menu_subscription_agreement",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.SUBSCRIPTION_AGREEMENT
                        }
                    )
                    MeMenuSubItem(
                        icon = Icons.Default.Groups,
                        title = tr("Community guideline", "কমিউনিটি গাইডলাইন"),
                        testTag = "me_menu_community_guideline",
                        onClick = {
                            menuExpanded = false
                            activeDialog = MeMenuDialogType.COMMUNITY_GUIDELINE
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 5. Contact us
            MeMenuMainItem(
                icon = Icons.Default.SupportAgent,
                title = tr("Contact us", "যোগাযোগ করুন"),
                testTag = "me_menu_contact_us",
                onClick = {
                    menuExpanded = false
                    activeDialog = MeMenuDialogType.CONTACT_US
                }
            )

            // 6. Check version Update
            MeMenuMainItem(
                icon = Icons.Default.SystemUpdate,
                title = tr("Check version Update", "ভার্সন আপডেট চেক করুন"),
                subtitle = "v1.2.0",
                testTag = "me_menu_check_version",
                onClick = {
                    menuExpanded = false
                    activeDialog = MeMenuDialogType.CHECK_VERSION_UPDATE
                }
            )
        }
    }

    // Dialogs for menu actions
    when (activeDialog) {
        MeMenuDialogType.CHANGE_EMAIL -> {
            ChangeEmailDialog(
                userId = profile.userId,
                currentEmail = currentEmail,
                pendingNewEmail = unverifiedEmail,
                isVerified = isEmailVerified,
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onEmailChangeSubmitted = { pendingEmail, msg ->
                    unverifiedEmail = pendingEmail
                    securityPrefs.edit()
                        .putString(unverifiedEmailKey, pendingEmail)
                        .apply()
                    activeDialog = MeMenuDialogType.NONE
                    onShowMessage(msg)
                },
                onEmailVerifiedConfirmed = { verifiedEmail, msg ->
                    unverifiedEmail = ""
                    currentEmail = verifiedEmail
                    securityPrefs.edit()
                        .putString(emailOverrideKey, verifiedEmail)
                        .remove(unverifiedEmailKey)
                        .apply()
                    onEmailUpdated(verifiedEmail)
                    activeDialog = MeMenuDialogType.NONE
                    onShowMessage(msg)
                }
            )
        }

        MeMenuDialogType.CHANGE_PASSWORD -> {
            ChangePasswordDialog(
                existingPassword = savedPassword,
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onPasswordUpdated = { updatedPassword, msg ->
                    savedPassword = updatedPassword
                    securityPrefs.edit().putString(passwordKey, updatedPassword).apply()
                    activeDialog = MeMenuDialogType.NONE
                    onShowMessage(msg)
                }
            )
        }

        MeMenuDialogType.HIDE_ACCOUNT -> {
            HideAccountDialog(
                isCurrentlyHidden = isAccountHidden,
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onConfirmToggle = { newHiddenState ->
                    onToggleAccountHidden(newHiddenState)
                    activeDialog = MeMenuDialogType.NONE
                    onShowMessage(
                        if (newHiddenState) {
                            if (isBangla) "আপনার অ্যাকাউন্ট অনুসন্ধান তালিকা থেকে লুকানো হয়েছে।"
                            else "Your account is now hidden from public search."
                        } else {
                            if (isBangla) "আপনার অ্যাকাউন্ট পুনরায় দৃশ্যমান করা হয়েছে।"
                            else "Your account is now visible in public search."
                        }
                    )
                }
            )
        }

        MeMenuDialogType.DELETE_ACCOUNT -> {
            DeleteAccountDialog(
                userId = profile.userId,
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onConfirmDelete = {
                    activeDialog = MeMenuDialogType.NONE
                    onDeleteAccountConfirmed()
                }
            )
        }

        MeMenuDialogType.PRIVACY_POLICY -> {
            PolicyInfoDialog(
                title = tr("Privacy Policy", "গোপনীয়তা নীতি"),
                icon = Icons.Default.Policy,
                content = tr(
                    """
                    1. Data Collection & Purpose
                    Ghotok collects your biodata details, partner preferences, and verification documents strictly to provide authentic Bangladeshi matrimonial matchmaking.

                    2. Verification Documents
                    Uploaded National ID (NID), Passport, or Birth Registration images are used exclusively to verify identity and grant the Verified Badge. They are never shared publicly with other members.

                    3. Contact & Photo Privacy
                    Your phone number and guardian contact details remain protected and are only accessible to verified or connected matches according to your privacy settings.

                    4. Account Control
                    You may hide your biodata temporarily or permanently delete your account and associated personal data at any time from Account Security.
                    """.trimIndent(),
                    """
                    ১. তথ্য সংগ্রহ ও উদ্দেশ্য
                    ঘটক শুধুমাত্র বিশ্বস্ত বাংলাদেশী ম্যাট্রিমনি ম্যাচমেকিং সেবা প্রদানের জন্য আপনার বায়োডাটা তথ্য, জীবনসঙ্গীর পছন্দ এবং ভেরিফিকেশন ডকুমেন্ট সংগ্রহ করে।

                    ২. ভেরিফিকেশন ডকুমেন্টের গোপনীয়তা
                    আপনার আপলোড করা জাতীয় পরিচয়পত্র (NID), পাসপোর্ট বা জন্ম সনদের ছবি শুধুমাত্র পরিচয় যাচাই ও ভেরিফাইড ব্যাজ প্রদানের জন্য ব্যবহৃত হয়। এটি কখনোই অন্য ব্যবহারকারীদের কাছে প্রকাশ করা হয় না।

                    ৩. যোগাযোগ ও ছবির সুরক্ষা
                    আপনার ফোন নম্বর এবং অভিভাবকের যোগাযোগের তথ্য সুরক্ষিত থাকে।

                    ৪. অ্যাকাউন্ট নিয়ন্ত্রণ
                    আপনি যেকোনো সময় 'অ্যাকাউন্ট নিরাপত্তা' থেকে আপনার প্রোফাইল লুকিয়ে রাখতে বা স্থায়ীভাবে মুছে ফেলতে পারেন।
                    """.trimIndent()
                ),
                onDismiss = { activeDialog = MeMenuDialogType.NONE }
            )
        }

        MeMenuDialogType.USER_AGREEMENT -> {
            PolicyInfoDialog(
                title = tr("User Agreement", "ব্যবহারকারীর চুক্তি"),
                icon = Icons.Default.Gavel,
                content = tr(
                    """
                    1. Eligibility
                    By creating a biodata on Ghotok, you confirm that you (or the candidate you represent as a parent/guardian) meet the legal marriage age requirements under Bangladesh law (minimum 21 years for males and 18 years for females).

                    2. Accuracy of Information
                    All information provided in your biodata—including marital status, education, profession, and family background—must be truthful and accurate.

                    3. Respectful Conduct
                    Ghotok is strictly a matrimonial platform for marriage proposals. Any misuse for casual dating, harassment, or fraudulent solicitation will result in immediate account termination.

                    4. Family & Guardian Involvement
                    We strongly encourage involving parents and family guardians early in the matrimonial discussion process.
                    """.trimIndent(),
                    """
                    ১. যোগ্যতা
                    ঘটক-এ বায়োডাটা তৈরির মাধ্যমে আপনি নিশ্চিত করছেন যে পাত্র বা পাত্রী বাংলাদেশের আইন অনুযায়ী বিবাহের ন্যূনতম বয়সসীমা (পাত্রের ক্ষেত্রে ২১ বছর এবং পাত্রীর ক্ষেত্রে ১৮ বছর) পূরণ করেছেন।

                    ২. তথ্যের সত্যতা
                    আপনার বায়োডাটায় প্রদত্ত সকল তথ্য—যেমন বৈবাহিক অবস্থা, শিক্ষাগত যোগ্যতা, পেশা এবং পারিবারিক তথ্য—অবশ্যই সত্য ও সঠিক হতে হবে।

                    ৩. শালীন আচরণ
                    ঘটক শুধুমাত্র বিবাহের প্রস্তাব ও পাত্র-পাত্রী খোঁজার প্ল্যাটফর্ম। যেকোনো ধরনের প্রতারণা বা অশালীন আচরণের ক্ষেত্রে অ্যাকাউন্ট স্থায়ীভাবে বন্ধ করে দেওয়া হবে।

                    ৪. অভিভাবকের সম্পৃক্ততা
                    বিবাহের আলোচনায় শুরু থেকেই পিতা-মাতা বা অভিভাবককে সম্পৃক্ত করার জন্য আমরা উৎসাহিত করি।
                    """.trimIndent()
                ),
                onDismiss = { activeDialog = MeMenuDialogType.NONE }
            )
        }

        MeMenuDialogType.SUBSCRIPTION_AGREEMENT -> {
            PolicyInfoDialog(
                title = tr("Subscription Agreement", "সাবস্ক্রিপশন চুক্তি"),
                icon = Icons.Default.WorkspacePremium,
                content = tr(
                    """
                    1. Membership Tiers
                    Ghotok offers Free, Silver, Gold, and Platinum (VIP) membership packages. Each tier grants a specific number of direct contact views ("Connections") and premium features.

                    2. Payment Methods
                    Subscriptions can be purchased securely via bKash, Nagad, Rocket, or Visa/Mastercard. Membership privileges activate immediately upon verification of payment.

                    3. Connection Credits
                    A connection credit is only deducted when you unlock a new candidate's guardian/direct contact information for the first time. Viewing the same contact again does not consume additional credits.

                    4. Refund & Cancellation Policy
                    Unused membership packages may request a review within 7 days of purchase if no connection credits have been consumed.
                    """.trimIndent(),
                    """
                    ১. মেম্বারশিপ প্যাকেজসমূহ
                    ঘটক-এ ফ্রি, সিলভার, গোল্ড এবং প্ল্যাটিনাম (VIP) মেম্বারশিপ প্যাকেজ রয়েছে। প্রতিটি প্যাকেজে নির্দিষ্ট সংখ্যক যোগাযোগের তথ্য দেখার (কানেকশন) সুবিধা থাকে।

                    ২. পেমেন্ট পদ্ধতি
                    বিকাশ (bKash), নগদ (Nagad), রকেট (Rocket) অথবা কার্ডের মাধ্যমে নিরাপদে পেমেন্ট সম্পন্ন করার সাথে সাথেই প্যাকেজ সক্রিয় হয়।

                    ৩. কানেকশন ক্রেডিট ব্যবহারের নিয়ম
                    নতুন কোনো বায়োডাটার অভিভাবক বা সরাসরি যোগাযোগের নম্বর প্রথমবার আনলক করলেই কেবল ১টি কানেকশন ব্যবহৃত হবে। একই বায়োডাটা পুনরায় দেখতে অতিরিক্ত কানেকশন কাটবে না।

                    ৪. রিফান্ড নীতি
                    কোনো কানেকশন ব্যবহার না করে থাকলে ক্রয়ের ৭ দিনের মধ্যে সাপোর্ট টিমের সাথে যোগাযোগ করে রিফান্ডের আবেদন করা যাবে।
                    """.trimIndent()
                ),
                onDismiss = { activeDialog = MeMenuDialogType.NONE }
            )
        }

        MeMenuDialogType.COMMUNITY_GUIDELINE -> {
            PolicyInfoDialog(
                title = tr("Community Guideline", "কমিউনিটি গাইডলাইন"),
                icon = Icons.Default.Groups,
                content = tr(
                    """
                    • Maintain modesty, honesty, and mutual respect in all proposals and messages.
                    • Upload clear, dignified photos or use guardian-approved profile settings.
                    • Never ask for money, financial transfers, or sensitive banking details from other members.
                    • Respect decisions when a proposal is declined; do not repeatedly contact uninterested members.
                    • Report any suspicious or misleading profiles immediately to the Ghotok moderation team.
                    """.trimIndent(),
                    """
                    • সকল প্রস্তাব এবং বার্তায় শালীনতা, সততা এবং পারস্পরিক সম্মান বজায় রাখুন।
                    • মার্জিত ও স্পষ্ট ছবি ব্যবহার করুন।
                    • কখনোই অন্য কোনো সদস্যের কাছে অর্থ লেনদেন বা ব্যাংকিং তথ্য চাইবেন না।
                    • কেউ প্রস্তাব প্রত্যাখ্যান করলে তার সিদ্ধান্তকে সম্মান করুন এবং পুনরায় বিরক্ত করা থেকে বিরত থাকুন।
                    • সন্দেহজনক বা অসত্য কোনো প্রোফাইল দেখলে সাথে সাথে ঘটক সাপোর্ট টিমকে জানান।
                    """.trimIndent()
                ),
                onDismiss = { activeDialog = MeMenuDialogType.NONE }
            )
        }

        MeMenuDialogType.CONTACT_US -> {
            ContactUsDialog(
                context = context,
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onCopied = { msg -> onShowMessage(msg) }
            )
        }

        MeMenuDialogType.CHECK_VERSION_UPDATE -> {
            CheckVersionUpdateDialog(
                onDismiss = { activeDialog = MeMenuDialogType.NONE },
                onShowMessage = { msg ->
                    activeDialog = MeMenuDialogType.NONE
                    onShowMessage(msg)
                }
            )
        }

        MeMenuDialogType.NONE -> Unit
    }
}

@Composable
private fun MeMenuMainItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isExpandable) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MeMenuSubItem(
    icon: ImageVector,
    title: String,
    badgeText: String? = null,
    isDestructive: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 36.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (!badgeText.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ChangeEmailDialog(
    userId: String,
    currentEmail: String,
    pendingNewEmail: String = "",
    isVerified: Boolean,
    onDismiss: () -> Unit,
    onEmailChangeSubmitted: (String, String) -> Unit,
    onEmailVerifiedConfirmed: (String, String) -> Unit
) {
    val context = LocalContext.current
    val isBangla = LocalLanguageController.current.isBangla
    var newEmail by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isCheckingVerification by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val targetVerifyEmail = pendingNewEmail.ifBlank { currentEmail }

    LaunchedEffect(pendingNewEmail) {
        if (pendingNewEmail.isNotBlank()) {
            val remoteVerified = SupabaseClient.checkIsEmailVerifiedRemotely(
                targetEmail = pendingNewEmail,
                context = context
            )
            if (remoteVerified) {
                onEmailVerifiedConfirmed(
                    pendingNewEmail,
                    if (isBangla) "আপনার নতুন ইমেইল ($pendingNewEmail) ভেরিফাইড এবং আপডেট হয়েছে!"
                    else "Your new email ($pendingNewEmail) is now verified and updated!"
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending && !isCheckingVerification) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr("Change Email", "ইমেইল পরিবর্তন করুন"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Current Email", "বর্তমান ইমেইল"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val badgeBg = if (isVerified) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                            val badgeTextColor = if (isVerified) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = badgeBg
                            ) {
                                Text(
                                    text = if (isVerified) {
                                        tr("Verified", "ভেরিফাইড")
                                    } else {
                                        tr("Unverified", "অযাচাইকৃত")
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = badgeTextColor,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .testTag("change_email_status_badge")
                                )
                            }
                        }
                        Text(
                            text = currentEmail.ifBlank { tr("No email set", "কোনো ইমেইল সেট করা নেই") },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("change_email_current_value")
                        )
                        if (pendingNewEmail.isNotBlank() && !pendingNewEmail.equals(currentEmail, ignoreCase = true)) {
                            Text(
                                text = tr(
                                    "Pending New Email: $pendingNewEmail",
                                    "অপেক্ষমান নতুন ইমেইল: $pendingNewEmail"
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!isVerified && targetVerifyEmail.isNotBlank()) {
                            Text(
                                text = tr(
                                    "A verification link was sent to $targetVerifyEmail. Your account email will only change after you click the verification link.",
                                    "$targetVerifyEmail-এ ভেরিফিকেশন লিংক পাঠানো হয়েছে। লিংকে ক্লিক করে ভেরিফাই করলেই কেবল আপনার অ্যাকাউন্ট ইমেইল পরিবর্তন হবে।"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = !isCheckingVerification && !isSending,
                                    onClick = {
                                        isSending = true
                                        errorMessage = null
                                        infoMessage = null
                                        CoroutineScope(Dispatchers.Main).launch {
                                            val res = SupabaseClient.requestEmailChangeAndVerificationLink(
                                                context = context,
                                                userId = userId,
                                                newEmail = targetVerifyEmail
                                            )
                                            isSending = false
                                            res.onSuccess {
                                                infoMessage = if (isBangla) {
                                                    "$targetVerifyEmail-এ পুনরায় ভেরিফিকেশন লিংক পাঠানো হয়েছে। আপনার ইনবক্স ও স্প্যাম ফোল্ডার চেক করুন।"
                                                } else {
                                                    "Verification link resent to $targetVerifyEmail. Please check your inbox and spam folder."
                                                }
                                            }.onFailure { err ->
                                                errorMessage = err.localizedMessage ?: if (isBangla) {
                                                    "ভেরিফিকেশন লিংক পাঠাতে সমস্যা হয়েছে। ৬০ সেকেন্ড পর আবার চেষ্টা করুন।"
                                                } else {
                                                    "Could not resend verification link. Please wait 60 seconds and try again."
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("resend_email_verification_button")
                                ) {
                                    Text(
                                        text = if (isSending) {
                                            tr("Sending...", "পাঠানো হচ্ছে...")
                                        } else {
                                            tr("Resend Link", "লিংক পুনরায় পাঠান")
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                TextButton(
                                    enabled = !isCheckingVerification && !isSending,
                                    onClick = {
                                        isCheckingVerification = true
                                        errorMessage = null
                                        infoMessage = null
                                        CoroutineScope(Dispatchers.Main).launch {
                                            val remoteVerified = SupabaseClient.checkIsEmailVerifiedRemotely(
                                                targetEmail = targetVerifyEmail,
                                                context = context
                                            )
                                            isCheckingVerification = false
                                            if (remoteVerified) {
                                                onEmailVerifiedConfirmed(
                                                    targetVerifyEmail,
                                                    if (isBangla) "আপনার নতুন ইমেইল ভেরিফাইড এবং আপডেট হয়েছে!"
                                                    else "Your new email is now verified and updated!"
                                                )
                                            } else {
                                                infoMessage = if (isBangla) {
                                                    "এখনও ভেরিফাই হয়নি। আপনার ইমেইল ইনবক্সে পাঠানো ভেরিফিকেশন লিংকে ক্লিক করুন।"
                                                } else {
                                                    "Not verified yet. Please click the verification link sent to your email inbox."
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (isCheckingVerification) {
                                            tr("Checking...", "চেক করা হচ্ছে...")
                                        } else {
                                            tr("Check Verification", "ভেরিফিকেশন চেক করুন")
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = tr(
                        "Enter your new email address below. A verification link will be sent to verify the new email; your account email will not change until verified.",
                        "নিচে আপনার নতুন ইমেইল ঠিকানা লিখুন। নতুন ইমেইল যাচাই করার জন্য একটি ভেরিফিকেশন লিংক পাঠানো হবে; ভেরিফাই না হওয়া পর্যন্ত আপনার বর্তমান ইমেইল পরিবর্তন হবে না।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newEmail,
                    onValueChange = {
                        newEmail = it
                        errorMessage = null
                        infoMessage = null
                    },
                    label = { Text(tr("New Email", "নতুন ইমেইল")) },
                    placeholder = { Text("example@email.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_email_new_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("change_email_error_text")
                    )
                }
                if (infoMessage != null) {
                    Text(
                        text = infoMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSending && !isCheckingVerification,
                onClick = {
                    val cleanNew = newEmail.trim().lowercase()
                    when {
                        cleanNew.isBlank() || !cleanNew.contains("@") || !cleanNew.contains(".") -> {
                            errorMessage = if (isBangla) {
                                "অনুগ্রহ করে একটি সঠিক নতুন ইমেইল ঠিকানা লিখুন।"
                            } else {
                                "Please enter a valid new email address."
                            }
                        }
                        cleanNew.equals(currentEmail.trim(), ignoreCase = true) -> {
                            errorMessage = if (isBangla) {
                                "নতুন ইমেইলটি বর্তমান ইমেইলের মতোই। অনুগ্রহ করে ভিন্ন ইমেইল দিন।"
                            } else {
                                "New email is the same as your current email."
                            }
                        }
                        else -> {
                            isSending = true
                            errorMessage = null
                            infoMessage = null
                            CoroutineScope(Dispatchers.Main).launch {
                                val res = SupabaseClient.requestEmailChangeAndVerificationLink(
                                    context = context,
                                    userId = userId,
                                    newEmail = cleanNew
                                )
                                isSending = false
                                res.onSuccess {
                                    onEmailChangeSubmitted(
                                        cleanNew,
                                        if (isBangla) {
                                            "$cleanNew-এ ভেরিফিকেশন লিংক পাঠানো হয়েছে। লিংকে ক্লিক করে ভেরিফাই করলেই আপনার ইমেইল পরিবর্তন হবে।"
                                        } else {
                                            "Verification link sent to $cleanNew. Your account email will update once you verify via the link."
                                        }
                                    )
                                }.onFailure { err ->
                                    errorMessage = err.localizedMessage ?: if (isBangla) {
                                        "ভেরিফিকেশন লিংক পাঠাতে ব্যর্থ হয়েছে। অনুগ্রহ করে ৬০ সেকেন্ড পর আবার চেষ্টা করুন।"
                                    } else {
                                        "Failed to send verification link. Please wait 60 seconds and try again."
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("change_email_submit_button")
            ) {
                Text(
                    if (isSending) {
                        tr("Sending Link...", "লিংক পাঠানো হচ্ছে...")
                    } else {
                        tr("Send Verification Link", "ভেরিফিকেশন লিংক পাঠান")
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending && !isCheckingVerification
            ) {
                Text(tr("Cancel", "বাতিল"))
            }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    existingPassword: String,
    onDismiss: () -> Unit,
    onPasswordUpdated: (String, String) -> Unit
) {
    val isBangla = LocalLanguageController.current.isBangla
    val hasExistingPassword = existingPassword.isNotEmpty()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (hasExistingPassword) {
                    tr("Change Password", "পাসওয়ার্ড পরিবর্তন করুন")
                } else {
                    tr("Create Password", "নতুন পাসওয়ার্ড তৈরি করুন")
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasExistingPassword) {
                        tr(
                            "Enter your current password and set a new password (minimum 6 characters).",
                            "আপনার বর্তমান পাসওয়ার্ড দিন এবং নতুন পাসওয়ার্ড (ন্যূনতম ৬ অক্ষর) সেট করুন।"
                        )
                    } else {
                        tr(
                            "No password was set previously. Create a new password (minimum 6 characters) to secure your Ghotok account.",
                            "পূর্বে কোনো পাসওয়ার্ড সেট করা হয়নি। আপনার ঘটক অ্যাকাউন্ট সুরক্ষিত রাখতে নতুন পাসওয়ার্ড (ন্যূনতম ৬ অক্ষর) তৈরি করুন।"
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasExistingPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            errorMessage = null
                        },
                        label = { Text(tr("Current Password", "বর্তমান পাসওয়ার্ড")) },
                        visualTransformation = if (showCurrentPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showCurrentPassword = !showCurrentPassword },
                                modifier = Modifier.testTag("toggle_current_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (showCurrentPassword) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (showCurrentPassword) {
                                        tr("Hide password", "পাসওয়ার্ড লুকান")
                                    } else {
                                        tr("Show password", "পাসওয়ার্ড দেখুন")
                                    }
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_current_input")
                    )
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text(tr("New Password", "নতুন পাসওয়ার্ড")) },
                    visualTransformation = if (showNewPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showNewPassword = !showNewPassword },
                            modifier = Modifier.testTag("toggle_new_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (showNewPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showNewPassword) {
                                    tr("Hide password", "পাসওয়ার্ড লুকান")
                                } else {
                                    tr("Show password", "পাসওয়ার্ড দেখুন")
                                }
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_new_input")
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text(tr("Confirm New Password", "নতুন পাসওয়ার্ড নিশ্চিত করুন")) },
                    visualTransformation = if (showConfirmPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showConfirmPassword = !showConfirmPassword },
                            modifier = Modifier.testTag("toggle_confirm_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (showConfirmPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showConfirmPassword) {
                                    tr("Hide password", "পাসওয়ার্ড লুকান")
                                } else {
                                    tr("Show password", "পাসওয়ার্ড দেখুন")
                                }
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_confirm_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("change_password_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        hasExistingPassword && currentPassword.isEmpty() -> {
                            errorMessage = if (isBangla) {
                                "অনুগ্রহ করে আপনার বর্তমান পাসওয়ার্ড দিন।"
                            } else {
                                "Please enter your current password."
                            }
                        }
                        hasExistingPassword && currentPassword != existingPassword -> {
                            errorMessage = if (isBangla) {
                                "বর্তমান পাসওয়ার্ড সঠিক নয়। অনুগ্রহ করে সঠিক পাসওয়ার্ড দিন।"
                            } else {
                                "Current password does not match."
                            }
                        }
                        newPassword.length < 6 -> {
                            errorMessage = if (isBangla) {
                                "নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"
                            } else {
                                "New password must be at least 6 characters."
                            }
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = if (isBangla) {
                                "নতুন পাসওয়ার্ড দুটি মিলছে না।"
                            } else {
                                "New password and confirmation do not match."
                            }
                        }
                        else -> {
                            try {
                                FirebaseAuth.getInstance().currentUser?.updatePassword(newPassword)
                            } catch (_: Exception) {
                                // Best-effort update if signed in via Google Credential
                            }
                            onPasswordUpdated(
                                newPassword,
                                if (hasExistingPassword) {
                                    if (isBangla) "আপনার পাসওয়ার্ড সফলভাবে আপডেট করা হয়েছে।"
                                    else "Your password has been updated successfully."
                                } else {
                                    if (isBangla) "আপনার নতুন পাসওয়ার্ড সফলভাবে তৈরি করা হয়েছে।"
                                    else "Your new password has been created successfully."
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("change_password_submit_button")
            ) {
                Text(
                    if (hasExistingPassword) {
                        tr("Update Password", "আপডেট করুন")
                    } else {
                        tr("Create Password", "পাসওয়ার্ড সেট করুন")
                    }
                )
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
private fun HideAccountDialog(
    isCurrentlyHidden: Boolean,
    onDismiss: () -> Unit,
    onConfirmToggle: (Boolean) -> Unit
) {
    var hiddenChecked by remember { mutableStateOf(isCurrentlyHidden) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (hiddenChecked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr("Hide Account", "অ্যাকাউন্ট লুকান"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tr(
                        "When your account is hidden, your biodata will not appear in public search results or recommendations to new members. You can unhide it anytime.",
                        "অ্যাকাউন্ট লুকানো থাকলে আপনার বায়োডাটা পাবলিক সার্চ বা নতুন সদস্যদের তালিকায় দেখা যাবে না। আপনি যেকোনো সময় এটি পুনরায় দৃশ্যমান করতে পারবেন।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Hide My Biodata", "আমার বায়োডাটা লুকান"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (hiddenChecked) {
                                    tr("Status: Hidden from search", "অবস্থা: অনুসন্ধান থেকে লুকানো")
                                } else {
                                    tr("Status: Visible to members", "অবস্থা: সকলের জন্য দৃশ্যমান")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hiddenChecked,
                            onCheckedChange = { hiddenChecked = it },
                            modifier = Modifier.testTag("hide_account_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmToggle(hiddenChecked) },
                modifier = Modifier.testTag("hide_account_save_button")
            ) {
                Text(tr("Save Preference", "সংরক্ষণ করুন"))
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
private fun DeleteAccountDialog(
    userId: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = tr("Delete Account", "অ্যাকাউন্ট মুছে ফেলুন"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = tr(
                    "Are you sure you want to permanently delete your Ghotok account and biodata? This action will remove your profile and sign you out.",
                    "আপনি কি নিশ্চিতভাবে আপনার ঘটক অ্যাকাউন্ট এবং বায়োডাটা স্থায়ীভাবে মুছে ফেলতে চান? এই পদক্ষেপটি আপনার প্রোফাইল মুছে ফেলবে এবং আপনাকে লগ আউট করবে।"
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (userId.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { SupabaseClient.deleteUserAccount(userId) }
                        }
                    }
                    onConfirmDelete()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_account_button")
            ) {
                Text(tr("Delete Permanently", "স্থায়ীভাবে মুছে ফেলুন"))
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
private fun PolicyInfoDialog(
    title: String,
    icon: ImageVector,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("policy_dialog_close_button")
            ) {
                Text(tr("Close", "বন্ধ করুন"))
            }
        }
    )
}

@Composable
private fun ContactUsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val isBangla = LocalLanguageController.current.isBangla
    val supportEmail = "support@ghotok.com.bd"
    val supportPhone = "+880 1711-000000"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = tr("Contact Us", "যোগাযোগ করুন"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr(
                        "Our Ghotok Matrimony support team is available Saturday – Thursday (9:00 AM – 8:00 PM BST).",
                        "ঘটক ম্যাট্রিমনি সাপোর্ট টিম শনিবার – বৃহস্পতিবার (সকাল ৯:০০ – রাত ৮:০০) আপনার সেবায় নিয়োজিত।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Email Support", "ইমেইল সাপোর্ট"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = supportEmail,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, "Support Email", supportEmail)
                                    onCopied(
                                        if (isBangla) "সাপোর্ট ইমেইল কপি করা হয়েছে"
                                        else "Support email copied"
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = tr("Copy Email", "ইমেইল কপি করুন"),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = tr("Helpline", "হেল্পলাইন"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = supportPhone,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        HorizontalDivider()

                        Text(
                            text = tr("Office Address", "অফিসের ঠিকানা"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tr(
                                "House 24, Road 11, Banani, Dhaka-1213, Bangladesh",
                                "বাড়ি ২৪, রোড ১১, বনানী, ঢাকা-১২১৩, বাংলাদেশ"
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$supportEmail")
                            putExtra(Intent.EXTRA_SUBJECT, "Ghotok Support Inquiry")
                        }
                        context.startActivity(emailIntent)
                    } catch (_: Exception) {
                        copyToClipboard(context, "Support Email", supportEmail)
                        onCopied(
                            if (isBangla) "ইমেইল ঠিকানা কপি করা হয়েছে: $supportEmail"
                            else "Copied support email: $supportEmail"
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("contact_us_email_button")
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr("Email Us", "ইমেইল করুন"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "বন্ধ করুন"))
            }
        }
    )
}

@Composable
private fun CheckVersionUpdateDialog(
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val isBangla = LocalLanguageController.current.isBangla

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = tr("Ghotok v1.2.0", "ঘটক ভার্সন ১.২.০"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tr(
                        "You are using the latest version of Ghotok Bangladeshi Matrimony.",
                        "আপনি ঘটক বাংলাদেশী ম্যাট্রিমনি অ্যাপের সর্বশেষ সংস্করণ ব্যবহার করছেন।"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = tr(
                        "Build 120 • All security and matchmaking updates are installed.",
                        "বিল্ড ১২০ • সকল নিরাপত্তা ও ম্যাচমেকিং আপডেট সক্রিয় রয়েছে।"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onShowMessage(
                        if (isBangla) "আপনার অ্যাপটি সর্বশেষ ভার্সনে আপডেট করা আছে (v1.2.0)।"
                        else "Your app is up to date (v1.2.0)."
                    )
                },
                modifier = Modifier.testTag("check_version_ok_button")
            ) {
                Text(tr("Up to Date", "ঠিক আছে"))
            }
        }
    )
}

private fun shareText(
    context: Context,
    subject: String,
    body: String,
    chooserTitle: String
) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        context.startActivity(chooser)
    } catch (_: Exception) {
        // Ignore if no share target available on emulator
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Exception) {
        // Ignore clipboard exceptions
    }
}
