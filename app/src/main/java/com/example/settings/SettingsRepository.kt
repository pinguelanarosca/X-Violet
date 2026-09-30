package com.example.settings

import com.example.storage.dao.AppSettingDao
import com.example.storage.entities.AppSettingEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val appSettingDao: AppSettingDao
) {
    val allSettingsFlow: Flow<List<AppSettingEntity>> = appSettingDao.getAllSettingsFlow()

    suspend fun getSetting(key: String, defaultValue: String): String {
        return appSettingDao.getSetting(key) ?: defaultValue
    }

    suspend fun setSetting(key: String, value: String) {
        appSettingDao.setSetting(AppSettingEntity(key = key, value = value, updatedAt = System.currentTimeMillis()))
    }
}
