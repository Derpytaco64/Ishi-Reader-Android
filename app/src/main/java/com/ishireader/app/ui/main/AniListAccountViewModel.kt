package com.ishireader.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.AniListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AniListAccountUiState(
    val user: PublicUser? = null,
    val authorizeUrl: String? = null,
    val isLoadingAuthorizeUrl: Boolean = false,
    val notConfigured: Boolean = false,
    val pinCode: String = "",
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val isDisconnecting: Boolean = false
)

/**
 * Backs the "AniList" user-menu entry -- a dedicated sheet, separate from "Edit User" (see
 * MainTabsScreen's dropdown), since connecting AniList is a per-user action independent of the
 * account-identity fields EditUserSheet handles. Fetches the PIN-flow authorize URL fresh each time
 * the sheet opens (it depends on the instance's admin-configured client_id, which this app never
 * hardcodes -- see /api/auth/anilist/authorize-url).
 */
class AniListAccountViewModel(private val aniListRepository: AniListRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AniListAccountUiState())
    val uiState: StateFlow<AniListAccountUiState> = _uiState.asStateFlow()

    fun start(user: PublicUser?) {
        _uiState.value = AniListAccountUiState(user = user, isLoadingAuthorizeUrl = true)
        viewModelScope.launch {
            when (val result = aniListRepository.getAuthorizeUrl()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingAuthorizeUrl = false, authorizeUrl = result.data, notConfigured = result.data == null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingAuthorizeUrl = false, connectError = result.message)
                }
            }
        }
    }

    fun onPinCodeChange(value: String) {
        _uiState.update { it.copy(pinCode = value, connectError = null) }
    }

    fun connect() {
        val code = _uiState.value.pinCode.trim()
        if (code.isEmpty()) return

        _uiState.update { it.copy(isConnecting = true, connectError = null) }
        viewModelScope.launch {
            when (val result = aniListRepository.connect(code)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isConnecting = false, pinCode = "", user = it.user?.copy(anilistConnected = true))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isConnecting = false, connectError = result.message) }
            }
        }
    }

    fun disconnect() {
        _uiState.update { it.copy(isDisconnecting = true, connectError = null) }
        viewModelScope.launch {
            when (val result = aniListRepository.disconnect()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isDisconnecting = false, user = it.user?.copy(anilistConnected = false))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isDisconnecting = false, connectError = result.message) }
            }
        }
    }

    class Factory(private val aniListRepository: AniListRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AniListAccountViewModel(aniListRepository) as T
    }
}
