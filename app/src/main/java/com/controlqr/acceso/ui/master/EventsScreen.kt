package com.controlqr.acceso.ui.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.ui.components.Chip
import com.controlqr.acceso.ui.components.EmptyState
import com.controlqr.acceso.ui.theme.RojoRechazo
import com.controlqr.acceso.ui.theme.VerdeAcceso
import com.controlqr.acceso.ui.vm.AdminViewModel

/**
 * Bitácora cruda de escaneos, incluidos los rechazados.
 * Es donde se ve si alguien intentó reusar un pase o presentó un QR ajeno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bitácora de escaneos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            EmptyState(
                title = "Sin escaneos todavía",
                detail = "Aquí aparecerá cada lectura de QR, aceptada o rechazada.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                event.fullName ?: event.folio ?: "Código desconocido",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Chip(
                                label = when (event.type) {
                                    ScanType.ENTRADA -> "Entrada"
                                    ScanType.SALIDA -> "Salida"
                                    ScanType.DENEGADO -> "Rechazado"
                                },
                                color = if (event.accepted) VerdeAcceso else RojoRechazo
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Formats.dateTime(event.at)} · ${event.byUser} · equipo ${event.deviceCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
