package com.controlqr.acceso.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controlqr.acceso.data.prefs.AppSettings
import com.controlqr.acceso.ui.components.BannerTone
import com.controlqr.acceso.ui.components.CameraPermissionGate
import com.controlqr.acceso.ui.components.InfoBanner
import com.controlqr.acceso.ui.components.QrScannerView
import com.controlqr.acceso.ui.vm.SessionViewModel

/**
 * Primer arranque en el teléfono del master. Aquí nace la llave del sitio,
 * que es lo que después permite validar pases sin conexión.
 */
@Composable
fun SetupScreen(
    state: SessionViewModel.State,
    viewModel: SessionViewModel,
    onProvisionRequested: () -> Unit
) {
    var siteName by rememberSaveable { mutableStateOf("") }
    var deviceCode by rememberSaveable { mutableStateOf("1") }
    var poolSize by rememberSaveable { mutableStateOf(AppSettings.DEFAULT_POOL_SIZE.toString()) }
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = "Configuración inicial") {
        Text(
            "Este equipo será el MASTER: emite los pases y guarda la llave del sitio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = siteName,
            onValueChange = { siteName = it },
            label = { Text("Nombre del sitio o planta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Row {
            OutlinedTextField(
                value = deviceCode,
                onValueChange = { deviceCode = it.filter(Char::isDigit).take(3) },
                label = { Text("Código de equipo") },
                supportingText = { Text("1 a 255") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = poolSize,
                onValueChange = { poolSize = it.filter(Char::isDigit).take(6) },
                label = { Text("QR disponibles") },
                supportingText = { Text("Bolsa de folios") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Cuenta master", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Nombre de la persona") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Repite la contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        InfoBanner(
            "Si pierdes esta contraseña y la llave del sitio, los pases ya emitidos dejan de poder verificarse. Anótala en un lugar seguro.",
            tone = BannerTone.WARNING
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(it, tone = BannerTone.DANGER)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.setupMaster(
                    siteName = siteName,
                    deviceCode = deviceCode.toIntOrNull() ?: 1,
                    poolSize = poolSize.toIntOrNull() ?: AppSettings.DEFAULT_POOL_SIZE,
                    username = username,
                    displayName = displayName,
                    password = password,
                    confirm = confirm
                )
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Crear sitio y cuenta master")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onProvisionRequested,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Este equipo es de un vigilante")
        }
        Text(
            "Úsalo si el sitio ya existe: pide al master su QR de vinculación.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoginScreen(
    state: SessionViewModel.State,
    viewModel: SessionViewModel,
    onProvisionRequested: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = state.siteName.ifBlank { "Control QR" }) {
        Text(
            "Inicia sesión para continuar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(it, tone = BannerTone.DANGER)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.login(username, password) },
            enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Entrar")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onProvisionRequested,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Vincular este equipo con un QR")
        }
    }
}

/** Lado del vasallo: escanea el QR de vinculación que le muestra el master. */
@Composable
fun ProvisionScreen(
    state: SessionViewModel.State,
    viewModel: SessionViewModel,
    onBack: () -> Unit
) {
    var handled by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Vincular equipo", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pide al administrador que abra Usuarios y te muestre el QR de vinculación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(it, tone = BannerTone.DANGER)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPermissionGate {
                    QrScannerView(
                        onCode = { raw ->
                            if (!handled) {
                                handled = true
                                viewModel.applyProvisioning(raw)
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            TextButton(
                onClick = {
                    handled = false
                    viewModel.clearFeedback()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Cancelar")
            }
        }
    }
}

/** Se muestra obligatoriamente en el primer ingreso de un vasallo recién vinculado. */
@Composable
fun ChangePasswordScreen(
    state: SessionViewModel.State,
    viewModel: SessionViewModel,
    forced: Boolean,
    onDone: () -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = if (forced) "Define tu contraseña" else "Cambiar contraseña") {
        Text(
            if (forced) {
                "Tu equipo se vinculó con una clave temporal. Elige una contraseña propia para continuar."
            } else {
                "Escribe tu contraseña actual y la nueva."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        if (!forced) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text("Contraseña actual") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Nueva contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Repite la nueva contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(it, tone = BannerTone.DANGER)
        }
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(it, tone = BannerTone.SUCCESS)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.changePassword(
                    current = if (forced) null else current,
                    newPassword = newPassword,
                    confirm = confirm
                )
            },
            enabled = !state.busy && newPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Guardar")
        }

        if (!state.mustChangePassword) {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar")
            }
        }
    }
}

@Composable
private fun AuthScaffold(title: String, content: @Composable () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}
