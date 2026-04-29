package ru.maxx.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.maxx.app.core.network.SessionManager
import ru.maxx.app.di.AppContainer

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class OtpSent(val token: String) : UiState()
        data class PasswordRequired(val trackId: String, val hint: String?) : UiState()
        object Success : UiState()
        data class Error(val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var authWatchJob: Job? = null

    private fun watchAuthState(onDone: () -> Unit = {}) {
        authWatchJob?.cancel()
        authWatchJob = viewModelScope.launch {
            container.session.authState.collect { auth ->
                when (auth) {
                    is SessionManager.AuthState.PhoneVerification -> {
                        _state.value = UiState.OtpSent(auth.token)
                        authWatchJob?.cancel()
                    }
                    is SessionManager.AuthState.Authenticated -> {
                        _state.value = UiState.Success
                        authWatchJob?.cancel()
                    }
                    is SessionManager.AuthState.PasswordRequired -> {
                        _state.value = UiState.PasswordRequired(auth.trackId, auth.hint)
                        authWatchJob?.cancel()
                    }
                    is SessionManager.AuthState.Error -> {
                        _state.value = UiState.Error(auth.msg)
                        authWatchJob?.cancel()
                    }
                    else -> {}
                }
            }
        }
    }

    fun requestOtp(phone: String) {
        if (_state.value == UiState.Loading) return
        _state.value = UiState.Loading
        watchAuthState()
        viewModelScope.launch {
            try {
                if (container.socket.state.value.let {
                    it != ru.maxx.app.core.network.MaxSocket.State.Connected &&
                    it != ru.maxx.app.core.network.MaxSocket.State.Authorized
                }) {
                    container.socket.connect()
                }
                container.session.requestOtp(phone)
            } catch (e: Exception) {
                authWatchJob?.cancel()
                _state.value = UiState.Error(e.message ?: "Ошибка подключения")
            }
        }
    }

    fun verifyOtp(token: String, code: String) {
        if (_state.value == UiState.Loading) return
        _state.value = UiState.Loading
        // watchAuthState слушает с текущего состояния — пропускаем PhoneVerification,
        // ждём только следующих состояний: Authenticated / PasswordRequired / Error
        authWatchJob?.cancel()
        authWatchJob = viewModelScope.launch {
            container.session.authState
                .dropWhile { it is SessionManager.AuthState.PhoneVerification }
                .collect { auth ->
                    when (auth) {
                        is SessionManager.AuthState.Authenticated -> {
                            _state.value = UiState.Success
                            authWatchJob?.cancel()
                        }
                        is SessionManager.AuthState.PasswordRequired -> {
                            _state.value = UiState.PasswordRequired(auth.trackId, auth.hint)
                            authWatchJob?.cancel()
                        }
                        is SessionManager.AuthState.Error -> {
                            _state.value = UiState.Error(auth.msg)
                            authWatchJob?.cancel()
                        }
                        else -> {}
                    }
                }
        }
        viewModelScope.launch {
            try { container.session.verifyOtp(token, code) }
            catch (e: Exception) { authWatchJob?.cancel(); _state.value = UiState.Error(e.message ?: "Ошибка") }
        }
    }

    fun sendPassword(trackId: String, password: String) {
        if (_state.value == UiState.Loading) return
        _state.value = UiState.Loading
        watchAuthState()
        viewModelScope.launch {
            try {
                val ok = container.session.sendPassword(trackId, password)
                if (!ok) { authWatchJob?.cancel(); _state.value = UiState.Error("Неверный пароль") }
            } catch (e: Exception) { authWatchJob?.cancel(); _state.value = UiState.Error(e.message ?: "Ошибка") }
        }
    }

    fun clearError() { if (_state.value is UiState.Error) _state.value = UiState.Idle }

    override fun onCleared() {
        super.onCleared()
        authWatchJob?.cancel()
    }
}
