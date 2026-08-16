package fr.geotower.data.backup

import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.data.notifications.NotificationHistoryEntry
import fr.geotower.data.share.ShareHistoryEntry
import fr.geotower.data.trip.TripLeg
import fr.geotower.data.trip.TripManeuver
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.TripStep
import fr.geotower.data.upload.ExternalPhotoUploadHistoryEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le codec de sauvegarde est écrit à la main, champ par champ : ajouter un champ à une entrée sans
 * l'ajouter ici le ferait disparaître d'un téléphone à l'autre **sans la moindre erreur**. Ces
 * tests comparent des objets entiers, pas des champs choisis : un champ oublié fait tomber
 * l'assertion d'égalité, ce qu'aucune vérification à l'exécution ne saurait signaler.
 */
class AppBackupCodecTest {

    @Test
    fun shareEntrySurvivesTheRoundTrip() {
        val entry = ShareHistoryEntry(
            id = "3a1f-share",
            kind = "mobile_site",
            destination = "share",
            supportId = "123456",
            stationId = "987654",
            label = "Orange",
            address = "12 rue des Tours, Grenoble",
            latitude = 45.188529,
            longitude = 5.724524,
            itemCount = 3,
            createdAtMillis = 1_755_000_000_000L,
            contents = "map,freq,ids",
            darkTheme = true,
            mapZoom = 14.5
        )

        assertEquals(entry, roundTrip(entry, AppBackupCodec::shareEntryToJson, AppBackupCodec::shareEntryFromJson))
    }

    @Test
    fun shareEntryKeepsItsEmptyFieldsEmpty() {
        // Une copie de champ n'a ni support, ni adresse, ni coordonnées : les champs nuls doivent
        // revenir nuls, et non transformés en chaînes vides ou en zéros.
        val entry = ShareHistoryEntry(
            id = "copy-1",
            kind = "field_copy",
            destination = "clipboard",
            label = "45.1885, 5.7245",
            itemCount = 1,
            createdAtMillis = 1_755_000_000_000L,
            contents = "field_gps"
        )

        assertEquals(entry, roundTrip(entry, AppBackupCodec::shareEntryToJson, AppBackupCodec::shareEntryFromJson))
    }

    @Test
    fun notificationEntrySurvivesTheRoundTrip() {
        val entry = NotificationHistoryEntry(
            id = "notif-1",
            type = "db_mobile",
            status = "success",
            label = "geotower_fr.db",
            detail = "rebuild",
            itemCount = 12,
            target = "geotower://map",
            posted = true,
            createdAtMillis = 1_755_100_000_000L
        )

        assertEquals(
            entry,
            roundTrip(entry, AppBackupCodec::notificationEntryToJson, AppBackupCodec::notificationEntryFromJson)
        )
    }

    @Test
    fun uploadEntryTravelsWithoutItsLocalThumbnailPath() {
        // Le chemin de la vignette est celui de l'appareil d'origine : il ne veut rien dire ailleurs,
        // et c'est la vignette elle-même qui voyage, en base64, à côté de l'entrée.
        val entry = ExternalPhotoUploadHistoryEntry(
            id = "upload-1",
            uploadId = "queue-42",
            sourceName = "SignalQuest",
            supportId = "123456",
            operator = "Free",
            createdAtMillis = 1_755_200_000_000L,
            thumbnailPath = "/data/user/0/fr.geotower/files/thumbs/upload-1.jpg",
            status = "success",
            stripExifBeforeUpload = true,
            remotePhotoId = "sq-9001",
            remoteImageUrl = "https://example.invalid/9001.jpg",
            remoteUploadedAt = "2026-08-14T10:12:00Z",
            lastValidationCheckAtMillis = 1_755_300_000_000L,
            validationCheckCount = 4
        )

        val json = AppBackupCodec.uploadEntryToJson(entry, thumbnailBase64 = "AAECAw==")
        val decoded = AppBackupCodec.uploadEntryFromJson(json)

        assertEquals(entry.copy(thumbnailPath = null), decoded)
        assertEquals("AAECAw==", AppBackupCodec.uploadThumbnailBase64(json))
    }

    @Test
    fun uploadEntryWithoutThumbnailCarriesNone() {
        val entry = ExternalPhotoUploadHistoryEntry(
            id = "upload-2",
            uploadId = "queue-43",
            sourceName = "SignalQuest",
            supportId = "654321",
            operator = "SFR",
            createdAtMillis = 1_755_200_000_000L,
            thumbnailPath = null,
            status = "pending",
            stripExifBeforeUpload = false
        )

        val json = AppBackupCodec.uploadEntryToJson(entry, thumbnailBase64 = null)

        assertEquals(entry, AppBackupCodec.uploadEntryFromJson(json))
        assertNull(AppBackupCodec.uploadThumbnailBase64(json))
    }

    @Test
    fun reportEntrySurvivesTheRoundTrip() {
        val entry = PhotoReportHistoryEntry(
            id = "report-1",
            photoId = "photo-777",
            siteId = "123456",
            reason = "wrong_location",
            description = "Photo prise sur un autre pylône",
            photoUrl = "https://example.invalid/777.jpg",
            operatorLabel = "Bouygues Telecom",
            createdAtMillis = 1_755_400_000_000L,
            status = "removed",
            lastCheckAtMillis = 1_755_500_000_000L,
            checkCount = 7,
            resolvedAtMillis = 1_755_500_000_000L
        )

        assertEquals(entry, roundTrip(entry, AppBackupCodec::reportEntryToJson, AppBackupCodec::reportEntryFromJson))
    }

    @Test
    fun tripSurvivesTheRoundTripWithItsStepsAndLegs() {
        val plan = TripPlan(
            id = "trip-1",
            schemaVersion = 1,
            name = "Tournée du 14 août",
            createdAtMillis = 1_755_000_000_000L,
            updatedAtMillis = 1_755_600_000_000L,
            profile = "car",
            returnToStart = true,
            steps = listOf(
                TripStep(
                    latitude = 45.188529,
                    longitude = 5.724524,
                    label = "Départ",
                    kind = "current_position",
                    supportId = null,
                    visitedAtMillis = 1_755_610_000_000L,
                    note = null,
                    profileToNext = null,
                    photosSentCount = 0
                ),
                TripStep(
                    latitude = 45.191000,
                    longitude = 5.730000,
                    label = "Pylône nord",
                    kind = "site",
                    supportId = "123456",
                    visitedAtMillis = null,
                    note = "Accès par le chemin",
                    profileToNext = "pedestrian",
                    // Le compte rendu de terrain doit passer d'un téléphone à l'autre : sans ce
                    // champ dans le codec, la tournée reviendrait vidée de ce qu'elle a produit.
                    photosSentCount = 4
                )
            ),
            legs = listOf(
                TripLeg(
                    fromIndex = 0,
                    toIndex = 1,
                    profile = "car",
                    distanceMeters = 1234.5,
                    durationSeconds = 180.0,
                    encodedGeometry = "abc123",
                    maneuvers = listOf(
                        TripManeuver("depart", null, "RUE DES TOURS", 0.0, 0.0),
                        TripManeuver("turn", "left", "AV DE LA GARE", 300.0, 45.0)
                    ),
                    optimization = "shortest"
                )
            ),
            plannedAtMillis = 1_755_700_000_000L,
            reminderOffsetsMinutes = listOf(1440, 180),
            stopDurationMinutes = 15,
            status = "planned",
            autoNamed = false
        )

        assertEquals(plan, roundTrip(plan, AppBackupCodec::tripToJson, AppBackupCodec::tripFromJson))
    }

    @Test
    fun tripWithoutManeuversKeepsThemNull() {
        // `maneuvers` nul et liste vide ne veulent pas dire la même chose : nul = trajet calculé
        // avant le guidage tour par tour, vide = calculé depuis, mais sans manœuvre.
        val leg = TripLeg(
            fromIndex = 0,
            toIndex = 1,
            profile = "pedestrian",
            distanceMeters = 42.0,
            durationSeconds = 30.0,
            encodedGeometry = "z",
            maneuvers = null,
            optimization = null
        )
        val plan = TripPlan(
            id = "trip-2",
            schemaVersion = 1,
            name = "",
            createdAtMillis = 1L,
            updatedAtMillis = 2L,
            profile = "pedestrian",
            returnToStart = false,
            steps = emptyList(),
            legs = listOf(leg),
            plannedAtMillis = null,
            reminderOffsetsMinutes = emptyList(),
            stopDurationMinutes = 0,
            status = "draft",
            autoNamed = true
        )

        assertEquals(plan, roundTrip(plan, AppBackupCodec::tripToJson, AppBackupCodec::tripFromJson))
    }

    @Test
    fun entryWithoutIdIsSkippedRatherThanGuessed() {
        assertNull(AppBackupCodec.shareEntryFromJson(JSONObject().put("kind", "map")))
        assertNull(AppBackupCodec.notificationEntryFromJson(JSONObject().put("type", "outages")))
        assertNull(AppBackupCodec.uploadEntryFromJson(JSONObject().put("operator", "Free")))
        assertNull(AppBackupCodec.tripFromJson(JSONObject().put("name", "Sans identifiant")))
        // Un signalement se fusionne par sa photo : sans elle, il est aussi inexploitable que sans id.
        assertNull(AppBackupCodec.reportEntryFromJson(JSONObject().put("id", "report-x")))
    }

    @Test
    fun unknownKeysAreIgnoredAndMissingOnesTakeTheirFallback() {
        // Le cas d'une sauvegarde écrite par une version plus récente : elle doit rester lisible.
        val json = JSONObject()
            .put("id", "share-minimal")
            .put("createdAtMillis", 1_755_000_000_000L)
            .put("futureFieldFromANewerVersion", "à ignorer")

        val decoded = AppBackupCodec.shareEntryFromJson(json)

        assertNotNull(decoded)
        assertEquals("share-minimal", decoded!!.id)
        assertEquals("", decoded.kind)
        assertEquals(1, decoded.itemCount)
        assertNull(decoded.latitude)
        assertNull(decoded.darkTheme)
        assertNull(decoded.contents)
    }

    @Test
    fun nonFiniteCoordinatesAreDroppedInsteadOfBreakingTheExport() {
        // JSONObject.put lève sur un NaN : une seule valeur aberrante ne doit pas emporter tout
        // l'export avec elle.
        val entry = ShareHistoryEntry(
            id = "share-nan",
            kind = "map",
            destination = "share",
            createdAtMillis = 1L,
            latitude = Double.NaN,
            longitude = Double.POSITIVE_INFINITY,
            mapZoom = Double.NaN
        )

        val decoded = AppBackupCodec.shareEntryFromJson(AppBackupCodec.shareEntryToJson(entry))

        assertNotNull(decoded)
        assertNull(decoded!!.latitude)
        assertNull(decoded.longitude)
        assertNull(decoded.mapZoom)
    }

    private fun <T> roundTrip(value: T, encode: (T) -> JSONObject, decode: (JSONObject) -> T?): T? {
        // On repasse par le texte, et non par l'objet JSON en mémoire : c'est bien un fichier qui
        // traverse d'un téléphone à l'autre.
        return decode(JSONObject(encode(value).toString()))
    }
}
