<<<<<<< HEAD
# ghotok
=======
<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/4211e3a9-df28-46c8-9b21-bd8ea1fc4a18

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Profile privacy setup

The Supabase schema provides `matchmaking_users` and `matchmaking_profiles` views for signed-in profile browsing. Back up the project and apply `supabase_schema.sql` in a staging project first; direct `users` and `profiles` reads are limited to the owner and admins, while the views omit exact birth dates, street addresses, contact channels, FCM tokens, and verification documents. Profile entitlement and moderation fields are protected by database triggers. The schema also adds the `apply_verified_google_play_purchase` RPC and receipt table used by the purchase-verification function.

Before deploying the Firestore rules, backfill existing `/users` documents into `/publicProfiles` with only public biodata fields. Use a trusted Firebase Admin SDK migration, never a client app; omit exact birth dates, street addresses, contact details, FCM tokens, verification documents, saved-profile lists, and partner preferences. New profile saves create the sanitized public mirror automatically. Until the backfill is complete, existing users will not appear in public Firestore matchmaking results.

Firestore contact documents are owner/admin-only; Supabase additionally permits contact reads for premium users with an accepted interest. The Supabase triggers protect future writes but do not validate historical entitlement values, so audit existing paid/verified/admin fields against trusted payment and moderation records before relying on them.

## Google Play purchase verification

The Android client does not grant memberships from a Play Billing callback. Deploy `supabase/functions/verify-google-play-purchase` only after applying the schema in Supabase. In Google Play Console, grant a dedicated Google Cloud service account access to the app and enable the Google Play Android Developer API. Configure these Edge Function secrets in Supabase: `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`, `GOOGLE_PLAY_PRIVATE_KEY` (PEM private key), and `GOOGLE_PLAY_PACKAGE_NAME` (`com.codevory.ghotok`). Supabase provides `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` to Edge Functions. Never put the service-account key or service-role key in the Android app or source control. Then deploy with `supabase functions deploy verify-google-play-purchase`; the function requires a valid Supabase user JWT, checks the purchase with Google, acknowledges it, and applies the entitlement through a service-role-only RPC.

## Profile image uploads

The `cloudinary-upload` Edge Function requires a valid signed-in Supabase user and accepts only JPEG, PNG, or WebP images up to 1 MB. Configure `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, and `CLOUDINARY_UPLOAD_PRESET` as Supabase function secrets. Use a signed Cloudinary upload preset with strict allowed formats, size limits, and transformations; unsigned fallback uploads are intentionally disabled. Deploy with `supabase functions deploy cloudinary-upload`. Keep all Cloudinary credentials server-side.

## Release build requirements

Release builds intentionally fail unless a real Firebase reCAPTCHA Enterprise site key and the existing Play upload keystore are configured. Provide `RECAPTCHA_SITE_KEY`, `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` as Gradle properties or environment variables in a protected build environment. Do not generate or replace an upload keystore for an app already registered with Google Play; use that app's existing upload key. Register the corresponding Play App Signing certificate fingerprints with Firebase for Google sign-in.
>>>>>>> b4329555 (commit)
