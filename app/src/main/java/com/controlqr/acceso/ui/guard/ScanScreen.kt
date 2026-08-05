package com.controlqr.acceso.ui.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.ScanOutcome
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.ui.components.CameraPermissionGate
import com.controlqr.acceso.ui.components.QrScannerView
import com.controlqr.acceso.ui.components.ScanFeedback
import com.controlqr.acceso.ui.components.SyncBadge
import com.controlqr.acceso.ui.theme.RojoRechazo
import com.controlqr.acceso.ui.theme.VerdeAcceso
import com.controlqr.acceso.ui.vm.ScanViewModel
import kotlinx.coroutines.delay

/**
 * Pantalla de caseta. Se usa igual para Entrada y para Salida; lo único que cambia es
 * el color, el título y la operación que se registra. Mantenerlas separadas evita el
 * error más caro en campo: marcar una salida creyendo que se marcaba una entrada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    type: ScanType,
    username: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isEntry = type == ScanType.ENTRADA
    val accent = if (isEntry) VerdeAcceso else Color(0xFF1565C0)
    val title = if (isEntry) "ENTRADA" else "SALIDA"

    LaunchedEffect(state.outcome) {
        val outcome = state.outcome ?: return@LaunchedEffect
        val granted = outcome is ScanOutcome.Granted
        if (granted) ScanFeedback.granted(context) else ScanFeedback.denied(context)
        // La tarjeta se retira sola para que el siguiente vehículo no espere al vigilante.
        delay(if (granted) 5_000 else 8_000)
        viewModel.dismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(
                            "Registradas en este turno: ${state.scannedToday}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    SyncBadge(syncStatus, compact = true)
                    IconButton(onClick = { viewModel.toggleTorch() }) {
                        Icon(
                            if (state.torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Linterna"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CameraPermissionGate {
                QrScannerView(
                    onCode = { raw -> viewModel.onCodeScanned(raw, username) },
                    torchOn = state.torchOn,
                    enabled = state.outcome == null && !state.processing,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Guía de encuadre
            if (state.outcome == null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isEntry) "Apunta al QR del pase para dar ingreso"
                        else "Apunta al QR del pase para registrar la salida",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            if (state.processing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            state.outcome?.let { outcome ->
                ResultOverlay(
                    outcome = outcome,
                    isEntry = isEntry,
                    onDismiss = { viewModel.dismiss() }
                )
            }
        }
    }
}

@Composable
private fun ResultOverlay(
    outcome: ScanOutcome,
    isEntry: Boolean,
    onDismiss: () -> Unit
) {
    val granted = outcome is ScanOutcome.Granted
    val color = if (granted) VerdeAcceso else RojoRechazo

    Box(
        Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.height(96.dp)
            )
            Spacer(Modifier.height(12.dp))

            when (outcome) {
                is ScanOutcome.Granted -> {
                    Text(
                        text = if (isEntry) "ACCESO AUTORIZADO" else "SALIDA REGISTRADA",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            BigValue("Nombre", outcome.pass.fullName)
                            BigValue("Línea transportista", outcome.pass.carrier)
                            if (outcome.pass.plate.isNotBlank()) BigValue("Placas / unidad", outcome.pass.plate)
                            BigValue("Folio", outcome.pass.folio)
                            BigValue(
                                if (isEntry) "Hora de ingreso" else "Hora de salida",
                                Formats.dateTime(if (isEntry) outcome.pass.entryAt else outcome.pass.exitAt)
                            )
                            if (!isEntry) {
                                BigValue("Entró", Formats.dateTime(outcome.pass.entryAt))
                                BigValue("Permanencia", Formats.duration(outcome.dwellMinutes))
                            }
                        }
                    }
                    outcome.warning?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is ScanOutcome.Denied -> {
                    Text(
                        text = outcome.title.uppercase(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = outcome.detail,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    outcome.pass?.let { pass ->
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                BigValue("Nombre", pass.fullName)
                                BigValue("Folio", pass.folio)
                                BigValue("Entrada", Formats.dateTime(pass.entryAt))
                                BigValue("Salida", Formats.dateTime(pass.exitAt))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = color
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Siguiente", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BigValue(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
