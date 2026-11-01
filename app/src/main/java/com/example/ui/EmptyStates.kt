package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.tr

@Composable
fun CandidateEmptyState(
    onOpenFilters: () -> Unit,
    onRetry: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateIcon(Icons.Filled.TouchApp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = tr(
                "No profiles match your filters",
                "আপনার ফিল্টারের সঙ্গে মেলে এমন কোনো প্রোফাইল নেই"
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 32.dp),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = tr(
                "Try changing your filters or search again.",
                "ফিল্টার পরিবর্তন করে আবার খুঁজে দেখুন।"
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 40.dp),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = onRetry) {
                Text(tr("Retry", "আবার চেষ্টা করুন"))
            }
            Button(onClick = onOpenFilters) {
                Text(tr("Open filters", "ফিল্টার খুলুন"))
            }
        }
    }
}

@Composable
fun ChatEmptyState(onStartChat: () -> Unit) {
    EmptyStateContent(
        icon = Icons.Filled.Message,
        title = tr("No messages yet", "এখনো কোনো বার্তা নেই"),
        subtitle = tr("Start a conversation to see your messages here.", "এখানে বার্তা দেখতে কথোপকথন শুরু করুন।"),
        actionLabel = tr("Start a new chat", "নতুন চ্যাট শুরু করুন"),
        onAction = onStartChat
    )
}

@Composable
fun SavedProfilesEmptyState(onNavigateToPackages: () -> Unit) {
    EmptyStateContent(
        icon = Icons.Filled.Star,
        title = tr("No saved profiles", "কোনো সংরক্ষিত প্রোফাইল নেই"),
        subtitle = tr("Profiles you save will appear here.", "আপনার সংরক্ষিত প্রোফাইলগুলো এখানে দেখা যাবে।"),
        actionLabel = tr("Browse packages", "প্যাকেজ দেখুন"),
        onAction = onNavigateToPackages
    )
}

@Composable
fun ErrorView(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit,
    icon: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, modifier = Modifier.width(120.dp)) {
            Text(tr("Retry", "আবার চেষ্টা করুন"))
        }
    }
}

@Composable
private fun EmptyStateIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyStateContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateIcon(icon)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onAction, modifier = Modifier.width(200.dp)) {
            Text(actionLabel)
        }
    }
}
