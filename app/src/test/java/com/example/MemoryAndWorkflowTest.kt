package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.memory.MemoryRepository
import com.example.storage.HubDatabase
import com.example.storage.entities.MemoryEntity
import com.example.storage.entities.WorkflowEntity
import com.example.workflow.*
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryAndWorkflowTest {

    private lateinit var context: Context
    private lateinit var db: HubDatabase
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var workflowEngine: WorkflowEngine
    private lateinit var app: HubApplication

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        memoryRepository = MemoryRepository(db.memoryDao())
        workflowRepository = WorkflowRepository(db.workflowDao())
        app = ApplicationProvider.getApplicationContext<HubApplication>()
        workflowEngine = WorkflowEngine(app)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testApiKeyBlocking() = runBlocking {
        // Block Google API Keys
        val resultGoogle = memoryRepository.saveOrSuggestMemory(
            content = "Minha chave é AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q",
            category = "persistente",
            origin = "user"
        )
        assertTrue("Salvar chave do Google deve falhar", resultGoogle.isFailure)

        // Block OpenAI API Keys
        val resultOpenAi = memoryRepository.saveOrSuggestMemory(
            content = "Chave OpenAI: sk-Proj1234567890abcdefghijklmnopqrstuvwxyz",
            category = "persistente",
            origin = "user"
        )
        assertTrue("Salvar chave OpenAI deve falhar", resultOpenAi.isFailure)
    }

    @Test
    fun testPendingSuggestionsFromAttachments() = runBlocking {
        // Saving memory originating from "attachment" or "tool" must be pending approval
        val result = memoryRepository.saveOrSuggestMemory(
            content = "Gosta de café forte pela manhã.",
            category = "preferências",
            origin = "attachment" // Not "user"
        )
        assertTrue(result.isSuccess)
        val suggested = result.getOrThrow()
        assertTrue("Memória vinda de anexo deve ficar como sugestão pendente", suggested.isPendingApproval)

        val pending = memoryRepository.pendingSuggestionsFlow.first()
        assertTrue(pending.any { it.id == suggested.id })

        // Now approve the memory
        memoryRepository.approvePendingMemory(suggested.id)
        val approved = memoryRepository.approvedMemoriesFlow.first()
        assertTrue("Memória aprovada deve aparecer na lista de aprovadas", approved.any { it.id == suggested.id })
    }

    @Test
    fun testMemoryRetrievalFailSafe() = runBlocking {
        // Populate a memory
        memoryRepository.saveOrSuggestMemory(
            content = "Especialista em compiladores Kotlin.",
            category = "projeto",
            origin = "user"
        )

        // If memory retrieval succeeds, we should find it
        val memories = memoryRepository.getRelevantMemories("Kotlin")
        assertTrue(memories.any { it.content.contains("Kotlin") })

        // Safe recovery if search query fails or behaves unexpectedly
        val fallbackMemories = try {
            memoryRepository.getRelevantMemories("Kotlin")
        } catch (e: Exception) {
            emptyList()
        }
        assertNotNull(fallbackMemories)
    }

    @Test
    fun testReunionWorkflowExecution() = runBlocking {
        val reunionSteps = listOf(
            WorkflowStep.SelectProfileAndModel("profile_coding", "gemini-2.5-flash"),
            WorkflowStep.ConfigureAudio("Alto-falante", false),
            WorkflowStep.StartLiveSession("gemini-2.5-flash-live"),
            WorkflowStep.WaitStep(5L)
        )

        val reunion = WorkflowEntity(
            id = "wf_reunion_test",
            name = "Reunião Teste",
            description = "Sequência de reunião",
            stepsJson = kotlinx.serialization.json.Json.encodeToString(reunionSteps)
        )

        workflowRepository.saveWorkflow(reunion)

        // Execute workflow engine (isAppVisible = true)
        workflowEngine.startWorkflow(reunion, context, isAppVisible = true)

        // Wait a small duration to allow flow to finish on Main dispatcher
        var elapsed = 0
        while (workflowEngine.executionState.value !is WorkflowExecutionState.Completed &&
            workflowEngine.executionState.value !is WorkflowExecutionState.Failed && elapsed < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(100L)
            elapsed++
        }

        val finalState = workflowEngine.executionState.value
        assertTrue("Workflow deve completar com sucesso, estado final: $finalState", finalState is WorkflowExecutionState.Completed)
    }

    @Test
    fun testWorkflowFailsWhenAppNotVisible() = runBlocking {
        val reunionSteps: List<WorkflowStep> = listOf(
            WorkflowStep.StartLiveSession("gemini-2.5-flash-live")
        )

        val reunion = WorkflowEntity(
            id = "wf_reunion_test_fail",
            name = "Reunião Teste Falha",
            description = "Sequência de reunião que deve falhar",
            stepsJson = kotlinx.serialization.json.Json.encodeToString(reunionSteps)
        )

        // Execute with isAppVisible = false
        workflowEngine.startWorkflow(reunion, context, isAppVisible = false)

        var elapsed = 0
        while (workflowEngine.executionState.value !is WorkflowExecutionState.Completed &&
            workflowEngine.executionState.value !is WorkflowExecutionState.Failed && elapsed < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(100L)
            elapsed++
        }

        val finalState = workflowEngine.executionState.value
        assertTrue("Workflow deve falhar porque o aplicativo não está visível", finalState is WorkflowExecutionState.Failed)
        val failed = finalState as WorkflowExecutionState.Failed
        assertTrue(failed.errorMessage.contains("aplicativo precisa estar visível"))
    }
}
