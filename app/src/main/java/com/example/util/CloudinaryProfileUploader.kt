package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses profile/verification images on-device to < 100 KB and uploads them via
 * the backend Cloud/Edge Function (`supabase/functions/cloudinary-upload`), keeping
 * `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` strictly server-side.
 */
object CloudinaryProfileUploader {
    private const val TAG = "CloudinaryUploader"

    /**
     * Resolves the backend Cloud Function endpoint URL:
     * 1. Uses `BuildConfig.CLOUDINARY_FUNCTION_URL` if explicitly set in Secrets.
     * 2. Otherwise defaults to `${SupabaseClient.supabaseUrl}/functions/v1/cloudinary-upload`.
     */
    val FUNCTION_URL: String
        get() {
            val customUrl = runCatching { BuildConfig.CLOUDINARY_FUNCTION_URL.trim() }.getOrDefault("")
                .takeIf {
                    it.isNotBlank() &&
                        it != "YOUR_CLOUDINARY_FUNCTION_URL" &&
                        (it.startsWith("https://") || it.startsWith("http://"))
                }
            if (!customUrl.isNullOrBlank()) return customUrl
            val baseSupabaseUrl = SupabaseClient.supabaseUrl
            return if (baseSupabaseUrl.isNotBlank() && baseSupabaseUrl != "YOUR_SUPABASE_URL") {
                "${baseSupabaseUrl.trimEnd('/')}/functions/v1/cloudinary-upload"
            } else {
                ""
            }
        }

    private const val MAX_FILE_BYTES = 98 * 1024 // Strictly under 100 KB (102,400 bytes)
    private const val INITIAL_MAX_DIMENSION = 800
    const val PROFILE_TRANSFORMATION = "f_auto,q_auto,w_400,h_400,c_fill,r_max"

    /**
     * Inserts Cloudinary circular profile optimization flags (`f_auto,q_auto,w_400,h_400,c_fill,r_max`)
     * into a Cloudinary image URL for circular profile previews.
     */
    fun getOptimizedProfileUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val uploadSegment = "/image/upload/"
        if (!trimmed.contains(uploadSegment)) return trimmed
        if (trimmed.contains(PROFILE_TRANSFORMATION)) return trimmed
        return trimmed.replaceFirst(uploadSegment, "$uploadSegment$PROFILE_TRANSFORMATION/")
    }

    /**
     * Compresses the image from [uri] to strictly less than 100 KB and writes it to a temporary JPEG file.
     */
    fun compressImageUnder100Kb(context: Context, uri: Uri): File {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            throw IllegalArgumentException("Unable to decode selected image")
        }

        var sampleSize = 1
        while (max(srcWidth / sampleSize, srcHeight / sampleSize) > INITIAL_MAX_DIMENSION * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalArgumentException("Unable to read selected image")

        var currentBitmap = scaleDownIfNeeded(decodedBitmap, INITIAL_MAX_DIMENSION)
        if (currentBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        var compressedBytes = compressToJpegUnderLimit(currentBitmap, MAX_FILE_BYTES)
        var scaleStep = 0
        while (compressedBytes.size >= 100 * 1024 && scaleStep < 6) {
            scaleStep++
            val nextMaxDim = (max(currentBitmap.width, currentBitmap.height) * 0.78f).roundToInt().coerceAtLeast(160)
            val scaled = scaleDownIfNeeded(currentBitmap, nextMaxDim)
            if (scaled !== currentBitmap) {
                currentBitmap.recycle()
                currentBitmap = scaled
            }
            compressedBytes = compressToJpegUnderLimit(currentBitmap, MAX_FILE_BYTES)
        }
        currentBitmap.recycle()

        val tempFile = File(context.cacheDir, "ghotok_dp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { fos ->
            fos.write(compressedBytes)
            fos.flush()
        }
        return tempFile
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressToJpegUnderLimit(bitmap: Bitmap, maxBytes: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        var quality = 88
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > maxBytes && quality > 15) {
            baos.reset()
            quality -= 10
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceAtLeast(12), baos)
        }
        return baos.toByteArray()
    }

    /**
     * Compresses the selected image to < 100 KB and uploads it via multipart POST
     * to the backend Cloud/Edge Function (`/functions/v1/cloudinary-upload`).
     * Returns the `secure_url` from the Cloud Function's JSON response.
     */
    suspend fun compressAndUploadToCloudinary(
        context: Context,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpointUrl = FUNCTION_URL
            if (endpointUrl.isEmpty()) {
                Log.e(TAG, "Cloud Function endpoint URL is missing! Configure SUPABASE_URL or CLOUDINARY_FUNCTION_URL in Secrets.")
                throw IllegalStateException("Upload service is not configured. Please set SUPABASE_URL or CLOUDINARY_FUNCTION_URL in Secrets.")
            }
            val compressedFile = compressImageUnder100Kb(context, imageUri)
            try {
                executeCloudFunctionUpload(
                    endpointUrl = endpointUrl,
                    imageFile = compressedFile,
                    bearerToken = SupabaseClient.currentAccessToken?.takeIf { it.isNotBlank() }
                        ?: SupabaseClient.supabaseAnonKey,
                    apiKeyHeader = SupabaseClient.supabaseAnonKey
                ).getOrThrow()
            } finally {
                runCatching { compressedFile.delete() }
            }
        }
    }

    internal fun executeCloudFunctionUpload(
        endpointUrl: String,
        imageFile: File,
        bearerToken: String,
        apiKeyHeader: String
    ): Result<String> = runCatching {
        val boundary = "----GhotokBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = 25_000
            readTimeout = 30_000
            requestMethod = "POST"
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (bearerToken.isNotBlank() && bearerToken != "YOUR_SUPABASE_ANON_KEY") {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (apiKeyHeader.isNotBlank() && apiKeyHeader != "YOUR_SUPABASE_ANON_KEY") {
                setRequestProperty("apikey", apiKeyHeader)
            }
        }

        DataOutputStream(connection.outputStream).use { outputStream ->
            // File part
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"${imageFile.name}\"$lineEnd"
            )
            outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
            outputStream.writeBytes(lineEnd)
            imageFile.inputStream().use { fileInput ->
                fileInput.copyTo(outputStream)
            }
            outputStream.writeBytes(lineEnd)

            // Final boundary
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
        }

        val statusCode = connection.responseCode
        val responseText = try {
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }

        parseUploadResponse(statusCode, responseText)
    }

    internal fun parseUploadResponse(statusCode: Int, responseText: String): String {
        if (statusCode !in 200..299) {
            val errMsg = runCatching {
                val json = JSONObject(responseText)
                json.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: json.optString("error").takeIf { it.isNotBlank() }
            }.getOrNull() ?: "Cloud Function upload failed (HTTP $statusCode)"
            throw IllegalStateException(errMsg)
        }

        val json = JSONObject(responseText)
        val secureUrl = json.optString("secure_url")
            .ifBlank { json.optString("url") }
            .ifBlank { json.optJSONObject("data")?.optString("secure_url").orEmpty() }
        if (secureUrl.isBlank()) {
            throw IllegalStateException("Cloud Function response missing secure_url")
        }
        return secureUrl
    }
}
