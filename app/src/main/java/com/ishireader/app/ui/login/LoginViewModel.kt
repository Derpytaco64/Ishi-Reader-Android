package com.ishireader.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.PublicUser
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.network.dataOrNull
import com.ishireader.app.data.prefs.AppPreferences
import com.ishireader.app.data.repository.AuthRepository
import com.ishireader.app.data.repository.LoginAttemptResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 8

enum class LoginStage { SERVER, PICK, PASSWORD, SETUP }

data class LoginUiState(
    val stage: LoginStage = LoginStage.SERVER,
    val serverUrl: String = "",
    val isConnecting: Boolean = false,
    val connectError: String? = null,

    /** Set once [connect] configures [NetworkModule] successfully -- PublicUser.avatarUrl is
     *  server-relative, so the picker/password stages need this prefix to actually load one. */
    val baseUrl: String? = null,
    val users: List<PublicUser> = emptyList(),
    val selectedUser: PublicUser? = null,

    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val lockedUntil: Long? = null,
    val isSubmitting: Boolean = false,

    val accentColor: String? = null,
    val themeMode: String = "dark",

    val loggedIn: Boolean = false
)

/** Mirrors the site's Jellyfin-style login flow (LoginPageClient.tsx): pick a profile, then a
 *  password field appears for that account, with a fall-through to a first-time password setup
 *  form for accounts that don't have one yet. Unlike the site (fixed to one server), this app
 *  needs a server URL up front -- that's its own initial stage, reachable again later via
 *  "Change server" so switching servers doesn't require reinstalling anything. */
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
            if (savedUrl.isNotBlank()) connect(savedUrl)
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, connectError = null)
    }

    fun connect() = connect(_uiState.value.serverUrl)

    private fun connect(url: String) {
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(connectError = "Server URL is required")
            return
        }
        _uiState.value = _uiState.value.copy(isConnecting = true, connectError = null)
        viewModelScope.launch {
            val configureResult = runCatching { network.configure(url) }
            if (configureResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    connectError = configureResult.exceptionOrNull()?.message ?: "Invalid server URL"
                )
                return@launch
            }
            preferences.setServerUrl(url)

            // Already have a valid session cookie for this server? Skip straight to the library.
            if (authRepository.fetchCurrentUser() is ApiResult.Success) {
                _uiState.value = _uiState.value.copy(isConnecting = false, loggedIn = true)
                return@launch
            }

            val accentColor = authRepository.getLoginAccentColor().dataOrNull()
            val themeMode = authRepository.getLoginThemeMode().dataOrNull() ?: _uiState.value.themeMode

            when (val usersResult = authRepository.listPublicUsers()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    stage = LoginStage.PICK,
                    baseUrl = network.baseUrl,
                    users = usersResult.data,
                    accentColor = accentColor,
                    themeMode = themeMode
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    connectError = usersResult.message,
                    accentColor = accentColor,
                    themeMode = themeMode
                )
            }
        }
    }

    /** Drops back to the server-entry stage without losing the URL that's already typed in --
     *  reachable from the picker so switching servers doesn't require reinstalling anything. */
    fun changeServer() {
        _uiState.value = LoginUiState(serverUrl = _uiState.value.serverUrl)
    }

    fun selectUser(user: PublicUser) {
        _uiState.value = _uiState.value.copy(
            stage = LoginStage.PASSWORD,
            selectedUser = user,
            password = "",
            confirmPassword = "",
            error = null,
            lockedUntil = null
        )
    }

    fun backToPicker() {
        _uiState.value = _uiState.value.copy(
            stage = LoginStage.PICK,
            selectedUser = null,
            password = "",
            confirmPassword = "",
            error = null,
            lockedUntil = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    // CLAUDE-ADDED: No blank-password guard here (unlike a typical login form) -- an account with
    // no password yet signs in with the field left blank, matching the site's own submitLogin.
    fun submitLogin() {
        val state = _uiState.value
        val user = state.selectedUser ?: return
        if (state.isSubmitting) return

        _uiState.value = state.copy(isSubmitting = true, error = null, lockedUntil = null)
        viewModelScope.launch {
            when (val result = authRepository.login(user.username, state.password)) {
                is LoginAttemptResult.LoggedIn -> _uiState.value = _uiState.value.copy(isSubmitting = false, loggedIn = true)
                is LoginAttemptResult.NeedsPasswordSetup -> _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    stage = LoginStage.SETUP,
                    password = "",
                    confirmPassword = ""
                )
                is LoginAttemptResult.Failed -> _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = result.message,
                    lockedUntil = result.lockedUntil
                )
            }
        }
    }

    fun submitSetup() {
        val state = _uiState.value
        val user = state.selectedUser ?: return
        if (state.isSubmitting) return

        if (state.password.length < MIN_PASSWORD_LENGTH) {
            _uiState.value = state.copy(error = "Password must be at least $MIN_PASSWORD_LENGTH characters")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords don't match")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = authRepository.setupPassword(user.id, state.password)) {
                is LoginAttemptResult.LoggedIn -> _uiState.value = _uiState.value.copy(isSubmitting = false, loggedIn = true)
                is LoginAttemptResult.NeedsPasswordSetup -> _uiState.value = _uiState.value.copy(isSubmitting = false)
                is LoginAttemptResult.Failed -> _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
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
