package fr.geotower.data.api

import fr.geotower.data.models.Antenna
import fr.geotower.data.models.AntennaMap // Nouvel import
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface AnfrService {

    // Ancien endpoint (toujours utile pour les listes)
    @GET("antennes")
    suspend fun getAntennas(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 2000
    ): List<Antenna>

    // NOUVEAU ENDPOINT "LIGHT" (Pour la carte)
    @GET("antennes/map")
    suspend fun getAntennasMap(
        @Query("min_lat") minLat: Double,
        @Query("min_lon") minLon: Double,
        @Query("max_lat") maxLat: Double,
        @Query("max_lon") maxLon: Double
    ): List<AntennaMap>

    @GET("/antennes/count") // <-- Ajoute bien le slash / ici
    suspend fun getAntennasCount(): Map<String, Int>

    @GET("/antennes/sync") // <-- Ajoute bien le slash / ici
    suspend fun syncAntennas(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): List<Antenna>

    @GET("antennes/{id}")
    suspend fun getAntennaById(@Path("id") id: Long): Antenna
}