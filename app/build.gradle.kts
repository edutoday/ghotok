import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

val recaptchaSiteKey = providers.gradleProperty("RECAPTCHA_SITE_KEY")
  .orElse(providers.environmentVariable("RECAPTCHA_SITE_KEY"))
  .getOrElse("")

abstract class ValidateReleaseConfigurationTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.Input
  abstract val siteKey: org.gradle.api.provider.Property<String>

  @get:org.gradle.api.tasks.Input
  abstract val keystorePath: org.gradle.api.provider.Property<String>

  @get:org.gradle.api.tasks.Input
  abstract val hasStorePassword: org.gradle.api.provider.Property<Boolean>

  @get:org.gradle.api.tasks.Input
  abstract val hasKeyPassword: org.gradle.api.provider.Property<Boolean>

  @org.gradle.api.tasks.TaskAction
  fun validate() {
    val configuredSiteKey = siteKey.get().trim()
    require(
      configuredSiteKey.isNotEmpty() &&
        !configuredSiteKey.startsWith("YOUR_", ignoreCase = true) &&
        !configuredSiteKey.contains("RECAPTCHA", ignoreCase = true)
    ) {
      "Release builds require a real RECAPTCHA_SITE_KEY Gradle property or environment variable."
    }

    require(File(keystorePath.get()).isFile) {
      "Release signing keystore was not found. Set KEYSTORE_PATH to your existing Play upload keystore."
    }
    require(hasStorePassword.get()) {
      "Release builds require the STORE_PASSWORD environment variable."
    }
    require(hasKeyPassword.get()) {
      "Release builds require the KEY_PASSWORD environment variable."
    }
  }
}

// Ensure debug.keystore is automatically reconstructed from debug.keystore.base64
// when building locally in Android Studio for USB debugging.
val debugKeystoreFile = file("${rootDir}/debug.keystore")
val debugKeystoreBase64File = file("${rootDir}/debug.keystore.base64")
if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
  val cleanBase64 = debugKeystoreBase64File.readText().replace("\\s".toRegex(), "")
  debugKeystoreFile.writeBytes(Base64.getDecoder().decode(cleanBase64))
}

// Ensure Hind Siliguri Regular (400), Medium (500), and SemiBold (600) always have the
// corrected Bengali digit ১ (U+09E7, gid=367) interpolated between Light (300) and Bold (700),
// even after VFS syncs when files are edited in the browser editor.
abstract class EnsureHindSiliguriPatchedTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.Internal
  abstract val moduleDir: org.gradle.api.provider.Property<File>

  private fun calcTtfChecksum(bytes: ByteArray): Long {
    val padded = if (bytes.size % 4 == 0) bytes else bytes.copyOf(bytes.size + (4 - bytes.size % 4))
    val bb = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN)
    var sum = 0L
    while (bb.hasRemaining()) {
      sum = (sum + (bb.int.toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
    }
    return sum
  }

  private fun patchFontFile(ttfFile: File, gid: Int, adv: Int, lsb: Int, glyphB64: String) {
    if (!ttfFile.exists()) return
    val data = ttfFile.readBytes()
    val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    val numTables = bb.getShort(4).toInt() and 0xFFFF
    val indices = IntArray(numTables)
    val tags = Array(numTables) { "" }
    val dirOffsets = IntArray(numTables)
    val dirLengths = IntArray(numTables)
    val byTag = mutableMapOf<String, Int>()
    for (i in 0 until numTables) {
      val off = 12 + i * 16
      val tag = String(data, off, 4, Charsets.ISO_8859_1)
      indices[i] = i
      tags[i] = tag
      dirOffsets[i] = bb.getInt(off + 8)
      dirLengths[i] = bb.getInt(off + 12)
      byTag[tag] = i
    }
    val headIdx = byTag["head"] ?: return
    val maxpIdx = byTag["maxp"] ?: return
    val locaIdx = byTag["loca"] ?: return
    val glyfIdx = byTag["glyf"] ?: return

    val locFmt = bb.getShort(dirOffsets[headIdx] + 50).toInt()
    val numGlyphs = bb.getShort(dirOffsets[maxpIdx] + 4).toInt() and 0xFFFF
    if (gid >= numGlyphs) return

    val offsets = IntArray(numGlyphs + 1) { i ->
      if (locFmt == 0) {
        (bb.getShort(dirOffsets[locaIdx] + i * 2).toInt() and 0xFFFF) * 2
      } else {
        bb.getInt(dirOffsets[locaIdx] + i * 4)
      }
    }
    val gStart = offsets[gid]
    val gEnd = offsets[gid + 1]
    val currentYMin = bb.getShort(dirOffsets[glyfIdx] + gStart + 4).toInt()
    if (currentYMin == -10) return // Already patched

    val newGlyphBytes = Base64.getDecoder().decode(glyphB64)
    val tablesData = (0 until numTables).associate { i ->
      tags[i] to data.copyOfRange(dirOffsets[i], dirOffsets[i] + dirLengths[i])
    }.toMutableMap()

    val oldGlyf = tablesData.getValue("glyf")
    val newGlyf = ByteArrayOutputStream(oldGlyf.size - (gEnd - gStart) + newGlyphBytes.size).apply {
      write(oldGlyf, 0, gStart)
      write(newGlyphBytes)
      write(oldGlyf, gEnd, oldGlyf.size - gEnd)
    }.toByteArray()
    tablesData["glyf"] = newGlyf

    val delta = newGlyphBytes.size - (gEnd - gStart)
    val newLoca = ByteBuffer.allocate(if (locFmt == 0) (numGlyphs + 1) * 2 else (numGlyphs + 1) * 4)
      .order(ByteOrder.BIG_ENDIAN)
    for (i in 0..numGlyphs) {
      val updatedOff = if (i <= gid) offsets[i] else offsets[i] + delta
      if (locFmt == 0) newLoca.putShort((updatedOff / 2).toShort()) else newLoca.putInt(updatedOff)
    }
    tablesData["loca"] = newLoca.array()

    val hmtxBytes = tablesData.getValue("hmtx")
    ByteBuffer.wrap(hmtxBytes).order(ByteOrder.BIG_ENDIAN).apply {
      putShort(gid * 4, adv.toShort())
      putShort(gid * 4 + 2, lsb.toShort())
    }

    val headBytes = tablesData.getValue("head")
    ByteBuffer.wrap(headBytes).order(ByteOrder.BIG_ENDIAN).putInt(8, 0)

    val headerLen = 12 + numTables * 16
    val headerBytes = data.copyOfRange(0, headerLen)
    val headerBb = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
    val bodyOut = ByteArrayOutputStream(data.size + 64)
    var curOff = headerLen
    var headPhysOff = 0

    val sortedOrder = (0 until numTables).sortedBy { dirOffsets[it] }
    for (idx in sortedOrder) {
      val tag = tags[idx]
      val tBytes = tablesData.getValue(tag)
      val csum = calcTtfChecksum(tBytes)
      val tOff = curOff
      val tLen = tBytes.size
      if (tag == "head") headPhysOff = tOff
      bodyOut.write(tBytes)
      val rem = tLen % 4
      if (rem != 0) {
        bodyOut.write(ByteArray(4 - rem))
        curOff += tLen + (4 - rem)
      } else {
        curOff += tLen
      }
      val dirEntryOff = 12 + idx * 16
      headerBb.putInt(dirEntryOff + 4, csum.toInt())
      headerBb.putInt(dirEntryOff + 8, tOff)
      headerBb.putInt(dirEntryOff + 12, tLen)
    }

    val fullBytes = ByteArrayOutputStream(headerBytes.size + bodyOut.size()).apply {
      write(headerBytes)
      write(bodyOut.toByteArray())
    }.toByteArray()
    val totalCsum = calcTtfChecksum(fullBytes)
    val adj = (0xB1B0AFBAL - totalCsum) and 0xFFFFFFFFL
    ByteBuffer.wrap(fullBytes).order(ByteOrder.BIG_ENDIAN).putInt(headPhysOff + 8, adj.toInt())
    ttfFile.writeBytes(fullBytes)
  }

  fun patchAllInDir(baseDir: File) {
    val rootDirFile = baseDir.parentFile
    if (rootDirFile != null) {
      val ksFile = File(rootDirFile, "debug.keystore")
      val ksB64File = File(rootDirFile, "debug.keystore.base64")
      if (!ksFile.exists() && ksB64File.exists()) {
        val cleanB64 = ksB64File.readText().replace("\\s".toRegex(), "")
        ksFile.writeBytes(Base64.getDecoder().decode(cleanB64))
      }
    }
    val rootAssetsDir = File(baseDir.parentFile, "public/assets/aistudio")
    val resDrawableDir = File(baseDir, "src/main/res/drawable")
    val manSrc = File(rootAssetsDir, "Man_dp_38b214ad.jpg")
    val manDst = File(resDrawableDir, "default_man_dp.jpg")
    if (manSrc.exists() && !manDst.exists()) {
      manSrc.copyTo(manDst, overwrite = true)
    }
    val womanSrc = File(rootAssetsDir, "Women_dp_90349003.jpg")
    val womanDst = File(resDrawableDir, "default_woman_dp.jpg")
    if (womanSrc.exists() && !womanDst.exists()) {
      womanSrc.copyTo(womanDst, overwrite = true)
    }
    val unusedBanner = File(resDrawableDir, "matrimony_banner.jpg")
    if (unusedBanner.exists()) {
      unusedBanner.delete()
    }
    listOf(
      "Screenshot__3__7a16494e.png",
      "image_e8ced7a0.png",
      "photo_6262479801734074758_y_4b1d19b6.jpg",
      "photo_6264607803410420350_y_660d1807.jpg"
    ).forEach { name ->
      val f = File(rootAssetsDir, name)
      if (f.exists()) f.delete()
    }

    val fontDir = File(baseDir, "src/main/res/font")
    patchFontFile(
      File(fontDir, "hind_siliguri_w400.ttf"), 367, 500, 42,
      "AAEAKv/2AcgCggAjAAATIwYVFBYWFhYWFQYjIiY1NDYzNSIGFRQWMzI2NTQmJiYmNTTiTAYmOD8zHAx6NkoyKEpcc1loajBFRjACghgXLlA5PDpFK3w0NCkvRlZHUF11VTZfR0JSKxI="
    )
    patchFontFile(
      File(fontDir, "hind_siliguri_w500.ttf"), 367, 508, 38,
      "AAEAJv/2AdECigAjAAATIwYVFBYWFhYWFQYjIiY1NDYzNSIGFRQWMzI2NTQmJiYmNTTqXQYpPEEyFBhlMEMtJE1hc15qcDFFRjECihoZLVI8PkI/LWcvLSUqUldJUV92WTdjSUNPKBM="
    )
    patchFontFile(
      File(fontDir, "hind_siliguri_w600.ttf"), 367, 517, 34,
      "AAEAIv/2AdsClAAjAAATIwYVFBYWFhYWFQYjIiY1NDYzNSIGFRQWMzI2NTQmJiYmNTTzcAcsQUMyCydLKzkmIFJlc2JtdzFGRjIClBwcLVJAQUw4LlEpJyAkX1hLU2B3XjlnS0VLJBQ="
    )
  }

  @org.gradle.api.tasks.TaskAction
  fun executePatch() {
    patchAllInDir(moduleDir.get())
  }
}

val ensureHindSiliguriPatched = tasks.register<EnsureHindSiliguriPatchedTask>("ensureHindSiliguriPatched") {
  moduleDir.set(projectDir)
}
tasks.matching {
  it.name == "preBuild"
}.configureEach {
  dependsOn(ensureHindSiliguriPatched)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.codevory.ghotok"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    resValue("string", "recaptcha_site_key", recaptchaSiteKey)
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val releaseKeystorePath = file(
  System.getenv("KEYSTORE_PATH") ?: "${rootProject.projectDir}/my-upload-key.jks"
).absolutePath
val validateReleaseConfiguration = tasks.register<ValidateReleaseConfigurationTask>("validateReleaseConfiguration") {
  siteKey.set(recaptchaSiteKey)
  keystorePath.set(releaseKeystorePath)
  hasStorePassword.set(!System.getenv("STORE_PASSWORD").isNullOrBlank())
  hasKeyPassword.set(!System.getenv("KEY_PASSWORD").isNullOrBlank())
}

tasks.configureEach {
  if (name == "preReleaseBuild") {
    dependsOn(validateReleaseConfiguration)
  }
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.crashlytics)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.billing.ktx)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
