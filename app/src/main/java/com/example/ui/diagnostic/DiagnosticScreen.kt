package com.example.ui.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.HubApplication
import com.example.ui.ResilienceScreen
import com.example.ui.SecurityScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    app: HubApplication,
    isFlagSecureActive: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var selectedDiagnosticTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostic_screen")
    ) {
        // Diagnostic Environment Banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ambiente de Execução",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnvironmentChip(
                            label = "DEBUG",
                            isActive = BuildConfig.DEBUG,
                            activeColor = MaterialTheme.colorScheme.primaryContainer,
                            inactiveColor = MaterialTheme.colorScheme.surface
                        )
                        EnvironmentChip(
                            label = "FLAG_SECURE",
                            isActive = isFlagSecureActive,
                            activeColor = MaterialTheme.colorScheme.tertiaryContainer,
                            inactiveColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }

        // Sub-tabs: Resiliência vs Segurança vs Segundo Plano
        SecondaryTabRow(
            selectedTabIndex = selectedDiagnosticTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedDiagnosticTab == 0,
                onClick = { selectedDiagnosticTab = 0 },
                text = { Text("Resiliência") },
                modifier = Modifier.testTag("tab_diag_resilience")
            )
            Tab(
                selected = selectedDiagnosticTab == 1,
                onClick = { selectedDiagnosticTab = 1 },
                text = { Text("Segurança") },
                modifier = Modifier.testTag("tab_diag_security")
            )
            Tab(
                selected = selectedDiagnosticTab == 2,
                onClick = { selectedDiagnosticTab = 2 },
                text = { Text("Segundo Plano & Bateria") },
                modifier = Modifier.testTag("tab_diag_background")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedDiagnosticTab) {
                0 -> ResilienceScreen(
                    app = app,
                    snackbarHostState = snackbarHostState
                )
                1 -> SecurityScreen()
                2 -> BackgroundDiagnosticTab(app = app)
            }
        }
    }
}

@Composable
private fun EnvironmentChip(
    label: String,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = if (isActive) activeColor else inactiveColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: ${if (isActive) "ATIVO" else "INATIVO"}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
