package com.controlqr.acceso.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlqr.acceso.AppContainer
import com.controlqr.acceso.data.IssueResult
import com.controlqr.acceso.data.PassRequest
import com.controlqr.acceso.data.db.PassEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Formulario de emisión de pases del master. */
class IssueViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val fullName: String = "",
        val carrier: String = "",
        val plate: String = "",
        val company: String = "",
        val notes: String = "",
        val validFrom: Long = System.currentTimeMillis(),
        val validUntil: Long = System.currentTimeMillis() + 12 * 60 * 60_000L,
        val busy: Boolean = false,
        val error: String? = null,
        val issued: PassEntity? = null,
        val tokensRemaining: Int = 0,
        val poolSize: Int = 0
    )

    private val _state = MutableStateFlow(
        State(
            validUntil = System.currentTimeMillis() + container.settings.defaultValidityHours * 60 * 60_000L,
            tokensRemaining = container.settings.tokensRemaining,
            poolSize = container.settings.poolSize
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: State.() -> State) {
        _state.value = _state.value.transform().copy(error = null)
    }

    fun issue(issuedBy: String) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(busy = true, error = null)

            val result = container.access.issue(
                PassRequest(
                    fullName = current.fullName,
                    carrier = current.carrier,
                    plate = current.plate,
                    company = current.company,
                    notes = current.notes,
                    validFrom = current.validFrom,
                    validUntil = current.validUntil
                ),
                issuedBy
            )

            _state.value = when (result) {
                is IssueResult.Ok -> current.copy(
                    busy = false,
                    issued = result.pass,
                    tokensRemaining = container.settings.tokensRemaining
                )

                is IssueResult.Error -> current.copy(busy = false, error = result.message)
            }
        }
    }

    /** Deja el formulario listo para el siguiente visitante, conservando la transportista. */
    fun newPass(keepCarrier: Boolean = true) {
        val current = _state.value
        val now = System.currentTimeMillis()
        _state.value = State(
            carrier = if (keepCarrier) current.carrier else "",
            company = if (keepCarrier) current.company else "",
            validFrom = now,
            validUntil = now + container.settings.defaultValidityHours * 60 * 60_000L,
            tokensRemaining = container.settings.tokensRemaining,
            poolSize = container.settings.poolSize
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { IssueViewModel(container) }
        }
    }
}
