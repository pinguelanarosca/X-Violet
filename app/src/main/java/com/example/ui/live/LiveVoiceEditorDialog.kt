package com.example.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.live.LiveAgentRepository
import com.example.live.LiveVoiceModel

@Composable
fun LiveVoiceEditorDialog(
    voiceModelToEdit: LiveVoiceModel? = null,
    liveAgentRepository: LiveAgentRepository,
    onDismiss: () -> Unit,
    onVoiceModelSaved: (LiveVoiceModel) -> Unit
) {
    var name by remember { mutableStateOf(voiceModelToEdit?.name ?: "Novo Modelo de Voz") }
    var baseVoice by remember { mutableStateOf(voiceModelToEdit?.baseVoice ?: "Puck") }
    var pitch by remember { mutableFloatStateOf(voiceModelToEdit?.pitch ?: 1.0f) }
    var speed by remember { mutableFloatStateOf(voiceModelToEdit?.speed ?: 1.0f) }
    var warmth by remember { mutableFloatStateOf(voiceModelToEdit?.warmth ?: 0.5f) }
    var energy by remember { mutableFloatStateOf(voiceModelToEdit?.energy ?: 0.6f) }
    var accentOrStyle by remember { mutableStateOf(voiceModelToEdit?.accentOrStyle ?: "Neutro") }
    var styleDescription by remember { mutableStateOf(voiceModelToEdit?.styleDescription ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .testTag("dialog_voice_editor")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (voiceModelToEdit == null) "Gerar Modelo de Voz" else "Editar Modelo de Voz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Controle granular de tom, cadência, energia e estilo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_voice_editor")) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Nome do Modelo de Voz
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Modelo de Voz") },
                        placeholder = { Text("Ex: Narrador Épico, Mentor Sábio, etc.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_voice_name")
                    )

                    // Timbre Base Oficial do Gemini Live
                    Text("Timbre de Base Oficial", fontWeight = FontWeight.SemiBold)
                    val baseVoices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        baseVoices.forEach { voice ->
                            FilterChip(
                                selected = baseVoice == voice,
                                onClick = { baseVoice = voice },
                                label = { Text(voice) },
                                modifier = Modifier.testTag("chip_base_voice_$voice")
                            )
                        }
                    }

                    // Pitch / Tom
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tom Vocal (Pitch)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "${String.format("%.2f", pitch)}x (${if (pitch < 0.95f) "Grave" else if (pitch > 1.05f) "Agudo" else "Natural"})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = pitch,
                            onValueChange = { pitch = it },
                            valueRange = 0.5f..1.5f,
                            modifier = Modifier.testTag("slider_voice_pitch")
                        )
                    }

                    // Velocidade / Rate
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Velocidade da Fala (Speed)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "${String.format("%.2f", speed)}x (${if (speed < 0.95f) "Cadenciada/Lenta" else if (speed > 1.05f) "Rápida/Dinâmica" else "Normal"})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = speed,
                            onValueChange = { speed = it },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.testTag("slider_voice_speed")
                        )
                    }

                    // Warmth / Calor
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ressonância / Calor Vocal", fontWeight = FontWeight.Medium)
                            Text(
                                text = "${String.format("%.2f", warmth)} (${if (warmth > 0.7f) "Caloroso & Humano" else if (warmth < 0.3f) "Frio & Sintético" else "Equilibrado"})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = warmth,
                            onValueChange = { warmth = it },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.testTag("slider_voice_warmth")
                        )
                    }

                    // Energy / Intensidade
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Energia & Projeção Vocal", fontWeight = FontWeight.Medium)
                            Text(
                                text = "${String.format("%.2f", energy)} (${if (energy > 0.7f) "Entusiasmado / Projetado" else if (energy < 0.3f) "Suave / Introspectivo" else "Moderado"})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Slider(
                            value = energy,
                            onValueChange = { energy = it },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.testTag("slider_voice_energy")
                        )
                    }

                    // Estilo / Sotaque / Entonação
                    Text("Estilo Narrativo / Entonação", fontWeight = FontWeight.SemiBold)
                    val styles = listOf(
                        "Neutro",
                        "Narrativo / Épico",
                        "Formal & Solene",
                        "Empático / Acolhedor",
                        "Dinâmico & Técnico",
                        "Coloquial & Descontraído"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        styles.chunked(2).forEach { rowStyles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowStyles.forEach { st ->
                                    FilterChip(
                                        selected = accentOrStyle == st,
                                        onClick = { accentOrStyle = st },
                                        label = { Text(st) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Descrição do Estilo Vocal em Linguagem Natural
                    OutlinedTextField(
                        value = styleDescription,
                        onValueChange = { styleDescription = it },
                        label = { Text("Instrução Vocal para o Gemini Live") },
                        placeholder = { Text("Ex: Fale com entonação cinematográfica, dicção perfeita, pausas solenes e timbre aveludado.") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("input_voice_style_desc")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val saved = if (voiceModelToEdit != null) {
                                val updated = voiceModelToEdit.copy(
                                    name = name.ifBlank { "Modelo ${baseVoice}" },
                                    baseVoice = baseVoice,
                                    pitch = pitch,
                                    speed = speed,
                                    warmth = warmth,
                                    energy = energy,
                                    accentOrStyle = accentOrStyle,
                                    styleDescription = styleDescription,
                                    isCustom = true
                                )
                                liveAgentRepository.saveVoiceModel(updated)
                                updated
                            } else {
                                liveAgentRepository.generateVoiceModel(
                                    name = name.ifBlank { "Modelo ${baseVoice}" },
                                    baseVoice = baseVoice,
                                    pitch = pitch,
                                    speed = speed,
                                    warmth = warmth,
                                    energy = energy,
                                    accentOrStyle = accentOrStyle,
                                    styleDescription = styleDescription
                                )
                            }
                            onVoiceModelSaved(saved)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_save_voice_model")
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (voiceModelToEdit == null) "Gerar Modelo de Voz" else "Salvar Alterações")
                    }
                }
            }
        }
    }
}
