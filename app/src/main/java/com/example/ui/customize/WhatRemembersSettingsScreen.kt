package com.example.ui.customize

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.HubApplication
import com.example.storage.entities.MemoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatRemembersSettingsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memoryRepo = app.memoryRepository

    var sessionMemEnabled by remember { mutableStateOf(true) }
    var conversationMemEnabled by remember { mutableStateOf(true) }
    var projectMemEnabled by remember { mutableStateOf(true) }
    var preferencesMemEnabled by remember { mutableStateOf(true) }
    var persistentMemEnabled by remember { mutableStateOf(true) }
    var privateMode by remember { mutableStateOf(false) }

    val pendingSuggestions by memoryRepo.pendingSuggestionsFlow.collectAsState(initial = emptyList())
    val approvedMemories by memoryRepo.approvedMemoriesFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações: O Que Lembra") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_what_remembers")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Gerenciamento de Memória de Longo Prazo e Privacidade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Private Mode Banner / Toggle
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (privateMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Modo Privado (Zero Memória)", fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = privateMode,
                                onCheckedChange = {
                                    privateMode = it
                                    if (it) {
                                        sessionMemEnabled = false
                                        conversationMemEnabled = false
                                        projectMemEnabled = false
                                        preferencesMemEnabled = false
                                        persistentMemEnabled = false
                                    }
                                },
                                modifier = Modifier.testTag("switch_private_mode_mem")
                            )
                        }
                        Text(
                            text = "Quando ativo, a memória de longo prazo e o salvamento de histórico ficam completamente desativados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("Categorias de Memória Ativas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemoryCategoryToggle("Sessão", sessionMemEnabled, !privateMode) { sessionMemEnabled = it }
                    MemoryCategoryToggle("Conversa", conversationMemEnabled, !privateMode) { conversationMemEnabled = it }
                    MemoryCategoryToggle("Projeto", projectMemEnabled, !privateMode) { projectMemEnabled = it }
                    MemoryCategoryToggle("Preferências", preferencesMemEnabled, !privateMode) { preferencesMemEnabled = it }
                    MemoryCategoryToggle("Persistente (Global)", persistentMemEnabled, !privateMode) { persistentMemEnabled = it }
                }
            }

            item {
                HorizontalDivider()
            }

            // Pending Suggestions Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sugestões Pendentes (${pendingSuggestions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Text("Memórias sugeridas por anexos ou ferramentas que exigem sua aprovação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (pendingSuggestions.isEmpty()) {
                item {
                    Text("Nenhuma sugestão pendente no momento.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(pendingSuggestions) { suggestion ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(suggestion.content, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    scope.launch {
                                        memoryRepo.rejectOrDeleteMemory(suggestion.id)
                                        Toast.makeText(context, "Sugestão rejeitada", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("Rejeitar", color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        memoryRepo.approvePendingMemory(suggestion.id)
                                        Toast.makeText(context, "Sugestão aprovada e salva na memória!", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aprovar")
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            // Approved Memories List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Memórias Aprovadas Armazenadas (${approvedMemories.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (approvedMemories.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                memoryRepo.clearAll()
                                Toast.makeText(context, "Todas as memórias foram limpas", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Limpar Tudo", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (approvedMemories.isEmpty()) {
                item {
                    Text("Nenhuma memória de longo prazo salva.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(approvedMemories) { mem ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Categoria: ${mem.category} • Origem: ${mem.origin}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    memoryRepo.rejectOrDeleteMemory(mem.id)
                                    Toast.makeText(context, "Memória apagada", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCategoryToggle(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Switch(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
