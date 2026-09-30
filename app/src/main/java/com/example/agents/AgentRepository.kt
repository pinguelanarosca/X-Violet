package com.example.agents

import com.example.storage.dao.AgentDao
import com.example.storage.entities.AgentProfileEntity
import kotlinx.coroutines.flow.Flow

class AgentRepository(
    private val agentDao: AgentDao
) {
    val agentsFlow: Flow<List<AgentProfileEntity>> = agentDao.getAllAgents()

    suspend fun saveAgent(agent: AgentProfileEntity) {
        agentDao.insert(agent)
    }

    suspend fun getAgentById(id: String): AgentProfileEntity? {
        return agentDao.getById(id)
    }
}
