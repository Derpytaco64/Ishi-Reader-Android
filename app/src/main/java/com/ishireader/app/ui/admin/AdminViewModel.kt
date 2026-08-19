package com.ishireader.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.AdminUser
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.OrphanedDataReport
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.repository.AdminRepository
import com.ishireader.app.data.repository.LibraryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Config-field keys used for [AdminUiState.savingField]/fieldErrors/fieldSaved -- one per
 *  server-config text setting on the admin page (mirrors the site's 4 separate useXxx hooks,
 *  just without a dedicated class per field). */
object ConfigField {
    const val BOOK_FOLDER = "bookFolder"
    const val READIUM_URL = "readiumUrl"
    const val READIUM_PORT = "readiumPort"
    const val USER_DATA_FOLDER = "userDataFolder"
}

data class AdminUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,

    val users: List<AdminUser> = emptyList(),
    val actionError: String? = null,

    val bookFolder: String = "",
    val readiumUrl: String = "",
    val readiumPort: String = "",
    val userDataFolder: String = "",
    val loginAccentColor: String = "#2f6fed",
    val loginThemeMode: String = "dark",
    val appearanceError: String? = null,

    val savingField: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val fieldSaved: Set<String> = emptySet(),

    val newUsername: String = "",
    val newName: String = "",
    val newPassword: String = "",
    val newIsAdmin: Boolean = false,
    val createError: String? = null,
    val isCreating: Boolean = false,

    val editingUserId: String? = null,
    val editUsername: String = "",
    val editName: String = "",

    val resetPasswordUserId: String? = null,
    val resetPasswordValue: String = "",

    val pendingDeleteUserId: String? = null,

    /** Non-null while that user's avatar upload is in flight -- drives the "Uploading…" state on
     *  their row's avatar button. */
    val avatarUploadingUserId: String? = null,
    /** Bumped per-user on a successful avatar upload and appended to that user's avatar URL as a
     *  cache-busting query param (see AdminUserAvatar) -- the URL itself never changes when a user
     *  re-uploads, so without this Coil would keep showing the old cached image. */
    val avatarUpdatedAt: Map<String, Long> = emptyMap(),

    val orphanedReport: OrphanedDataReport? = null,
    val isScanningOrphaned: Boolean = false,
    val isDeletingOrphaned: Boolean = false,
    val orphanedError: String? = null,
    val deletedOrphanedReport: OrphanedDataReport? = null,
    val pendingDeleteOrphaned: Boolean = false,

    /** Non-null while a given orphaned row's destination-book picker is expanded -- only one row
     *  can be open at a time. */
    val migrateOrphanTarget: OrphanMigrateTarget? = null,
    /** Null until the picker is first opened (lazy-loaded, then shared across every row for the
     *  rest of this screen's lifetime) -- distinct from an empty list, which means "loaded, no
     *  books in the library." */
    val migrateLibraryBooks: List<Book>? = null,
    /** Book picked from [migrateLibraryBooks], awaiting the overwrite confirmation dialog. */
    val pendingMigrateOrphanDest: Book? = null,
    val isMigratingOrphan: Boolean = false,
    val migrateOrphanError: String? = null,

    val isClearingSpeedSamples: Boolean = false,
    val speedSamplesError: String? = null,
    val clearedSpeedSamplesCount: Int? = null,
    val pendingClearSpeedSamples: Boolean = false
)

private const val MIN_PASSWORD_LENGTH = 8

/** Identifies which orphaned book row's "Migrate to a live book" picker is currently open --
 *  [userId]/[hash] are what the eventual migrate call needs, [title] is only for the confirm
 *  dialog's copy. */
data class OrphanMigrateTarget(val userId: String, val hash: String, val title: String?)

/** Reimplements AdminPageClient.tsx: user management (list/create/rename/toggle admin/reset
 *  password/unlock/disable/delete) plus the admin-only server-config and login-appearance
 *  settings. Skips the site's 30s active-status polling -- this screen is opened occasionally,
 *  not left running in a background tab. */
class AdminViewModel(
    private val repository: AdminRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
        viewModelScope.launch {
            coroutineScope {
                val usersDeferred = async { repository.listUsers() }
                val bookFolderDeferred = async { repository.getBookFolder() }
                val readiumUrlDeferred = async { repository.getReadiumUrl() }
                val readiumPortDeferred = async { repository.getReadiumPort() }
                val userDataFolderDeferred = async { repository.getUserDataFolder() }
                val accentColorDeferred = async { repository.getLoginAccentColor() }
                val themeModeDeferred = async { repository.getLoginThemeMode() }

                val usersResult = usersDeferred.await()
                if (usersResult is ApiResult.Failure) {
                    _uiState.value = _uiState.value.copy(isLoading = false, loadError = usersResult.message)
                    return@coroutineScope
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    users = (usersResult as ApiResult.Success).data,
                    bookFolder = bookFolderDeferred.await().dataOrNull() ?: _uiState.value.bookFolder,
                    readiumUrl = readiumUrlDeferred.await().dataOrNull() ?: _uiState.value.readiumUrl,
                    readiumPort = readiumPortDeferred.await().dataOrNull()?.toString() ?: _uiState.value.readiumPort,
                    userDataFolder = userDataFolderDeferred.await().dataOrNull() ?: _uiState.value.userDataFolder,
                    loginAccentColor = accentColorDeferred.await().dataOrNull() ?: _uiState.value.loginAccentColor,
                    loginThemeMode = themeModeDeferred.await().dataOrNull() ?: _uiState.value.loginThemeMode
                )
            }
        }
    }

    private fun refreshUsers() {
        viewModelScope.launch {
            when (val result = repository.listUsers()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(users = result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    // --- Server config text fields --------------------------------------------------------

    fun commitBookFolder(value: String) = saveConfigField(ConfigField.BOOK_FOLDER, value, repository::setBookFolder) {
        it.copy(bookFolder = value)
    }

    fun commitReadiumUrl(value: String) = saveConfigField(ConfigField.READIUM_URL, value, repository::setReadiumUrl) {
        it.copy(readiumUrl = value)
    }

    fun commitReadiumPort(value: String) = saveConfigField(ConfigField.READIUM_PORT, value, { port ->
        when (val result = repository.setReadiumPort(port)) {
            is ApiResult.Success -> ApiResult.Success(result.data.toString())
            is ApiResult.Failure -> result
        }
    }) { it.copy(readiumPort = value) }

    fun commitUserDataFolder(value: String) = saveConfigField(ConfigField.USER_DATA_FOLDER, value, repository::setUserDataFolder) {
        it.copy(userDataFolder = value)
    }

    private fun saveConfigField(
        key: String,
        value: String,
        save: suspend (String) -> ApiResult<String>,
        apply: (AdminUiState) -> AdminUiState
    ) {
        _uiState.value = _uiState.value.copy(
            savingField = key,
            fieldErrors = _uiState.value.fieldErrors - key,
            fieldSaved = _uiState.value.fieldSaved - key
        )
        viewModelScope.launch {
            when (val result = save(value)) {
                is ApiResult.Success -> _uiState.value = apply(_uiState.value).copy(
                    savingField = null,
                    fieldSaved = _uiState.value.fieldSaved + key
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    savingField = null,
                    fieldErrors = _uiState.value.fieldErrors + (key to result.message)
                )
            }
        }
    }

    // --- Appearance (recolors/retheme's the site's own /login + admin panel, not this app) ----

    private var accentColorJob: Job? = null
    private var themeModeJob: Job? = null

    fun setLoginAccentColor(hex: String) {
        val previous = _uiState.value.loginAccentColor
        _uiState.value = _uiState.value.copy(loginAccentColor = hex, appearanceError = null)
        // CLAUDE-ADDED: ColorWheelPicker calls this on every drag-move event, so a stale in-flight
        // save from an earlier drag position must be cancelled -- otherwise it can complete *after*
        // this one and overwrite the server with a color the user already dragged past.
        accentColorJob?.cancel()
        accentColorJob = viewModelScope.launch {
            // CLAUDE-ADDED: The old fire-and-forget version discarded this result entirely, so a
            // failed save (expired session, network blip) would leave the switch/picker showing the
            // new value indefinitely -- only reverting silently the next time something reloaded the
            // real server value (e.g. the login screen), looking like the setting "didn't stick".
            val result = repository.setLoginAccentColor(hex)
            if (result is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    loginAccentColor = previous,
                    appearanceError = result.message
                )
            }
        }
    }

    fun setLoginThemeMode(mode: String) {
        val previous = _uiState.value.loginThemeMode
        _uiState.value = _uiState.value.copy(loginThemeMode = mode, appearanceError = null)
        themeModeJob?.cancel()
        themeModeJob = viewModelScope.launch {
            val result = repository.setLoginThemeMode(mode)
            if (result is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    loginThemeMode = previous,
                    appearanceError = result.message
                )
            }
        }
    }

    // --- Add user ---------------------------------------------------------------------------

    fun onNewUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(newUsername = value)
    }

    fun onNewNameChange(value: String) {
        _uiState.value = _uiState.value.copy(newName = value)
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value)
    }

    fun onNewIsAdminChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(newIsAdmin = value)
    }

    fun submitCreateUser() {
        val state = _uiState.value
        if (state.newPassword.isNotEmpty() && state.newPassword.length < MIN_PASSWORD_LENGTH) {
            _uiState.value = state.copy(createError = "Password must be at least $MIN_PASSWORD_LENGTH characters")
            return
        }
        if (state.newIsAdmin && state.newPassword.isEmpty()) {
            _uiState.value = state.copy(createError = "Admin accounts require a password")
            return
        }

        _uiState.value = state.copy(isCreating = true, createError = null)
        viewModelScope.launch {
            val result = repository.createUser(state.newUsername, state.newName.ifBlank { state.newUsername }, state.newPassword, state.newIsAdmin)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        newUsername = "",
                        newName = "",
                        newPassword = "",
                        newIsAdmin = false
                    )
                    refreshUsers()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isCreating = false, createError = result.message)
            }
        }
    }

    // --- Per-user actions ---------------------------------------------------------------------

    fun startEditingUser(user: AdminUser) {
        _uiState.value = _uiState.value.copy(
            editingUserId = user.id,
            editUsername = user.username,
            editName = user.name,
            actionError = null
        )
    }

    fun cancelEditingUser() {
        _uiState.value = _uiState.value.copy(editingUserId = null)
    }

    fun onEditUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(editUsername = value)
    }

    fun onEditNameChange(value: String) {
        _uiState.value = _uiState.value.copy(editName = value)
    }

    fun saveEdit() {
        val id = _uiState.value.editingUserId ?: return
        val username = _uiState.value.editUsername
        val name = _uiState.value.editName
        _uiState.value = _uiState.value.copy(actionError = null)
        viewModelScope.launch {
            when (val result = repository.renameUser(id, username, name)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(editingUserId = null)
                    refreshUsers()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    fun toggleAdmin(user: AdminUser) {
        _uiState.value = _uiState.value.copy(actionError = null)
        viewModelScope.launch {
            when (val result = repository.setAdmin(user.id, !user.isAdmin)) {
                is ApiResult.Success -> refreshUsers()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    fun toggleDisabled(user: AdminUser) {
        _uiState.value = _uiState.value.copy(actionError = null)
        viewModelScope.launch {
            when (val result = repository.setDisabled(user.id, !user.disabled)) {
                is ApiResult.Success -> refreshUsers()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    fun startResetPassword(userId: String) {
        _uiState.value = _uiState.value.copy(resetPasswordUserId = userId, resetPasswordValue = "", actionError = null)
    }

    fun cancelResetPassword() {
        _uiState.value = _uiState.value.copy(resetPasswordUserId = null, resetPasswordValue = "")
    }

    fun onResetPasswordValueChange(value: String) {
        _uiState.value = _uiState.value.copy(resetPasswordValue = value)
    }

    fun submitResetPassword() {
        val id = _uiState.value.resetPasswordUserId ?: return
        val password = _uiState.value.resetPasswordValue
        if (password.length < MIN_PASSWORD_LENGTH) {
            _uiState.value = _uiState.value.copy(actionError = "Password must be at least $MIN_PASSWORD_LENGTH characters")
            return
        }
        viewModelScope.launch {
            when (val result = repository.resetPassword(id, password)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(resetPasswordUserId = null, resetPasswordValue = "", actionError = null)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    fun unlockUser(userId: String) {
        _uiState.value = _uiState.value.copy(actionError = null)
        viewModelScope.launch {
            when (val result = repository.unlockUser(userId)) {
                is ApiResult.Success -> refreshUsers()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    fun uploadAvatar(userId: String, imageDataUrl: String) {
        _uiState.value = _uiState.value.copy(avatarUploadingUserId = userId, actionError = null)
        viewModelScope.launch {
            when (val result = repository.uploadUserAvatar(userId, imageDataUrl)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        avatarUploadingUserId = null,
                        avatarUpdatedAt = _uiState.value.avatarUpdatedAt + (userId to System.currentTimeMillis())
                    )
                    refreshUsers()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(avatarUploadingUserId = null, actionError = result.message)
            }
        }
    }

    fun requestDeleteUser(userId: String) {
        _uiState.value = _uiState.value.copy(pendingDeleteUserId = userId)
    }

    fun cancelDeleteUser() {
        _uiState.value = _uiState.value.copy(pendingDeleteUserId = null)
    }

    fun confirmDeleteUser() {
        val id = _uiState.value.pendingDeleteUserId ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteUserId = null, actionError = null)
        viewModelScope.launch {
            when (val result = repository.deleteUser(id)) {
                is ApiResult.Success -> refreshUsers()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(actionError = result.message)
            }
        }
    }

    // --- Orphaned data cleanup ----------------------------------------------------------------

    fun scanOrphanedData() {
        _uiState.value = _uiState.value.copy(isScanningOrphaned = true, orphanedError = null, deletedOrphanedReport = null)
        viewModelScope.launch {
            when (val result = repository.scanOrphanedData()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isScanningOrphaned = false, orphanedReport = result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isScanningOrphaned = false, orphanedError = result.message)
            }
        }
    }

    fun requestDeleteOrphanedData() {
        _uiState.value = _uiState.value.copy(pendingDeleteOrphaned = true)
    }

    fun cancelDeleteOrphanedData() {
        _uiState.value = _uiState.value.copy(pendingDeleteOrphaned = false)
    }

    fun confirmDeleteOrphanedData() {
        _uiState.value = _uiState.value.copy(pendingDeleteOrphaned = false, isDeletingOrphaned = true, orphanedError = null)
        viewModelScope.launch {
            when (val result = repository.deleteOrphanedData()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isDeletingOrphaned = false,
                    deletedOrphanedReport = result.data,
                    orphanedReport = null
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isDeletingOrphaned = false, orphanedError = result.message)
            }
        }
    }

    // --- Orphaned data migration (recover-instead-of-delete) -----------------------------------

    /** Opens the destination-book picker for one orphaned row -- lazy-loads the library on first
     *  use, then reuses it for every subsequent row this screen instance opens. */
    fun openMigrateOrphan(userId: String, hash: String, title: String?) {
        _uiState.value = _uiState.value.copy(
            migrateOrphanTarget = OrphanMigrateTarget(userId, hash, title),
            pendingMigrateOrphanDest = null,
            migrateOrphanError = null
        )
        if (_uiState.value.migrateLibraryBooks == null) {
            viewModelScope.launch {
                when (val result = libraryRepository.fetchBooks()) {
                    is ApiResult.Success -> _uiState.value = _uiState.value.copy(migrateLibraryBooks = result.data)
                    is ApiResult.Failure -> _uiState.value = _uiState.value.copy(migrateLibraryBooks = emptyList())
                }
            }
        }
    }

    fun closeMigrateOrphan() {
        _uiState.value = _uiState.value.copy(
            migrateOrphanTarget = null,
            pendingMigrateOrphanDest = null,
            migrateOrphanError = null
        )
    }

    fun pickMigrateOrphanDest(book: Book) {
        _uiState.value = _uiState.value.copy(pendingMigrateOrphanDest = book)
    }

    fun cancelMigrateOrphanDest() {
        _uiState.value = _uiState.value.copy(pendingMigrateOrphanDest = null)
    }

    fun confirmMigrateOrphan() {
        val target = _uiState.value.migrateOrphanTarget ?: return
        val dest = _uiState.value.pendingMigrateOrphanDest ?: return

        _uiState.value = _uiState.value.copy(isMigratingOrphan = true, migrateOrphanError = null)
        viewModelScope.launch {
            when (val result = repository.migrateOrphanedData(target.userId, target.hash, dest.manifestUrl())) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isMigratingOrphan = false,
                        migrateOrphanTarget = null,
                        pendingMigrateOrphanDest = null
                    )
                    scanOrphanedData()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isMigratingOrphan = false,
                    pendingMigrateOrphanDest = null,
                    migrateOrphanError = result.message
                )
            }
        }
    }

    // --- Reading speed samples -----------------------------------------------------------------

    fun requestClearSpeedSamples() {
        _uiState.value = _uiState.value.copy(pendingClearSpeedSamples = true)
    }

    fun cancelClearSpeedSamples() {
        _uiState.value = _uiState.value.copy(pendingClearSpeedSamples = false)
    }

    fun confirmClearSpeedSamples() {
        _uiState.value = _uiState.value.copy(
            pendingClearSpeedSamples = false,
            isClearingSpeedSamples = true,
            speedSamplesError = null,
            clearedSpeedSamplesCount = null
        )
        viewModelScope.launch {
            when (val result = repository.clearReadingSpeedSamples()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isClearingSpeedSamples = false, clearedSpeedSamplesCount = result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isClearingSpeedSamples = false, speedSamplesError = result.message)
            }
        }
    }

    class Factory(
        private val repository: AdminRepository,
        private val libraryRepository: LibraryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(repository, libraryRepository) as T
    }
}
