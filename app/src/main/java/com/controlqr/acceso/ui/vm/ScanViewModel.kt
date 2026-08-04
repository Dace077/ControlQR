package com.controlqr.acceso.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlqr.acceso.AppContainer
import com.controlqr.acceso.data.ScanOutcome
import com.controlqr.acceso.data.db.ScanType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controla una caseta (entrada o salida).
 *
 * La cámara dispara varias lecturas por segundo del mismo código; sin el filtro de
 * abajo un solo pase generaría decenas de registros duplicados.
 */
class ScanViewModel(
    private val container: AppContainer,
    private val type: ScanType
) : ViewModel() {

    data class State(
        val processing: Boolean = false,
        val outcome: ScanOutcome? = null,
        val torchOn: Boolean = false,
        val scannedToday: Int = 0
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var lastCode: String? = null
    private var lastCodeAt: Long = 0L

    fun onCodeScanned(raw: String, byUser: String) {
        val now = System.currentTimeMillis()
        if (raw == lastCode && now - lastCodeAt < REPEAT_WINDOW_MS) return
        if (_state.value.processing || _state.value.outcome != null) return

        lastCode = raw
        lastCodeAt = now

        viewModelScope.launch {
            _state.value = _state.value.copy(processing = true)
            val outcome = when (type) {
                ScanType.SALIDA -> container.access.registerExit(raw, byUser)
                else -> container.access.registerEntry(raw, byUser)
            }
            _state.value = _state.value.copy(
                processing = false,
                outcome = outcome,
                scannedToday = _state.value.scannedToday + if (outcome is ScanOutcome.Granted) 1 else 0
            )
        }
    }

    /** Cierra la tarjeta de resultado y deja la cámara lista para el siguiente vehículo. */
    fun dismiss() {
        _state.value = _state.value.copy(outcome = null)
        lastCodeAt = System.currentTimeMillis()
    }

    fun toggleTorch() {
        _state.value = _state.value.copy(torchOn = !_state.value.torchOn)
    }

    companion object {
        private const val REPEAT_WINDOW_MS = 4_000L

        fun factory(container: AppContainer, type: ScanType) = viewModelFactory {
            initializer { ScanViewModel(container, type) }
        }
    }
}
