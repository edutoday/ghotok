package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.ui.theme.tr
import com.example.util.GhotokAnalytics
import com.example.util.GhotokLog
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auth Method Tab: 0 = Google (Recommended), 1 = Email / Password
    var selectedAuthTab by remember { mutableIntStateOf(0) }

    // Email/Password states
    var isRegisterMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Forgot Password / Recovery dialog states
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var recoveryStep by remember { mutableIntStateOf(1) }
    var recoveryEmailInput by remember { mutableStateOf("") }
    var recoveryChallenge by remember { mutableStateOf<com.example.data.supabase.SupabaseClient.PasswordRecoveryChallenge?>(null) }
    var recoveryVerificationInput by remember { mutableStateOf("") }
    var recoveryNewPasswordInput by remember { mutableStateOf("") }
    var recoveryConfirmPasswordInput by remember { mutableStateOf("") }
    var recoveryPasswordVisible by remember { mutableStateOf(false) }
    var isRecoveryLoading by remember { mutableStateOf(false) }
    var recoveryErrorMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val pendingDeepLink by com.example.data.supabase.SupabaseClient.pendingRecoveryDeepLink.collectAsState()

    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink
        if (!link.isNullOrBlank()) {
            selectedAuthTab = 1
            isRegisterMode = false
            recoveryVerificationInput = link
            recoveryStep = 2
            recoveryErrorMessage = null
            showForgotPasswordDialog = true
        }
    }

    // Silent Auto Sign-In on launch (skipped if user explicitly signed out)
    LaunchedEffect(Unit) {
        if (com.example.data.supabase.SupabaseClient.isExplicitlySignedOut(context)) {
            runCatching {
                Firebase.auth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
            return@LaunchedEffect
        }
        val existingSupabaseUid = com.example.data.supabase.SupabaseClient.getSessionUserId(context)
        val existingFirebaseUser = Firebase.auth.currentUser
        if (!existingSupabaseUid.isNullOrBlank()) {
            onAuthSuccess()
        } else if (existingFirebaseUser != null) {
            com.example.data.supabase.SupabaseClient.saveSession(
                context = context,
                userId = existingFirebaseUser.uid,
                email = existingFirebaseUser.email.orEmpty(),
                displayName = existingFirebaseUser.displayName.orEmpty()
            )
            onAuthSuccess()
        } else {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    var lastAuthBackPressTime by remember { mutableStateOf(0L) }
    val isBanglaAuth = com.example.ui.theme.LocalLanguageController.current.isBangla
    androidx.activity.compose.BackHandler {
        when {
            selectedAuthTab == 1 && isRegisterMode -> {
                isRegisterMode = false
            }
            selectedAuthTab == 1 -> {
                selectedAuthTab = 0
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastAuthBackPressTime <= 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastAuthBackPressTime = now
                    android.widget.Toast.makeText(
                        context,
                        if (isBanglaAuth) "ঘটক বন্ধ করতে পরপর দুইবার ব্যাক চাপুন (Double tap to close Ghotok)"
                        else "Double tap to close Ghotok",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val languageController = com.example.ui.theme.LocalLanguageController.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { languageController.onToggleBangla(!languageController.isBangla) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("auth_language_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (!languageController.isBangla) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Text(
                                text = "English",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!languageController.isBangla) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (languageController.isBangla) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Text(
                                text = "বাংলা",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (languageController.isBangla) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = R.drawable.ghotok_splash),
                contentDescription = "Ghotok",
                modifier = Modifier
                    .size(210.dp)
                    .testTag("auth_splash_image"),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Auth Option Tabs
            TabRow(
                selectedTabIndex = selectedAuthTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedAuthTab == 0,
                    onClick = { selectedAuthTab = 0 },
                    text = { Text(tr("Google Sign-In", "গুগল সাইন-ইন")) },
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedAuthTab == 1,
                    onClick = { selectedAuthTab = 1 },
                    text = { Text(tr("Email / Password", "ইমেইল / পাসওয়ার্ড")) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            when (selectedAuthTab) {
                0 -> {
                    // Google 1-Tap Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            FeatureItem(
                                icon = Icons.Default.Verified,
                                title = tr("100% Verified Biodata", "১০০% যাচাইকৃত বায়োডাটা"),
                                subtitle = tr("Smart NID, Passport & Professional Credentials verified", "স্মার্ট এনআইডি, পাসপোর্ট ও পেশাগত পরিচয় যাচাইকৃত")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FeatureItem(
                                icon = Icons.Default.Security,
                                title = tr("Family-Oriented Matchmaking", "পরিবার-কেন্দ্রিক ম্যাচমেকিং"),
                                subtitle = tr("Detailed family background, education & partner preferences", "বিস্তারিত পারিবারিক পটভূমি, শিক্ষা ও সঙ্গীর পছন্দসমূহ")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FeatureItem(
                                icon = Icons.Default.Lock,
                                title = tr("Bank-Grade Privacy & Security", "সর্বোচ্চ গোপনীয়তা ও নিরাপত্তা"),
                                subtitle = tr("Contact details protected. Control who views your biodata.", "যোগাযোগের তথ্য সুরক্ষিত। কারা আপনার বায়োডাটা দেখবে তা নিয়ন্ত্রণ করুন।")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { error ->
                                    isLoading = false
                                    errorMessage = error
                                },
                                scope = coroutineScope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_dialog_email),
                                    contentDescription = tr("Sign In", "সাইন ইন"),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = tr("Sign in with Google", "গুগল দিয়ে সাইন ইন করুন"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Email & Password Registration / Sign-In Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Sub-toggle: Sign In vs Register
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !isRegisterMode,
                                    onClick = { isRegisterMode = false; errorMessage = null },
                                    label = { Text(tr("Sign In", "সাইন ইন")) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = isRegisterMode,
                                    onClick = { isRegisterMode = true; errorMessage = null },
                                    label = { Text(tr("Register New", "নতুন নিবন্ধন")) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (isRegisterMode) {
                                OutlinedTextField(
                                    value = fullNameInput,
                                    onValueChange = { fullNameInput = it },
                                    label = { Text(tr("Full Name *", "পূর্ণ নাম *")) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_fullname_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text(tr("Email Address *", "ইমেইল *")) },
                                placeholder = { Text("name@example.com") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_input"),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text(tr("Password *", "পাসওয়ার্ড *")) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) tr("Hide password", "পাসওয়ার্ড লুকান") else tr("Show password", "পাসওয়ার্ড দেখুন")
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_password_input"),
                                singleLine = true
                            )

                            if (isRegisterMode) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = confirmPasswordInput,
                                    onValueChange = { confirmPasswordInput = it },
                                    label = { Text(tr("Confirm Password *", "পাসওয়ার্ড নিশ্চিত করুন *")) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_confirm_password_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            recoveryEmailInput = emailInput.trim()
                                            recoveryStep = 1
                                            recoveryChallenge = null
                                            recoveryVerificationInput = ""
                                            recoveryNewPasswordInput = ""
                                            recoveryConfirmPasswordInput = ""
                                            recoveryErrorMessage = null
                                            showForgotPasswordDialog = true
                                        },
                                        modifier = Modifier.testTag("forgot_password_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockReset,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = tr("Forgot Password?", "পাসওয়ার্ড ভুলে গেছেন?"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val errInvalidEmail = tr("Please enter a valid email address.", "অনুগ্রহ করে একটি সঠিক ইমেইল লিখুন।")
                            val errShortPassword = tr("Password must be at least 6 characters long.", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।")
                            val errPasswordMismatch = tr("Passwords do not match.", "পাসওয়ার্ড মেলেনি।")
                            val errEmptyName = tr("Please enter your Full Name.", "অনুগ্রহ করে আপনার পূর্ণ নাম লিখুন।")

                            Button(
                                onClick = {
                                    if (emailInput.trim().isEmpty() || !emailInput.contains("@")) {
                                        errorMessage = errInvalidEmail
                                        return@Button
                                    }
                                    if (passwordInput.length < 6) {
                                        errorMessage = errShortPassword
                                        return@Button
                                    }
                                    if (isRegisterMode && passwordInput != confirmPasswordInput) {
                                        errorMessage = errPasswordMismatch
                                        return@Button
                                    }
                                    if (isRegisterMode && fullNameInput.trim().isEmpty()) {
                                        errorMessage = errEmptyName
                                        return@Button
                                    }
                                    errorMessage = null
                                    isLoading = true

                                    val cleanEmail = emailInput.trim()
                                    val cleanName = fullNameInput.trim()
                                    val pwd = passwordInput
                                    val register = isRegisterMode

                                    coroutineScope.launch {
                                        val authResult = if (register) {
                                            com.example.data.supabase.SupabaseClient.signUpWithEmailPassword(
                                                context = context,
                                                email = cleanEmail,
                                                password = pwd,
                                                fullName = cleanName
                                            )
                                        } else {
                                            com.example.data.supabase.SupabaseClient.signInWithEmailPassword(
                                                context = context,
                                                email = cleanEmail,
                                                password = pwd
                                            )
                                        }

                                        authResult.onSuccess { uid ->
                                            runCatching {
                                                com.example.data.repository.MatrimonyRepository.create(context).prefetchUserProfile(uid)
                                            }
                                            isLoading = false
                                            onAuthSuccess()
                                        }.onFailure { err ->
                                            isLoading = false
                                            errorMessage = err.localizedMessage ?: if (register) {
                                                "Failed to create account. Please try again."
                                            } else {
                                                "Failed to sign in. Please check your email and password."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("email_auth_submit_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(if (isRegisterMode) tr("Create Account & Sign In", "অ্যাকাউন্ট তৈরি ও সাইন ইন করুন") else tr("Sign In with Email", "ইমেইল দিয়ে সাইন ইন করুন"))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("By continuing, you agree to Ghotok Terms & Matrimonial Code of Conduct.", "চালিয়ে যাওয়ার মাধ্যমে আপনি ঘটক-এর শর্তাবলী ও বৈবাহিক আচরণবিধিতে সম্মত হচ্ছেন।"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showForgotPasswordDialog) {
        val errInvalidRecoveryEmail = tr("Please enter your registered email address.", "অনুগ্রহ করে আপনার নিবন্ধিত ইমেইল লিখুন।")
        val errEmptyVerification = tr("Please enter the 6-digit OTP code sent to your email.", "অনুগ্রহ করে আপনার ইমেইলে পাঠানো ৬-সংখ্যার ওটিপি কোডটি লিখুন।")
        val errShortNewPassword = tr("New password must be at least 6 characters long.", "নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।")
        val errNewPasswordMismatch = tr("New passwords do not match.", "নতুন পাসওয়ার্ড দুটি মেলেনি।")
        val toastResetSuccess = tr("Password reset successful! Signing you in...", "পাসওয়ার্ড সফলভাবে রিসেট হয়েছে! সাইন ইন করা হচ্ছে...")

        AlertDialog(
            onDismissRequest = {
                if (!isRecoveryLoading) {
                    showForgotPasswordDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (recoveryStep == 1) {
                        tr("Recover Your Password", "পাসওয়ার্ড পুনরুদ্ধার করুন")
                    } else {
                        tr("Enter OTP & New Password", "ওটিপি কোড ও নতুন পাসওয়ার্ড দিন")
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (recoveryErrorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = recoveryErrorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (recoveryStep == 1) {
                        Text(
                            text = tr(
                                "Enter your registered email address. We will send a 6-digit OTP code to your email inbox.",
                                "আপনার নিবন্ধিত ইমেইল ঠিকানাটি লিখুন। আপনার ইমেইল ইনবক্সে একটি ৬-সংখ্যার ওটিপি (OTP) কোড পাঠানো হবে।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = recoveryEmailInput,
                            onValueChange = {
                                recoveryEmailInput = it
                                recoveryErrorMessage = null
                            },
                            label = { Text(tr("Registered Email Address *", "নিবন্ধিত ইমেইল ঠিকানা *")) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_email_input"),
                            singleLine = true
                        )
                    } else {
                        val challenge = recoveryChallenge
                        Text(
                            text = tr(
                                "Enter the 6-digit OTP code sent to ${challenge?.email?.ifBlank { recoveryEmailInput } ?: "your email"} and set your new password.",
                                "${challenge?.email?.ifBlank { recoveryEmailInput } ?: "আপনার ইমেইলে"} পাঠানো ৬-সংখ্যার ওটিপি কোডটি লিখুন এবং নতুন পাসওয়ার্ড দিন।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = recoveryVerificationInput,
                            onValueChange = {
                                recoveryVerificationInput = it.trim()
                                recoveryErrorMessage = null
                            },
                            label = {
                                Text(tr("6-Digit Email OTP Code *", "৬-সংখ্যার ইমেইল ওটিপি কোড *"))
                            },
                            placeholder = {
                                Text("123456")
                            },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_verification_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = recoveryNewPasswordInput,
                            onValueChange = {
                                recoveryNewPasswordInput = it
                                recoveryErrorMessage = null
                            },
                            label = { Text(tr("New Password (min 6 chars) *", "নতুন পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *")) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { recoveryPasswordVisible = !recoveryPasswordVisible }) {
                                    Icon(
                                        imageVector = if (recoveryPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (recoveryPasswordVisible) tr("Hide password", "পাসওয়ার্ড লুকান") else tr("Show password", "পাসওয়ার্ড দেখুন")
                                    )
                                }
                            },
                            visualTransformation = if (recoveryPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_new_password_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = recoveryConfirmPasswordInput,
                            onValueChange = {
                                recoveryConfirmPasswordInput = it
                                recoveryErrorMessage = null
                            },
                            label = { Text(tr("Confirm New Password *", "নতুন পাসওয়ার্ড নিশ্চিত করুন *")) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = if (recoveryPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_confirm_password_input"),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                if (recoveryStep == 1) {
                    Button(
                        onClick = {
                            val targetEmail = recoveryEmailInput.trim()
                            if (targetEmail.isEmpty() || !targetEmail.contains("@")) {
                                recoveryErrorMessage = errInvalidRecoveryEmail
                                return@Button
                            }
                            recoveryErrorMessage = null
                            isRecoveryLoading = true
                            coroutineScope.launch {
                                val res = com.example.data.supabase.SupabaseClient.requestPasswordRecovery(
                                    context = context,
                                    email = targetEmail
                                )
                                isRecoveryLoading = false
                                res.onSuccess { challenge ->
                                    recoveryChallenge = challenge
                                    recoveryVerificationInput = ""
                                    recoveryStep = 2
                                }.onFailure { err ->
                                    recoveryErrorMessage = err.localizedMessage ?: "Failed to send OTP code."
                                }
                            }
                        },
                        enabled = !isRecoveryLoading,
                        modifier = Modifier.testTag("send_recovery_button")
                    ) {
                        if (isRecoveryLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(tr("Send OTP Code", "ওটিপি কোড পাঠান"))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (recoveryVerificationInput.trim().isEmpty()) {
                                recoveryErrorMessage = errEmptyVerification
                                return@Button
                            }
                            if (recoveryNewPasswordInput.length < 6) {
                                recoveryErrorMessage = errShortNewPassword
                                return@Button
                            }
                            if (recoveryNewPasswordInput != recoveryConfirmPasswordInput) {
                                recoveryErrorMessage = errNewPasswordMismatch
                                return@Button
                            }
                            recoveryErrorMessage = null
                            isRecoveryLoading = true
                            val targetEmail = recoveryEmailInput.trim()
                            val verifyVal = recoveryVerificationInput.trim()
                            val newPwd = recoveryNewPasswordInput
                            coroutineScope.launch {
                                val res = com.example.data.supabase.SupabaseClient.resetPasswordWithRecovery(
                                    context = context,
                                    email = targetEmail,
                                    verificationInput = verifyVal,
                                    newPassword = newPwd
                                )
                                res.onSuccess { uid ->
                                    runCatching {
                                        com.example.data.repository.MatrimonyRepository.create(context).prefetchUserProfile(uid)
                                    }
                                    isRecoveryLoading = false
                                    emailInput = targetEmail
                                    passwordInput = newPwd
                                    showForgotPasswordDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        toastResetSuccess,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onAuthSuccess()
                                }.onFailure { err ->
                                    isRecoveryLoading = false
                                    recoveryErrorMessage = err.localizedMessage ?: "Invalid or expired OTP code. Please try again."
                                }
                            }
                        },
                        enabled = !isRecoveryLoading,
                        modifier = Modifier.testTag("confirm_reset_password_button")
                    ) {
                        if (isRecoveryLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(tr("Reset Password & Sign In", "পাসওয়ার্ড রিসেট ও সাইন ইন"))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (recoveryStep == 2 && !isRecoveryLoading) {
                            recoveryStep = 1
                            recoveryErrorMessage = null
                        } else if (!isRecoveryLoading) {
                            showForgotPasswordDialog = false
                        }
                    },
                    modifier = Modifier.testTag("cancel_recovery_button")
                ) {
                    Text(
                        if (recoveryStep == 2) tr("Back", "পেছনে") else tr("Cancel", "বাতিল")
                    )
                }
            }
        )
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (com.example.data.supabase.SupabaseClient.isExplicitlySignedOut(context)) {
        onUnauthenticated()
        return
    }
    val existingSupabaseUid = com.example.data.supabase.SupabaseClient.getSessionUserId(context)
    if (!existingSupabaseUid.isNullOrBlank() || Firebase.auth.currentUser != null) {
        Firebase.auth.currentUser?.let { fbUser ->
            if (existingSupabaseUid.isNullOrBlank()) {
                com.example.data.supabase.SupabaseClient.saveSession(
                    context = context,
                    userId = fbUser.uid,
                    email = fbUser.email.orEmpty(),
                    displayName = fbUser.displayName.orEmpty()
                )
            }
        }
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    val targetScope = (context as? ComponentActivity)?.lifecycleScope ?: scope
    targetScope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                val fbUser = Firebase.auth.currentUser
                runCatching {
                    com.example.data.supabase.SupabaseClient.signInWithGoogleIdToken(
                        idToken = googleIdToken,
                        email = fbUser?.email.orEmpty(),
                        displayName = fbUser?.displayName.orEmpty(),
                        context = context
                    )
                }
                if (fbUser != null) {
                    val repo = com.example.data.repository.MatrimonyRepository.create(context)
                    val existingByFbUid = runCatching { repo.prefetchUserProfile(fbUser.uid) }.getOrNull()
                    val jwtUid = com.example.data.supabase.SupabaseClient.getAuthenticatedJwtUid().orEmpty()
                    val effectiveUid = when {
                        existingByFbUid != null && existingByFbUid.userId.isNotBlank() -> existingByFbUid.userId
                        jwtUid.isNotBlank() -> jwtUid
                        else -> fbUser.uid
                    }
                    com.example.data.supabase.SupabaseClient.saveSession(
                        context = context,
                        userId = effectiveUid,
                        email = fbUser.email.orEmpty(),
                        displayName = fbUser.displayName.orEmpty()
                    )
                    if (effectiveUid != fbUser.uid) {
                        runCatching { repo.prefetchUserProfile(effectiveUid) }
                    }
                }
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            // Normal coroutine cancellation
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    val targetScope = (context as? ComponentActivity)?.lifecycleScope ?: scope
    targetScope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                val fbUser = Firebase.auth.currentUser
                runCatching {
                    com.example.data.supabase.SupabaseClient.signInWithGoogleIdToken(
                        idToken = googleIdToken,
                        email = fbUser?.email.orEmpty(),
                        displayName = fbUser?.displayName.orEmpty(),
                        context = context
                    )
                }
                if (fbUser != null) {
                    val repo = com.example.data.repository.MatrimonyRepository.create(context)
                    val existingByFbUid = runCatching { repo.prefetchUserProfile(fbUser.uid) }.getOrNull()
                    val jwtUid = com.example.data.supabase.SupabaseClient.getAuthenticatedJwtUid().orEmpty()
                    val effectiveUid = when {
                        existingByFbUid != null && existingByFbUid.userId.isNotBlank() -> existingByFbUid.userId
                        jwtUid.isNotBlank() -> jwtUid
                        else -> fbUser.uid
                    }
                    com.example.data.supabase.SupabaseClient.saveSession(
                        context = context,
                        userId = effectiveUid,
                        email = fbUser.email.orEmpty(),
                        displayName = fbUser.displayName.orEmpty()
                    )
                    if (effectiveUid != fbUser.uid) {
                        runCatching { repo.prefetchUserProfile(effectiveUid) }
                    }
                }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: CancellationException) {
            // Coroutine cancelled when composition changes; do not treat as an error
            Log.d("Auth", "Sign-in cancelled cleanly: ${e.message}")
            onAuthCancelled()
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    com.example.data.supabase.SupabaseClient.clearSession()
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}
