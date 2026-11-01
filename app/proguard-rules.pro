# ProGuard / R8 Keep Rules for Ghotok BD Matrimony Release Builds
# Note: isMinifyEnabled = true and isShrinkResources = true are enabled in app/build.gradle.kts

# Preserve line numbers and source file info for crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# 1. Domain Models & Reflective JSON/Firestore Mappers
# ---------------------------------------------------------------------------
# SupabaseRowMappers and Firestore CustomClassMapper use reflection on data models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.supabase.** { *; }
-keep class com.example.data.model.UserProfile {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.Interest {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.ChatThread {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.ChatMessage {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.AdminNotification {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.PaymentRequest {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.VerificationRequest {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.OnboardingDraft {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.MembershipPackage {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.MembershipPackages { *; }


# ---------------------------------------------------------------------------
# 2. Firebase Firestore & Auth & AppCheck
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.DocumentId <fields>;
}

# ---------------------------------------------------------------------------
# 3. Google Play Billing
# ---------------------------------------------------------------------------
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ---------------------------------------------------------------------------
# 4. Jetpack Credential Manager & Google Identity
# ---------------------------------------------------------------------------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ---------------------------------------------------------------------------
# 5. OkHttp3 & Okio (used by SupabaseClient and CloudinaryUploader)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------------------------------------------------------------------------
# 6. Kotlin Coroutines & Serialization
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
