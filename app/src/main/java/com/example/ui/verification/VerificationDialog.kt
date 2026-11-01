package com.example.ui.verification

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.tr
import com.example.util.CloudinaryProfileUploader
import kotlinx.coroutines.launch

@Composable
fun VerificationDialog(
    currentDocType: String,
    currentIdNumber: String = "",
    currentDocUri: String = "",
    currentVerificationStatus: String = "UNSUBMITTED",
    onDismiss: () -> Unit,
    onSubmitVerification: (docType: String, idNumber: String, docUri: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val docOptions = listOf(
        "Smart NID Card (জাতীয় পরিচয়পত্র)",
        "Bangladesh Passport",
        "Dual Citizenship / Foreign Passport",
        "BCS / BMDC / Bar Council Professional ID"
    )

    var selectedDoc by remember {
        mutableStateOf(
            if (currentDocType.isNotBlank() && docOptions.contains(currentDocType)) currentDocType
            else "Smart NID Card (জাতীয় পরিচয়পত্র)"
        )
    }
    var idNumber by remember { mutableStateOf(currentIdNumber) }
    var uploadedDocUri by remember { mutableStateOf(currentDocUri) }
    var uploadedFileName by remember {
        mutableStateOf(
            if (currentDocUri.isNotBlank()) "Verification_Document_Scan.jpg" else ""
        )
    }
    var isUploadingDoc by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun resolveFileName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            } ?: uri.lastPathSegment ?: "document_upload.jpg"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "document_upload.jpg"
        }
    }

    fun handleSelectedUri(uri: Uri) {
        val fileName = resolveFileName(uri)
        uploadedFileName = fileName
        validationError = null
        if (fileName.endsWith(".pdf", ignoreCase = true)) {
            uploadedDocUri = uri.toString()
        } else {
            isUploadingDoc = true
            coroutineScope.launch {
                val uploadRes = CloudinaryProfileUploader.compressAndUploadToCloudinary(
                    context = context,
                    imageUri = uri
                )
                isUploadingDoc = false
                uploadedDocUri = uploadRes.getOrElse { uri.toString() }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedUri(uri)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedUri(uri)
        }
    }

    val errMissingId = tr(
        "Please enter your Document / NID Number.",
        "অনুগ্রহ করে আপনার ডকুমেন্ট / এনআইডি নম্বর লিখুন।"
    )
    val errMissingDoc = tr(
        "Please upload a photo or scan of your verification document.",
        "অনুগ্রহ করে আপনার ভেরিফিকেশন ডকুমেন্টের ছবি বা ফাইল আপলোড করুন।"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tr("Identity Verification Badge", "ভেরিফিকেশন ব্যাজ"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (currentVerificationStatus.equals("PENDING", ignoreCase = true)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("verification_pending_status_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = tr("Under Admin Review", "প্রশাসকের যাচাইয়ের অপেক্ষায়"),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = tr(
                                        "Your document has been submitted. Once approved by the Ghotok Admin team, your Verified Badge will activate automatically.",
                                        "আপনার ডকুমেন্ট জমা দেওয়া হয়েছে। ঘটক অ্যাডমিন টিম যাচাই করার সাথে সাথে আপনার ভেরিফাইড ব্যাজ সক্রিয় হয়ে যাবে।"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = tr(
                        "A Verified Badge gives 3x more proposal responses and assures families of genuine background & marital credentials.",
                        "ভেরিফাইড ব্যাজ থাকলে ৩ গুণ বেশি বিবাহের প্রস্তাব পাওয়া যায় এবং প্রকৃত পারিবারিক তথ্যের নিশ্চয়তা দেয়।"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    tr("1. Select Document Type:", "১. ডকুমেন্টের ধরন নির্বাচন করুন:"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                docOptions.forEach { doc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedDoc = doc }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDoc == doc,
                            onClick = { selectedDoc = doc }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(doc, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    tr("2. Document / ID Number:", "২. ডকুমেন্ট / এনআইডি নম্বর:"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = {
                        idNumber = it
                        validationError = null
                    },
                    label = { Text(tr("Document / NID Number *", "ডকুমেন্ট / এনআইডি নম্বর *")) },
                    placeholder = { Text("e.g. 1998XXXXXXXXX") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_id_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    tr("3. Upload Document / Image *", "৩. ডকুমেন্ট / ছবি আপলোড করুন *"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (isUploadingDoc) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tr("Uploading document securely...", "নিরাপদে ডকুমেন্ট আপলোড হচ্ছে..."),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (uploadedDocUri.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tr(
                                    "Upload clear photo or PDF scan of your NID / Passport",
                                    "আপনার এনআইডি / পাসপোর্টের স্পষ্ট ছবি বা পিডিএফ আপলোড করুন"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("upload_verification_image_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tr("Upload Image", "ছবি নির্বাচন"),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        documentPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("upload_verification_doc_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tr("Browse File", "ফাইল নির্বাচন"),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("uploaded_doc_preview_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = tr("Document / Image Attached", "ডকুমেন্ট / ছবি সংযুক্ত হয়েছে"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = uploadedFileName.ifBlank { "verification_document.jpg" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        uploadedDocUri = ""
                                        uploadedFileName = ""
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = tr("Remove document", "ডকুমেন্ট সরান"),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (!uploadedFileName.endsWith(".pdf", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AsyncImage(
                                    model = Uri.parse(uploadedDocUri),
                                    contentDescription = tr("Uploaded Verification Document", "আপলোডকৃত ডকুমেন্ট"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = tr(
                        "🔒 Your government document is reviewed only by authorized Ghotok admins and is never shared publicly.",
                        "🔒 আপনার সরকারি ডকুমেন্ট শুধুমাত্র ঘটক অ্যাডমিন টিম দ্বারা যাচাই করা হয় এবং প্রকাশ্যে কাউকে দেখানো হয় না।"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (idNumber.trim().isEmpty()) {
                        validationError = errMissingId
                        return@Button
                    }
                    if (uploadedDocUri.isBlank()) {
                        validationError = errMissingDoc
                        return@Button
                    }
                    onSubmitVerification(selectedDoc, idNumber.trim(), uploadedDocUri)
                    onDismiss()
                },
                enabled = !isUploadingDoc,
                modifier = Modifier.testTag("submit_verification_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(tr("Submit for Verification", "ভেরিফিকেশনের জন্য জমা দিন"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "বাতিল"))
            }
        }
    )
}
