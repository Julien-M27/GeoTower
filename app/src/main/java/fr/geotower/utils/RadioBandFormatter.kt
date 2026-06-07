package fr.geotower.utils

fun radioBandCode(gen: Int, value: Int): String? {
    return when (gen) {
        5 -> when (value) {
            700 -> "N28"
            800 -> "N20"
            900 -> "N8"
            1400 -> "N75"
            1800 -> "N3"
            2100 -> "N1"
            2600 -> "N7"
            3500 -> "N78"
            4200 -> "N77"
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
