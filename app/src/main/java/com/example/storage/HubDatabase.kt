package com.example.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.storage.dao.AgentDao
import com.example.storage.dao.AiModelDao
import com.example.storage.dao.AppSettingDao
import com.example.storage.dao.ConversationDao
import com.example.storage.dao.CustomModelDao
import com.example.storage.dao.LogDao
import com.example.storage.dao.MemoryDao
import com.example.storage.dao.MessageDao
import com.example.storage.dao.VaultDao
import com.example.storage.entities.AgentProfileEntity
import com.example.storage.entities.AiModelEntity
import com.example.storage.entities.AppSettingEntity
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.CustomModelEntity
import com.example.storage.entities.LogEntryEntity
import com.example.storage.entities.MemoryEntity
import com.example.storage.entities.MessageEntity
import com.example.storage.entities.VaultCredentialEntity

@Database(
    entities = [
        LogEntryEntity::class,
        VaultCredentialEntity::class,
        AppSettingEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AgentProfileEntity::class,
        AiModelEntity::class,
        MemoryEntity::class,
        CustomModelEntity::class,
        com.example.storage.entities.WorkflowEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun vaultDao(): VaultDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun agentDao(): AgentDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun memoryDao(): MemoryDao
    abstract fun customModelDao(): CustomModelDao
    abstract fun workflowDao(): com.example.storage.dao.WorkflowDao

    companion object {
        const val DATABASE_NAME = "hub_database.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_models` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `baseModelId` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `traitsJson` TEXT NOT NULL,
                        `reasoningLevel` INTEGER NOT NULL,
                        `featureFlagsJson` TEXT NOT NULL,
                        `technicalParamsJson` TEXT NOT NULL,
                        `routingMode` TEXT NOT NULL,
                        `fallbackChainJson` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_models_providerId` ON `custom_models` (`providerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_models_isFavorite` ON `custom_models` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_models_isDefault` ON `custom_models` (`isDefault`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `tagsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `scope` TEXT NOT NULL DEFAULT 'global'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `scopeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `isPendingApproval` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `workflows` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `stepsJson` TEXT NOT NULL DEFAULT '[]',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `maxSteps` INTEGER NOT NULL DEFAULT 20,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }
    }
}
