package com.controlqr.acceso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.controlqr.acceso.sync.SyncState
import com.controlqr.acceso.sync.SyncStatus
import com.controlqr.acceso.ui.theme.AmbarAviso
import com.controlqr.acceso.ui.theme.VerdeAcceso

/**
 * Estado de la sincronización, siempre visible.
 *
 * Importa que el vigilante distinga "sin conexión" de "no funciona": sin señal la caseta
 * sigue operando y los movimientos se suben después, así que el aviso informa, no alarma.
 */
@Composable
fun SyncBadge(status: SyncStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (label, color, icon) = presentation(status)

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun presentation(status: SyncStatus): Triple<String, Color, ImageVector> = when (status.state) {
    SyncState.EN_LINEA -> Triple(
        if (status.pendingWrites > 0) "Subiendo ${status.pendingWrites}" else "En línea",
        VerdeAcceso,
        Icons.Default.CloudDone
    )

    SyncState.SIN_CONEXION -> Triple(
        if (status.pendingWrites > 0) "Sin señal · ${status.pendingWrites} pendientes" else "Sin señal",
        AmbarAviso,
        Icons.Default.CloudOff
    )

    SyncState.CONECTANDO -> Triple("Conectando…", AmbarAviso, Icons.Default.CloudQueue)
    SyncState.APAGADA -> Triple("Solo local", Color(0xFF78909C), Icons.Default.CloudOff)
    SyncState.NO_CONFIGURADA -> Triple("Sin nube", Color(0xFF78909C), Icons.Default.CloudOff)
}
