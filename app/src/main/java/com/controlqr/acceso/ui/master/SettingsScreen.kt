package com.controlqr.acceso.ui.master

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.BuildConfig
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.ui.components.BannerTone
import com.controlqr.acceso.ui.components.InfoBanner
import com.controlqr.acceso.ui.components.SectionTitle
import com.controlqr.acceso.ui.util.Sharing
import com.controlqr.acceso.ui.vm.AdminViewModel

/** Ajustes del sitio, respaldo, conciliación entre equipos y actualización. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    onChangePassword: () -> Unit
) {
    val context = LocalContext.current
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val release by viewModel.release.collectAsStateWithLifecycle()
    val settings = viewModel.settings

    var poolSize by remember { mutableStateOf(settings.poolSize.toString()) }
    var deviceCode by remember { mutableStateOf(settings.deviceCode.toString()) }
    var validityHours by remember { mutableStateOf(settings.defaultValidityHours.toString()) }
    var grace by remember { mutableStateOf(settings.earlyEntryGraceMinutes.toString()) }
    var allowExit by remember { mutableStateOf(settings.allowExitWithoutEntry) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importJson) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            feedback.message?.let {
                Spacer(Modifier.height(12.dp))
                InfoBanner(it, tone = if (feedback.isError) BannerTone.DANGER else BannerTone.SUCCESS)
            }

            SectionTitle("Sitio")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(settings.siteName.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Huella de la llave: ${settings.keyFingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Los equipos vinculados deben mostrar exactamente esta misma huella.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle("Bolsa de folios")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = poolSize,
                    onValueChange = { poolSize = it.filter(Char::isDigit).take(7) },
                    label = { Text("Total de QR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = deviceCode,
                    onValueChange = { deviceCode = it.filter(Char::isDigit).take(3) },
                    label = { Text("Código de equipo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Quedan ${settings.tokensRemaining} folios. Ampliar la bolsa no invalida los pases ya emitidos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    poolSize.toIntOrNull()?.let(viewModel::updatePoolSize)
                    deviceCode.toIntOrNull()?.let(viewModel::updateDeviceCode)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar bolsa y código") }

            SectionTitle("Reglas de acceso")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = validityHours,
                    onValueChange = {
                        validityHours = it.filter(Char::isDigit).take(3)
                        validityHours.toIntOrNull()?.let(viewModel::updateValidityHours)
                    },
                    label = { Text("Vigencia (horas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = grace,
                    onValueChange = {
                        grace = it.filter(Char::isDigit).take(4)
                        grace.toIntOrNull()?.let(viewModel::updateEarlyGrace)
                    },
                    label = { Text("Tolerancia (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = allowExit,
                    onCheckedChange = {
                        allowExit = it
                        viewModel.updateAllowExitWithoutEntry(it)
                    }
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Permitir salida sin entrada local", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Actívalo si la entrada y la salida se registran en teléfonos distintos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle("Respaldo y conciliación")
            Button(
                onClick = {
                    viewModel.exportJson { file ->
                        Sharing.shareFile(context, file, "application/json", "Respaldo Control QR")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Exportar respaldo (.json)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.exportCsv { file ->
                        Sharing.shareFile(context, file, "text/csv", "Accesos Control QR")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Exportar accesos a Excel (.csv)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Importar respaldo de otro equipo") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Al importar, las entradas y salidas que falten se agregan; nunca se borra información existente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Cuenta")
            OutlinedButton(
                onClick = onChangePassword,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Cambiar mi contraseña") }

            SectionTitle("Actualizaciones")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Versión instalada: ${BuildConfig.VERSION_NAME}")
                    Text(
                        "Repositorio: ${BuildConfig.UPDATE_REPO}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.lastUpdateCheck > 0) {
                        Text(
                            "Última consulta: ${Formats.dateTime(settings.lastUpdateCheck)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.checkForUpdate(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buscar actualización") }

                    release?.let { info ->
                        Spacer(Modifier.height(12.dp))
                        InfoBanner("Versión ${info.versionName} disponible.\n${info.notes}", tone = BannerTone.INFO)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { Sharing.openUrl(context, info.apkUrl ?: info.pageUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Descargar APK") }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
