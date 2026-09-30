package com.example.workflow

import android.content.Context
import com.example.HubApplication
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.storage.dao.WorkflowDao
import com.example.storage.entities.WorkflowEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
sealed class WorkflowStep {
    @Serializable
    data class CallTool(
        val toolName: String,
        val argumentsJson: String,
        val timeoutMs: Long = 10000L,
        val requiresConfirmation: Boolean = false
    ) : WorkflowStep()

    @Serializable
    data class WaitStep(
        val durationMs: Long
    ) : WorkflowStep()

    @Serializable
    data class SimpleCondition(
        val conditionType: String, // e.g. "last_output_contains"
        val expectedValue: String,
        val onSuccessStepIndex: Int,
        val onFailureStepIndex: Int
    ) : WorkflowStep()

    @Serializable
    data class SelectProfileAndModel(
        val profileId: String,
        val modelId: String
    ) : WorkflowStep()

    @Serializable
    data class ConfigureAudio(
        val source: String, // Speaker, Earpiece, etc.
        val isMuted: Boolean
    ) : WorkflowStep()

    @Serializable
    data class StartLiveSession(
        val liveModelId: String
    ) : WorkflowStep()
}

class WorkflowRepository(
    private val workflowDao: WorkflowDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val workflowsFlow: Flow<List<WorkflowEntity>> = workflowDao.getAllWorkflowsFlow()

    suspend fun initializePreCreatedWorkflows() = withContext(Dispatchers.IO) {
        val existing = workflowDao.getAllWorkflowsList()
        if (existing.isEmpty()) {
            // 1. Pre-created "Reunião"
            val reunionSteps = listOf(
                WorkflowStep.SelectProfileAndModel("profile_coding", "gemini-2.5-flash"),
                WorkflowStep.ConfigureAudio("Alto-falante", false),
                WorkflowStep.StartLiveSession("gemini-2.5-flash-live"),
                WorkflowStep.WaitStep(1000L)
            )

            // 2. Pre-created "Pesquisa profunda"
            val deepResearchSteps = listOf(
                WorkflowStep.CallTool("web_search", "{\"query\":\"últimas notícias sobre IA\"}", timeoutMs = 15000L),
                WorkflowStep.SimpleCondition("last_output_contains", "IA", onSuccessStepIndex = 3, onFailureStepIndex = 2),
                WorkflowStep.CallTool("web_search", "{\"query\":\"pesquisa complementar IA\"}", timeoutMs = 15000L),
                WorkflowStep.WaitStep(500L)
            )

            val reunion = WorkflowEntity(
                id = "wf_reunion",
                name = "Reunião",
                description = "Seleciona perfil e modelo, inicia a sessão Live de voz, configura o áudio no alto-falante e ativa o contexto.",
                stepsJson = json.encodeToString(reunionSteps),
                isActive = true
            )

            val deepResearch = WorkflowEntity(
                id = "wf_deep_research",
                name = "Pesquisa profunda",
                description = "Busca na web, analisa de forma condicional se contém resultados e executa buscas adicionais filtradas.",
                stepsJson = json.encodeToString(deepResearchSteps),
                isActive = true
            )

            workflowDao.insert(reunion)
            workflowDao.insert(deepResearch)
            HubLogger.log(LogLevel.INFO, LogCategory.APP, "Workflows pré-criados (Reunião e Pesquisa Profunda) inicializados.")
        }
    }

    suspend fun saveWorkflow(workflow: WorkflowEntity) = withContext(Dispatchers.IO) {
        workflowDao.insert(workflow.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteWorkflow(id: String) = withContext(Dispatchers.IO) {
        workflowDao.deleteById(id)
    }

    suspend fun getWorkflowById(id: String): WorkflowEntity? = withContext(Dispatchers.IO) {
        workflowDao.getById(id)
    }
}

sealed class WorkflowExecutionState {
    object Idle : WorkflowExecutionState()
    data class Executing(val workflowName: String, val currentStepIndex: Int, val totalSteps: Int, val currentStepDescription: String) : WorkflowExecutionState()
    data class Completed(val workflowName: String, val log: List<String>) : WorkflowExecutionState()
    data class Failed(val workflowName: String, val stepIndex: Int, val errorMessage: String, val log: List<String>) : WorkflowExecutionState()
}

class WorkflowEngine(
    private val app: HubApplication
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var executionJob: Job? = null

    private val _executionState = MutableStateFlow<WorkflowExecutionState>(WorkflowExecutionState.Idle)
    val executionState: StateFlow<WorkflowExecutionState> = _executionState.asStateFlow()

    private val executionLog = mutableListOf<String>()

    fun startWorkflow(workflow: WorkflowEntity, context: Context, isAppVisible: Boolean) {
        if (_executionState.value is WorkflowExecutionState.Executing) {
            HubLogger.log(LogLevel.WARN, LogCategory.APP, "Tentativa de rodar workflow rejeitada: um já está em execução.")
            return
        }

        executionLog.clear()
        executionLog.add("Iniciando workflow: ${workflow.name}")

        executionJob = app.appScope.launch(Dispatchers.Default) {
            val steps: List<WorkflowStep> = try {
                json.decodeFromString(workflow.stepsJson)
            } catch (e: Exception) {
                val err = "Erro ao decodificar passos do workflow: ${e.message}"
                executionLog.add("FALHA: $err")
                _executionState.value = WorkflowExecutionState.Failed(workflow.name, 0, err, executionLog.toList())
                return@launch
            }

            if (steps.size > workflow.maxSteps) {
                val err = "Quantidade de passos (${steps.size}) excede o limite máximo de ${workflow.maxSteps}."
                executionLog.add("FALHA: $err")
                _executionState.value = WorkflowExecutionState.Failed(workflow.name, 0, err, executionLog.toList())
                return@launch
            }

            var currentStepIndex = 0
            var lastStepOutput: String = ""

            while (currentStepIndex < steps.size && isActive) {
                val step = steps[currentStepIndex]
                val desc = getStepDescription(step, currentStepIndex)
                executionLog.add("Passo ${currentStepIndex + 1}: Executando - $desc")
                _executionState.value = WorkflowExecutionState.Executing(workflow.name, currentStepIndex, steps.size, desc)

                try {
                    withTimeout(15000L) { // Max timeout of 15 seconds per step
                        when (step) {
                            is WorkflowStep.CallTool -> {
                                if (step.toolName == "run_workflow") {
                                    throw IllegalArgumentException("Recursão proibida: um workflow não pode chamar outro run_workflow.")
                                }
                                executionLog.add("Chamando ferramenta ${step.toolName} com ${step.argumentsJson}")
                                delay(10L) // Simulate tool execution
                                lastStepOutput = "Resultado simulado para ${step.toolName}"
                            }
                            is WorkflowStep.WaitStep -> {
                                delay(step.durationMs)
                            }
                            is WorkflowStep.SimpleCondition -> {
                                val conditionMet = if (step.conditionType == "last_output_contains") {
                                    lastStepOutput.contains(step.expectedValue)
                                } else {
                                    false
                                }
                                executionLog.add("Condição '${step.conditionType}' com valor '${step.expectedValue}': ${if (conditionMet) "Satisfeita" else "Não Satisfeita"}")
                                currentStepIndex = if (conditionMet) {
                                    step.onSuccessStepIndex - 1 // -1 because loop will do +1
                                } else {
                                    step.onFailureStepIndex - 1
                                }
                            }
                            is WorkflowStep.SelectProfileAndModel -> {
                                executionLog.add("Selecionando perfil '${step.profileId}' e modelo '${step.modelId}'")
                                try {
                                    app.providerRegistry.setActiveModelId(step.modelId)
                                } catch (e: Exception) {
                                    executionLog.add("Erro ao selecionar modelo: ${e.message}")
                                }
                            }
                            is WorkflowStep.ConfigureAudio -> {
                                executionLog.add("Configurando rota de áudio para '${step.source}' (Mute: ${step.isMuted})")
                                try {
                                    val matched = app.audioRoutingManager.availableDevices.value.find {
                                        it.name.contains(step.source, ignoreCase = true)
                                    }
                                    if (matched != null) {
                                        app.audioRoutingManager.selectCommunicationDevice(matched)
                                    } else {
                                        executionLog.add("Dispositivo de áudio '${step.source}' não encontrado, mantendo atual.")
                                    }
                                } catch (e: Exception) {
                                    executionLog.add("Erro ao selecionar dispositivo de áudio: ${e.message}")
                                }
                            }
                            is WorkflowStep.StartLiveSession -> {
                                if (!isAppVisible) {
                                    throw IllegalStateException("Para iniciar o modo Live de voz, o aplicativo precisa estar visível em primeiro plano.")
                                }
                                executionLog.add("Iniciando sessão Live para '${step.liveModelId}'")
                            }
                        }
                    }
                    executionLog.add("Passo ${currentStepIndex + 1}: Sucesso")
                    currentStepIndex++
                } catch (e: TimeoutCancellationException) {
                    val err = "Passo ${currentStepIndex + 1} falhou devido a timeout."
                    executionLog.add("FALHA: $err")
                    _executionState.value = WorkflowExecutionState.Failed(workflow.name, currentStepIndex, err, executionLog.toList())
                    return@launch
                } catch (e: Exception) {
                    val err = "Erro no Passo ${currentStepIndex + 1}: ${e.message}"
                    executionLog.add("FALHA: $err")
                    _executionState.value = WorkflowExecutionState.Failed(workflow.name, currentStepIndex, err, executionLog.toList())
                    return@launch
                }
            }

            executionLog.add("Workflow concluído com sucesso!")
            _executionState.value = WorkflowExecutionState.Completed(workflow.name, executionLog.toList())
        }
    }

    fun cancelWorkflow() {
        executionJob?.cancel()
        executionJob = null
        executionLog.add("Workflow cancelado pelo usuário.")
        _executionState.value = WorkflowExecutionState.Idle
        HubLogger.log(LogLevel.WARN, LogCategory.APP, "Workflow cancelado ativamente pelo usuário.")
    }

    private fun getStepDescription(step: WorkflowStep, index: Int): String {
        return when (step) {
            is WorkflowStep.CallTool -> "Chamar ferramenta: ${step.toolName}"
            is WorkflowStep.WaitStep -> "Aguardar: ${step.durationMs}ms"
            is WorkflowStep.SimpleCondition -> "Verificar condição: ${step.conditionType}"
            is WorkflowStep.SelectProfileAndModel -> "Selecionar Perfil/Modelo: ${step.profileId} / ${step.modelId}"
            is WorkflowStep.ConfigureAudio -> "Configurar áudio: ${step.source}"
            is WorkflowStep.StartLiveSession -> "Iniciar Live Session: ${step.liveModelId}"
        }
    }
}
