package fr.geotower.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun UserLocationArrow(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF007AFF) // Bleu Apple Maps
) {
    // Taille globale du canvas (cône + flèche)
    Canvas(modifier = modifier.size(120.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        // 1. DESSIN DU CÔNE (CHAMP DE VISION)
        val conePath = Path().apply {
            moveTo(centerX, centerY) // Part du centre
            lineTo(centerX - 40f, centerY - 90f) // Coin haut gauche
            // Arc arrondi en haut
            quadraticBezierTo(centerX, centerY - 120f, centerX + 40f, centerY - 90f)
            lineTo(centerX, centerY) // Retour au centre
            close()
        }

        drawPath(
            path = conePath,
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = 150f
            )
        )

        // 2. DESSIN DE LA FLÈCHE BLANCHE
        val arrowPath = Path().apply {
            // On dessine une flèche qui pointe vers le haut (-Y)
            moveTo(centerX, centerY + 8f) // Base un peu sous le centre
            lineTo(centerX - 14f, centerY - 20f) // Aile gauche
            lineTo(centerX, centerY - 32f) // Pointe
            lineTo(centerX + 14f, centerY - 20f) // Aile droite
            close()
        }

        // Contour Bleu
        drawPath(
            path = arrowPath,
            color = color,
            style = Stroke(width = 6f)
        )
        // Remplissage Blanc
        drawPath(
            path = arrowPath,
            color = Color.White,
            style = Fill
        )
    }
}