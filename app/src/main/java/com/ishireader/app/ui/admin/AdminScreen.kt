package com.ishireader.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.data.model.AdminUser
import com.ishireader.app.ui.settings.ColorWheelPicker
import com.ishireader.app.ui.settings.parseAccentColor
import com.ishireader.app.ui.settings.toHex

/** Reimplements AdminPageClient.tsx as a mobile screen -- Appearance, the four server-config
 *  fields, Add User, and the user management list (edit/toggle admin/reset password/unlock/
 *  disable/delete), all against the same admin and settings endpoints the site uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    currentUserId: String?,
    avatarBaseUrl: String?,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.loadError != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = viewModel::loadAll) { Text("Retry") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { AppearanceSection(state = state, viewModel = viewModel) }

                    item {
                        SectionCard("Book Folder") {
                            ConfigTextField(
                                label = "Folder to scan for books",
                                value = state.bookFolder,
                                isSaving = state.savingField == ConfigField.BOOK_FOLDER,
                                error = state.fieldErrors[ConfigField.BOOK_FOLDER],
                                saved = ConfigField.BOOK_FOLDER in state.fieldSaved,
                                onCommit = viewModel::commitBookFolder
                            )
                        }
                    }

                    item {
                        SectionCard("Readium URL") {
                            ConfigTextField(
                                label = "Readium Web Publication Server URL",
                                value = state.readiumUrl,
                                isSaving = state.savingField == ConfigField.READIUM_URL,
                                error = state.fieldErrors[ConfigField.READIUM_URL],
                                saved = ConfigField.READIUM_URL in state.fieldSaved,
                                onCommit = viewModel::commitReadiumUrl
                            )
                        }
                    }

                    item {
                        SectionCard("Server Port") {
                            ConfigTextField(
                                label = "Readium server listening port",
                                value = state.readiumPort,
                                isSaving = state.savingField == ConfigField.READIUM_PORT,
                                error = state.fieldErrors[ConfigField.READIUM_PORT],
                                saved = ConfigField.READIUM_PORT in state.fieldSaved,
                                keyboardType = KeyboardType.Number,
                                onCommit = viewModel::commitReadiumPort
                            )
                        }
                    }

                    item {
                        SectionCard("User Data Folder") {
                            ConfigTextField(
                                label = "Folder for reading progress, annotations, and other saved data",
                                value = state.userDataFolder,
                                isSaving = state.savingField == ConfigField.USER_DATA_FOLDER,
                                error = state.fieldErrors[ConfigField.USER_DATA_FOLDER],
                                saved = ConfigField.USER_DATA_FOLDER in state.fieldSaved,
                                onCommit = viewModel::commitUserDataFolder
                            )
                        }
                    }

                    item { AddUserSection(state = state, viewModel = viewModel) }

                    item { Text("Users", style = MaterialTheme.typography.titleMedium) }

                    state.actionError?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }

                    items(state.users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            isCurrentUser = user.id == currentUserId,
                            state = state,
                            viewModel = viewModel,
                            avatarBaseUrl = avatarBaseUrl
                        )
                    }
                }
            }
        }
    }

    if (state.pendingDeleteUserId != null) {
        val pendingUser = state.users.find { it.id == state.pendingDeleteUserId }
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteUser,
            title = { Text("Delete user?") },
            text = { Text("Delete \"${pendingUser?.name.orEmpty()}\"? This can't be undone.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteUser) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteUser) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AppearanceSection(state: AdminUiState, viewModel: AdminViewModel) {
    SectionCard("Appearance") {
        Text("Login & admin panel accent color", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        ColorWheelPicker(
            color = parseAccentColor(state.loginAccentColor) ?: Color(0xFF2F6FED),
            onColorChange = { viewModel.setLoginAccentColor(it.toHex()) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (state.loginThemeMode == "dark") "Dark mode" else "Light mode")
            Switch(
                checked = state.loginThemeMode == "dark",
                onCheckedChange = { viewModel.setLoginThemeMode(if (it) "dark" else "light") }
            )
        }
    }
}

/** Draft is a separate local state from [value] (only overwritten when [value] itself changes,
 *  e.g. after a successful save) so typing doesn't fire [onCommit] on every keystroke -- committed
 *  on focus loss instead, mirroring the site's onBlur-commit text settings. */
@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    isSaving: Boolean,
    error: String?,
    saved: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onCommit: (String) -> Unit
) {
    var draft by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> if (!focusState.isFocused && draft != value) onCommit(draft) }
    )
    when {
        isSaving -> Text("Saving…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        saved -> Text("Saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddUserSection(state: AdminUiState, viewModel: AdminViewModel) {
    SectionCard("Add User") {
        OutlinedTextField(
            value = state.newUsername,
            onValueChange = viewModel::onNewUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newName,
            onValueChange = viewModel::onNewNameChange,
            label = { Text("Display name") },
            placeholder = { Text(state.newUsername.ifBlank { "Optional -- defaults to username" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newPassword,
            onValueChange = viewModel::onNewPasswordChange,
            label = { Text(if (state.newIsAdmin) "Password" else "Password (optional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Admin")
            Switch(checked = state.newIsAdmin, onCheckedChange = viewModel::onNewIsAdminChange)
        }
        state.createError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = viewModel::submitCreateUser,
            enabled = !state.isCreating && state.newUsername.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isCreating) "Creating…" else "Create User")
        }
    }
}

@Composable
private fun UserRow(
    user: AdminUser,
    isCurrentUser: Boolean,
    state: AdminUiState,
    viewModel: AdminViewModel,
    avatarBaseUrl: String?
) {
    val isLocked = user.lockedUntil != null && user.lockedUntil > System.currentTimeMillis()
    val isEditing = state.editingUserId == user.id
    val isResettingPassword = state.resetPasswordUserId == user.id

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdminUserAvatar(user = user, baseUrl = avatarBaseUrl)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = state.editUsername,
                            onValueChange = viewModel::onEditUsernameChange,
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.editName,
                            onValueChange = viewModel::onEditNameChange,
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(user.name, style = MaterialTheme.typography.titleSmall)
                            if (user.isAdmin) StatusBadge("Admin")
                            if (isLocked) StatusBadge("Locked", danger = true)
                            if (user.disabled) StatusBadge("Disabled", danger = true)
                        }
                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::cancelEditingUser) { Text("Cancel") }
                    Button(onClick = viewModel::saveEdit) { Text("Save") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { viewModel.startEditingUser(user) }) { Text("Edit") }
                    TextButton(onClick = { viewModel.toggleAdmin(user) }, enabled = !(isCurrentUser && user.isAdmin)) {
                        Text(if (user.isAdmin) "Remove Admin" else "Make Admin")
                    }
                    TextButton(onClick = { viewModel.startResetPassword(user.id) }) { Text("Reset Password") }
                    if (isLocked) {
                        TextButton(onClick = { viewModel.unlockUser(user.id) }) { Text("Unlock") }
                    }
                    TextButton(onClick = { viewModel.toggleDisabled(user) }, enabled = !(isCurrentUser && !user.disabled)) {
                        Text(if (user.disabled) "Enable" else "Disable")
                    }
                    TextButton(
                        onClick = { viewModel.requestDeleteUser(user.id) },
                        enabled = !isCurrentUser,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
            }

            if (isResettingPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.resetPasswordValue,
                    onValueChange = viewModel::onResetPasswordValueChange,
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::cancelResetPassword) { Text("Cancel") }
                    Button(onClick = viewModel::submitResetPassword) { Text("Set Password") }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Mirrors AdminPageClient.tsx's avatarUrlFor -- the id+avatarExt path, no cache-busting version
 *  query param (the site's own admin list endpoint doesn't compute one either, unlike PublicUser's
 *  avatarUrl). A small colored dot mirrors the site's isActive status dot. */
@Composable
private fun AdminUserAvatar(user: AdminUser, baseUrl: String?, modifier: Modifier = Modifier) {
    val avatarUrl = user.avatarExt?.let { baseUrl?.let { base -> "$base/api/users/${user.id}/avatar" } }
    Box(modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = user.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(if (user.isActive) Color(0xFF4CAF50) else Color.Gray)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}
