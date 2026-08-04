package com.controlqr.acceso.ui.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.ui.components.BannerTone
import com.controlqr.acceso.ui.components.InfoBanner
import com.controlqr.acceso.ui.components.SectionTitle
import com.controlqr.acceso.ui.components.StatTile
import com.controlqr.acceso.ui.theme.VerdeAcceso
import com.controlqr.acceso.ui.vm.AdminViewModel

/** Tablero del master: el estado del sitio de un vistazo y el acceso a todo lo demás. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterHomeScreen(
    displayName: String,
    siteName: String,
    viewModel: AdminViewModel,
    onIssue: () -> Unit,
    onScanEntry: () -> Unit,
    onScanExit: () -> Unit,
    onPasses: () -> Unit,
    onReports: () -> Unit,
    onUsers: () -> Unit,
    onEvents: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val insideNow by viewModel.insideCount.collectAsStateWithLifecycle()

    LaunchedEffect(insideNow) { viewModel.refreshDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(siteName.ifBlank { "Control QR" })
                        Text("$displayName · Master", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Ocupación en este momento")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = insideNow.toString(),
                    label = "Adentro ahora",
                    accent = VerdeAcceso,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = dashboard.activeQrToday.toString(),
                    label = "QR activos hoy",
                    caption = "vigentes en el día",
                    modifier = Modifier.weight(1f)
                )
            }

            SectionTitle("Movimiento de hoy · ${Formats.date(System.currentTimeMillis())}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = dashboard.entriesToday.toString(),
                    label = "Entradas",
                    accent = VerdeAcceso,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = dashboard.exitsToday.toString(),
                    label = "Salidas",
                    accent = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = dashboard.issuedToday.toString(),
                    label = "Pases emitidos",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = Formats.duration(dashboard.avgDwellToday),
                    label = "Permanencia media",
                    modifier = Modifier.weight(1f)
                )
            }

            if (dashboard.deniedToday > 0) {
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    "Hoy se rechazaron ${dashboard.deniedToday} lecturas. Revísalas en la bitácora.",
                    tone = BannerTone.WARNING
                )
            }

            Spacer(Modifier.height(12.dp))
            val remaining = dashboard.tokensRemaining
            val poolTone = when {
                remaining <= 0 -> BannerTone.DANGER
                remaining < dashboard.poolSize / 10 -> BannerTone.WARNING
                else -> BannerTone.INFO
            }
            InfoBanner(
                "Bolsa de folios: quedan $remaining de ${dashboard.poolSize}.",
                tone = poolTone
            )

            SectionTitle("Emitir")
            Button(
                onClick = onIssue,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("GENERAR PASE QR", fontWeight = FontWeight.Bold)
            }

            SectionTitle("Caseta")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScanEntry,
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeAcceso),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ENTRADA")
                }
                Button(
                    onClick = onScanExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SALIDA")
                }
            }

            SectionTitle("Administración")
            NavRow(Icons.Default.ListAlt, "Pases y accesos", "Historial completo, buscar y cancelar", onPasses)
            NavRow(Icons.Default.Assessment, "Reportes", "Entradas y salidas por día, semana y mes", onReports)
            NavRow(Icons.Default.Groups, "Usuarios", "Alta de vasallos y vinculación de equipos", onUsers)
            NavRow(Icons.Default.History, "Bitácora de escaneos", "Incluye los intentos rechazados", onEvents)
            NavRow(Icons.Default.Settings, "Ajustes y respaldo", "Bolsa de folios, exportar e importar", onSettings)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).height(68.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
