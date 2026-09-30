package com.example.memory

import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.storage.dao.MemoryDao
import com.example.storage.entities.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MemoryRepository(
    private val memoryDao: MemoryDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val allMemoriesFlow: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val approvedMemoriesFlow: Flow<List<MemoryEntity>> = memoryDao.getAllApprovedMemoriesFlow()
    val pendingSuggestionsFlow: Flow<List<MemoryEntity>> = memoryDao.getPendingSuggestionsFlow()

    companion object {
        private val API_KEY_PATTERNS = listOf(
            Regex("(?i)AIzaSy[A-Za-z0-9_-]{33}"), // Google
            Regex("(?i)sk-[A-Za-z0-9_-]{32,}"),   // OpenAI / Anthropic
            Regex("(?i)ghp_[A-Za-z0-9]{36}"),     // GitHub
            Regex("(?i)bearer\\s+[A-Za-z0-9\\._-]{40,}") // Generic Bearer
        )

        fun containsSecrets(text: String): Boolean {
            return API_KEY_PATTERNS.any { it.containsMatchIn(text) }
        }

        fun calculateWordOverlap(s1: String, s2: String): Double {
            val words1 = s1.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
            val words2 = s2.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
            if (words1.isEmpty() || words2.isEmpty()) return 0.0
            val intersection = words1.intersect(words2).size
            val union = words1.union(words2).size
            return intersection.toDouble() / union.toDouble()
        }
    }

    suspend fun saveOrSuggestMemory(
        content: String,
        category: String = "persistente", // sessão, conversa, projeto, preferências, persistente
        tags: List<String> = emptyList(),
        origin: String = "user", // user, tool, auto_suggest, etc.
        scope: String = "global", // global, custom_model, agent
        scopeId: String? = null
    ): Result<MemoryEntity> {
        if (containsSecrets(content)) {
            HubLogger.log(LogLevel.WARN, LogCategory.SECURITY, "Tentativa de salvar segredo/chave de API na memória bloqueada.")
            return Result.failure(IllegalArgumentException("Contém padrões suspeitos de chaves de API/segredos."))
        }

        // Auto determine if it requires pending approval
        // "Tudo o que NÃO vem digitado pelo usuário (tool, sugestão automática, conteúdo de anexo, câmera ou tela) entra como SUGESTÃO PENDENTE"
        val isPending = origin != "user"

        // Search for nearly identical approved/pending memories to avoid duplicates
        val allMemories = memoryDao.getAllApprovedMemoriesList() + memoryDao.getPendingSuggestionsList()
        val nearDuplicate = allMemories.find { calculateWordOverlap(it.content, content) > 0.8 }

        val finalId = nearDuplicate?.id ?: "mem_${UUID.randomUUID()}"
        val finalContent = if (nearDuplicate != null && nearDuplicate.content != content) {
            // Merge content if different
            "${nearDuplicate.content}\n${content}"
        } else {
            content
        }

        val tagsJson = json.encodeToString((tags + (nearDuplicate?.let { json.decodeFromString<List<String>>(it.tagsJson) } ?: emptyList())).distinct())

        val key = content.take(30).trim()

        val entity = MemoryEntity(
            id = finalId,
            key = key,
            content = finalContent,
            category = category,
            tagsJson = tagsJson,
            origin = origin,
            scope = scope,
            scopeId = scopeId,
            isPendingApproval = isPending,
            createdAt = nearDuplicate?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        memoryDao.insert(entity)
        HubLogger.log(
            LogLevel.INFO,
            LogCategory.APP,
            "Memória processada com sucesso. ID: $finalId, Pendente: $isPending, Origem: $origin"
        )
        return Result.success(entity)
    }

    suspend fun approvePendingMemory(id: String) {
        val memories = memoryDao.getPendingSuggestionsList()
        val target = memories.find { it.id == id } ?: return
        memoryDao.insert(target.copy(isPendingApproval = false, updatedAt = System.currentTimeMillis()))
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Sugestão de memória aprovada pelo usuário: $id")
    }

    suspend fun rejectOrDeleteMemory(id: String) {
        memoryDao.deleteById(id)
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Memória/Sugestão excluída: $id")
    }

    suspend fun clearAll() {
        memoryDao.deleteAll()
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Todas as memórias foram apagadas.")
    }

    suspend fun getRelevantMemories(
        query: String,
        scope: String = "global",
        scopeId: String? = null,
        limit: Int = 10
    ): List<MemoryEntity> {
        return try {
            val approved = memoryDao.getAllApprovedMemoriesList()
            // Score memories based on keyword matching, tags overlap, and recency
            val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()

            approved.filter {
                // Filter by scope
                if (it.scope == "global") true
                else it.scope == scope && it.scopeId == scopeId
            }.map { mem ->
                val memWords = mem.content.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
                val overlap = queryWords.intersect(memWords).size
                val tags: List<String> = try { json.decodeFromString(mem.tagsJson) } catch (e: Exception) { emptyList() }
                val tagOverlap = queryWords.intersect(tags.map { it.lowercase() }.toSet()).size

                // Score: Overlap * 2 + TagOverlap * 3 + recencyBonus
                val recencyHours = (System.currentTimeMillis() - mem.updatedAt) / (1000 * 60 * 60)
                val recencyBonus = if (recencyHours < 24) 1.5 else 1.0

                val finalScore = (overlap * 2.0 + tagOverlap * 3.0) * recencyBonus
                Pair(mem, finalScore)
            }.filter { it.second > 0.0 || query.isBlank() }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        } catch (e: Exception) {
            HubLogger.log(LogLevel.ERROR, LogCategory.APP, "Erro de recuperação na memória: ${e.message}", throwable = e)
            throw e // propagate to be handled as "memória indisponível"
        }
    }

    fun exportMemoriesToJson(memories: List<MemoryEntity>): String {
        val safe = memories.map {
            it.copy(id = "imported_${UUID.randomUUID()}", isPendingApproval = true) // Make imported memories pending approval by default
        }
        return json.encodeToString(safe)
    }

    suspend fun importMemoriesFromJson(jsonString: String): Result<List<MemoryEntity>> {
        return try {
            val importedList = json.decodeFromString<List<MemoryEntity>>(jsonString)
            val added = mutableListOf<MemoryEntity>()
            for (imported in importedList) {
                if (containsSecrets(imported.content)) {
                    continue // Skip containing api keys
                }
                val safeMemory = imported.copy(
                    id = "imported_${UUID.randomUUID()}",
                    isPendingApproval = true, // Import as pending suggestion for security
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                memoryDao.insert(safeMemory)
                added.add(safeMemory)
            }
            Result.success(added)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
