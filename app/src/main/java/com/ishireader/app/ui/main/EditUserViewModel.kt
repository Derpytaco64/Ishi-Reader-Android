package com.ishireader.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 8

data class EditUserUiState(
    val user: PublicUser? = null,
    val nameDraft: String = "",
    val isSavingName: Boolean = false,
    val nameSaved: Boolean = false,
    val nameError: String? = null,
    val isUploadingAvatar: Boolean = false,
    val avatarError: String? = null,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSavingPassword: Boolean = false,
    val passwordSaved: Boolean = false,
    val passwordError: String? = null
)

/**
 * Backs the "Edit User" user-menu entry: avatar upload, display-name rename (auto-saves on blur,
 * mirroring the website's commitName), and a change-password form. Mirrors StatefulUserMenu.tsx's
 * edit dialog step-for-step; state lives here, EditUserSheet is purely presentational.
 */
class EditUserViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUserUiState())
    val uiState: StateFlow<EditUserUiState> = _uiState.asStateFlow()

    /** Called each time the sheet opens -- resets any leftover form state from a previous run,
     *  seeded from whatever the top bar already knows about the user (no extra network round trip
     *  needed just to open the dialog). */
    fun start(user: PublicUser?) {
        _uiState.value = EditUserUiState(user = user, nameDraft = user?.name.orEmpty())
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameDraft = name, nameSaved = false) }
    }

    fun commitName() {
        val state = _uiState.value
        val trimmed = state.nameDraft.trim()
        if (trimmed.isEmpty() || trimmed == state.user?.name) return

        _uiState.update { it.copy(isSavingName = true, nameError = null, nameSaved = false) }
        viewModelScope.launch {
            when (val result = authRepository.updateProfile(trimmed)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSavingName = false, nameSaved = true, user = result.data, nameDraft = result.data.name.orEmpty())
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSavingName = false, nameError = result.message) }
            }
        }
    }

    fun uploadAvatar(imageDataUrl: String) {
        _uiState.update { it.copy(isUploadingAvatar = true, avatarError = null) }
        viewModelScope.launch {
            when (val result = authRepository.uploadAvatar(imageDataUrl)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isUploadingAvatar = false, user = it.user?.copy(avatarUrl = result.data))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isUploadingAvatar = false, avatarError = result.message) }
            }
        }
    }

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, passwordSaved = false, passwordError = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordSaved = false, passwordError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, passwordSaved = false, passwordError = null) }
    }

    fun submitPasswordChange() {
        val state = _uiState.value

        val validationError = when {
            state.newPassword.length < MIN_PASSWORD_LENGTH -> "New password must be at least $MIN_PASSWORD_LENGTH characters"
            state.newPassword != state.confirmPassword -> "New passwords don't match"
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(passwordError = validationError) }
            return
        }

        _uiState.update { it.copy(isSavingPassword = true, passwordError = null, passwordSaved = false) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(state.currentPassword, state.newPassword)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isSavingPassword = false,
                        passwordSaved = true,
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSavingPassword = false, passwordError = result.message) }
            }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditUserViewModel(authRepository) as T
    }
}
