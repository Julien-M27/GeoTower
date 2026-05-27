package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class AndroidI18nResourcesTest {
    private val localizedValueDirs = listOf(
        "values-en",
        "values-fr",
        "values-pt",
        "values-it",
        "values-de",
        "values-es"
    )

    @Test
    fun localizedStringsExposeEveryDefaultTranslatableKey() {
        val resDir = findResDir()
        val defaultStrings = parseStrings(resDir.resolve("values/strings.xml"))
        val translatableDefaultKeys = defaultStrings
            .filterValues { it.translatable }
            .keys

        localizedValueDirs.forEach { dirName ->
            val localizedStrings = parseStrings(resDir.resolve("$dirName/strings.xml"))
            assertEquals(
                "String key mismatch in $dirName",
                translatableDefaultKeys,
                localizedStrings.keys
            )
        }
    }

    @Test
    fun localizedStringsKeepTheSamePlaceholdersAsDefault() {
        val resDir = findResDir()
        val defaultStrings = parseStrings(resDir.resolve("values/strings.xml"))
            .filterValues { it.translatable }

        localizedValueDirs.forEach { dirName ->
            val localizedStrings = parseStrings(resDir.resolve("$dirName/strings.xml"))
            defaultStrings.forEach { (key, defaultString) ->
                assertEquals(
                    "Placeholder mismatch for $key in $dirName",
                    defaultString.placeholders,
                    localizedStrings.getValue(key).placeholders
                )
            }
        }
    }

    @Test
    fun localizedPluralsExposeEveryDefaultQuantityKey() {
        val resDir = findResDir()
        val defaultPlurals = parsePlurals(resDir.resolve("values/plurals.xml"))

        localizedValueDirs.forEach { dirName ->
            val localizedPlurals = parsePlurals(resDir.resolve("$dirName/plurals.xml"))
            assertEquals(
                "Plural key mismatch in $dirName",
                defaultPlurals.keys,
                localizedPlurals.keys
            )

            defaultPlurals.forEach { (pluralName, defaultItems) ->
                assertEquals(
                    "Plural quantity mismatch for $pluralName in $dirName",
                    defaultItems.keys,
                    localizedPlurals.getValue(pluralName).keys
                )
            }
        }
    }

    @Test
    fun localizedPluralsKeepTheSamePlaceholdersAsDefault() {
        val resDir = findResDir()
        val defaultPlurals = parsePlurals(resDir.resolve("values/plurals.xml"))

        localizedValueDirs.forEach { dirName ->
            val localizedPlurals = parsePlurals(resDir.resolve("$dirName/plurals.xml"))
            defaultPlurals.forEach { (pluralName, defaultItems) ->
                defaultItems.forEach { (quantity, defaultString) ->
                    assertEquals(
                        "Plural placeholder mismatch for $pluralName/$quantity in $dirName",
                        defaultString.placeholders,
                        localizedPlurals.getValue(pluralName).getValue(quantity).placeholders
                    )
                }
            }
        }
    }

    @Test
    fun localeConfigMatchesLocalizedResourceDirectories() {
        val resDir = findResDir()
        val configuredLocales = parseLocaleConfig(resDir.resolve("xml/locales_config.xml"))
        val resourceLocales = localizedValueDirs
            .map { it.removePrefix("values-") }
            .toSet()

        assertEquals(resourceLocales, configuredLocales)
    }

    @Test
    fun colonTerminatedPrefixLabelsKeepEscapedTrailingSpace() {
        val resDir = findResDir()
        val valueDirs = listOf("values") + localizedValueDirs

        valueDirs.forEach { dirName ->
            val strings = parseStrings(resDir.resolve("$dirName/strings.xml"))
            val colonLabels = strings.filterValues { it.isColonTerminatedPrefixLabel }

            assertTrue("Expected to find colon prefix labels in $dirName", colonLabels.isNotEmpty())
            colonLabels.forEach { (key, string) ->
                assertTrue(
                    "Expected $key in $dirName to end with an escaped space after ':'",
                    string.value.endsWith("""\u0020""")
                )
            }
        }
    }

    @Test
    fun simulatedLabelValueStringsKeepSpaceAfterColon() {
        val resDir = findResDir()
        val valueDirs = listOf("values") + localizedValueDirs

        valueDirs.forEach { dirName ->
            val strings = parseStrings(resDir.resolve("$dirName/strings.xml"))
            strings
                .filterValues { it.isColonTerminatedPrefixLabel }
                .forEach { (key, string) ->
                    val simulatedDisplay = string.androidEscapedValue + "VALUE"
                    assertTrue(
                        "Simulated display for $key in $dirName is missing a space after ':' -> $simulatedDisplay",
                        !simulatedDisplay.contains(":VALUE")
                    )
                }
        }
    }

    private fun findResDir(): Path {
        val candidates = listOf(
            Paths.get("src/main/res"),
            Paths.get("app/src/main/res")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate Android res directory from ${Paths.get("").toAbsolutePath()}")
    }

    private fun parseStrings(path: Path): Map<String, AndroidString> {
        assertTrue("Missing resource file $path", Files.exists(path))
        val root = parseXml(path).documentElement
        val strings = root.getElementsByTagName("string")

        return buildMap {
            for (index in 0 until strings.length) {
                val element = strings.item(index) as Element
                val name = element.getAttribute("name")
                val translatable = element.getAttribute("translatable") != "false"
                put(
                    name,
                    AndroidString(
                        value = element.textContent,
                        translatable = translatable
                    )
                )
            }
        }
    }

    private fun parsePlurals(path: Path): Map<String, Map<String, AndroidString>> {
        assertTrue("Missing resource file $path", Files.exists(path))
        val root = parseXml(path).documentElement
        val plurals = root.getElementsByTagName("plurals")

        return buildMap {
            for (pluralIndex in 0 until plurals.length) {
                val pluralElement = plurals.item(pluralIndex) as Element
                val pluralName = pluralElement.getAttribute("name")
                val items = pluralElement.getElementsByTagName("item")
                put(
                    pluralName,
                    buildMap {
                        for (itemIndex in 0 until items.length) {
                            val itemElement = items.item(itemIndex) as Element
                            put(
                                itemElement.getAttribute("quantity"),
                                AndroidString(
                                    value = itemElement.textContent,
                                    translatable = true
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    private fun parseLocaleConfig(path: Path): Set<String> {
        assertTrue("Missing locale config $path", Files.exists(path))
        val locales = parseXml(path).documentElement.getElementsByTagName("locale")
        return buildSet {
            for (index in 0 until locales.length) {
                val element = locales.item(index) as Element
                add(element.getAttribute("android:name"))
            }
        }
    }

    private fun parseXml(path: Path) = DocumentBuilderFactory
        .newInstance()
        .apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        .newDocumentBuilder()
        .parse(path.toFile())

    private data class AndroidString(
        val value: String,
        val translatable: Boolean
    ) {
        val placeholders: List<String> = placeholderPattern
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()
        val androidEscapedValue: String = value.replace("""\u0020""", " ")
        val isColonTerminatedPrefixLabel: Boolean = androidEscapedValue.trimEnd().endsWith(":")
    }

    private companion object {
        val placeholderPattern = Regex("""(?<!%)%(?!%)(\d+\$)?[a-zA-Z]""")
    }
}
