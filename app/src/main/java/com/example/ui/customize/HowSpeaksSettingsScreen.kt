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
fun HowSpeaksSettingsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = app.settingsRepository

    var voiceTimbre by remember { mutableStateOf("Puck (Neutro/Equilibrado)") }
    var speechSpeed by remember { mutableFloatStateOf(1.0f) }
    var audioOutputRoute by remember { mutableStateOf("Alto-falante") }
    var autoReadEnabled by remember { mutableStateOf(false) }
    var ttsProvider by remember { mutableStateOf("Gemini TTS Nativo") }

    LaunchedEffect(Unit) {
        voiceTimbre = settingsRepo.getSetting("voice_timbre", "Puck (Neutro/Equilibrado)")
        speechSpeed = settingsRepo.getSetting("speech_speed", "1.0").toFloatOrNull() ?: 1.0f
        audioOutputRoute = settingsRepo.getSetting("audio_output_route", "Alto-falante")
        autoReadEnabled = settingsRepo.getSetting("auto_read_enabled", "false").toBoolean()
        ttsProvider = settingsRepo.getSetting("tts_provider", "Gemini TTS Nativo")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações: Como Falar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_how_speaks")) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Parâmetros Avançados de Voz e Áudio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Timbre de Voz
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Timbre de Voz (Live & TTS)", fontWeight = FontWeight.SemiBold)
                val voices = listOf("Puck (Neutro/Equilibrado)", "Charon (Grave/Firme)", "Kore (Claro/Suave)", "Fenrir (Robusto)", "Aoede (Melódico)")
                voices.forEach { voice ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = voiceTimbre == voice,
                            onClick = { voiceTimbre = voice },
                            modifier = Modifier.testTag("radio_voice_$voice")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(voice)
                    }
                }
            }

            HorizontalDivider()

            // Velocidade da Fala
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Velocidade da Fala", fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.1fx", speechSpeed), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = speechSpeed,
                    onValueChange = { speechSpeed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    modifier = Modifier.testTag("slider_speech_speed")
                )
            }

            HorizontalDivider()

            // Rota de Áudio
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rota Padrão de Saída de Áudio", fontWeight = FontWeight.SemiBold)
                val routes = listOf("Alto-falante", "Auricular (Chamada)", "Fone Bluetooth SCO")
                routes.forEach { route ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioOutputRoute == route,
                            onClick = { audioOutputRoute = route },
                            modifier = Modifier.testTag("radio_route_$route")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(route)
                    }
                }
            }

            HorizontalDivider()

            // Leitura Automática e Provedor TTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Leitura Automática de Respostas", fontWeight = FontWeight.SemiBold)
                    Text("Lê em voz alta as respostas de texto recebidas do modelo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoReadEnabled,
                    onCheckedChange = { autoReadEnabled = it },
                    modifier = Modifier.testTag("switch_auto_read")
                )
            }

            OutlinedTextField(
                value = ttsProvider,
                onValueChange = { ttsProvider = it },
                label = { Text("Mecanismo TTS (Text-to-Speech)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.setSetting("voice_timbre", voiceTimbre)
                        settingsRepo.setSetting("speech_speed", speechSpeed.toString())
                        settingsRepo.setSetting("audio_output_route", audioOutputRoute)
                        settingsRepo.setSetting("auto_read_enabled", autoReadEnabled.toString())
                        settingsRepo.setSetting("tts_provider", ttsProvider)
                        Toast.makeText(context, "Configurações de fala salvas com sucesso!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_how_speaks")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Configurações de Fala")
            }
        }
    }
}
