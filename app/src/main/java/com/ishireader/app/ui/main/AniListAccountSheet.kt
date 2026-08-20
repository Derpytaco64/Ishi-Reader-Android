package com.ishireader.app.ui.main

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * "AniList" user-menu entry -- its own sheet, separate from [EditUserSheet], since connecting
 * AniList is a distinct per-user action (see [AniListAccountViewModel]'s doc comment). PIN-flow
 * only: opens AniList's own authorize page in a Custom Tab, the user approves there and lands on
 * AniList's own /oauth/pin page showing a code, then pastes that code back here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniListAccountSheet(
    state: AniListAccountUiState,
    onPinCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val connected = state.user?.anilistConnected == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(text = "AniList", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

            when {
                connected -> {
                    Text("Your AniList account is connected. Link a manga from its tracking sheet to start syncing progress, status, and score.")
                    Button(
                        onClick = onDisconnect,
                        enabled = !state.isDisconnecting,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text(if (state.isDisconnecting) "Disconnecting…" else "Disconnect")
                    }
                }
                state.isLoadingAuthorizeUrl -> {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall)
                }
                state.notConfigured -> {
                    Text(
                        "This server hasn't been set up for AniList sync yet -- ask your admin to add an AniList client ID/secret in Admin Settings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    Text("Connect your AniList account to sync manga progress, status, and score as you read.")
                    OutlinedButton(
                        onClick = {
                            state.authorizeUrl?.let { url ->
                                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Open AniList to Connect")
                    }
                    Text(
                        "After approving, AniList shows you a code on its own page -- paste it below.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = state.pinCode,
                        onValueChange = onPinCodeChange,
                        label = { Text("Code from AniList") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.connectError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Button(
                        onClick = onConnect,
                        enabled = !state.isConnecting && state.pinCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(if (state.isConnecting) "Connecting…" else "Connect")
                    }
                }
            }
        }
    }
}
