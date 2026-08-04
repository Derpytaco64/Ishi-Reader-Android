package com.ishireader.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ishireader.app.R
import com.ishireader.app.data.model.PublicUser

/** Reimplements LoginPageClient.tsx's Jellyfin-style flow (pick a profile, then its password)
 *  as a mobile screen, with one addition the site doesn't need: a server-entry stage up front,
 *  since this app (unlike the site) isn't fixed to a single server. */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(CircleShape)
            )

            when (state.stage) {
                LoginStage.SERVER -> ServerStage(state, viewModel)
                LoginStage.PICK -> PickStage(state, viewModel)
                LoginStage.PASSWORD -> PasswordStage(state, viewModel)
                LoginStage.SETUP -> SetupStage(state, viewModel)
            }
        }
    }
}

@Composable
private fun ServerStage(state: LoginUiState, viewModel: LoginViewModel) {
    Text(text = "Connect to your server", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Sign in to your self-hosted Ishi-Read server",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = { Text("Server URL (e.g. https://reader.example.com)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
    )

    if (state.connectError != null) {
        Text(text = state.connectError, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = viewModel::connect,
        enabled = !state.isConnecting,
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
    ) {
        if (state.isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(2.dp))
        } else {
            Text("Continue")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickStage(state: LoginUiState, viewModel: LoginViewModel) {
    Text(text = "Who's reading?", style = MaterialTheme.typography.headlineSmall)

    if (state.users.isEmpty()) {
        Text(
            text = "No accounts yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.users.forEach { user ->
                UserTile(user = user, baseUrl = state.baseUrl, onClick = { viewModel.selectUser(user) })
            }
        }
    }

    TextButton(onClick = viewModel::changeServer) {
        Text("Change server")
    }
}

@Composable
private fun PasswordStage(state: LoginUiState, viewModel: LoginViewModel) {
    val user = state.selectedUser ?: return

    AvatarCircle(user = user, baseUrl = state.baseUrl, size = 96.dp)
    Text(text = user.name ?: user.username, style = MaterialTheme.typography.headlineSmall)

    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Password (leave blank if none set)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
    )

    if (state.error != null) {
        val lockedSuffix = if (state.lockedUntil != null) " Try again later." else ""
        Text(text = state.error + lockedSuffix, color = MaterialTheme.colorScheme.error)
    }

    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = viewModel::backToPicker, modifier = Modifier.weight(1f)) {
            Text("Back")
        }
        Button(
            onClick = viewModel::submitLogin,
            enabled = !state.isSubmitting,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (state.isSubmitting) "Signing in…" else "Sign In")
        }
    }
}

@Composable
private fun SetupStage(state: LoginUiState, viewModel: LoginViewModel) {
    val user = state.selectedUser ?: return

    AvatarCircle(user = user, baseUrl = state.baseUrl, size = 96.dp)
    Text(text = "Set a password for ${user.name ?: user.username}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        text = "This account doesn't have one yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("New password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
    )
    OutlinedTextField(
        value = state.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = { Text("Confirm password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
    )

    if (state.error != null) {
        Text(text = state.error, color = MaterialTheme.colorScheme.error)
    }

    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = viewModel::backToPicker, modifier = Modifier.weight(1f)) {
            Text("Back")
        }
        Button(
            onClick = viewModel::submitSetup,
            enabled = !state.isSubmitting,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (state.isSubmitting) "Saving…" else "Set Password & Sign In")
        }
    }
}

@Composable
private fun UserTile(user: PublicUser, baseUrl: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AvatarCircle(user = user, baseUrl = baseUrl, size = 72.dp)
        Text(
            text = user.name ?: user.username,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Mirrors the site's AvatarCircle: the account's uploaded avatar if it has one, otherwise a
 *  circle with its name's first letter (avatarUrl is server-relative, so it needs [baseUrl]
 *  prepended before Coil can load it). */
@Composable
private fun AvatarCircle(user: PublicUser, baseUrl: String?, size: Dp) {
    val avatarUrl = user.avatarUrl?.let { path -> baseUrl?.let { it + path } }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = (user.name ?: user.username).take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
