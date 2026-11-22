package fr.geotower.utils

fun radioBandCode(gen: Int, value: Int): String? {
    return when (gen) {
        5 -> when (value) {
            600 -> "N71"
            700 -> "N28"
            800 -> "N20"
            850 -> "N5"
            900 -> "N8"
            1400 -> "N75"
            1800 -> "N3"
            1900 -> "N2/N25"
            2000 -> "N70"
            2100 -> "N1"
            2600 -> "N7"
            3500 -> "N78"
            3800 -> "N77"
            4200 -> "N77"
            26000 -> "N258"
            else -> null
        }
        4 -> when (value) {
            600 -> "B71"
            700 -> "B28"
            800 -> "B20"
            850 -> "B5"
            900 -> "B8"
            1800 -> "B3"
            1900 -> "B2/B25"
            2100 -> "B1"
            2300 -> "B30/B40"
            2600 -> "B7"
            3500 -> "B42"
            else -> null
        }
        3 -> when (value) {
            850 -> "B5"
            900 -> "B8"
            1900 -> "B2"
            2100 -> "B1"
            else -> null
        }
        2 -> when (value) {
            850 -> "GSM 850"
            900 -> "GSM 900"
            1800 -> "DCS 1800"
            1900 -> "PCS 1900"
            else -> null
        }
        else -> null
    }
}

fun radioFrequencyLabel(value: Int): String {
    return when (value) {
        1400 -> "1400 MHz (exp)"
        4200 -> "4200 MHz (exp)"
        26000 -> "26 GHz (exp)"
        else -> "$value MHz"
    }
}

fun radioTechnologyFrequencyLabel(gen: Int, value: Int): String {
    return if (gen in 2..5 && value > 0) {
        "${gen}G ${radioFrequencyLabel(value)}"
    } else {
        "${gen}G"
    }
}
