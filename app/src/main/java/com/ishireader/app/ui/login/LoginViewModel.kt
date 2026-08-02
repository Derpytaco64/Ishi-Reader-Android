package com.ishireader.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.prefs.AppPreferences
import com.ishireader.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false
)

class LoginViewModel(
    private val preferences: AppPreferences,
    private val network: NetworkModule,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = preferences.serverUrl.first().orEmpty()
            _uiState.value = _uiState.value.copy(serverUrl = savedUrl)

            // Already have a valid session cookie for this server? Skip straight to the library.
            if (savedUrl.isNotBlank()) {
                network.configure(savedUrl)
                if (authRepository.fetchCurrentUser() is ApiResult.Success) {
                    _uiState.value = _uiState.value.copy(loggedIn = true)
                }
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, error = null)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank()) {
            _uiState.value = state.copy(error = "Server URL and username are required")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            preferences.setServerUrl(state.serverUrl)
            network.configure(state.serverUrl)

            when (val result = authRepository.login(state.username, state.password)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    class Factory(
        private val preferences: AppPreferences,
        private val network: NetworkModule,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(preferences, network, authRepository) as T
    }
}
