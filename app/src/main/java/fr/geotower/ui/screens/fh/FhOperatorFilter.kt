package fr.geotower.ui.screens.fh

import fr.geotower.data.models.FhLinkItem
import fr.geotower.utils.OperatorColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class FhOperatorMeta(
    val id: String,
    val label: String,
    val colorInt: Int,
    val isMajorMobile: Boolean
)

data class FhOperatorBucket(
    val id: String,
    val label: String,
    val colorInt: Int,
    val count: Int,
    val isMajorMobile: Boolean
)

data class FhPreparedLink(
    val link: FhLinkItem,
    val operatorId: String,
    val operatorLabel: String,
    val colorInt: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val mxA: Double,
    val myA: Double,
    val mxB: Double,
    val myB: Double
)

object FhOperatorResolver {
    const val OTHER_OPERATOR_ID = "OTHER"
    private const val OTHER_COLOR = 0xFF78909C.toInt()

    private val majorMetroKeys = setOf(
        OperatorColors.ORANGE_KEY,
        OperatorColors.BOUYGUES_KEY,
        OperatorColors.SFR_KEY,
        OperatorColors.FREE_KEY
    )

    private val extraOperatorPalette = intArrayOf(
        0xFF26A69A.toInt(),
        0xFFAB47BC.toInt(),
        0xFF5C6BC0.toInt(),
        0xFFEC407A.toInt(),
        0xFF8D6E63.toInt(),
        0xFF29B6F6.toInt(),
        0xFF9CCC65.toInt(),
        0xFFFFA726.toInt()
    )

    fun resolve(link: FhLinkItem): FhOperatorMeta {
        val matchedKey = OperatorColors.keyFor(link.operatorLabel)
        if (matchedKey != null) {
            val spec = OperatorColors.specForKey(matchedKey)
            return FhOperatorMeta(
                id = matchedKey,
                label = spec?.label ?: matchedKey,
                colorInt = spec?.colorArgb?.toInt() ?: OTHER_COLOR,
                isMajorMobile = matchedKey in majorMetroKeys
            )
        }

        val rawLabel = link.operatorLabel?.trim().orEmpty()
        if (rawLabel.isNotBlank() && !rawLabel.startsWith("adm_") && !rawLabel.startsWith("op_")) {
            val normalizedId = "RAW:${rawLabel.uppercase(Locale.ROOT)}"
            val colorIndex = abs(normalizedId.hashCode()) % extraOperatorPalette.size
            return FhOperatorMeta(
                id = normalizedId,
                label = rawLabel,
                colorInt = extraOperatorPalette[colorIndex],
                isMajorMobile = false
            )
        }

        return FhOperatorMeta(
            id = OTHER_OPERATOR_ID,
            label = "Autres / Inconnu",
            colorInt = OTHER_COLOR,
            isMajorMobile = false
        )
    }

    fun mercatorX(lon: Double): Double = (lon + 180.0) / 360.0

    fun mercatorY(lat: Double): Double {
        val sinLat = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
        return 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)
    }

    fun prepare(links: List<FhLinkItem>): List<FhPreparedLink> {
        return links.map { link ->
            val meta = resolve(link)
            FhPreparedLink(
                link = link,
                operatorId = meta.id,
                operatorLabel = meta.label,
                colorInt = meta.colorInt,
                minLat = min(link.latA, link.latB),
                maxLat = max(link.latA, link.latB),
                minLon = min(link.lonA, link.lonB),
                maxLon = max(link.lonA, link.lonB),
                mxA = mercatorX(link.lonA),
                myA = mercatorY(link.latA),
                mxB = mercatorX(link.lonB),
                myB = mercatorY(link.latB)
            )
        }
    }

    fun buildBuckets(preparedLinks: List<FhPreparedLink>): List<FhOperatorBucket> {
        if (preparedLinks.isEmpty()) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        val metas = HashMap<String, FhPreparedLink>()
        for (item in preparedLinks) {
            counts[item.operatorId] = (counts[item.operatorId] ?: 0) + 1
            if (item.operatorId !in metas) {
                metas[item.operatorId] = item
            }
        }

        val orderMap = OperatorColors.orderedKeys
            .withIndex()
            .associate { (idx, key) -> key to idx }

        return counts.entries
            .mapNotNull { (id, count) ->
                val sample = metas[id] ?: return@mapNotNull null
                FhOperatorBucket(
                    id = id,
                    label = sample.operatorLabel,
                    colorInt = sample.colorInt,
                    count = count,
                    isMajorMobile = id in majorMetroKeys
                )
            }
            .sortedWith(
                compareBy<FhOperatorBucket>(
                    { bucket ->
                        when {
                            bucket.id == OTHER_OPERATOR_ID -> 3
                            bucket.id in orderMap -> 0
                            else -> 1
                        }
                    },
                    { bucket -> orderMap[bucket.id] ?: Int.MAX_VALUE },
                    { bucket -> -bucket.count },
                    { bucket -> bucket.label.lowercase(Locale.ROOT) }
                )
            )
    }
}
