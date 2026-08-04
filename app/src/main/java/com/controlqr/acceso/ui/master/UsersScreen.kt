package com.controlqr.acceso.ui.master

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.core.qr.QrRenderer
import com.controlqr.acceso.data.db.UserEntity
import com.controlqr.acceso.data.db.UserRole
import com.controlqr.acceso.ui.components.BannerTone
import com.controlqr.acceso.ui.components.Chip
import com.controlqr.acceso.ui.components.InfoBanner
import com.controlqr.acceso.ui.theme.VerdeAcceso
import com.controlqr.acceso.ui.vm.AdminViewModel

/**
 * Alta de usuarios y vinculación de equipos.
 *
 * Al crear un vasallo se genera un QR que lleva la llave del sitio: con eso el
 * teléfono del vigilante puede validar pases sin conexión desde el primer minuto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var provisioning by remember { mutableStateOf<ProvisionRequest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo vasallo") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            feedback.message?.let {
                InfoBanner(
                    it,
                    tone = if (feedback.isError) BannerTone.DANGER else BannerTone.SUCCESS,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        onToggleActive = { viewModel.setUserActive(user.id, it) },
                        onDelete = { viewModel.deleteUser(user.id) },
                        onLink = {
                            provisioning = ProvisionRequest(
                                username = user.username,
                                displayName = user.displayName
                            )
                        }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            onDismiss = { showCreate = false },
            onCreate = { username, displayName, pin ->
                viewModel.createUser(username, displayName, UserRole.VASALLO, pin)
                showCreate = false
                provisioning = ProvisionRequest(username, displayName, pin)
            }
        )
    }

    provisioning?.let { request ->
        ProvisioningDialog(
            request = request,
            viewModel = viewModel,
            onDismiss = { provisioning = null }
        )
    }
}

private data class ProvisionRequest(
    val username: String,
    val displayName: String,
    val pin: String? = null
)

@Composable
private fun UserRow(
    user: UserEntity,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onLink: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Chip(
                    label = if (user.role == UserRole.MASTER) "Master" else "Vasallo",
                    color = if (user.role == UserRole.MASTER) MaterialTheme.colorScheme.primary else VerdeAcceso
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(if (user.active) "Activo" else "Desactivado")
                    user.lastLoginAt?.let { append(" · último ingreso ${Formats.dateTime(it)}") }
                    if (user.mustChangePassword) append(" · debe definir contraseña")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = user.active, onCheckedChange = onToggleActive)
                Spacer(Modifier.width(8.dp))
                if (user.role == UserRole.VASALLO) {
                    TextButton(onClick = onLink) { Text("QR de vinculación") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true }
                ) {
                    Text(
                        if (confirmDelete) "Confirmar" else "Borrar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo usuario vasallo") },
        text = {
            Column {
                Text(
                    "Solo podrá escanear entradas y salidas. No verá reportes ni podrá emitir pases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nombre del vigilante") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().lowercase() },
                    label = { Text("Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN temporal") },
                    supportingText = { Text("Lo cambiará en su primer ingreso") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(username, displayName, pin) },
                enabled = username.length >= 3 && pin.length >= 4
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProvisioningDialog(
    request: ProvisionRequest,
    viewModel: AdminViewModel,
    onDismiss: () -> Unit
) {
    var deviceCode by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf(request.pin ?: "") }
    var payload by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vincular equipo de ${request.displayName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (payload == null) {
                    Text(
                        "Asigna un código distinto a cada teléfono: así la bitácora indica desde qué caseta se registró cada movimiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deviceCode,
                        onValueChange = { deviceCode = it.filter(Char::isDigit).take(3) },
                        label = { Text("Código del equipo (1-255)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("PIN temporal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        "Este QR contiene la llave del sitio. Muéstralo en persona y no lo envíes por mensajería.",
                        tone = BannerTone.WARNING
                    )
                } else {
                    val bitmap = remember(payload) { QrRenderer.bitmap(payload!!, 720) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR de vinculación",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(12.dp)
                            .aspectRatio(1f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "En el otro teléfono: instala la app, toca «Vincular este equipo con un QR» y apunta a este código. Luego entra con el usuario @${request.username} y el PIN.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (payload == null) {
                TextButton(
                    onClick = {
                        payload = viewModel.provisioningQr(
                            username = request.username,
                            displayName = request.displayName,
                            tempPin = pin,
                            deviceCode = deviceCode.toIntOrNull() ?: 2
                        )
                    },
                    enabled = pin.length >= 4 && (deviceCode.toIntOrNull() ?: 0) in 1..255
                ) { Text("Generar QR") }
            } else {
                TextButton(onClick = onDismiss) { Text("Listo") }
            }
        },
        dismissButton = {
            if (payload == null) TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
