package fr.geotower.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.R
import fr.geotower.data.api.ApiServer
import fr.geotower.data.api.DownloadManifestRepository
import fr.geotower.data.api.EnbOperatorVersion
import fr.geotower.data.api.LiveDatabaseStatus
import fr.geotower.ui.screens.about.formatAboutDatabaseVersion
import fr.geotower.ui.screens.about.formatAnfrWeeklyDate
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.LocalizedDateLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Données complètes de versions et statistiques servies par un serveur API GeoTower (principal ou miroir).
 */
data class ApiServerDbVersionInfo(
    val server: ApiServer,
    val mobileDbVersion: String? = null,
    val mobileSizeBytes: Long? = null,
    val radioDbVersion: String? = null,
    val radioSizeBytes: Long? = null,
    val enbDbVersion: String? = null,
    val enbSizeBytes: Long? = null,
    val enbRowCount: Int? = null,
    val liveDbVersion: String? = null,
    val liveGeneratedAt: String? = null,
    val liveStationCount: Int? = null,
    val anfrWeeklyDate: String? = null,
    val anfrMonthlyVersion: String? = null,
    val arcepQuarterlyVersion: String? = null,
    val enbOperators: List<EnbOperatorVersion> = emptyList(),
    val unreachable: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Récupère l'ensemble des versions et métadonnées publiées par [server] (manifeste signé + base en ligne).
 */
suspend fun fetchApiServerDbVersionInfo(
    server: ApiServer,
    forceRefresh: Boolean = false
): ApiServerDbVersionInfo = withContext(Dispatchers.IO) {
    val manifest = try {
        DownloadManifestRepository.getVerifiedManifest(server, forceRefresh)?.value
    } catch (_: Exception) {
        null
    }

    val liveStatus = try {
        LiveDatabaseStatus.datasetForServer(server, forceRefresh)
    } catch (_: Exception) {
        null
    }

    val isUnreachable = manifest == null && liveStatus == null
    val mobileDb = manifest?.database
    val radioDb = manifest?.radioDatabase
    val enbDb = manifest?.enbDatabase

    ApiServerDbVersionInfo(
        server = server,
        mobileDbVersion = mobileDb?.version,
        mobileSizeBytes = mobileDb?.sizeBytes,
        radioDbVersion = radioDb?.version,
        radioSizeBytes = radioDb?.sizeBytes,
        enbDbVersion = enbDb?.version,
        enbSizeBytes = enbDb?.sizeBytes,
        enbRowCount = enbDb?.rowCount ?: enbDb?.operators?.sumOf { it.rowCount }?.takeIf { it > 0 },
        liveDbVersion = liveStatus?.offlineDbVersion,
        liveGeneratedAt = liveStatus?.generatedAt,
        liveStationCount = liveStatus?.stationCount,
        anfrWeeklyDate = liveStatus?.dateMajAnfr ?: mobileDb?.dateAnfr,
        anfrMonthlyVersion = liveStatus?.zipVersion,
        arcepQuarterlyVersion = liveStatus?.quarterlyVersion ?: mobileDb?.quarterlyVersion,
        enbOperators = enbDb?.operators.orEmpty(),
        unreachable = isUnreachable,
        isLoading = false
    )
}

/**
 * Carte affichant un tableau comparatif détaillé des versions des bases de données et des métadonnées
 * fournies par les deux serveurs API GeoTower (principal et miroir).
 */
@Composable
fun ApiServersDatabaseVersionsCard(
    cardShape: Shape,
    cardColor: Color,
    primaryVersions: ApiServerDbVersionInfo,
    mirrorVersions: ApiServerDbVersionInfo,
    activeServer: ApiServer,
    timeAtLabel: String
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = sizing.spacing(16.dp),
                vertical = sizing.spacing(12.dp)
            )
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(24.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Column {
                    Text(
                        stringResource(R.string.appstrings_version_api_table_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.appstrings_version_api_table_desc),
                        style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(10.dp)),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            ApiServersDatabaseVersionsTable(
                primaryVersions = primaryVersions,
                mirrorVersions = mirrorVersions,
                activeServer = activeServer,
                timeAtLabel = timeAtLabel
            )
        }
    }
}

private data class TableRowData(
    val label: String,
    val primaryValue: String,
    val mirrorValue: String
)

private data class TableGroupData(
    val categoryTitle: String,
    val rows: List<TableRowData>
)

@Composable
private fun ApiServersDatabaseVersionsTable(
    primaryVersions: ApiServerDbVersionInfo,
    mirrorVersions: ApiServerDbVersionInfo,
    activeServer: ApiServer,
    timeAtLabel: String
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tableShape = RoundedCornerShape(sizing.component(8.dp))
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    val txtSearching = stringResource(R.string.database_searching)
    val txtUnreachable = stringResource(R.string.appstrings_version_server_unreachable)

    fun formatVersion(raw: String?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && raw == null) return txtUnreachable
        if (raw.isNullOrBlank()) return "-"
        return formatAboutDatabaseVersion(raw, timeAtLabel)
    }

    fun formatDate(raw: String?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && raw == null) return txtUnreachable
        if (raw.isNullOrBlank()) return "-"
        return formatAnfrWeeklyDate(raw)
    }

    fun formatMonthly(raw: String?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && raw == null) return txtUnreachable
        if (raw.isNullOrBlank()) return "-"
        return LocalizedDateLabels.formatMonthlyVersion(context, raw)
    }

    fun formatQuarterly(raw: String?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && raw == null) return txtUnreachable
        if (raw.isNullOrBlank()) return "-"
        return LocalizedDateLabels.formatQuarterlyVersion(context, raw)
    }

    fun formatSize(bytes: Long?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && bytes == null) return txtUnreachable
        if (bytes == null || bytes <= 0L) return "-"
        return String.format(Locale.getDefault(), "%.1f Mo", bytes / (1024.0 * 1024.0))
    }

    fun formatCount(count: Int?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && count == null) return txtUnreachable
        if (count == null || count <= 0) return "-"
        return String.format(Locale.getDefault(), "%,d", count)
    }

    fun formatPublished(isoString: String?, unreachable: Boolean, isLoading: Boolean): String {
        if (isLoading) return txtSearching
        if (unreachable && isoString == null) return txtUnreachable
        if (isoString.isNullOrBlank()) return "-"
        val millis = LocalizedDateLabels.isoInstantMillis(isoString)
        return if (millis > 0L) {
            LocalizedDateLabels.formatVersionDateTime(context, millis, timeAtLabel)
        } else {
            "-"
        }
    }

    fun operatorDate(operators: List<EnbOperatorVersion>, nameKeyword: String): String? =
        operators.firstOrNull { it.operator.contains(nameKeyword, ignoreCase = true) }?.sourceDate

    val groups = buildList {
        // 1. Versions des bases
        add(
            TableGroupData(
                categoryTitle = stringResource(R.string.appstrings_version_table_category_versions),
                rows = listOf(
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_mobile_db_short),
                        primaryValue = formatVersion(primaryVersions.mobileDbVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatVersion(mirrorVersions.mobileDbVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_radio_db_short),
                        primaryValue = formatVersion(primaryVersions.radioDbVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatVersion(mirrorVersions.radioDbVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_enb_db_short),
                        primaryValue = formatVersion(primaryVersions.enbDbVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatVersion(mirrorVersions.enbDbVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_live_db_short),
                        primaryValue = formatVersion(primaryVersions.liveDbVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatVersion(mirrorVersions.liveDbVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    )
                )
            )
        )

        // 2. Extractions sources
        add(
            TableGroupData(
                categoryTitle = stringResource(R.string.appstrings_version_table_category_sources),
                rows = listOf(
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_anfr_weekly_short),
                        primaryValue = formatDate(primaryVersions.anfrWeeklyDate, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatDate(mirrorVersions.anfrWeeklyDate, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_monthly_label).replace("\n", " "),
                        primaryValue = formatMonthly(primaryVersions.anfrMonthlyVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatMonthly(mirrorVersions.anfrMonthlyVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_quarterly_label).replace("\n", " "),
                        primaryValue = formatQuarterly(primaryVersions.arcepQuarterlyVersion, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatQuarterly(mirrorVersions.arcepQuarterlyVersion, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    )
                )
            )
        )

        // 3. Tailles des fichiers
        add(
            TableGroupData(
                categoryTitle = stringResource(R.string.appstrings_version_table_category_sizes),
                rows = listOf(
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_size_mobile),
                        primaryValue = formatSize(primaryVersions.mobileSizeBytes, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatSize(mirrorVersions.mobileSizeBytes, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_size_radio),
                        primaryValue = formatSize(primaryVersions.radioSizeBytes, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatSize(mirrorVersions.radioSizeBytes, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_size_enb),
                        primaryValue = formatSize(primaryVersions.enbSizeBytes, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatSize(mirrorVersions.enbSizeBytes, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    )
                )
            )
        )

        // 4. Volumes & Publication
        add(
            TableGroupData(
                categoryTitle = stringResource(R.string.appstrings_version_table_category_stats),
                rows = listOf(
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_live_stations),
                        primaryValue = formatCount(primaryVersions.liveStationCount, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatCount(mirrorVersions.liveStationCount, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_enb_rows),
                        primaryValue = formatCount(primaryVersions.enbRowCount, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatCount(mirrorVersions.enbRowCount, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    ),
                    TableRowData(
                        label = stringResource(R.string.appstrings_version_table_published),
                        primaryValue = formatPublished(primaryVersions.liveGeneratedAt, primaryVersions.unreachable, primaryVersions.isLoading),
                        mirrorValue = formatPublished(mirrorVersions.liveGeneratedAt, mirrorVersions.unreachable, mirrorVersions.isLoading)
                    )
                )
            )
        )

        // 5. Dates eNB par opérateur (si disponibles)
        val hasEnbOps = primaryVersions.enbOperators.isNotEmpty() || mirrorVersions.enbOperators.isNotEmpty()
        if (hasEnbOps) {
            add(
                TableGroupData(
                    categoryTitle = stringResource(R.string.appstrings_version_table_category_enb_operators),
                    rows = listOf(
                        TableRowData(
                            label = stringResource(R.string.appstrings_operator_orange),
                            primaryValue = formatDate(operatorDate(primaryVersions.enbOperators, "Orange"), primaryVersions.unreachable, primaryVersions.isLoading),
                            mirrorValue = formatDate(operatorDate(mirrorVersions.enbOperators, "Orange"), mirrorVersions.unreachable, mirrorVersions.isLoading)
                        ),
                        TableRowData(
                            label = stringResource(R.string.appstrings_operator_sfr),
                            primaryValue = formatDate(operatorDate(primaryVersions.enbOperators, "SFR"), primaryVersions.unreachable, primaryVersions.isLoading),
                            mirrorValue = formatDate(operatorDate(mirrorVersions.enbOperators, "SFR"), mirrorVersions.unreachable, mirrorVersions.isLoading)
                        ),
                        TableRowData(
                            label = stringResource(R.string.appstrings_operator_free),
                            primaryValue = formatDate(operatorDate(primaryVersions.enbOperators, "Free"), primaryVersions.unreachable, primaryVersions.isLoading),
                            mirrorValue = formatDate(operatorDate(mirrorVersions.enbOperators, "Free"), mirrorVersions.unreachable, mirrorVersions.isLoading)
                        ),
                        TableRowData(
                            label = stringResource(R.string.appstrings_operator_bouygues),
                            primaryValue = formatDate(operatorDate(primaryVersions.enbOperators, "Bouygues"), primaryVersions.unreachable, primaryVersions.isLoading),
                            mirrorValue = formatDate(operatorDate(mirrorVersions.enbOperators, "Bouygues"), mirrorVersions.unreachable, mirrorVersions.isLoading)
                        )
                    )
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(sizing.component(0.5.dp), borderColor, tableShape)
    ) {
        // En-tête du tableau
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor, tableShape)
                .padding(vertical = sizing.spacing(8.dp), horizontal = sizing.spacing(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.appstrings_version_api_table_db_header),
                modifier = Modifier.weight(1.15f),
                fontSize = sizing.text(12.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )
            ServerHeaderCell(
                title = stringResource(R.string.appstrings_src_server_primary),
                host = ApiServer.PRIMARY.host,
                isActive = activeServer == ApiServer.PRIMARY,
                modifier = Modifier.weight(1.0f)
            )
            ServerHeaderCell(
                title = stringResource(R.string.appstrings_src_server_mirror),
                host = ApiServer.MIRROR.host,
                isActive = activeServer == ApiServer.MIRROR,
                modifier = Modifier.weight(1.0f)
            )
        }

        HorizontalDivider(color = borderColor, thickness = sizing.component(0.5.dp))

        // Groupes et lignes du tableau
        var globalRowIndex = 0
        groups.forEachIndexed { groupIndex, group ->
            // En-tête de catégorie
            if (groupIndex > 0) {
                HorizontalDivider(color = borderColor, thickness = sizing.component(0.5.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(vertical = sizing.spacing(5.dp), horizontal = sizing.spacing(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.categoryTitle,
                    fontSize = sizing.text(11.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = borderColor, thickness = sizing.component(0.5.dp))

            group.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (globalRowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .padding(vertical = sizing.spacing(6.dp), horizontal = sizing.spacing(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier.weight(1.15f),
                        fontSize = sizing.text(11.5.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = row.primaryValue,
                        modifier = Modifier.weight(1.0f),
                        fontSize = sizing.text(11.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                    Text(
                        text = row.mirrorValue,
                        modifier = Modifier.weight(1.0f),
                        fontSize = sizing.text(11.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
                globalRowIndex++
            }
        }
    }
}

@Composable
private fun ServerHeaderCell(
    title: String,
    host: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = sizing.text(11.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = host,
            fontSize = sizing.text(9.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (isActive) {
            Surface(
                shape = RoundedCornerShape(sizing.component(8.dp)),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = sizing.spacing(2.dp))
            ) {
                Text(
                    text = stringResource(R.string.appstrings_version_api_server_active),
                    fontSize = sizing.text(9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = sizing.spacing(5.dp), vertical = sizing.spacing(1.dp))
                )
            }
        }
    }
}
