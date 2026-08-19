package com.ishireader.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ishireader.app.data.model.GithubRelease

@Composable
fun UpdateAvailableDialog(release: GithubRelease, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = { Text("Ishi Reader ${release.tagName} is available on GitHub. You're on an older version.") },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("View on GitHub") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
