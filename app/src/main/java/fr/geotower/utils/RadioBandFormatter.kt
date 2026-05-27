package fr.geotower.utils

fun radioBandCode(gen: Int, value: Int): String? {
    return when (gen) {
        5 -> when (value) {
            700 -> "N28"
            800 -> "N20"
            900 -> "N8"
            1800 -> "N3"
            2100 -> "N1"
            2600 -> "N7"
            3500 -> "N78"
            26000 -> "N258"
            else -> null
        }
        4 -> when (value) {
            700 -> "B28"
            800 -> "B20"
            900 -> "B8"
            1800 -> "B3"
            2100 -> "B1"
            2600 -> "B7"
            3500 -> "B42"
            else -> null
        }
        3 -> when (value) {
            900 -> "B8"
            2100 -> "B1"
            else -> null
        }
        2 -> when (value) {
            900 -> "GSM 900"
            1800 -> "DCS 1800"
            else -> null
        }
        else -> null
    }
}
