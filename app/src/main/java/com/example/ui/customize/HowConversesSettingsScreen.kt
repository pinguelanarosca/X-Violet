package com.example.ui.customize

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.HubApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowConversesSettingsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = app.settingsRepository

    var globalSystemPrompt by remember { mutableStateOf("") }
    var responseStyle by remember { mutableStateOf("Markdown estruturado") }
    var tonePreset by remember { mutableStateOf("Profissional e Objetivo") }
    var includeCodeHighlighting by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        globalSystemPrompt = settingsRepo.getSetting("global_system_prompt", "Você é um assistente de IA avançado, prestativo e especialista.")
        responseStyle = settingsRepo.getSetting("response_style", "Markdown estruturado")
        tonePreset = settingsRepo.getSetting("tone_preset", "Profissional e Objetivo")
        includeCodeHighlighting = settingsRepo.getSetting("code_highlighting", "true").toBoolean()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações: Como Conversa") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_how_converses")) {
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
            Text(
                text = "Instruções Globais do Sistema e Estilo de Resposta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = globalSystemPrompt,
                onValueChange = { globalSystemPrompt = it },
                label = { Text("Prompt de Sistema Global (Instruções Base)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("input_global_system_prompt"),
                maxLines = 6
            )

            Text("Estilo de Tom e Comportamento", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            val tones = listOf("Profissional e Objetivo", "Amigável e Casual", "Técnico e Acadêmico", "Criativo e Inspirador")
            tones.forEach { tone ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = tonePreset == tone,
                        onClick = { tonePreset = tone },
                        modifier = Modifier.testTag("radio_tone_$tone")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tone)
                }
            }

            HorizontalDivider()

            Text("Formatação de Saída", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            val styles = listOf("Markdown estruturado", "Respostas concisas e diretas", "Explicações detalhadas com exemplos")
            styles.forEach { style ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = responseStyle == style,
                        onClick = { responseStyle = style },
                        modifier = Modifier.testTag("radio_style_$style")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(style)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Realce de Sintaxe em Código", fontWeight = FontWeight.SemiBold)
                    Text("Formata blocos de código com suporte a Markdown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = includeCodeHighlighting,
                    onCheckedChange = { includeCodeHighlighting = it },
                    modifier = Modifier.testTag("switch_code_highlighting")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.setSetting("global_system_prompt", globalSystemPrompt)
                        settingsRepo.setSetting("response_style", responseStyle)
                        settingsRepo.setSetting("tone_preset", tonePreset)
                        settingsRepo.setSetting("code_highlighting", includeCodeHighlighting.toString())
                        Toast.makeText(context, "Configurações de conversação salvas!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_how_converses")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Estilo de Conversa")
            }
        }
    }
}
