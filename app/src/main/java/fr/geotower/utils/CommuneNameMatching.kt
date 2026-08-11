package fr.geotower.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Motifs de recherche des noms de communes, tels que stockés dans `ref_commune` : en majuscules
 * **accentuées** (« SAINT-ÉTIENNE », « L'ÎLE-ROUSSE »).
 *
 * SQLite ne sait pas comparer sans accents, et la colonne n'est pas dépliable (base préconstruite,
 * schéma figé — pas de colonne normalisée à ajouter). Plutôt que de déplier la colonne à coups de
 * `REPLACE` imbriqués sur chaque ligne, on déplie la **saisie** : chaque lettre devient la classe de
 * ses variantes accentuées (`E` → `[EÉÈÊË]`) et les séparateurs deviennent interchangeables, si bien
 * que « saint-e », « SAINT E » et « Saint-É » tombent tous sur « SAINT-ÉTIENNE ».
 *
 * C'est un GLOB et non un LIKE : SQLite ne reconnaît les classes de caractères que dans GLOB.
 */
object CommuneNameMatching {

    private val combiningMarksRegex = Regex("\\p{Mn}+")

    /** Lettres françaises accentuables, en majuscules — le reste de l'alphabet passe tel quel. */
    private val letterClasses = mapOf(
        'A' to "[AÀÁÂÄÅ]",
        'C' to "[CÇ]",
        'E' to "[EÉÈÊË]",
        'I' to "[IÎÏÍ]",
        'N' to "[NÑ]",
        'O' to "[OÔÖÒÓ]",
        'U' to "[UÙÛÜÚ]",
        'Y' to "[YŸ]"
    )

    /**
     * Séparateurs interchangeables : espace, tiret, apostrophe droite ou courbe. Sans ça, « l ile
     * rousse » ne trouverait pas « L'ÎLE-ROUSSE » — or personne ne tape les apostrophes au clavier
     * d'un téléphone. Le tiret est en tête de classe : ailleurs, GLOB y lirait une plage.
     */
    private const val SEPARATOR_CLASS = "[- '’]"

    /** Jokers GLOB : SQLite n'a pas d'échappement pour eux, on les retire de la saisie. */
    private const val GLOB_WILDCARDS = "*?[]"

    /** Communes dont le nom **commence** par la saisie. */
    fun startsWithPattern(query: String): String? = pattern(query)?.let { "$it*" }

    /** Communes dont le nom **contient** la saisie : « etienne » doit trouver « SAINT-ÉTIENNE ». */
    fun containsPattern(query: String): String? = pattern(query)?.let { "*$it*" }

    private fun pattern(query: String): String? {
        val folded = Normalizer.normalize(query.trim(), Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")
            .uppercase(Locale.ROOT)
        if (folded.isEmpty()) return null

        val builder = StringBuilder()
        folded.forEach { character ->
            when {
                character in GLOB_WILDCARDS -> Unit
                character == '-' || character == ' ' || character == '\'' || character == '’' ->
                    builder.append(SEPARATOR_CLASS)
                else -> builder.append(letterClasses[character] ?: character.toString())
            }
        }
        return builder.takeIf { it.isNotEmpty() }?.toString()
    }

    /** Particules qui ne prennent pas la majuscule au milieu d'un nom (« Le Puy-en-Velay »). */
    private val lowercaseParticles = setOf(
        "de", "du", "des", "d", "la", "le", "les", "l", "en", "sur", "sous", "au", "aux", "et", "lès"
    )

    /**
     * Nom de commune présentable à partir du stockage tout en majuscules : « SAINT-ÉTIENNE » →
     * « Saint-Étienne », « LE PUY-EN-VELAY » → « Le Puy-en-Velay ».
     *
     * Découpage sur les séparateurs, qui sont réinsérés tels quels : c'est le seul moyen de traiter
     * « SAINT-JEAN-DE-LUZ », où la particule à laisser en minuscules suit un tiret et non un espace.
     */
    fun displayName(storedName: String): String {
        val builder = StringBuilder(storedName.length)
        var wordStart = 0
        var isFirstWord = true

        fun appendWord(endExclusive: Int) {
            if (endExclusive <= wordStart) return
            val word = storedName.substring(wordStart, endExclusive).lowercase(Locale.FRENCH)
            builder.append(
                if (!isFirstWord && word in lowercaseParticles) word
                else word.replaceFirstChar { it.uppercaseChar() }
            )
            isFirstWord = false
        }

        storedName.forEachIndexed { index, character ->
            if (character == ' ' || character == '-' || character == '\'' || character == '’') {
                appendWord(index)
                builder.append(character)
                wordStart = index + 1
            }
        }
        appendWord(storedName.length)
        return builder.toString()
    }
}
