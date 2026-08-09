package fr.geotower.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regle de l'affichage fractionne. Elle a remplace une liste de modeles en dur (Z Fold, Pixel Fold)
 * qui laissait les tablettes et les autres pliables en plein ecran, et qui figeait le choix au
 * demarrage : c'est desormais la taille reelle de la fenetre qui tranche.
 */
class DisplayStyleSplitRuleTest {

    private val phone = 411
    private val foldCoverScreen = 360
    private val foldOpen = 774
    private val tablet = 800

    @Test
    fun autoSplitsOnLargeScreensOnly() {
        // Sans choix de l'utilisateur : deux volets des qu'il y a la place, plein ecran sinon.
        assertTrue(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_AUTO, foldOpen))
        assertTrue(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_AUTO, tablet))

        assertFalse(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_AUTO, phone))
        assertFalse(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_AUTO, foldCoverScreen))
    }

    @Test
    fun explicitFullScreenAlwaysWins() {
        // Le seul choix qui vaut partout : personne ne doit se retrouver en deux volets contre son gre.
        assertFalse(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_FULL_SCREEN, tablet))
        assertFalse(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_FULL_SCREEN, foldOpen))
    }

    @Test
    fun explicitSplitStillNeedsTheRoom() {
        // Choisi sur l'ecran interne d'un pliable, puis l'appareil se referme : deux colonnes a 50 %
        // sur l'ecran de couverture seraient illisibles, on repasse en plein ecran.
        assertTrue(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_SPLIT, foldOpen))
        assertFalse(AppConfig.splitDisplayEnabled(AppConfig.DISPLAY_STYLE_SPLIT, foldCoverScreen))
    }

    @Test
    fun thresholdMatchesTheAppWideLargeScreenRule() {
        // Meme seuil que les mises en page a deux volets (ResponsiveDualPaneLayout, accueil,
        // reglages) : un ecart ici afficherait le reglage la ou il n'agit pas, ou l'inverse.
        assertFalse(AppConfig.isLargeScreenDimension(AppConfig.LARGE_SCREEN_MIN_DIMENSION_DP - 1))
        assertTrue(AppConfig.isLargeScreenDimension(AppConfig.LARGE_SCREEN_MIN_DIMENSION_DP))
    }
}
