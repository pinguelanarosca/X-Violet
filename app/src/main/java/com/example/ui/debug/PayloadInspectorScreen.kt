package com.example.ui.debug

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadInspectorScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rawRequestJson by remember { mutableStateOf("{\n  \"model\": \"gemini-2.5-flash\",\n  \"contents\": [\n    {\n      \"role\": \"user\",\n      \"parts\": [{ \"text\": \"Olá, teste de payload bruto\" }]\n    }\n  ]\n}") }
    var rawResponseJson by remember { mutableStateOf("{\n  \"candidates\": [\n    {\n      \"content\": {\n        \"parts\": [{ \"text\": \"Olá! Resposta simulada do payload bruto.\" }]\n      },\n      \"finishReason\": \"STOP\"\n    }\n  ]\n}") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspetor de Payload Bruto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_payload_inspector")) {
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
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Visualização e Edição de Payload JSON", style = MaterialTheme.typography.bodySmall)
                        Text("Inspeção de Requisições e Respostas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Payload de Requisição HTTP (Request JSON)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = rawRequestJson,
                onValueChange = { rawRequestJson = it },
                label = { Text("JSON de Requisição Bruta") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("input_raw_request_json"),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        Toast.makeText(context, "Payload enviado para simulação de inferência!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reenviar Bruto")
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Request JSON", rawRequestJson))
                        Toast.makeText(context, "Request copiado para a área de transferência", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar Request")
                }
            }

            HorizontalDivider()

            Text("Payload de Resposta HTTP (Response JSON)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = rawResponseJson,
                onValueChange = { rawResponseJson = it },
                label = { Text("JSON de Resposta Bruta") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("input_raw_response_json"),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Response JSON", rawResponseJson))
                    Toast.makeText(context, "Response copiado para a área de transferência", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar Response")
            }
        }
    }
}
