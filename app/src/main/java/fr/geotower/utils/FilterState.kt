package fr.geotower.utils

import androidx.compose.runtime.mutableStateListOf

object FilterState {
    // Par défaut, tout est coché
    val activeOperators = mutableStateListOf(
        Constants.OP_ORANGE,
        Constants.OP_SFR,
        Constants.OP_BOUYGUES,
        Constants.OP_FREE
    )

    val activeTechnos = mutableStateListOf("2G", "3G", "4G", "5G")

    // Fonctions d'aide pour cocher/décocher proprement
    fun toggleOperator(opName: String) {
        if (activeOperators.contains(opName)) {
            activeOperators.remove(opName)
        } else {
            activeOperators.add(opName)
        }
    }

    fun toggleTechno(tech: String) {
        if (activeTechnos.contains(tech)) {
            activeTechnos.remove(tech)
        } else {
            activeTechnos.add(tech)
        }
    }

    // Vérifie si un site doit être affiché selon les filtres actuels
    fun isSiteVisible(operatorName: String, technos: List<String>): Boolean {
        // 1. Est-ce que l'opérateur est coché ?
        if (!activeOperators.contains(operatorName)) return false

        // 2. Est-ce qu'au moins une des technos du site est cochée ?
        // (Si le site a 4G et 5G, et qu'on affiche la 4G, on le montre)
        return technos.any { activeTechnos.contains(it) }
    }
}