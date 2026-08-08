package fr.geotower.services

import androidx.car.app.AppInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Poignée de main Android Auto. `CarAppBinder.getAppInfo()` — le tout premier appel de l'hôte après
 * le bind — construit un [AppInfo], qui lit `androidx.car.app.minCarApiLevel` dans les meta-data de
 * l'APPLICATION (`ApplicationInfo.metaData`). Déclaré sur le `<service>`, le meta-data reste
 * invisible ici : `AppInfo.create` lève alors une IllegalArgumentException, l'hôte reçoit un échec
 * et se déconnecte au bout de quelques secondes sans jamais afficher l'app.
 *
 * Ce test rejoue exactement ce chemin, sans voiture ni Desktop Head Unit.
 */
@RunWith(AndroidJUnit4::class)
class CarAppHandshakeTest {

    @Test
    fun appInfoIsBuildableAsTheCarHostDoesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appInfo = AppInfo.create(context)
        assertTrue(
            "minCarApiLevel déclaré = ${appInfo.minCarAppApiLevel}",
            appInfo.minCarAppApiLevel >= 1
        )
    }
}
