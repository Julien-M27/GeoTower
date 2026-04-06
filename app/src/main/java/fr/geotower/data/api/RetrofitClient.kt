package fr.geotower.data.api

import android.os.Build
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {
    private const val BASE_URL = "https://api.cajejuma.fr/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    // 1. Le client de secours (Désécurisé) pour les vieux téléphones
    private val unsafeOkHttpClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder().build()
        }
    }

    // 2. Le client Normal (Sécurisé) pour les téléphones récents
    private val safeOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    // 3. Le choix intelligent selon la version d'Android
    val currentClient: OkHttpClient by lazy {
        // Build.VERSION_CODES.N_MR1 correspond à Android 7.1.1
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            unsafeOkHttpClient
        } else {
            safeOkHttpClient
        }
    }

    val apiService: AnfrService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(currentClient) // <-- On injecte le client adapté ici !
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AnfrService::class.java)
    }
}