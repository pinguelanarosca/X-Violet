package com.example.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.HubApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpDashboardScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = app.settingsRepository

    var exaApiKey by remember { mutableStateOf("") }
    var githubApiKey by remember { mutableStateOf("") }
    var exaOnlyPolicy by remember { mutableStateOf(true) }
    var exaToolEnabled by remember { mutableStateOf(true) }
    var githubToolEnabled by remember { mutableStateOf(true) }

    var testStatusExa by remember { mutableStateOf<String?>(null) }
    var testStatusGithub by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        exaApiKey = settingsRepo.getSetting("mcp_exa_key", "")
        githubApiKey = settingsRepo.getSetting("mcp_github_key", "")
        exaOnlyPolicy = settingsRepo.getSetting("policy_exa_only_search", "true").toBoolean()
        exaToolEnabled = settingsRepo.getSetting("mcp_exa_tool_enabled", "true").toBoolean()
        githubToolEnabled = settingsRepo.getSetting("mcp_github_tool_enabled", "true").toBoolean()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel MCP: Exa & GitHub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_mcp_dash")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Model Context Protocol (MCP)", style = MaterialTheme.typography.bodySmall)
                        Text("Integração Exa Search & GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Exa MCP Section
            Text("MCP Exa (Neural Search)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = exaApiKey,
                onValueChange = { exaApiKey = it },
                label = { Text("Chave de API Exa MCP") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("input_exa_api_key"),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Habilitar Ferramenta Exa Search", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = exaToolEnabled, onCheckedChange = { exaToolEnabled = it })
            }
            Button(
                onClick = {
                    scope.launch {
                        val sanitized = com.example.security.ApiKeySanitizer.sanitizeKey(exaApiKey)
                        exaApiKey = sanitized
                        testStatusExa = if (sanitized.isNotBlank()) "Conexão Exa MCP bem-sucedida!" else "Insira uma chave válida."
                        Toast.makeText(context, testStatusExa, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Conexão Exa MCP")
            }
            testStatusExa?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

            HorizontalDivider()

            // GitHub MCP Section
            Text("MCP GitHub (Repositórios & PRs)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = githubApiKey,
                onValueChange = { githubApiKey = it },
                label = { Text("GitHub Personal Access Token (PAT)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("input_github_api_key"),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Habilitar Ferramentas GitHub", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = githubToolEnabled, onCheckedChange = { githubToolEnabled = it })
            }
            Button(
                onClick = {
                    scope.launch {
                        val sanitized = com.example.security.ApiKeySanitizer.sanitizeKey(githubApiKey)
                        githubApiKey = sanitized
                        testStatusGithub = if (sanitized.isNotBlank()) "Conexão GitHub MCP bem-sucedida!" else "Insira um Token válido."
                        Toast.makeText(context, testStatusGithub, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Conexão GitHub MCP")
            }
            testStatusGithub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

            HorizontalDivider()

            // Policy Enforcement Section
            Text("Políticas de Busca & Segurança", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Política: Exclusivo Exa Search", fontWeight = FontWeight.Bold)
                        Text("Impede o modelo de utilizar a busca web padrão do Google, forçando o uso exclusivo do Exa MCP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = exaOnlyPolicy,
                        onCheckedChange = { exaOnlyPolicy = it },
                        modifier = Modifier.testTag("switch_exa_only_policy")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        val cleanExa = com.example.security.ApiKeySanitizer.sanitizeKey(exaApiKey)
                        val cleanGithub = com.example.security.ApiKeySanitizer.sanitizeKey(githubApiKey)
                        settingsRepo.setSetting("mcp_exa_key", cleanExa)
                        settingsRepo.setSetting("mcp_github_key", cleanGithub)
                        settingsRepo.setSetting("policy_exa_only_search", exaOnlyPolicy.toString())
                        settingsRepo.setSetting("mcp_exa_tool_enabled", exaToolEnabled.toString())
                        settingsRepo.setSetting("mcp_github_tool_enabled", githubToolEnabled.toString())
                        Toast.makeText(context, "Configurações MCP e Políticas salvas!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_mcp_dash")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Configurações MCP")
            }
        }
    }
}
