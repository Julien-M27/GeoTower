package fr.geotower.data.models

/**
 * Catalogue des cartes vectorielles hors-ligne embarqué directement dans l'app.
 *
 * Les cartes sont hébergées par mapsforge (tiers) à des URLs stables ; l'app n'a donc plus
 * besoin d'interroger le serveur pour lister les cartes téléchargeables. Résultat : la liste
 * s'affiche instantanément et le serveur n'est plus sollicité pour ce catalogue.
 *
 * Le SHA256 n'est volontairement PAS figé ici : mapsforge régénère périodiquement ses cartes
 * à partir de nouvelles données OSM, ce qui change le hash. On s'appuie donc sur HTTPS +
 * l'hôte autorisé (download.mapsforge.org) + le plafond de taille (voir
 * [fr.geotower.data.workers.OfflineMapDownloadValidator]) plutôt que sur une vérification par hash.
 *
 * Ce catalogue est le miroir client de `base_catalog` dans docs/server/main.py. Les tailles
 * correspondent aux fichiers mapsforge actuels ; en cas d'ajout/retrait de région, synchroniser
 * les deux sources. Le serveur continue par ailleurs d'exposer les cartes (rétrocompat des
 * anciennes versions de l'app), mais cette version ne les lit plus.
 */
object OfflineMapCatalog {

    private const val MAPSFORGE_FRANCE_BASE =
        "https://download.mapsforge.org/maps/v5/europe/france"

    /**
     * Construit une entrée : l'URL et le nom de fichier se déduisent de [id]
     * (mapsforge nomme chaque carte `<id>.map`). [sha256] reste nul (voir doc de l'objet).
     */
    private fun entry(
        id: String,
        name: String,
        description: String,
        estimatedSizeMb: Int
    ): OfflineMapDto = OfflineMapDto(
        id = id,
        name = name,
        description = description,
        mapUrl = "$MAPSFORGE_FRANCE_BASE/$id.map",
        estimatedSizeMb = estimatedSizeMb,
        mapFilename = "$id.map",
        sha256 = null
    )

    val entries: List<OfflineMapDto> = listOf(
        entry("alsace", "Alsace", "Region Grand Est", 85),
        entry("aquitaine", "Aquitaine", "Region Nouvelle-Aquitaine", 209),
        entry("auvergne", "Auvergne", "Region Auvergne-Rhone-Alpes", 109),
        entry("basse-normandie", "Basse-Normandie", "Region Normandie", 102),
        entry("bourgogne", "Bourgogne", "Region Bourgogne-Franche-Comte", 150),
        entry("bretagne", "Bretagne", "Region Bretagne", 206),
        entry("centre", "Centre", "Region Centre-Val de Loire", 172),
        entry("champagne-ardenne", "Champagne-Ardenne", "Region Grand Est", 79),
        entry("corse", "Corse", "Ile de Beaute", 25),
        entry("franche-comte", "Franche-Comte", "Region Bourgogne-Franche-Comte", 87),
        entry("haute-normandie", "Haute-Normandie", "Region Normandie", 77),
        entry("ile-de-france", "Ile-de-France", "Paris et region parisienne", 197),
        entry("languedoc-roussillon", "Languedoc-Roussillon", "Region Occitanie", 175),
        entry("limousin", "Limousin", "Region Nouvelle-Aquitaine", 72),
        entry("lorraine", "Lorraine", "Region Grand Est", 121),
        entry("midi-pyrenees", "Midi-Pyrenees", "Region Occitanie", 244),
        entry("nord-pas-de-calais", "Nord-Pas-de-Calais", "Region Hauts-de-France", 157),
        entry("pays-de-la-loire", "Pays de la Loire", "Region Pays de la Loire", 235),
        entry("picardie", "Picardie", "Region Hauts-de-France", 104),
        entry("poitou-charentes", "Poitou-Charentes", "Region Nouvelle-Aquitaine", 146),
        entry("provence-alpes-cote-d-azur", "Provence-Alpes-Cote d'Azur", "Region Sud", 229),
        entry("rhone-alpes", "Rhone-Alpes", "Region Auvergne-Rhone-Alpes", 335),
        entry("guadeloupe", "Guadeloupe", "Antilles Francaises", 17),
        entry("martinique", "Martinique", "Antilles Francaises", 13),
        entry("guyane", "Guyane", "Amerique du Sud", 16),
        entry("mayotte", "Mayotte", "Ocean Indien", 7),
        entry("reunion", "La Reunion", "Ocean Indien", 21),
    )
}
