package com.example

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.example.agents.AgentRepository
import com.example.errors.AbnormalShutdownManager
import com.example.history.HistoryRepository
import com.example.live.LiveSessionManager
import com.example.logging.CrashHandler
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.memory.MemoryRepository
import com.example.network.ConnectivityObserver
import com.example.network.OkHttpClientFactory
import com.example.providers.ProviderRegistry
import com.example.resilience.CircuitBreaker
import com.example.resilience.RetryPolicy
import com.example.security.AndroidKeyStoreCryptoEngine
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.settings.SettingsRepository
import com.example.state.AppStateManager
import com.example.storage.DatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class HubApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var keystoreVault: KeystoreVault
        private set

    lateinit var vaultBlobStorage: VaultBlobStorage
        private set

    lateinit var providerRegistry: ProviderRegistry
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var agentRepository: AgentRepository
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    lateinit var customModelRepository: com.example.models.CustomModelRepository
        private set

    lateinit var workflowRepository: com.example.workflow.WorkflowRepository
        private set

    lateinit var workflowEngine: com.example.workflow.WorkflowEngine
        private set

    lateinit var toolRegistry: com.example.tools.ToolRegistry
        private set

    lateinit var appStateManager: AppStateManager
        private set

    lateinit var connectivityObserver: ConnectivityObserver
        private set

    lateinit var abnormalShutdownManager: AbnormalShutdownManager
        private set

    lateinit var globalOkHttpClient: OkHttpClient
        private set

    lateinit var liveSessionManager: LiveSessionManager
        private set

    lateinit var audioRoutingManager: com.example.audio.AudioRoutingManager
        private set

    lateinit var cameraVisionManager: com.example.vision.CameraVisionManager
        private set

    lateinit var hyperOsBatteryManager: com.example.live.HyperOsBatteryManager
        private set

    lateinit var liveAgentRepository: com.example.live.LiveAgentRepository
        private set

    val globalRetryPolicy = RetryPolicy()
    val geminiCircuitBreaker = CircuitBreaker("Gemini")
    val openAiCircuitBreaker = CircuitBreaker("OpenAI")
    val anthropicCircuitBreaker = CircuitBreaker("Anthropic")
    val groqCircuitBreaker = CircuitBreaker("Groq")
    val openRouterCircuitBreaker = CircuitBreaker("OpenRouter")
    val xAiCircuitBreaker = CircuitBreaker("xAI")

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. StrictMode enabled in debug builds, logging violations only (Rule 17)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // 2. Set up synchronous CrashHandler before anything else
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this, previousHandler))

        // 3. Initialize Database Manager (handles integrity checks & corrupt recovery)
        databaseManager = DatabaseManager(this, appScope)

        // 4. Initialize Logger with Room LogDao
        HubLogger.init(databaseManager.database, appScope)
        HubLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.APP,
            message = "Hub Application iniciado. Android SDK: ${android.os.Build.VERSION.SDK_INT}, Modelo: ${android.os.Build.MODEL}"
        )

        // 5. Initialize Abnormal Shutdown Manager
        abnormalShutdownManager = AbnormalShutdownManager(this).apply {
            onAppStart()
        }

        // 6. Initialize Keystore Vault with atomic blob storage and production AndroidKeyStore
        vaultBlobStorage = VaultBlobStorage(this)
        keystoreVault = KeystoreVault(
            vaultDao = databaseManager.database.vaultDao(),
            blobStorage = vaultBlobStorage,
            cryptoEngine = AndroidKeyStoreCryptoEngine()
        )
        appScope.launch {
            keystoreVault.reconcileOrphanBlobs()
        }

        // 7. Initialize Repositories and Registry
        providerRegistry = ProviderRegistry(
            keystoreVault = keystoreVault,
            aiModelDao = databaseManager.database.aiModelDao(),
            appSettingDao = databaseManager.database.appSettingDao()
        )
        historyRepository = HistoryRepository(
            databaseManager.database.conversationDao(),
            databaseManager.database.messageDao()
        )
        settingsRepository = SettingsRepository(databaseManager.database.appSettingDao())
        agentRepository = AgentRepository(databaseManager.database.agentDao())
        memoryRepository = MemoryRepository(databaseManager.database.memoryDao())
        customModelRepository = com.example.models.CustomModelRepository(databaseManager.database.customModelDao())
        workflowRepository = com.example.workflow.WorkflowRepository(databaseManager.database.workflowDao())
        workflowEngine = com.example.workflow.WorkflowEngine(this)
        toolRegistry = com.example.tools.ToolRegistry(this)
        appStateManager = AppStateManager()

        appScope.launch {
            workflowRepository.initializePreCreatedWorkflows()
        }

        // 8. Initialize Network and Connectivity Observer
        globalOkHttpClient = OkHttpClientFactory.create()
        connectivityObserver = ConnectivityObserver(this)

        // 9. Initialize Live Session Manager, Audio Routing, and Battery Manager
        liveSessionManager = com.example.live.LiveSessionManager(
            context = this,
            keystoreVault = keystoreVault,
            historyRepository = historyRepository,
            connectivityObserver = connectivityObserver,
            okHttpClient = globalOkHttpClient
        )
        audioRoutingManager = com.example.audio.AudioRoutingManager(this)
        cameraVisionManager = com.example.vision.CameraVisionManager(this).apply {
            onFrameCaptured = { jpegBytes ->
                liveSessionManager.sendVideoFrameToWebSocket(jpegBytes)
            }
        }
        hyperOsBatteryManager = com.example.live.HyperOsBatteryManager(this)
        liveAgentRepository = com.example.live.LiveAgentRepository(settingsRepository, appScope)
        liveSessionManager.liveAgentRepository = liveAgentRepository

        HubLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.SECURITY,
            message = "Cofre de chaves Keystore inicializado com sucesso (AES-256-GCM)."
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        HubLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.APP,
            message = "Alerta de consumo de memória do sistema: onTrimMemory level=$level"
        )
    }

    companion object {
        lateinit var instance: HubApplication
            private set

        fun get(): HubApplication = instance
    }
}
