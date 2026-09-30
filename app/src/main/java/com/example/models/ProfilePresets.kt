package com.example.models

data class ProfilePreset(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val traits: TraitValues,
    val reasoningLevelValue: Int,
    val isMemoryEnabled: Boolean = true,
    val isHistorySaved: Boolean = true,
    val isServerStorageEnabled: Boolean = true,
    val isAppControlEnabled: Boolean = true,
    val warningMessage: String? = null
)

object ProfilePresets {

    val LIST = listOf(
        ProfilePreset(
            id = "profile_coding",
            name = "Programação",
            description = "Especialista em código limpo, refatoração, debug e arquitetura de software.",
            iconName = "code",
            traits = TraitValues(
                humor = 10, formalidade = 60, criatividade = 20,
                objetividade = 90, iniciativa = 60, questionamento = 80,
                profundidade = 90, personalidade = 40
            ),
            reasoningLevelValue = 75 // Profundo
        ),
        ProfilePreset(
            id = "profile_study",
            name = "Estudo",
            description = "Professor didático que explica conceitos complexos com analogias claras e questionamentos.",
            iconName = "school",
            traits = TraitValues(
                humor = 50, formalidade = 40, criatividade = 60,
                objetividade = 70, iniciativa = 70, questionamento = 85,
                profundidade = 80, personalidade = 60
            ),
            reasoningLevelValue = 60 // Equilibrado
        ),
        ProfilePreset(
            id = "profile_research",
            name = "Pesquisa",
            description = "Análise acadêmica rigorosa, checagem de fatos e busca por evidências profundas.",
            iconName = "science",
            traits = TraitValues(
                humor = 10, formalidade = 90, criatividade = 15,
                objetividade = 85, iniciativa = 50, questionamento = 90,
                profundidade = 95, personalidade = 20
            ),
            reasoningLevelValue = 85 // Extremo
        ),
        ProfilePreset(
            id = "profile_heavy_work",
            name = "Trabalho Pesado",
            description = "Foco em produtividade executiva, relatórios, resumos e entregáveis estruturados.",
            iconName = "work",
            traits = TraitValues(
                humor = 20, formalidade = 80, criatividade = 30,
                objetividade = 85, iniciativa = 80, questionamento = 50,
                profundidade = 80, personalidade = 40
            ),
            reasoningLevelValue = 70 // Profundo
        ),
        ProfilePreset(
            id = "profile_conversation",
            name = "Conversa",
            description = "Empático, amigável e fluido para bate-papos gerais e brainstorming.",
            iconName = "chat",
            traits = TraitValues(
                humor = 70, formalidade = 20, criatividade = 70,
                objetividade = 40, iniciativa = 60, questionamento = 30,
                profundidade = 50, personalidade = 80
            ),
            reasoningLevelValue = 30 // Leve
        ),
        ProfilePreset(
            id = "profile_private",
            name = "Privado",
            description = "Sessão confidencial: sem gravação de histórico local, sem memória e sem envio para logs.",
            iconName = "lock",
            traits = TraitValues(
                humor = 30, formalidade = 50, criatividade = 50,
                objetividade = 80, iniciativa = 30, questionamento = 40,
                profundidade = 60, personalidade = 30
            ),
            reasoningLevelValue = 50,
            isMemoryEnabled = false,
            isHistorySaved = false,
            isServerStorageEnabled = false,
            isAppControlEnabled = false,
            warningMessage = "Aviso: O histórico local e a memória estão desligados no modo Privado, mas a conversa ainda é transmitida aos servidores do provedor selecionado."
        )
    )
}
