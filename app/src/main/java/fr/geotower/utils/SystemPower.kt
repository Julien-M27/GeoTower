package fr.geotower.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/**
 * Reflète l'état « économie d'énergie » d'Android ([PowerManager.isPowerSaveMode]).
 *
 * Consulté par [PowerProfile] : si l'utilisateur a activé [AppConfig.lowPowerFollowSystem], le passage
 * du téléphone en économie d'énergie relève automatiquement le mode faible consommation au niveau Éco.
 *
 * [isSaveMode] est adossé à un état Compose : l'UI et PowerProfile réagissent à ses changements.
 * Le receiver est enregistré sur l'applicationContext pour toute la vie du process (singleton, pas de fuite).
 */
object SystemPower {
    private val _isSaveMode = mutableStateOf(false)
    val isSaveMode: Boolean get() = _isSaveMode.value

    private var installed = false

    fun init(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isSaveMode.value = pm?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                _isSaveMode.value = pm?.isPowerSaveMode == true
            }
        }
        // Broadcast système protégé → NOT_EXPORTED (exigé par Android 14+ pour les receivers dynamiques).
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
