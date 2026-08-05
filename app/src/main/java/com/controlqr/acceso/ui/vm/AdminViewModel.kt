package com.controlqr.acceso.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlqr.acceso.AppContainer
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.AuthResult
import com.controlqr.acceso.data.ReportPeriod
import com.controlqr.acceso.data.ReportSummary
import com.controlqr.acceso.data.ReportsBuilder
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassStatus
import com.controlqr.acceso.data.db.ScanEventEntity
import com.controlqr.acceso.data.db.UserEntity
import com.controlqr.acceso.data.db.UserRole
import com.controlqr.acceso.sync.SyncStatus
import com.controlqr.acceso.update.ReleaseInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Consola del master: tablero, historial, reportes, usuarios y mantenimiento. */
class AdminViewModel(private val container: AppContainer) : ViewModel() {

    data class Dashboard(
        val insideNow: Int = 0,
        val entriesToday: Int = 0,
        val exitsToday: Int = 0,
        val activeQrToday: Int = 0,
        val issuedToday: Int = 0,
        val deniedToday: Int = 0,
        val tokensRemaining: Int = 0,
        val poolSize: Int = 0,
        val avgDwellToday: Long? = null
    )

    data class Feedback(val message: String? = null, val isError: Boolean = false)

    // ------------------------------------------------------------------ flujos

    val insideCount: StateFlow<Int> = container.access.observeInsideCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val insidePasses: StateFlow<List<PassEntity>> = container.access.observeInside()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentPasses: StateFlow<List<PassEntity>> = container.access.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<ScanEventEntity>> = container.access.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val users: StateFlow<List<UserEntity>> = container.users.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado de la réplica entre equipos; alimenta el indicador del tablero. */
    val syncStatus: StateFlow<SyncStatus> = container.sync.status

    private val _dashboard = MutableStateFlow(Dashboard())
    val dashboard: StateFlow<Dashboard> = _dashboard.asStateFlow()

    private val _report = MutableStateFlow<ReportSummary?>(null)
    val report: StateFlow<ReportSummary?> = _report.asStateFlow()

    private val _period = MutableStateFlow(ReportPeriod.DIA)
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    private val _feedback = MutableStateFlow(Feedback())
    val feedback: StateFlow<Feedback> = _feedback.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<PassEntity>> = _query
        .debounce(250)
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList()) else container.access.search(text)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _release = MutableStateFlow<ReleaseInfo?>(null)
    val release: StateFlow<ReleaseInfo?> = _release.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refreshDashboard()
        loadReport(ReportPeriod.DIA)
    }

    // --------------------------------------------------------------- tablero

    fun refreshDashboard() {
        viewModelScope.launch {
            val from = Formats.startOfDay()
            val to = Formats.endOfDay()
            val passes = container.access.allPasses()

            val entries = passes.count { it.entryAt != null && it.entryAt in from..to }
            val exits = passes.count { it.exitAt != null && it.exitAt in from..to }
            val dwell = passes.filter { it.exitAt != null && it.exitAt in from..to }
                .mapNotNull { it.dwellMinutes }

            _dashboard.value = Dashboard(
                insideNow = passes.count { it.status == PassStatus.DENTRO },
                entriesToday = entries,
                exitsToday = exits,
                activeQrToday = passes.count {
                    it.status != PassStatus.REVOCADO && it.validFrom <= to && it.validUntil >= from
                },
                issuedToday = passes.count { it.issuedAt in from..to },
                deniedToday = container.access.allEvents().count { !it.accepted && it.at in from..to },
                tokensRemaining = container.settings.tokensRemaining,
                poolSize = container.settings.poolSize,
                avgDwellToday = if (dwell.isEmpty()) null else dwell.average().toLong()
            )
        }
    }

    // --------------------------------------------------------------- reportes

    fun loadReport(period: ReportPeriod) {
        _period.value = period
        viewModelScope.launch {
            _busy.value = true
            val passes = container.access.allPasses()
            _report.value = ReportsBuilder.build(
                passes = passes,
                period = period,
                currentlyInside = passes.count { it.status == PassStatus.DENTRO }
            )
            _busy.value = false
        }
    }

    // ------------------------------------------------------------- historial

    fun search(text: String) {
        _query.value = text
    }

    fun revoke(tokenId: Int, reason: String) {
        viewModelScope.launch {
            container.access.revoke(tokenId, reason.ifBlank { "Cancelado por el administrador" })
            _feedback.value = Feedback("Pase cancelado.")
            refreshDashboard()
        }
    }

    // -------------------------------------------------------------- usuarios

    fun createUser(username: String, displayName: String, role: UserRole, password: String) {
        viewModelScope.launch {
            when (val result = container.users.createUser(username, displayName, role, password)) {
                is AuthResult.Ok -> _feedback.value =
                    Feedback("Usuario ${result.user.username} creado.")

                is AuthResult.Error -> _feedback.value = Feedback(result.message, isError = true)
            }
        }
    }

    fun setUserActive(userId: Long, active: Boolean) {
        viewModelScope.launch {
            when (val result = container.users.setActive(userId, active)) {
                is AuthResult.Ok -> _feedback.value =
                    Feedback(if (active) "Cuenta reactivada." else "Cuenta desactivada.")

                is AuthResult.Error -> _feedback.value = Feedback(result.message, isError = true)
            }
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            when (val result = container.users.deleteUser(userId)) {
                is AuthResult.Ok -> _feedback.value = Feedback("Usuario eliminado.")
                is AuthResult.Error -> _feedback.value = Feedback(result.message, isError = true)
            }
        }
    }

    fun provisioningQr(username: String, displayName: String, tempPin: String, deviceCode: Int): String? =
        container.users.provisioningQr(username, displayName, tempPin, deviceCode)

    // --------------------------------------------------------------- ajustes

    val settings get() = container.settings

    fun updatePoolSize(value: Int) {
        container.settings.poolSize = value
        refreshDashboard()
        _feedback.value = Feedback("Bolsa ajustada a $value pases.")
    }

    fun updateDeviceCode(value: Int) {
        container.settings.deviceCode = value
        _feedback.value = Feedback("Código de equipo: $value")
    }

    fun updateValidityHours(value: Int) {
        container.settings.defaultValidityHours = value
    }

    fun updateAllowExitWithoutEntry(value: Boolean) {
        container.settings.allowExitWithoutEntry = value
    }

    fun updateSyncEnabled(value: Boolean) {
        container.settings.syncEnabled = value
        if (value) container.sync.restart() else container.sync.stop()
        _feedback.value = Feedback(
            if (value) "Sincronización activada." else "Sincronización apagada; el equipo opera solo local."
        )
    }

    fun retrySync() {
        container.sync.restart()
        _feedback.value = Feedback("Reintentando conexión…")
    }

    fun updateEarlyGrace(value: Int) {
        container.settings.earlyEntryGraceMinutes = value
    }

    // --------------------------------------------------------------- respaldo

    fun exportJson(onReady: (File) -> Unit) = exporting { container.backup.exportJson().also(onReady) }

    fun exportCsv(onReady: (File) -> Unit) = exporting { container.backup.exportCsv().also(onReady) }

    private fun exporting(block: suspend () -> File) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onSuccess { _feedback.value = Feedback("Archivo generado: ${it.name}") }
                .onFailure { _feedback.value = Feedback("No se pudo exportar: ${it.message}", isError = true) }
            _busy.value = false
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            container.backup.importJson(uri)
                .onSuccess {
                    _feedback.value = Feedback("Se integraron ${it.passes} pases y ${it.events} eventos.")
                    refreshDashboard()
                    loadReport(_period.value)
                }
                .onFailure {
                    _feedback.value = Feedback("No se pudo importar: ${it.message}", isError = true)
                }
            _busy.value = false
        }
    }

    // ---------------------------------------------------------- actualización

    fun checkForUpdate(repo: String, currentVersion: String) {
        viewModelScope.launch {
            _busy.value = true
            container.updates.latestRelease(repo)
                .onSuccess { info ->
                    container.settings.lastUpdateCheck = System.currentTimeMillis()
                    if (container.updates.isNewer(info.versionName, currentVersion)) {
                        _release.value = info
                        _feedback.value = Feedback("Hay una versión nueva: ${info.versionName}")
                    } else {
                        _release.value = null
                        _feedback.value = Feedback("Ya tienes la última versión.")
                    }
                }
                .onFailure {
                    _feedback.value = Feedback("No se pudo consultar GitHub. Revisa tu conexión.", isError = true)
                }
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = Feedback()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AdminViewModel(container) }
        }
    }
}
