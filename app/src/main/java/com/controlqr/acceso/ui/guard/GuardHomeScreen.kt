package com.controlqr.acceso.ui.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controlqr.acceso.sync.SyncStatus
import com.controlqr.acceso.ui.components.SyncBadge
import com.controlqr.acceso.ui.theme.VerdeAcceso

/**
 * Pantalla del vasallo. Dos botones enormes y nada más: en una caseta se opera de pie,
 * con guantes y con prisa, así que cualquier elemento extra es una fuente de error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardHomeScreen(
    displayName: String,
    siteName: String,
    syncStatus: SyncStatus,
    onEntry: () -> Unit,
    onExit: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(siteName.ifBlank { "Control QR" })
                        Text(displayName, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    SyncBadge(syncStatus, compact = true)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BigActionButton(
                label = "ESCANEAR ENTRADA",
                caption = "Registrar el ingreso de un pase",
                color = VerdeAcceso,
                icon = { Icon(Icons.Default.Login, null, tint = Color.White, modifier = Modifier.size(40.dp)) },
                onClick = onEntry
            )
            Spacer(Modifier.height(24.dp))
            BigActionButton(
                label = "ESCANEAR SALIDA",
                caption = "Cerrar el pase y calcular la permanencia",
                color = Color(0xFF1565C0),
                icon = { Icon(Icons.Default.Logout, null, tint = Color.White, modifier = Modifier.size(40.dp)) },
                onClick = onExit
            )
        }
    }
}

@Composable
private fun BigActionButton(
    label: String,
    caption: String,
    color: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(18.dp))
            Column {
                Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
