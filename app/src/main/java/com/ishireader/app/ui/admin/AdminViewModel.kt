package com.ishireader.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.AdminUser
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.AdminRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    val pendingDeleteUserId: String? = null
)

private const val MIN_PASSWORD_LENGTH = 8

/** Reimplements AdminPageClient.tsx: user management (list/create/rename/toggle admin/reset
 *  password/unlock/disable/delete) plus the admin-only server-config and login-appearance
 *  settings. Skips the site's 30s active-status polling -- this screen is opened occasionally,
 *  not left running in a background tab. */
class AdminViewModel(private val repository: AdminRepository) : ViewModel() {

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

    fun setLoginAccentColor(hex: String) {
        _uiState.value = _uiState.value.copy(loginAccentColor = hex)
        viewModelScope.launch { repository.setLoginAccentColor(hex) }
    }

    fun setLoginThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(loginThemeMode = mode)
        viewModelScope.launch { repository.setLoginThemeMode(mode) }
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

    class Factory(private val repository: AdminRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(repository) as T
    }
}

private fun <T> ApiResult<T>.dataOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}
