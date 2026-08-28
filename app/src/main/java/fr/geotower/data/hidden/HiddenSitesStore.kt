package fr.geotower.data.hidden

import android.content.Context
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Registre local des opérateurs retirés d'un site physique.
 *
 * La clé du site repose sur ses coordonnées arrondies, et non sur l'identifiant ANFR : les
 * identifiants peuvent changer lors d'une mise à jour de la base alors que le pylône reste au
 * même endroit. Une entrée est indépendante par opérateur afin de pouvoir masquer un opérateur
 * sans faire disparaître les autres du même site.
 */
data class HiddenSiteRecord(
    val physicalSiteKey: String,
    val operatorKey: String,
    val operatorLabel: String,
    val idAnfr: String,
    val latitude: Double,
    val longitude: Double,
    val azimuts: String? = null,
    val azimutsFh: String? = null,
    val hiddenAtMillis: Long = System.currentTimeMillis()
)

object HiddenSitesStore {
    private const val PREF_KEY = "hidden_sites_v1"
    private val lock = Any()
    private val _records = MutableStateFlow<List<HiddenSiteRecord>>(emptyList())
    private val recordsState: StateFlow<List<HiddenSiteRecord>> = _records.asStateFlow()

    @Volatile
    private var loaded = false

    fun flow(context: Context): StateFlow<List<HiddenSiteRecord>> {
        ensureLoaded(context)
        return recordsState
    }

    fun records(context: Context): List<HiddenSiteRecord> {
        ensureLoaded(context)
        return _records.value
    }

    fun hasAny(context: Context): Boolean = records(context).isNotEmpty()

    fun operatorKeysFor(raw: String?): List<String> {
        val known = OperatorColors.keysFor(raw)
        if (known.isNotEmpty()) return known
        val normalized = raw.orEmpty().trim()
        return normalized.takeIf { it.isNotBlank() }?.let { listOf(fallbackOperatorKey(it)) }.orEmpty()
    }

    fun operatorLabelFor(key: String, fallback: String? = null): String {
        return OperatorColors.specForKey(key)?.label
            ?: fallback?.trim()?.takeIf { it.isNotBlank() }
            ?: key.removePrefix("raw:").replace('_', ' ')
    }

    fun isHidden(context: Context, antenna: LocalisationEntity): Boolean {
        if (antenna.idAnfr.startsWith("CLUSTER_")) return false
        val operators = operatorKeysFor(antenna.operateur)
        return operators.isNotEmpty() && operators.all { isHidden(context, antenna.physicalSiteKey(), it) }
    }

    fun isHidden(context: Context, site: SiteHsEntity): Boolean {
        return isHidden(context, site.physicalSiteKey(), operatorKeysFor(site.operateur))
    }

    fun isHidden(context: Context, siteKey: String, operatorKey: String): Boolean {
        ensureLoaded(context)
        val normalizedOperator = normalizeOperatorKey(operatorKey)
        return _records.value.any {
            it.physicalSiteKey == siteKey && it.operatorKey == normalizedOperator
        }
    }

    fun isHidden(context: Context, siteKey: String, operatorKeys: List<String>): Boolean {
        if (operatorKeys.isEmpty()) return false
        return operatorKeys.all { isHidden(context, siteKey, it) }
    }

    fun filter(context: Context, antennas: List<LocalisationEntity>): List<LocalisationEntity> {
        if (antennas.isEmpty() || !hasAny(context)) return antennas
        return antennas.filterNot { isHidden(context, it) }
    }

    fun recordFor(antenna: LocalisationEntity, operatorKey: String): HiddenSiteRecord {
        val normalizedKey = normalizeOperatorKey(operatorKey)
        return HiddenSiteRecord(
            physicalSiteKey = antenna.physicalSiteKey(),
            operatorKey = normalizedKey,
            operatorLabel = operatorLabelFor(normalizedKey, antenna.operateur),
            idAnfr = antenna.idAnfr,
            latitude = antenna.latitude,
            longitude = antenna.longitude,
            azimuts = antenna.azimuts,
            azimutsFh = antenna.azimutsFh
        )
    }

    fun recordsFor(antennas: List<LocalisationEntity>): List<HiddenSiteRecord> {
        return antennas
            .flatMap { antenna ->
                operatorKeysFor(antenna.operateur).map { operatorKey -> recordFor(antenna, operatorKey) }
            }
            .distinctBy { it.identity() }
    }

    /** Ajoute les entrées absentes et renvoie le nombre réellement ajouté. */
    fun hide(context: Context, record: HiddenSiteRecord): Boolean {
        return merge(context, listOf(record)) > 0
    }

    fun merge(context: Context, incoming: List<HiddenSiteRecord>): Int {
        if (incoming.isEmpty()) return 0
        synchronized(lock) {
            ensureLoadedLocked(context)
            val current = _records.value.toMutableList()
            val known = current.mapTo(HashSet()) { it.identity() }
            var added = 0
            incoming.forEach { raw ->
                val record = raw.normalized()
                if (record.physicalSiteKey.isBlank() || record.operatorKey.isBlank()) return@forEach
                if (known.add(record.identity())) {
                    current += record
                    added++
                }
            }
            if (added > 0) persistLocked(context, current)
            return added
        }
    }

    fun restore(context: Context, record: HiddenSiteRecord): Boolean {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val identity = record.identity()
            val updated = _records.value.filterNot { it.identity() == identity }
            if (updated.size == _records.value.size) return false
            persistLocked(context, updated)
            return true
        }
    }

    private fun ensureLoaded(context: Context) {
        synchronized(lock) { ensureLoadedLocked(context) }
    }

    private fun ensureLoadedLocked(context: Context) {
        val prefs = appContext(context).getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        if (loaded) return
        _records.value = parse(prefs.getString(PREF_KEY, null))
        loaded = true
    }

    private fun persistLocked(context: Context, records: List<HiddenSiteRecord>) {
        val appContext = appContext(context)
        val prefs = appContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY, JSONArray().apply {
            records.sortedWith(compareBy<HiddenSiteRecord> { it.operatorLabel }.thenBy { it.physicalSiteKey })
                .forEach { put(it.toJson()) }
        }.toString()).apply()
        _records.value = records
        loaded = true
    }

    private fun parse(raw: String?): List<HiddenSiteRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val record = runCatching { array.optJSONObject(index)?.toRecord() }.getOrNull()
                if (record != null && record.physicalSiteKey.isNotBlank() && record.operatorKey.isNotBlank()) {
                    add(record.normalized())
                }
            }
        }.distinctBy { it.identity() }
    }

    private fun HiddenSiteRecord.toJson(): JSONObject = JSONObject()
        .put("physicalSiteKey", physicalSiteKey)
        .put("operatorKey", operatorKey)
        .put("operatorLabel", operatorLabel)
        .put("idAnfr", idAnfr)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("azimuts", azimuts)
        .put("azimutsFh", azimutsFh)
        .put("hiddenAtMillis", hiddenAtMillis)

    private fun JSONObject.toRecord(): HiddenSiteRecord = HiddenSiteRecord(
        physicalSiteKey = optString("physicalSiteKey"),
        operatorKey = optString("operatorKey"),
        operatorLabel = optString("operatorLabel"),
        idAnfr = optString("idAnfr"),
        latitude = optDouble("latitude", 0.0),
        longitude = optDouble("longitude", 0.0),
        azimuts = optString("azimuts").takeIf { it.isNotBlank() },
        azimutsFh = optString("azimutsFh").takeIf { it.isNotBlank() },
        hiddenAtMillis = optLong("hiddenAtMillis", System.currentTimeMillis())
    )

    private fun HiddenSiteRecord.normalized(): HiddenSiteRecord = copy(
        physicalSiteKey = physicalSiteKey.trim(),
        operatorKey = normalizeOperatorKey(operatorKey),
        operatorLabel = operatorLabelFor(operatorKey, operatorLabel)
    )

    private fun HiddenSiteRecord.identity(): String =
        "${physicalSiteKey.trim()}|${normalizeOperatorKey(operatorKey)}"

    private fun normalizeOperatorKey(raw: String): String {
        val clean = raw.trim()
        return OperatorColors.keyFor(clean) ?: fallbackOperatorKey(clean)
    }

    private fun fallbackOperatorKey(raw: String): String =
        "raw:" + raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")

    private fun appContext(context: Context): Context = context.applicationContext ?: context

    private fun SiteHsEntity.physicalSiteKey(): String =
        "${Math.round(latitude * 10000.0)}_${Math.round(longitude * 10000.0)}"
}
