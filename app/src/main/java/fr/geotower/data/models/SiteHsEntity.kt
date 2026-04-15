package fr.geotower.data.models

/**
 * Modèle de données détaillé pour les antennes en panne (Sites HS).
 * Il est alimenté par notre propre GeoJSON interne.
 */
data class SiteHsEntity(
    // --- Identifiants et Position ---
    val idAnfr: String,     // Correspond à "station_anfr"
    val operateur: String,
    val latitude: Double,   // Extrait de geometry.coordinates[1]
    val longitude: Double,  // Extrait de geometry.coordinates[0]

    // --- Localisation ---
    val departement: String? = null,
    val codePostal: String? = null, // "code_postal" dans le JSON
    val codeInsee: String? = null,  // "code_insee" dans le JSON
    val commune: String? = null,

    // --- État détaillé des services Voix ---
    val voix2g: String? = null,
    val voix3g: String? = null,
    val voix4g: String? = null,
    val voix5g: String? = null, // Ajouté par sécurité pour le futur

    // --- État détaillé des services Data (Internet) ---
    val data2g: String? = null, // Ajouté par sécurité
    val data3g: String? = null,
    val data4g: String? = null,
    val data5g: String? = null,

    // --- État global (les anciennes variables) ---
    val voixGlobal: String? = null, // Correspond à "voix" dans le JSON
    val dataGlobal: String? = null, // Correspond à "data" dans le JSON

    // --- Détails de la panne ---
    val propre: Int? = null,
    val raison: String? = null,
    val detail: String? = null,

    // --- Dates de pannes spécifiques ---
    val debutVoix: String? = null, // "debut_voix"
    val finVoix: String? = null,   // "fin_voix"
    val debutData: String? = null, // "debut_data"
    val finData: String? = null,   // "fin_data"

    // --- Dates globales ---
    val dateDebut: String? = null, // Correspond à "debut"
    val dateFin: String? = null    // Correspond à "fin"
)