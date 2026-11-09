package fr.geotower.data.models

import com.google.gson.annotations.SerializedName
import fr.geotower.utils.OperatorColors

/**
 * Modèles pour les nouveautés et changements hebdomadaires de l'Observatoire ANFR.
 */
data class ChangesWeeksResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("weeks") val weeks: List<ChangesWeekItem>
)

data class ChangesWeekItem(
    @SerializedName("date") val date: String,
    @SerializedName("from") val fromDate: String,
    @SerializedName("to") val toDate: String,
    @SerializedName("points_count") val pointsCount: Int,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long
)

data class ChangesMapPayload(
    @SerializedName("from") val fromDate: String?,
    @SerializedName("to") val toDate: String?,
    @SerializedName("points_count") val pointsCount: Int,
    @SerializedName("points") val points: List<ChangeMapPoint>
)

data class ChangeMapPoint(
    @SerializedName("sup") val supportId: String?,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    @SerializedName("dept") val department: String?,
    @SerializedName("adr") val address: String? = null,
    @SerializedName("chg") val changes: List<ChangeDetailItem>
) {
    /**
     * Déduit tous les opérateurs distincts concernés par ce support.
     */
    fun operators(): List<String> {
        val ops = mutableListOf<String>()
        changes.forEach { item ->
            item.op?.let { raw ->
                val clean = OperatorColors.keyFor(raw) ?: raw.trim()
                if (clean.isNotBlank() && !ops.contains(clean)) ops.add(clean)
            }
            item.ops?.forEach { raw ->
                val clean = OperatorColors.keyFor(raw) ?: raw.trim()
                if (clean.isNotBlank() && !ops.contains(clean)) ops.add(clean)
            }
        }
        return ops
    }

    /**
     * Identifie le type d'événement prioritaire à représenter sur la carte.
     */
    fun primaryChangeType(): ChangeType {
        val hasActivation = changes.any { item ->
            item.code == "STATUS_CHANGED" && (
                item.nowStatus?.contains("service", ignoreCase = true) == true ||
                item.nowStatus?.contains("activ", ignoreCase = true) == true ||
                item.nowStatus?.contains("opérat", ignoreCase = true) == true ||
                item.nowStatus?.contains("operat", ignoreCase = true) == true ||
                item.nowStatus?.contains("opǸrat", ignoreCase = true) == true
            )
        }
        val hasExtinction = changes.any { item ->
            item.code == "STATUS_CHANGED" && (
                item.nowStatus?.contains("arrêt", ignoreCase = true) == true ||
                item.nowStatus?.contains("arret", ignoreCase = true) == true ||
                item.nowStatus?.contains("hors service", ignoreCase = true) == true ||
                item.nowStatus?.contains("supprim", ignoreCase = true) == true ||
                item.nowStatus?.contains("désactiv", ignoreCase = true) == true ||
                item.nowStatus?.contains("desactiv", ignoreCase = true) == true
            )
        }
        val codes = changes.map { it.code }
        return when {
            codes.contains("SITE_ADDED") -> ChangeType.SITE_ADDED
            codes.contains("SITE_REMOVED") -> ChangeType.SITE_REMOVED
            hasActivation -> ChangeType.ACTIVATION
            hasExtinction -> ChangeType.EXTINCTION
            codes.contains("STATUS_CHANGED") -> ChangeType.ACTIVATION
            codes.contains("SYSTEM_ADDED") -> ChangeType.SYSTEM_ADDED
            codes.contains("SYSTEM_REMOVED") -> ChangeType.EXTINCTION
            codes.contains("OPERATOR_ADDED") -> ChangeType.OPERATOR_ADDED
            codes.contains("OPERATOR_REMOVED") -> ChangeType.OPERATOR_REMOVED
            codes.contains("MOVED") -> ChangeType.MOVED
            else -> ChangeType.UNKNOWN
        }
    }
}

data class ChangeDetailItem(
    @SerializedName("sta") val stationId: String,
    @SerializedName("c") val code: String,
    @SerializedName("op") val op: String? = null,
    @SerializedName("ops") val ops: List<String>? = null,
    @SerializedName("sys") val system: String? = null,
    @SerializedName("systems") val systems: List<String>? = null,
    @SerializedName("was") val wasStatus: String? = null,
    @SerializedName("now") val nowStatus: String? = null,
    @SerializedName("m") val movedMeters: Double? = null
)

enum class ChangeType {
    SITE_ADDED,
    SITE_REMOVED,
    SYSTEM_ADDED,
    SYSTEM_REMOVED,
    OPERATOR_ADDED,
    OPERATOR_REMOVED,
    STATUS_CHANGED,
    ACTIVATION,
    EXTINCTION,
    MOVED,
    UNKNOWN
}
