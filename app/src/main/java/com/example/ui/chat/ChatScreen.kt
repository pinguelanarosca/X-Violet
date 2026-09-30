package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HubApplication
import com.example.R
import com.example.history.ExportManager
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.MessageEntity
import com.example.ui.components.AppErrorBanner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToLive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val draftText by viewModel.draftText.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val assistantStatus by viewModel.assistantStatus.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeModelId.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val attachments by viewModel.activeAttachments.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context) }

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addAttachmentFromUri(context, it) }
    }

    val isGenerating = generationState is GenerationState.Connecting ||
            generationState is GenerationState.Connected ||
            generationState is GenerationState.Thinking ||
            generationState is GenerationState.Receiving

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showOptions by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount < -50) showOptions = true
                        else if (dragAmount > 50) showOptions = false
                        change.consume()
                    }
                )
            }
    ) {
        // Main Chat Area
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    EmptyConversationPlaceholder(providerDisplayName = activeProviderId.uppercase())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItemCard(
                                message = message,
                                timeFormatted = timeFormat.format(Date(message.createdAt)),
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Mensagem", message.content))
                                    scope.launch { snackbarHostState.showSnackbar("Mensagem copiada.") }
                                },
                                onDelete = { viewModel.deleteMessage(message.id) },
                                onEdit = { if (message.role == "user") editingMessage = message },
                                onRegenerate = { viewModel.regenerateLast() },
                                onContinue = { viewModel.continueGeneration(message.id) }
                            )
                        }
                    }
                }
            }

            // Input Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Anexar")
                    }
                    TextField(
                        value = draftText,
                        onValueChange = { viewModel.onDraftChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mensagem...") },
                        shape = RoundedCornerShape(28.dp)
                    )
                    IconButton(onClick = { viewModel.sendMessage() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
        
        // Options Screen Overlay
        AnimatedVisibility(visible = showOptions, modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Configurações do Chat", style = MaterialTheme.typography.headlineMedium)
                    // Add options here
                }
            }
        }
    }
}

@Composable
private fun MessageItemCard(
    message: MessageEntity,
    timeFormatted: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit
) {
    // ... (implementation)
}

@Composable
private fun EmptyConversationPlaceholder(providerDisplayName: String) {
    // ... (implementation)
}

// ... (etc for StatusChip, HistorySessionsDialog)
