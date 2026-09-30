package com.example.tools

import android.content.Context
import com.example.HubApplication
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.providers.model.ToolDefinition
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolRegistry(
    private val app: HubApplication
) {
    val registeredToolsCount: Int = 2
    val statusMessage = "Registro de ferramentas client-side funcional."

    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "memory_save",
                description = "Sugere salvar uma nova informação na memória do assistente. Entra como sugestão pendente para aprovação.",
                parametersSchemaJson = """
                    {
                        "type": "object",
                        "properties": {
                            "content": { "type": "string", "description": "O conteúdo da informação a ser lembrada." },
                            "category": { "type": "string", "description": "Categoria da memória (sessão, conversa, projeto, preferências, persistente)." },
                            "tags": { "type": "array", "items": { "type": "string" }, "description": "Tags para organizar a memória." }
                        },
                        "required": ["content"]
                    }
                """.trimIndent()
            ),
            ToolDefinition(
                name = "run_workflow",
                description = "Executa uma sequência predefinida de passos/workflow em runtime.",
                parametersSchemaJson = """
                    {
                        "type": "object",
                        "properties": {
                            "workflowId": { "type": "string", "description": "O ID do workflow predefinido a ser executado." }
                        },
                        "required": ["workflowId"]
                    }
                """.trimIndent()
            )
        )
    }

    suspend fun executeTool(name: String, argumentsJson: String, context: Context, isAppVisible: Boolean): String {
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Executando ferramenta '$name' com argumentos: $argumentsJson")
        return try {
            val element = Json.parseToJsonElement(argumentsJson).jsonObject
            when (name) {
                "memory_save" -> {
                    val content = element["content"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Parâmetro 'content' obrigatório.")
                    val category = element["category"]?.jsonPrimitive?.content ?: "persistente"
                    val tags = try {
                        element["tags"]?.let { Json.decodeFromString<List<String>>(it.toString()) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val result = app.memoryRepository.saveOrSuggestMemory(
                        content = content,
                        category = category,
                        tags = tags,
                        origin = "tool"
                    )
                    if (result.isSuccess) {
                        "Sucesso: Memória registrada como sugestão pendente de aprovação do usuário."
                    } else {
                        "Falha: ${result.exceptionOrNull()?.message}"
                    }
                }
                "run_workflow" -> {
                    val workflowId = element["workflowId"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Parâmetro 'workflowId' obrigatório.")
                    val workflow = app.workflowRepository.getWorkflowById(workflowId)
                        ?: return "Falha: Workflow '$workflowId' não encontrado."

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        app.workflowEngine.startWorkflow(workflow, context, isAppVisible)
                    }
                    "Sucesso: Workflow iniciado com sucesso."
                }
                else -> "Erro: Ferramenta desconhecida."
            }
        } catch (e: Exception) {
            HubLogger.log(LogLevel.ERROR, LogCategory.APP, "Erro de execução da ferramenta '$name': ${e.message}", throwable = e)
            "Erro ao executar: ${e.message}"
        }
    }
}
