package com.controlqr.acceso.ui.master

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.core.qr.QrRenderer
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.ui.components.BannerTone
import com.controlqr.acceso.ui.components.DateTimeField
import com.controlqr.acceso.ui.components.InfoBanner
import com.controlqr.acceso.ui.util.Sharing
import com.controlqr.acceso.ui.vm.IssueViewModel

/**
 * Emisión de un pase. Cada pase consume un folio de la bolsa; por eso el contador
 * está siempre a la vista y no se cobra el folio si la generación falla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuePassScreen(
    issuedByUsername: String,
    siteName: String,
    viewModel: IssueViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.issued == null) "Nuevo pase" else "Pase generado") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        val issued = state.issued
        if (issued == null) {
            IssueForm(state, viewModel, issuedByUsername, Modifier.padding(padding))
        } else {
            IssuedPassView(
                pass = issued,
                siteName = siteName,
                onNew = { viewModel.newPass() },
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun IssueForm(
    state: IssueViewModel.State,
    viewModel: IssueViewModel,
    issuedByUsername: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        InfoBanner(
            "Folios disponibles: ${state.tokensRemaining} de ${state.poolSize}",
            tone = if (state.tokensRemaining <= 0) BannerTone.DANGER else BannerTone.INFO
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.fullName,
            onValueChange = { v -> viewModel.update { copy(fullName = v) } },
            label = { Text("Nombre completo *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.carrier,
            onValueChange = { v -> viewModel.update { copy(carrier = v) } },
            label = { Text("Línea transportista *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.plate,
            onValueChange = { v -> viewModel.update { copy(plate = v.uppercase()) } },
            label = { Text("Placas / número de unidad") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.company,
            onValueChange = { v -> viewModel.update { copy(company = v) } },
            label = { Text("Empresa o destino") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.notes,
            onValueChange = { v -> viewModel.update { copy(notes = v) } },
            label = { Text("Observaciones") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("Vigencia del pase", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        DateTimeField(
            label = "Fecha y hora de ingreso",
            millis = state.validFrom,
            onChange = { v -> viewModel.update { copy(validFrom = v) } }
        )
        Spacer(Modifier.height(12.dp))
        DateTimeField(
            label = "Válido hasta",
            millis = state.validUntil,
            onChange = { v -> viewModel.update { copy(validUntil = v) } }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Después de esta fecha el QR ya no permite entrar. La salida siempre se permite, aunque el pase haya vencido.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(it, tone = BannerTone.DANGER)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.issue(issuedByUsername) },
            enabled = !state.busy && state.fullName.isNotBlank() && state.carrier.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.height(20.dp))
            else Text("GENERAR QR", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun IssuedPassView(
    pass: PassEntity,
    siteName: String,
    onNew: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val qrBitmap = remember(pass.payload) { QrRenderer.bitmap(pass.payload, 720) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Folio ${pass.folio}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sirve para 1 entrada y 1 salida",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp)) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Código QR del pase ${pass.folio}",
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                LabelValue("Nombre", pass.fullName)
                LabelValue("Línea transportista", pass.carrier)
                LabelValue("Placas / unidad", pass.plate)
                LabelValue("Empresa / destino", pass.company)
                LabelValue("Ingreso programado", Formats.dateTime(pass.validFrom))
                LabelValue("Válido hasta", Formats.dateTime(pass.validUntil))
                LabelValue("Emitido por", pass.issuedBy)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val credential = QrRenderer.credential(
                    content = pass.payload,
                    siteName = siteName.ifBlank { "Control QR" },
                    folio = pass.folio,
                    fullName = pass.fullName,
                    carrier = pass.carrier,
                    plate = pass.plate,
                    validity = "${Formats.dateTime(pass.validFrom)} a ${Formats.dateTime(pass.validUntil)}"
                )
                Sharing.shareBitmap(
                    context = context,
                    bitmap = credential,
                    fileName = "pase-${pass.folio}.png",
                    subject = "Pase de acceso ${pass.folio}"
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enviar o imprimir credencial")
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) {
                Text("Otro pase")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Terminar")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
