package fr.geotower.data.netlog

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NetworkLogInterceptor(private val contextProvider: () -> Context?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val url = request.url.toString()
        val host = request.url.host
        val path = request.url.encodedPath
        val method = request.method
        val timestamp = System.currentTimeMillis()

        var response: Response? = null
        var caughtException: Exception? = null

        try {
            response = chain.proceed(request)
            return response
        } catch (e: Exception) {
            caughtException = e
            throw e
        } finally {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            val statusCode = response?.code ?: -1
            val responseSize = runCatching { response?.body?.contentLength() }.getOrNull() ?: -1L
            val errorMessage = caughtException?.let {
                val msg = it.message
                if (!msg.isNullOrBlank()) "${it.javaClass.simpleName}: $msg" else it.javaClass.simpleName
            }

            val purpose = request.header("X-GeoTower-Purpose")
            val feature = when (purpose) {
                "ping" -> "Test de latence (Ping)"
                "probe" -> "Sonde serveur (Disponibilité)"
                else -> null
            }

            val entry = NetworkRequestEntry(
                timestamp = timestamp,
                method = method,
                url = url,
                host = host,
                path = path,
                statusCode = statusCode,
                durationMs = durationMs,
                responseSizeBytes = responseSize,
                errorMessage = errorMessage,
                feature = feature
            )

            contextProvider()?.let { ctx ->
                NetworkLogStore.record(ctx, entry)
            }
        }
    }
}
