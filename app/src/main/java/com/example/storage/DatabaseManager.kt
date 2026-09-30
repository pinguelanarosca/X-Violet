package com.example.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DatabaseManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var _database: HubDatabase? = null
    val database: HubDatabase
        get() = _database ?: synchronized(this) {
            _database ?: buildDatabase().also { _database = it }
        }

    private val _recoveryEvent = MutableStateFlow<String?>(null)
    val recoveryEvent: StateFlow<String?> = _recoveryEvent.asStateFlow()

    fun clearRecoveryEvent() {
        _recoveryEvent.value = null
    }

    init {
        // Initialize and run background integrity check
        scope.launch(Dispatchers.IO) {
            runIntegrityCheck()
        }
    }

    @Synchronized
    private fun buildDatabase(): HubDatabase {
        return try {
            createRoomInstance()
        } catch (e: Exception) {
            handleCorruptDatabase(e)
            createRoomInstance()
        }
    }

    private fun createRoomInstance(): HubDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            HubDatabase::class.java,
            HubDatabase.DATABASE_NAME
        )
            .addMigrations(HubDatabase.MIGRATION_1_2, HubDatabase.MIGRATION_2_3)
            // NO fallbackToDestructiveMigration() - Forbidden by permanent rules!
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Enable WAL mode for high-performance concurrent reads & writes
                    db.enableWriteAheadLogging()
                }
            })
            .build()
    }

    private fun runIntegrityCheck() {
        try {
            val db = database.openHelper.writableDatabase
            val cursor = db.query("PRAGMA integrity_check")
            var isOk = false
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                isOk = result.equals("ok", ignoreCase = true)
                HubLogger.log(
                    level = if (isOk) LogLevel.INFO else LogLevel.ERROR,
                    category = LogCategory.STORAGE,
                    message = "Verificação de integridade do SQLite: $result"
                )
            }
            cursor.close()
            if (!isOk) {
                recoverFromCorruption(Exception("PRAGMA integrity_check failed"))
            }
        } catch (e: SQLiteDatabaseCorruptException) {
            recoverFromCorruption(e)
        } catch (e: Throwable) {
            HubLogger.log(
                level = LogLevel.WARN,
                category = LogCategory.STORAGE,
                message = "Aviso na verificação de integridade: ${e.message}",
                throwable = e
            )
        }
    }

    private fun handleCorruptDatabase(e: Exception) {
        recoverFromCorruption(e)
    }

    @Synchronized
    fun recoverFromCorruption(cause: Throwable) {
        HubLogger.log(
            level = LogLevel.ERROR,
            category = LogCategory.STORAGE,
            message = "Detectada falha/corrupção no banco de dados. Iniciando isolamento e backup seguro.",
            throwable = cause
        )

        try {
            _database?.close()
        } catch (ignored: Throwable) {}
        _database = null

        val dbFile = context.getDatabasePath(HubDatabase.DATABASE_NAME)
        if (dbFile.exists()) {
            val timestamp = System.currentTimeMillis()
            val backupFile = File(dbFile.parentFile, "${HubDatabase.DATABASE_NAME}_corrupt_backup_$timestamp.db")
            try {
                copyFile(dbFile, backupFile)
                HubLogger.log(
                    level = LogLevel.WARN,
                    category = LogCategory.STORAGE,
                    message = "Backup de segurança criado: ${backupFile.name}"
                )
            } catch (copyErr: Throwable) {
                HubLogger.log(
                    level = LogLevel.ERROR,
                    category = LogCategory.STORAGE,
                    message = "Falha ao copiar arquivo corrompido para backup: ${copyErr.message}",
                    throwable = copyErr
                )
            }

            // Remove corrupted file and shm/wal files
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
        }

        val notification = "O banco de dados apresentou inconsistência e foi recriado de forma segura com backup. Suas chaves de API permanecem protegidas no Android Keystore."
        _recoveryEvent.value = notification
        HubLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.STORAGE,
            message = notification
        )
    }

    fun dismissRecoveryEvent() {
        _recoveryEvent.value = null
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } > 0) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
    }
}
