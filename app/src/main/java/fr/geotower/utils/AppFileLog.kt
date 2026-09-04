package fr.geotower.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import fr.geotower.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de débogage écrit sur disque, pour les chemins qu'on ne peut pas observer avec un PC
 * branché : Android Auto (l'USB est pris par la voiture), workers en arrière-plan, plantage au
 * démarrage. [AppLogger] ne parle qu'à logcat et seulement en debug ; ici on garde une trace lisible
 * depuis l'appareil, exportable depuis la page Diagnostic.
 *
 * Le volume est borné pour conserver les dernières sessions, mais les traces détaillées utiles au
 * diagnostic (surface, permissions, rendu, réseau et exceptions) restent conservées.
 */
object AppFileLog {
    private const val TAG = "AppFileLog"
    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "geotower-debug.log"

    /** Au-delà, on ne garde que la fin du journal : c'est la dernière session qui intéresse. */
    private const val MAX_BYTES = 512L * 1024L
    private const val KEEP_BYTES = 192 * 1024

    const val MIME_TYPE = "text/plain"

    private val lock = Any()

    // SimpleDateFormat n'est pas thread-safe : toutes les écritures passent par `lock`.
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        i(
            TAG,
            "--- GeoTower ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) ---"
        )
    }

    /**
     * Enchaîne sur le gestionnaire précédent : on ne remplace pas le rapporteur du système, on
     * s'insère juste devant pour garder la pile d'appel d'un crash qui, sinon, ne laisse rien.
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(previous))
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = write("E", tag, message, throwable)

    fun file(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun sizeBytes(context: Context): Long = runCatching { file(context).length() }.getOrDefault(0L)

    fun isEmpty(context: Context): Boolean = sizeBytes(context) <= 0L

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
        }
    }

    fun shareUri(context: Context): Uri? = runCatching {
        val target = file(context)
        if (!target.exists() || target.length() <= 0L) return@runCatching null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()

    /**
     * @return false si le journal est vide (rien à envoyer) ou si aucune application ne sait le
     * recevoir — l'appelant affiche alors un message plutôt qu'un partage silencieux.
     */
    fun share(context: Context, chooserTitle: String): Boolean {
        val uri = shareUri(context) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, FILE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // FLAG_ACTIVITY_NEW_TASK requis : le contexte Compose est un contexte localisé, pas une
        // Activity → sinon crash OxygenOS (cf. partages de la fiche site).
        val chooser = Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val context = appContext ?: return
        synchronized(lock) {
            runCatching {
                val target = file(context)
                truncateIfTooLarge(target)
                val stack = throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()
                target.appendText("${stampFormat.format(Date())} $level/$tag: $message$stack\n", Charsets.UTF_8)
            }
        }
    }

    private fun truncateIfTooLarge(target: File) {
        if (target.length() <= MAX_BYTES) return
        runCatching {
            val bytes = target.readBytes()
            val from = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
            target.writeBytes(
                "--- début du journal tronqué ---\n".toByteArray(Charsets.UTF_8) +
                    bytes.copyOfRange(from, bytes.size)
            )
        }
    }

    private class CrashHandler(
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { e("Crash", "Exception non capturée sur le thread « ${thread.name} »", error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
