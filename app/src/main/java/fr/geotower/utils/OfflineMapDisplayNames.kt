package fr.geotower.utils

import java.util.Locale

object OfflineMapDisplayNames {
    fun formatMapName(rawName: String): String {
        val cleanName = rawName.replace(".map", "", ignoreCase = true)
        return when (cleanName.lowercase(Locale.ROOT)) {
            "alsace" -> "Alsace"
            "aquitaine" -> "Aquitaine"
            "auvergne" -> "Auvergne"
            "basse-normandie" -> "Basse-Normandie"
            "bourgogne" -> "Bourgogne"
            "bretagne" -> "Bretagne"
            "centre" -> "Centre-Val de Loire"
            "champagne-ardenne" -> "Champagne-Ardenne"
            "corse" -> "Corse"
            "franche-comte" -> "Franche-Comté"
            "guadeloupe" -> "Guadeloupe"
            "guyane" -> "Guyane"
            "haute-normandie" -> "Haute-Normandie"
            "ile-de-france" -> "Île-de-France"
            "languedoc-roussillon" -> "Languedoc-Roussillon"
            "limousin" -> "Limousin"
            "lorraine" -> "Lorraine"
            "martinique" -> "Martinique"
            "mayotte" -> "Mayotte"
            "midi-pyrenees" -> "Midi-Pyrénées"
            "nord-pas-de-calais" -> "Nord-Pas-de-Calais"
            "pays-de-la-loire" -> "Pays de la Loire"
            "picardie" -> "Picardie"
            "poitou-charentes" -> "Poitou-Charentes"
            "provence-alpes-cote-d-azur" -> "Provence-Alpes-Côte d'Azur"
            "reunion" -> "La Réunion"
            "rhone-alpes" -> "Rhône-Alpes"
            else -> cleanName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }
}
