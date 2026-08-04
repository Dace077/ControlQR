package com.controlqr.acceso.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlqr.acceso.AppContainer
import com.controlqr.acceso.data.AuthResult
import com.controlqr.acceso.data.db.UserEntity
import com.controlqr.acceso.data.db.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de sesión: decide qué ve la app al abrirse y quién firma cada movimiento. */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val needsSetup: Boolean = false,
        val user: UserEntity? = null,
        val siteName: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val message: String? = null
    ) {
        val isMaster: Boolean get() = user?.role == UserRole.MASTER
        val signedIn: Boolean get() = user != null
        val mustChangePassword: Boolean get() = user?.mustChangePassword == true
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val needsSetup = container.users.needsSetup()
            val user = if (needsSetup) null else container.users.currentUser()
            _state.value = _state.value.copy(
                loading = false,
                needsSetup = needsSetup,
                user = user?.takeIf { it.active },
                siteName = container.settings.siteName
            )
        }
    }

    fun setupMaster(
        siteName: String,
        deviceCode: Int,
        poolSize: Int,
        username: String,
        displayName: String,
        password: String,
        confirm: String
    ) {
        if (password != confirm) {
            _state.value = _state.value.copy(error = "Las contraseñas no coinciden.")
            return
        }
        launchAuth {
            container.users.setupMaster(siteName, deviceCode, poolSize, username, displayName, password)
        }
    }

    fun login(username: String, password: String) = launchAuth {
        container.users.login(username, password)
    }

    /** Vincula este equipo a un sitio a partir del QR que muestra el master. */
    fun applyProvisioning(raw: String) = launchAuth {
        container.users.applyProvisioning(raw).also {
            if (it is AuthResult.Ok) container.settings.currentUserId = 0L
        }
    }

    fun changePassword(current: String?, newPassword: String, confirm: String) {
        val userId = _state.value.user?.id ?: return
        if (newPassword != confirm) {
            _state.value = _state.value.copy(error = "Las contraseñas no coinciden.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (val result = container.users.changePassword(userId, current, newPassword)) {
                is AuthResult.Ok -> _state.value = _state.value.copy(
                    busy = false,
                    user = result.user,
                    message = "Contraseña actualizada."
                )
                is AuthResult.Error -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun signOut() {
        container.users.signOut()
        _state.value = _state.value.copy(user = null, message = null, error = null)
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(error = null, message = null)
    }

    private fun launchAuth(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            when (val result = block()) {
                is AuthResult.Ok -> _state.value = _state.value.copy(
                    busy = false,
                    loading = false,
                    needsSetup = container.users.needsSetup(),
                    user = container.users.currentUser(),
                    siteName = container.settings.siteName,
                    error = null
                )

                is AuthResult.Error -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SessionViewModel(container) }
        }
    }
}
