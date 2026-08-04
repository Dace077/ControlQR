package com.controlqr.acceso.ui.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassStatus
import com.controlqr.acceso.ui.components.DetailRow
import com.controlqr.acceso.ui.components.EmptyState
import com.controlqr.acceso.ui.components.StatusChip
import com.controlqr.acceso.ui.vm.AdminViewModel

private enum class PassFilter(val label: String) {
    TODOS("Todos"),
    ADENTRO("Adentro"),
    SIN_USAR("Sin usar"),
    CERRADOS("Cerrados")
}

/** Historial de pases con búsqueda y detalle. Es la vista de auditoría del master. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassesScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val recent by viewModel.recentPasses.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(PassFilter.TODOS) }
    var selected by remember { mutableStateOf<PassEntity?>(null) }

    val source = if (query.isBlank()) recent else results
    val list = source.filter { pass ->
        when (filter) {
            PassFilter.TODOS -> true
            PassFilter.ADENTRO -> pass.status == PassStatus.DENTRO
            PassFilter.SIN_USAR -> pass.status == PassStatus.EMITIDO
            PassFilter.CERRADOS -> pass.status == PassStatus.COMPLETADO || pass.status == PassStatus.REVOCADO
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pases y accesos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::search,
                label = { Text("Buscar por nombre, transportista, placas o folio") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PassFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }

            if (list.isEmpty()) {
                EmptyState(
                    title = "Sin resultados",
                    detail = if (query.isBlank()) "Todavía no se ha emitido ni escaneado ningún pase."
                    else "No hay pases que coincidan con \"$query\"."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.tokenId }) { pass ->
                        PassRow(pass) { selected = pass }
                    }
                }
            }
        }
    }

    selected?.let { pass ->
        PassDetailDialog(
            pass = pass,
            onDismiss = { selected = null },
            onRevoke = { reason ->
                viewModel.revoke(pass.tokenId, reason)
                selected = null
            }
        )
    }
}

@Composable
private fun PassRow(pass: PassEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pass.fullName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(pass.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${pass.carrier}${if (pass.plate.isNotBlank()) " · ${pass.plate}" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Folio ${pass.folio}")
                    pass.entryAt?.let { append(" · Entró ${Formats.time(it)}") }
                    pass.exitAt?.let { append(" · Salió ${Formats.time(it)}") }
                    pass.dwellMinutes?.let { append(" · ${Formats.duration(it)}") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PassDetailDialog(
    pass: PassEntity,
    onDismiss: () -> Unit,
    onRevoke: (String) -> Unit
) {
    var confirmRevoke by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folio ${pass.folio}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StatusChip(pass.status)
                Spacer(Modifier.height(10.dp))
                DetailRow("Nombre", pass.fullName)
                DetailRow("Línea transportista", pass.carrier)
                DetailRow("Placas / unidad", pass.plate)
                DetailRow("Empresa / destino", pass.company)
                DetailRow("Observaciones", pass.notes)
                DetailRow("Emitido por", pass.issuedBy)
                DetailRow("Emitido", Formats.dateTime(pass.issuedAt))
                DetailRow("Ingreso programado", Formats.dateTime(pass.validFrom))
                DetailRow("Válido hasta", Formats.dateTime(pass.validUntil))
                DetailRow("Entrada", "${Formats.dateTime(pass.entryAt)}${pass.entryBy?.let { " · $it" } ?: ""}")
                DetailRow("Salida", "${Formats.dateTime(pass.exitAt)}${pass.exitBy?.let { " · $it" } ?: ""}")
                DetailRow("Permanencia", Formats.duration(pass.dwellMinutes))
                pass.revokedReason?.let { DetailRow("Motivo de cancelación", it) }

                if (confirmRevoke) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Al cancelar, el QR deja de servir para entrar. Si la persona ya está adentro, aún podrá registrar su salida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (pass.status == PassStatus.EMITIDO || pass.status == PassStatus.DENTRO) {
                TextButton(
                    onClick = {
                        if (confirmRevoke) onRevoke("Cancelado por el administrador") else confirmRevoke = true
                    }
                ) {
                    Text(
                        if (confirmRevoke) "Sí, cancelar pase" else "Cancelar pase",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
