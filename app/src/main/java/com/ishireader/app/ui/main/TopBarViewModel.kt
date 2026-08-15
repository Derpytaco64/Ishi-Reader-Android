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
import kotlinx.coroutines.launch

/** Just enough user info for the avatar pinned top-right of the tab strip -- failures are left
 *  silent (the avatar simply falls back to its initial-letter placeholder) since this isn't
 *  critical chrome worth its own error UI. */
class TopBarViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _user = MutableStateFlow<PublicUser?>(null)
    val user: StateFlow<PublicUser?> = _user.asStateFlow()

    init {
        refresh()
    }

    /** Re-fetches after the Edit User sheet closes, so a display-name or avatar change made there
     *  shows up in the avatar pinned top-right without waiting for the next app launch. */
    fun refresh() {
        viewModelScope.launch {
            when (val result = authRepository.fetchCurrentUser()) {
                is ApiResult.Success -> _user.value = result.data
                is ApiResult.Failure -> {}
            }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TopBarViewModel(authRepository) as T
    }
}
