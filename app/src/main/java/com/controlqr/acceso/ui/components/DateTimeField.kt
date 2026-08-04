package com.controlqr.acceso.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlqr.acceso.core.Formats
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Campo de fecha y hora en dos pasos (día y luego hora).
 * El selector de Android devuelve la fecha en UTC, así que se recompone contra la
 * hora local ya elegida para que no se corra un día en zonas negativas como México.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pending by remember { mutableLongStateOf(millis) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = Formats.dateTime(millis),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Capa transparente encima: el TextField deshabilitado no recibe clics por sí solo.
        Box(
            Modifier
                .matchParentSize()
                .clickable {
                    pending = millis
                    showDate = true
                }
        )
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Formats.local(pending).toLocalTime()
                        pending = Formats.millisOf(LocalDateTime.of(date, time))
                    }
                    showDate = false
                    showTime = true
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val local = Formats.local(pending)
        val timeState = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val updated = Formats.local(pending)
                        .withHour(timeState.hour)
                        .withMinute(timeState.minute)
                        .withSecond(0)
                    onChange(Formats.millisOf(updated))
                    showTime = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancelar") }
            },
            title = { Text(label) },
            text = {
                Box(Modifier.padding(top = 8.dp)) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}
