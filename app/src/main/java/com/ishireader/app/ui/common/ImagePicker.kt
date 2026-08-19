package com.ishireader.app.ui.common

import android.content.Context
import android.net.Uri
import android.util.Base64

/** Reads a picked image URI into a "data:<mime>;base64,<bytes>" string -- the format both the
 *  self-service (api/auth/avatar) and admin-scoped (api/admin/users/{id}/avatar) upload endpoints
 *  expect, see AvatarUploadRequest. Shared by EditUserSheet's own avatar picker and the admin
 *  panel's per-user one. */
fun readImageAsDataUrl(context: Context, uri: Uri): String? = runCatching {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?.let { bytes -> "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}" }
}.getOrNull()
