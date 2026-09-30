package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.ui.LogsScreen
import com.example.ui.VaultScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.HubIcons
import com.example.ui.components.PulsingLiveDot
import com.example.ui.customize.CustomizeScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.diagnostic.DiagnosticScreen
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HubApplication

        setContent {
            var currentTheme by rememberSaveable { mutableStateOf(ThemeType.STEAMPUNK) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val savedTheme = app.settingsRepository.getSetting("app_theme", ThemeType.STEAMPUNK.name)
                val resolved = runCatching { ThemeType.valueOf(savedTheme) }.getOrDefault(ThemeType.STEAMPUNK)
                currentTheme = resolved
            }

            MyApplicationTheme(themeType = currentTheme) {
                MainContent(
                    app = app,
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                        scope.launch {
                            app.settingsRepository.setSetting("app_theme", newTheme.name)
                        }
                        HubLogger.log(
                            level = LogLevel.INFO,
                            category = LogCategory.UI,
                            message = "Tema alterado para: ${newTheme.name}"
                        )
                    },
                    onToggleFlagSecure = { isSecure ->
                        // Em builds de depuração/emulador, FLAG_SECURE é desativado para evitar congelamento/tela preta no streaming WebRTC
                        if (!BuildConfig.DEBUG && isSecure) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val app = application as? HubApplication
            app?.abnormalShutdownManager?.onAppCleanExit()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    app: HubApplication,
    currentTheme: ThemeType,
    onThemeChange: (ThemeType) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) } // "PROVIDERS_VAULT" or "DIAGNOSTIC"

    var protectKeyScreens by rememberSaveable { mutableStateOf(!BuildConfig.DEBUG) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val saved = app.settingsRepository.getSetting("protect_key_screens", (!BuildConfig.DEBUG).toString())
        protectKeyScreens = saved.toBooleanStrictOrNull() ?: (!BuildConfig.DEBUG)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Check for abnormal shutdown alert
    var showAbnormalCrashDialog by remember { mutableStateOf(app.abnormalShutdownManager.wasAbnormalShutdown) }
    var showCrashDetails by remember { mutableStateOf(false) }

    // Check for database recovery notification
    val dbRecoveryEvent by app.databaseManager.recoveryEvent.collectAsStateWithLifecycle()

    // FLAG_SECURE active strictly on Providers Vault screen and when protectKeyScreens is true
    val isFlagSecureActive = activeSubScreen == "PROVIDERS_VAULT" && protectKeyScreens
    LaunchedEffect(activeSubScreen, protectKeyScreens) {
        onToggleFlagSecure(isFlagSecureActive)
    }

    // Predictive back handling (Rule 8, Rule 12 & targetSdk 36)
    BackHandler(enabled = activeSubScreen != null || selectedTab != 0) {
        if (activeSubScreen != null) {
            val from = activeSubScreen
            activeSubScreen = null
            val correlationId = "NAV_BACK_${System.currentTimeMillis()}"
            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.UI,
                message = "Navegação: Retorno de '$from' para as abas principais ($correlationId)."
            )
        } else if (selectedTab != 0) {
            val fromTab = selectedTab
            selectedTab = 0
            val correlationId = "NAV_BACK_TAB_${System.currentTimeMillis()}"
            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.UI,
                message = "Navegação: Retorno da aba $fromTab para o Chat ($correlationId)."
            )
        }
    }

    // Chat ViewModel initialized with SavedStateHandle
    val chatViewModel: ChatViewModel = viewModel {
        ChatViewModel(SavedStateHandle(), app)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (activeSubScreen != "LIVE_MODE" && activeSubScreen != "LIVE_SESSION" && activeSubScreen != "LIVE_AGENTS") {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (activeSubScreen == null && selectedTab == 0) {
                                PulsingLiveDot(
                                    dotColor = MaterialTheme.colorScheme.primary,
                                    size = 7.dp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = when (activeSubScreen) {
                                    "PROVIDERS_VAULT" -> "Provedores & Cofre"
                                    "DIAGNOSTIC" -> "Diagnóstico do Sistema"
                                    "HOW_SPEAKS" -> "Como Falar"
                                    "HOW_THINKS" -> "Como Pensa"
                                    "WHAT_REMEMBERS" -> "O Que Lembra"
                                    "WHAT_CAN_DO" -> "O Que Pode Fazer"
                                    "HOW_CONVERSES" -> "Como Conversa"
                                    "BRAIN_DASH" -> "Painel: Cérebro"
                                    "MEMORY_DASH" -> "Painel: Memória"
                                    "VOICE_DASH" -> "Painel: Voz & Áudio"
                                    "VISION_DASH" -> "Painel: Visão"
                                    "TOOLS_DASH" -> "Painel: Ferramentas"
                                    "MCP_DASH" -> "Painel MCP: Exa & GitHub"
                                    "PAYLOAD_INSPECTOR" -> "Inspetor de Payload Bruto"
                                    else -> when (selectedTab) {
                                        0 -> "X-Violet Hub"
                                        1 -> stringResource(R.string.tab_dashboard)
                                        2 -> stringResource(R.string.tab_customize)
                                        else -> stringResource(R.string.tab_logs)
                                    }
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 0.5.sp
                            )
                        }
                    },
                    navigationIcon = {
                        if (activeSubScreen != null) {
                            IconButton(
                                onClick = {
                                    val from = activeSubScreen
                                    activeSubScreen = null
                                    val correlationId = "NAV_UP_${System.currentTimeMillis()}"
                                    HubLogger.log(
                                        level = LogLevel.INFO,
                                        category = LogCategory.UI,
                                        message = "Navegação: Botão Voltar de '$from' ($correlationId)."
                                    )
                                },
                                modifier = Modifier.testTag("btn_back_navigation")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Action Live Mode Button
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    activeSubScreen = "LIVE_MODE"
                                    val correlationId = "NAV_LIVE_${System.currentTimeMillis()}"
                                    HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Modo Live ($correlationId).")
                                }
                                .testTag("btn_top_live_mode")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PulsingLiveDot(
                                    dotColor = MaterialTheme.colorScheme.primary,
                                    size = 6.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Modo Live",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Live",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (app.abnormalShutdownManager.lastCrashReport != null) {
                            IconButton(onClick = { showCrashDetails = true }) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Ver último crash",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        },
        bottomBar = {
            Column {
                if (activeSubScreen != "LIVE_MODE" && activeSubScreen != "LIVE_SESSION") {
                    com.example.ui.live.LiveFloatingBar(
                        app = app,
                        onExpandLive = { activeSubScreen = "LIVE_MODE" }
                    )
                }
                if (activeSubScreen == null) {
                    // Modern Floating Capsule Navigation Bar with subtle border and elevation
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        NavigationBar(
                            modifier = Modifier
                                .testTag("bottom_nav_bar")
                                .fillMaxWidth()
                                .height(64.dp),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = {
                                    if (selectedTab != 0) {
                                        selectedTab = 0
                                        val correlationId = "TAB_CHAT_${System.currentTimeMillis()}"
                                        HubLogger.log(LogLevel.INFO, LogCategory.UI, "Troca de aba para 'Chat' ($correlationId).")
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 0) Icons.AutoMirrored.Filled.Chat else Icons.Default.ChatBubbleOutline,
                                        contentDescription = stringResource(R.string.tab_chat)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_chat),
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_item_chat")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = {
                                    if (selectedTab != 1) {
                                        selectedTab = 1
                                        val correlationId = "TAB_DASH_${System.currentTimeMillis()}"
                                        HubLogger.log(LogLevel.INFO, LogCategory.UI, "Troca de aba para 'Painel' ($correlationId).")
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Default.Dashboard else Icons.Default.DashboardCustomize,
                                        contentDescription = stringResource(R.string.tab_dashboard)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_dashboard),
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_item_dashboard")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = {
                                    if (selectedTab != 2) {
                                        selectedTab = 2
                                        val correlationId = "TAB_CUST_${System.currentTimeMillis()}"
                                        HubLogger.log(LogLevel.INFO, LogCategory.UI, "Troca de aba para 'Personalizar' ($correlationId).")
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 2) Icons.Default.Tune else Icons.Default.DisplaySettings,
                                        contentDescription = stringResource(R.string.tab_customize)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_customize),
                                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_item_customize")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = {
                                    if (selectedTab != 3) {
                                        selectedTab = 3
                                        val correlationId = "TAB_LOGS_${System.currentTimeMillis()}"
                                        HubLogger.log(LogLevel.INFO, LogCategory.UI, "Troca de aba para 'Logs' ($correlationId).")
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 3) Icons.AutoMirrored.Filled.ListAlt else Icons.Default.Terminal,
                                        contentDescription = stringResource(R.string.tab_logs)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.tab_logs),
                                        fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_item_logs")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (activeSubScreen == "LIVE_MODE" || activeSubScreen == "LIVE_SESSION") PaddingValues(0.dp) else innerPadding)
        ) {
            when (activeSubScreen) {
                "LIVE_MODE", "LIVE_SESSION" -> com.example.ui.live.LiveScreen(
                    app = app,
                    onNavigateBack = { activeSubScreen = null }
                )
                "PROVIDERS_VAULT" -> VaultScreen(
                    vault = app.keystoreVault,
                    snackbarHostState = snackbarHostState,
                    protectKeyScreens = protectKeyScreens,
                    onToggleProtectKeyScreens = {
                        protectKeyScreens = it
                        scope.launch {
                            app.settingsRepository.setSetting("protect_key_screens", it.toString())
                        }
                    }
                )
                "DIAGNOSTIC" -> DiagnosticScreen(
                    app = app,
                    isFlagSecureActive = isFlagSecureActive,
                    snackbarHostState = snackbarHostState
                )
                "HOW_SPEAKS" -> com.example.ui.customize.HowSpeaksSettingsScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "HOW_THINKS" -> com.example.ui.customize.HowThinksSettingsScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "WHAT_REMEMBERS" -> com.example.ui.customize.WhatRemembersSettingsScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "WHAT_CAN_DO" -> com.example.ui.customize.WhatCanDoSettingsScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "HOW_CONVERSES" -> com.example.ui.customize.HowConversesSettingsScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "BRAIN_DASH" -> com.example.ui.dashboard.BrainDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "MEMORY_DASH" -> com.example.ui.dashboard.MemoryDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "VOICE_DASH" -> com.example.ui.dashboard.VoiceDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "VISION_DASH" -> com.example.ui.dashboard.VisionDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "TOOLS_DASH" -> com.example.ui.dashboard.ToolsDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "MCP_DASH" -> com.example.ui.dashboard.McpDashboardScreen(app = app, onNavigateBack = { activeSubScreen = null })
                "PAYLOAD_INSPECTOR" -> com.example.ui.debug.PayloadInspectorScreen(app = app, onNavigateBack = { activeSubScreen = null })
                else -> when (selectedTab) {
                    0 -> ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToLive = {
                            activeSubScreen = "LIVE_MODE"
                            val correlationId = "NAV_LIVE_${System.currentTimeMillis()}"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Modo Live a partir do Chat ($correlationId).")
                        }
                    )
                    1 -> DashboardScreen(
                        onNavigateToBrain = {
                            activeSubScreen = "BRAIN_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel Cérebro.")
                        },
                        onNavigateToMemory = {
                            activeSubScreen = "MEMORY_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel Memória.")
                        },
                        onNavigateToVoice = {
                            activeSubScreen = "VOICE_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel Voz.")
                        },
                        onNavigateToVision = {
                            activeSubScreen = "VISION_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel Visão.")
                        },
                        onNavigateToTools = {
                            activeSubScreen = "TOOLS_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel Ferramentas.")
                        },
                        onNavigateToMcp = {
                            activeSubScreen = "MCP_DASH"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Painel MCP Exa & GitHub.")
                        },
                        onNavigateToPayloadInspector = {
                            activeSubScreen = "PAYLOAD_INSPECTOR"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Inspetor de Payload Bruto.")
                        },
                        onNavigateToDiagnostic = {
                            activeSubScreen = "DIAGNOSTIC"
                            val correlationId = "NAV_DIAG_${System.currentTimeMillis()}"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Diagnóstico ($correlationId).")
                        }
                    )
                    2 -> CustomizeScreen(
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        onNavigateToProviders = {
                            activeSubScreen = "PROVIDERS_VAULT"
                            val correlationId = "NAV_VAULT_${System.currentTimeMillis()}"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Provedores/Cofre ($correlationId).")
                        },
                        onNavigateToHowSpeaks = {
                            activeSubScreen = "HOW_SPEAKS"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Como Falar.")
                        },
                        onNavigateToHowThinks = {
                            activeSubScreen = "HOW_THINKS"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Como Pensa.")
                        },
                        onNavigateToWhatRemembers = {
                            activeSubScreen = "WHAT_REMEMBERS"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para O Que Lembra.")
                        },
                        onNavigateToWhatCanDo = {
                            activeSubScreen = "WHAT_CAN_DO"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para O Que Pode Faz.")
                        },
                        onNavigateToHowConverses = {
                            activeSubScreen = "HOW_CONVERSES"
                            HubLogger.log(LogLevel.INFO, LogCategory.UI, "Navegação para Como Conversa.")
                        }
                    )
                    3 -> LogsScreen(
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }

    // Abnormal shutdown notification dialog (Rule 1A & Prompt 1B)
    if (showAbnormalCrashDialog) {
        AlertDialog(
            onDismissRequest = {
                showAbnormalCrashDialog = false
                app.abnormalShutdownManager.clearFlag()
            },
            title = { Text(stringResource(R.string.crash_warning_title)) },
            text = { Text(stringResource(R.string.crash_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showAbnormalCrashDialog = false
                    app.abnormalShutdownManager.clearFlag()
                    if (app.abnormalShutdownManager.lastCrashReport != null) {
                        showCrashDetails = true
                    }
                }) {
                    Text(if (app.abnormalShutdownManager.lastCrashReport != null) "Ver Relatório" else stringResource(R.string.btn_dismiss))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAbnormalCrashDialog = false
                    app.abnormalShutdownManager.clearFlag()
                }) {
                    Text(stringResource(R.string.btn_dismiss))
                }
            }
        )
    }

    // Database recovery snackbar event
    LaunchedEffect(dbRecoveryEvent) {
        dbRecoveryEvent?.let { msg ->
            snackbarHostState.showSnackbar("Aviso de Banco: $msg")
            app.databaseManager.clearRecoveryEvent()
        }
    }

    // Modal dialog to view last crash stack trace (sanitized)
    if (showCrashDetails) {
        val report = app.abnormalShutdownManager.lastCrashReport ?: "Sem dados disponíveis."
        AlertDialog(
            onDismissRequest = { showCrashDetails = false },
            title = { Text("Relatório de Crash Sanitizado") },
            text = {
                Column {
                    Text(
                        text = "Rastro do erro sanitizado (chaves e tokens omitidos):",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Text(
                            text = report,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrashDetails = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}
