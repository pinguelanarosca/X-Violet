package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.storage.entities.LogEntryEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val logs by HubLogger.getLogsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedLevel, selectedCategory, searchQuery) {
        logs.filter { entry ->
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel?.name
            val matchesCategory = selectedCategory == null || entry.category == selectedCategory?.name
            val matchesQuery = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    (entry.correlationId?.contains(searchQuery, ignoreCase = true) == true) ||
                    (entry.dataJson?.contains(searchQuery, ignoreCase = true) == true)
            matchesLevel && matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("logs_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Action Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Logs (${filteredLogs.size}/${logs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                // Copy to clipboard
                IconButton(
                    onClick = {
                        val textToCopy = buildString {
                            filteredLogs.forEach { entry ->
                                val date = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
                                appendLine("[$date] [${entry.level}] [${entry.category}] ${entry.message}")
                            }
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        scope.launch {
                            snackbarHostState.showSnackbar("Logs copiados para a área de transferência!")
                        }
                    },
                    modifier = Modifier.testTag("btn_copy_logs")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(R.string.btn_copy_logs))
                }

                // Export TXT
                IconButton(
                    onClick = {
                        scope.launch {
                            val file = HubLogger.exportLogsAsText(context)
                            snackbarHostState.showSnackbar("Logs exportados em: ${file.name}")
                        }
                    },
                    modifier = Modifier.testTag("btn_export_txt")
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = stringResource(R.string.btn_export_txt))
                }

                // Clear
                IconButton(
                    onClick = {
                        scope.launch {
                            HubLogger.clearLogs()
                            snackbarHostState.showSnackbar("Logs limpos com sucesso.")
                        }
                    },
                    modifier = Modifier.testTag("btn_clear_logs")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.btn_clear_logs))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.logs_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("logs_search_field"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Level Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text(stringResource(R.string.logs_filter_all)) }
                )
            }
            items(LogLevel.values()) { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = {
                        selectedLevel = if (selectedLevel == level) null else level
                    },
                    label = { Text(level.name) }
                )
            }
        }

        // Category Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Todas Categorias") }
                )
            }
            items(LogCategory.values()) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    },
                    label = { Text(cat.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs list
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum log corresponde aos filtros atuais.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = filteredLogs,
                    key = { it.id }
                ) { entry ->
                    LogEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntryEntity) {
    var isExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = remember(entry.timestamp) { dateFormat.format(Date(entry.timestamp)) }

    val levelColor = when (entry.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARN" -> MaterialTheme.colorScheme.tertiary
        "INFO" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = levelColor.copy(alpha = 0.2f),
                        contentColor = levelColor
                    ) {
                        Text(
                            text = entry.level,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = entry.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (entry.correlationId != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${entry.correlationId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (entry.dataJson != null) {
                        Text(
                            text = "Dados:\n${entry.dataJson}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (entry.throwableSanitized != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Causa Técnica Sanitizada:\n${entry.throwableSanitized}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
