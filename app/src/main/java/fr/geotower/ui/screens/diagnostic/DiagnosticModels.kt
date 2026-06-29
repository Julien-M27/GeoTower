package fr.geotower.ui.screens.diagnostic

enum class DiagnosticSeverity {
    Ok,
    Info,
    Warning,
    Error,
    Unknown
}

sealed class DiagnosticAction {
    data class Navigate(val route: String) : DiagnosticAction()
    object OpenAppSettings : DiagnosticAction()
    object OpenNotificationSettings : DiagnosticAction()
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val summary: String,
    val severity: DiagnosticSeverity,
    val details: List<String> = emptyList(),
    val actionLabel: String? = null,
    val action: DiagnosticAction? = null
)

data class DiagnosticState(
    val globalTitle: String,
    val globalSummary: String,
    val globalSeverity: DiagnosticSeverity,
    val generatedAt: String,
    val report: String,
    val items: List<DiagnosticItem>
)
