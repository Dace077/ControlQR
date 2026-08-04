package com.controlqr.acceso.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.ReportBucket
import com.controlqr.acceso.data.ReportPeriod
import com.controlqr.acceso.ui.components.SectionTitle
import com.controlqr.acceso.ui.components.StatTile
import com.controlqr.acceso.ui.theme.VerdeAcceso
import com.controlqr.acceso.ui.vm.AdminViewModel

/**
 * Reportes de entradas y salidas por día, semana y mes, con la ocupación y la
 * permanencia media. Las barras son comparativas dentro del propio periodo: lo que
 * importa en una caseta es ver qué días se salieron de lo normal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes") },
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
        ) {
            TabRow(selectedTabIndex = ReportPeriod.entries.indexOf(period)) {
                ReportPeriod.entries.forEach { option ->
                    Tab(
                        selected = option == period,
                        onClick = { viewModel.loadReport(option) },
                        text = { Text(option.title) }
                    )
                }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            val summary = report
            if (summary == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Calculando…")
                }
                return@Column
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionTitle("Resumen del periodo")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        value = summary.totalEntries.toString(),
                        label = "Entradas",
                        accent = VerdeAcceso,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = summary.totalExits.toString(),
                        label = "Salidas",
                        accent = Color(0xFF1565C0),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        value = summary.currentlyInside.toString(),
                        label = "Adentro ahora",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = Formats.duration(summary.avgDwellMinutes),
                        label = "Permanencia media",
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionTitle("Entradas y salidas")
                BarChart(summary.buckets, summary.maxBucketValue)

                SectionTitle("Detalle")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        TableHeader()
                        summary.buckets.asReversed().forEach { bucket ->
                            TableRow(bucket)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "«QR activos» son los pases cuya vigencia cubre ese periodo. «Pendientes» son entradas sin salida registrada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BarChart(buckets: List<ReportBucket>, maxValue: Int) {
    val safeMax = maxValue.coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEach { bucket ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Bar(bucket.entries, safeMax, VerdeAcceso)
                            Bar(bucket.exits, safeMax, Color(0xFF1565C0))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                buckets.forEach { bucket ->
                    Text(
                        text = bucket.label.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot("Entradas", VerdeAcceso)
                LegendDot("Salidas", Color(0xFF1565C0))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Bar(value: Int, max: Int, color: Color) {
    val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight(fraction.coerceAtLeast(0.02f))
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
    )
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(12.dp)
                .height(12.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Cell("Periodo", 2f, bold = true)
        Cell("Ent.", 1f, bold = true)
        Cell("Sal.", 1f, bold = true)
        Cell("QR", 1f, bold = true)
        Cell("Pend.", 1f, bold = true)
    }
}

@Composable
private fun TableRow(bucket: ReportBucket) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Cell(bucket.label, 2f)
        Cell(bucket.entries.toString(), 1f)
        Cell(bucket.exits.toString(), 1f)
        Cell(bucket.activeQr.toString(), 1f)
        Cell(bucket.pending.toString(), 1f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (bold) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}
