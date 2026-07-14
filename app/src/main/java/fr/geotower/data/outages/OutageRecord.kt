package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import java.util.Locale

/**
 * Représentation intermédiaire d'une panne opérateur, avant conversion en [SiteHsEntity].
 *
 * Porte tout ce dont le builder a besoin pour géocoder et dédupliquer (operatorKey, codeSiteOp,
 * coordonnées), champs que [SiteHsEntity] ne contient pas. Port Kotlin de la structure
 * `properties` + `row` de `normalize_operator_row` (`docs/server/build_sites_hs.py`).
 */
internal data class OutageRecord(
    val operatorKey: String,
    val operatorLabel: String,
    val lat: Double,
    val lon: Double,
    val stationAnfr: String?,
    val codeInsee: String?,
    val codePostal: String?,
    val departement: String?,
    val commune: String?,
    val voix2g: String?,
    val voix3g: String?,
    val voix4g: String?,
    val voix5g: String?,
    val data2g: String?,
    val data3g: String?,
    val data4g: String?,
    val data5g: String?,
    val voix: String?,
    val data: String?,
    val propre: Int?,
    val raison: String?,
    val detail: String?,
    val debutVoix: String?,
    val finVoix: String?,
    val debutData: String?,
    val finData: String?,
    val debut: String?,
    val fin: String?,
    val codeSiteOp: String?,
    val matchType: String? = null,
    val matchDistanceMeters: Double? = null,
)

/**
 * Normalise une ligne CSV opérateur (clés déjà passées par [normalizedHeader]) en [OutageRecord].
 * Renvoie null si la ligne n'a pas de coordonnées exploitables (comme `normalize_operator_row`,
 * qui écarte les lignes sans lat/lon).
 */
internal fun normalizeOperatorRow(source: OperatorOutageSource, row: Map<String, String?>): OutageRecord? {
    val operatorCode = source.codeColumns.firstNotNullOfOrNull { rowGet(row, it) }
    val lat = parseCoordinate(rowGet(row, "lat", "latitude")) ?: return null
    val lon = parseCoordinate(rowGet(row, "lon", "lng", "longitude")) ?: return null

    val debutVoix = normalizeDate(rowGet(row, "debut_interruption_voix", "debut_voix"))
    val finVoix = normalizeDate(rowGet(row, "fin_interruption_voix", "fin_voix"))
    val debutData = normalizeDate(rowGet(row, "debut_interruption_data", "debut_data"))
    val finData = normalizeDate(rowGet(row, "fin_interruption_data", "fin_data"))
    val debut = normalizeDate(rowGet(row, "debut")) ?: earliestDate(debutVoix, debutData)
    val fin = normalizeDate(rowGet(row, "fin", "fin_prev")) ?: latestDate(finVoix, finData)

    var codeInsee = rowGet(row, "code_insee")
    var codePostal = rowGet(row, "code_postal")
    if (source == OperatorOutageSource.FREE) {
        // Free n'expose pas de code postal ; l'INSEE réel se déduit du préfixe du code site.
        codePostal = codePostal ?: codeInsee
        codeInsee = freeInseeFromCodeSite(operatorCode)
    }

    val explicitStation = stationId(rowGet(row, "station_anfr", "id_anfr", "id_station_anfr"))

    return OutageRecord(
        operatorKey = source.key,
        operatorLabel = source.label,
        lat = lat,
        lon = lon,
        stationAnfr = explicitStation ?: stationIdFromOperatorCode(operatorCode),
        codeInsee = codeInsee,
        codePostal = codePostal,
        departement = rowGet(row, "departement"),
        commune = rowGet(row, "commune"),
        voix2g = normalizeStatus(rowGet(row, "2gvoix")),
        voix3g = normalizeStatus(rowGet(row, "3gvoix")),
        voix4g = normalizeStatus(rowGet(row, "4gvoix")),
        voix5g = normalizeStatus(rowGet(row, "5gvoix")),
        data2g = normalizeStatus(rowGet(row, "2gdata")),
        data3g = normalizeStatus(rowGet(row, "3gdata")),
        data4g = normalizeStatus(rowGet(row, "4gdata")),
        data5g = normalizeStatus(rowGet(row, "5gdata")),
        voix = normalizeStatus(rowGet(row, "voix")),
        data = normalizeStatus(rowGet(row, "data")),
        propre = ownSiteValue(rowGet(row, "antenne_relais_geree_par_sfr", "propre")),
        raison = normalizeStatus(rowGet(row, "raison")),
        detail = rowGet(row, "detail"),
        debutVoix = debutVoix,
        finVoix = finVoix,
        debutData = debutData,
        finData = finData,
        debut = debut,
        fin = fin,
        codeSiteOp = operatorCode,
    )
}

/**
 * Clé de déduplication : par station si connue, sinon par code site opérateur, sinon par position
 * arrondie. Équivalent de `dedup_key`.
 */
internal fun dedupKey(record: OutageRecord): String {
    val station = record.stationAnfr
    if (!station.isNullOrEmpty()) return "station|${record.operatorKey}|$station"
    val code = cleanText(record.codeSiteOp)
    if (code != null) return "code|${record.operatorKey}|$code"
    val latRounded = String.format(Locale.US, "%.5f", record.lat)
    val lonRounded = String.format(Locale.US, "%.5f", record.lon)
    return "pos|${record.operatorKey}|$latRounded|$lonRounded"
}

/**
 * Fusionne une seconde ligne du même opérateur ciblant le même site. La première fait foi pour les
 * statuts ; on comble seulement les trous et on élargit la fenêtre de dates. Équivalent de
 * `merge_duplicate_row`.
 */
internal fun mergeOutage(existing: OutageRecord, incoming: OutageRecord): OutageRecord {
    fun fill(current: String?, other: String?): String? = current ?: other
    return existing.copy(
        stationAnfr = existing.stationAnfr ?: incoming.stationAnfr,
        codeInsee = fill(existing.codeInsee, incoming.codeInsee),
        codePostal = fill(existing.codePostal, incoming.codePostal),
        departement = fill(existing.departement, incoming.departement),
        commune = fill(existing.commune, incoming.commune),
        voix2g = fill(existing.voix2g, incoming.voix2g),
        voix3g = fill(existing.voix3g, incoming.voix3g),
        voix4g = fill(existing.voix4g, incoming.voix4g),
        voix5g = fill(existing.voix5g, incoming.voix5g),
        data2g = fill(existing.data2g, incoming.data2g),
        data3g = fill(existing.data3g, incoming.data3g),
        data4g = fill(existing.data4g, incoming.data4g),
        data5g = fill(existing.data5g, incoming.data5g),
        voix = fill(existing.voix, incoming.voix),
        data = fill(existing.data, incoming.data),
        propre = existing.propre ?: incoming.propre,
        raison = fill(existing.raison, incoming.raison),
        detail = fill(existing.detail, incoming.detail),
        debut = earliestDate(existing.debut, incoming.debut) ?: incoming.debut,
        debutVoix = earliestDate(existing.debutVoix, incoming.debutVoix) ?: incoming.debutVoix,
        debutData = earliestDate(existing.debutData, incoming.debutData) ?: incoming.debutData,
        fin = latestDate(existing.fin, incoming.fin) ?: incoming.fin,
        finVoix = latestDate(existing.finVoix, incoming.finVoix) ?: incoming.finVoix,
        finData = latestDate(existing.finData, incoming.finData) ?: incoming.finData,
        codeSiteOp = existing.codeSiteOp ?: incoming.codeSiteOp,
    )
}

/** Conversion finale vers le modèle consommé par l'app. Une station absente devient `""` (comme le parsing serveur). */
internal fun OutageRecord.toSiteHsEntity(sourceLastUpdate: String?): SiteHsEntity = SiteHsEntity(
    idAnfr = stationAnfr ?: "",
    operateur = operatorLabel,
    latitude = lat,
    longitude = lon,
    geometryType = "Point",
    departement = departement,
    codePostal = codePostal,
    codeInsee = codeInsee,
    commune = commune,
    voix2g = voix2g,
    voix3g = voix3g,
    voix4g = voix4g,
    voix5g = voix5g,
    data2g = data2g,
    data3g = data3g,
    data4g = data4g,
    data5g = data5g,
    voixGlobal = voix,
    dataGlobal = data,
    propre = propre,
    raison = raison,
    detail = detail,
    debutVoix = debutVoix,
    finVoix = finVoix,
    debutData = debutData,
    finData = finData,
    dateDebut = debut,
    dateFin = fin,
    sourceLastUpdate = sourceLastUpdate,
    isPotential = false,
)
