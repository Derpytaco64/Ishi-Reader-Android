package com.ishireader.app.ui.main

import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * "Edit User" user-menu entry: avatar upload, display-name rename, change-password form. Mirrors
 * StatefulUserMenu.tsx's edit dialog; state lives in [EditUserViewModel], this is purely
 * presentational aside from the file-picker/base64 plumbing (Compose's own Context concern, same
 * as MainTabsScreen's own notes-export file picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUserSheet(
    state: EditUserUiState,
    avatarBaseUrl: String?,
    onNameChange: (String) -> Unit,
    onCommitName: () -> Unit,
    onPickAvatar: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmitPasswordChange: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dataUrl = runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.let { bytes -> "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}" }
        }.getOrNull()
        if (dataUrl != null) onPickAvatar(dataUrl) else Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
    }

    val avatarUrl = remember(state.user?.avatarUrl, avatarBaseUrl) {
        state.user?.avatarUrl?.let { path -> avatarBaseUrl?.let { it + path } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(text = "Edit User", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = state.user?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = state.user?.name?.take(1)?.uppercase().orEmpty(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                OutlinedButton(
                    onClick = { pickImage.launch("image/*") },
                    enabled = !state.isUploadingAvatar,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (state.isUploadingAvatar) "Uploading…" else "Change Picture")
                }
                state.avatarError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 20.dp)) {
                OutlinedTextField(
                    value = state.nameDraft,
                    onValueChange = onNameChange,
                    label = { Text("Display name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onCommitName() }
                )
                when {
                    state.isSavingName -> Text("Saving…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    state.nameError != null -> Text(
                        text = state.nameError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    state.nameSaved -> Text("Saved", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Change Password", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

            OutlinedTextField(
                value = state.currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = { Text("Current password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = state.newPassword,
                onValueChange = onNewPasswordChange,
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            state.passwordError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (state.passwordSaved) {
                Text("Password updated", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(onClick = onSubmitPasswordChange, enabled = !state.isSavingPassword, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isSavingPassword) "Updating…" else "Update Password")
            }
        }
    }
}
