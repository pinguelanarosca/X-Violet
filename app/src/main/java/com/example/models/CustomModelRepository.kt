package com.example.models

import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.storage.dao.CustomModelDao
import com.example.storage.entities.CustomModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class CustomModelRepository(
    private val customModelDao: CustomModelDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val customModelsFlow: Flow<List<CustomModelEntity>> = customModelDao.getAllCustomModelsFlow()

    suspend fun getAllModelsList(): List<CustomModelEntity> = withContext(Dispatchers.IO) {
        customModelDao.getAllCustomModelsList()
    }

    suspend fun getById(id: String): CustomModelEntity? = withContext(Dispatchers.IO) {
        customModelDao.getById(id)
    }

    suspend fun saveCustomModel(model: CustomModelEntity) = withContext(Dispatchers.IO) {
        if (model.isDefault) {
            customModelDao.clearDefaultFlags()
        }
        customModelDao.insertOrUpdate(model.copy(updatedAt = System.currentTimeMillis()))
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Modelo personalizado salvo: ${model.name} (${model.id})")
    }

    suspend fun duplicateCustomModel(sourceId: String): CustomModelEntity? = withContext(Dispatchers.IO) {
        val original = customModelDao.getById(sourceId) ?: return@withContext null
        val copy = original.copy(
            id = "custom_${UUID.randomUUID()}",
            name = "${original.name} (Cópia)",
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        customModelDao.insertOrUpdate(copy)
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Modelo duplicado com sucesso: ${copy.name}")
        copy
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        customModelDao.deleteById(id)
        HubLogger.log(LogLevel.INFO, LogCategory.APP, "Modelo personalizado excluído: $id")
    }

    suspend fun setDefaultModel(id: String) = withContext(Dispatchers.IO) {
        val target = customModelDao.getById(id) ?: return@withContext
        customModelDao.clearDefaultFlags()
        customModelDao.insertOrUpdate(target.copy(isDefault = true, updatedAt = System.currentTimeMillis()))
    }

    fun exportModelToJson(model: CustomModelEntity): String {
        // Safe export: JSON format without API keys or credentials
        return json.encodeToString(model)
    }

    suspend fun importModelFromJson(jsonString: String): Result<CustomModelEntity> = withContext(Dispatchers.IO) {
        try {
            val imported = json.decodeFromString<CustomModelEntity>(jsonString)

            // Schema version check
            if (imported.schemaVersion > 1) {
                return@withContext Result.failure(
                    IllegalArgumentException("Versão de schema do arquivo (${imported.schemaVersion}) não suportada nesta versão do app.")
                )
            }

            // Safe Import Rules:
            // 1. Generate new UUID so it doesn't overwrite existing
            // 2. Disable ALL tools and auto-execution for security until user reviews
            val safeModel = imported.copy(
                id = "imported_${UUID.randomUUID()}",
                name = "${imported.name} (Importado)",
                featureFlagsJson = json.encodeToString(
                    mapOf(
                        "tools" to false,
                        "autoExecuteTools" to false,
                        "appControl" to false
                    )
                ),
                isDefault = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            customModelDao.insertOrUpdate(safeModel)
            HubLogger.log(LogLevel.INFO, LogCategory.SECURITY, "Modelo personalizado importado com segurança: ${safeModel.name}")
            Result.success(safeModel)

        } catch (e: Exception) {
            HubLogger.log(LogLevel.ERROR, LogCategory.SECURITY, "Falha ao importar modelo personalizado: ${e.message}")
            Result.failure(e)
        }
    }
}
