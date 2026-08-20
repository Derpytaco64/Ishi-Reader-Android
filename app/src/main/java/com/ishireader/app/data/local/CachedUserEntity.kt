package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row cache of the last successful /api/auth/me fetch, so the top bar's name/avatar
 * survive a server-unreachable resume instead of silently falling back to the initial-letter
 * placeholder (see TopBarViewModel via AuthRepository.fetchCurrentUser). The avatar image itself
 * isn't cached here -- Coil's ImageLoader already disk-caches whatever it last loaded from
 * avatarUrl, so keeping the URL around is enough to let it serve from that cache offline.
 */
@Entity(tableName = "cached_user")
data class CachedUserEntity(
    @PrimaryKey val id: Int = 0,
    val userId: String,
    val username: String,
    val name: String?,
    val isAdmin: Boolean,
    val avatarUrl: String?,
    val needsPasswordSetup: Boolean,
    val anilistConnected: Boolean = false,
    val anilistScoreFormat: String? = null
)
