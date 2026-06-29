package fr.geotower.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geotower.data.coverage.SiteCoverage
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Dessine la couverture théorique d'un site comme une **enveloppe fusionnée mono-teinte estompée** :
 * tous les secteurs (un par antenne réelle) sont réunis dans un seul [Path] en règle non-zéro
 * (les recouvrements ne créent pas de coutures), rempli d'un **dégradé radial** qui s'estompe avec la
 * distance au site. Les creux de l'enveloppe matérialisent naturellement les ombres du relief / du bâti.
 *
 * Choix produit : mono-teinte (couleur opérateur), surtout pas une heatmap RF colorée — il s'agit d'une
 * estimation géométrique de ligne de visée, pas d'une couverture mesurée.
 */
class TheoreticalCoverageOverlay(context: Context) : Overlay() {
    private val density = context.resources.displayMetrics.density
    private val point = Point()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private var coverage: SiteCoverage? = null
    private var tint: Int = DEFAULT_TINT

    fun setCoverage(next: SiteCoverage?, color: Int = DEFAULT_TINT) {
        coverage = next
        tint = color
    }

    fun clear() {
        coverage = null
    }

    val hasCoverage: Boolean get() = coverage?.isEmpty == false

    override fun draw(canvas: Canvas, projection: Projection) {
        val cov = coverage ?: return
        if (cov.isEmpty) return

        projection.toPixels(GeoPoint(cov.siteLat, cov.siteLon), point)
        val siteX = point.x.toFloat()
        val siteY = point.y.toFloat()

        val path = Path().apply { fillType = Path.FillType.WINDING }
        var maxRadiusPx = 0f
        for (sector in cov.sectors) {
            val outline = sector.outline(cov.siteLat, cov.siteLon)
            if (outline.size < 2) continue
            var first = true
            for (ll in outline) {
                projection.toPixels(GeoPoint(ll.latitude, ll.longitude), point)
                val px = point.x.toFloat()
                val py = point.y.toFloat()
                if (first) {
                    path.moveTo(px, py)
                    first = false
                } else {
                    path.lineTo(px, py)
                }
                val r = hypot((px - siteX).toDouble(), (py - siteY).toDouble()).toFloat()
                if (r > maxRadiusPx) maxRadiusPx = r
            }
            path.close()
        }
        if (maxRadiusPx <= 1f) return

        val baseR = Color.red(tint)
        val baseG = Color.green(tint)
        val baseB = Color.blue(tint)
        fillPaint.shader = RadialGradient(
            siteX, siteY, maxRadiusPx,
            Color.argb(140, baseR, baseG, baseB),
            Color.argb(28, baseR, baseG, baseB),
            Shader.TileMode.CLAMP
        )
        strokePaint.color = Color.argb(170, baseR, baseG, baseB)

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        fillPaint.shader = null
    }

    companion object {
        /** Teinte par défaut (bleu GeoTower) si l'opérateur n'a pas de couleur. */
        private val DEFAULT_TINT = Color.rgb(33, 118, 207)
    }
}
