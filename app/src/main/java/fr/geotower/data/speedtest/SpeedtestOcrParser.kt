package fr.geotower.data.speedtest

import java.util.Locale

data class ParsedSpeedtestData(
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val operatorName: String? = null,
    val networkName: String? = null,
    val networkTech: String? = null,
    val band: String? = null,
    val cellIdentifier: String? = null
)

/**
 * Analyseur heuristique de texte OCR issu de captures d'écran de speedtests
 * (nPerf, Speedtest by Ookla, Cellular FR, Fast.com, Débit Test, etc.).
 *
 * Gère à la fois :
 * - les valeurs avec unité collée (`482,5 Mb/s`, `21 ms`) ;
 * - les séparateurs de milliers par espace (`1 015`) ;
 * - les mises en page multi-colonnes (Ookla, Cellular FR) où l'unité est dans l'en-tête
 *   (`Descendant Mbps`, `Ascendant Mbps`, `Ping ms ... Gigue ms ...`).
 */
object SpeedtestOcrParser {

    private val downloadKeywords = listOf(
        "téléchargement", "telechargement",
        "réception", "reception", "download", "descendant", "débit descendant",
        "debit descendant", "dl", "empfang", "herunterladen", "descarga", "ricezione"
    )
    private val uploadKeywords = listOf(
        "envoi", "upload", "montant", "ascendant", "débit montant", "debit montant",
        "ul", "senden", "hochladen", "subida", "caricamento", "invio"
    )
    private val pingKeywords = listOf(
        "ping", "latence", "latency", "latenz", "latencia", "latenza"
    )
    private val jitterKeywords = listOf(
        "gigue", "jitter", "jittee", "gig"
    )

    private val knownOperators = listOf(
        "Orange" to listOf("orange", "orange f", "orange caraibe", "orange caraïbe"),
        "SFR" to listOf("sfr", "f-sfr", "sfr caraibe", "sfr caraïbe", "srr", "outremer telecom"),
        "Bouygues Telecom" to listOf("bouygues", "bouygues telecom", "bouygues teleco", "b&you", "byou"),
        "Free" to listOf("free", "free mobile", "freemobile", "free caraibe", "free caraïbe", "telco oi"),
        "Sosh" to listOf("sosh"),
        "RED by SFR" to listOf("red by sfr", "red sfr"),
        "Lebara" to listOf("lebara"),
        "La Poste Mobile" to listOf("la poste mobile", "la poste"),
        "NRJ Mobile" to listOf("nrj mobile"),
        "Auchan Telecom" to listOf("auchan telecom"),
        "Cdiscount Mobile" to listOf("cdiscount mobile"),
        "Prixtel" to listOf("prixtel"),
        "Syma" to listOf("syma"),
        "Lycamobile" to listOf("lycamobile"),
        "YouPrice" to listOf("youprice"),
        "Coriolis" to listOf("coriolis"),
        "Rogers" to listOf("rogers"),
        "Bell" to listOf("bell"),
        "Telus" to listOf("telus"),
        "Vidéotron" to listOf("videotron", "vidéotron"),
        "Freedom Mobile" to listOf("freedom mobile", "freedom"),
        "Swisscom" to listOf("swisscom"),
        "Salt" to listOf("salt"),
        "Sunrise" to listOf("sunrise"),
        "Proximus" to listOf("proximus"),
        "Telenet" to listOf("telenet", "base"),
        "Vodafone" to listOf("vodafone"),
        "Telekom" to listOf("telekom", "t-mobile", "deutsche telekom"),
        "O2" to listOf("o2"),
        "EE" to listOf("ee mobile"),
        "Three" to listOf("three", "3 uk"),
        "AT&T" to listOf("at&t", "att"),
        "Verizon" to listOf("verizon"),
        "Movistar" to listOf("movistar"),
        "Yoigo" to listOf("yoigo"),
        "TIM" to listOf("tim"),
        "WindTre" to listOf("windtre", "wind tre"),
        "Iliad" to listOf("iliad"),
        "MEO" to listOf("meo"),
        "NOS" to listOf("nos telecom"),
        "Telstra" to listOf("telstra"),
        "Optus" to listOf("optus"),
        "NTT Docomo" to listOf("docomo", "ntt docomo"),
        "SoftBank" to listOf("softbank"),
        "KDDI" to listOf("kddi", "au by kddi"),
        "Rakuten Mobile" to listOf("rakuten"),
        "Jio" to listOf("jio", "reliance jio"),
        "Airtel" to listOf("airtel"),
        "Vivo" to listOf("vivo"),
        "Claro" to listOf("claro")
    )

    // Table PLMN (MCC+MNC normalisés sans tiret ni espace)
    private val operatorByPlmn = mapOf(
        // France métropolitaine (208)
        "20801" to "Orange",
        "20802" to "Orange",
        "20809" to "SFR",
        "20810" to "SFR",
        "20811" to "SFR",
        "20813" to "SFR",
        "20815" to "Free",
        "20816" to "Free",
        "20835" to "Free",
        "20820" to "Bouygues Telecom",
        "20821" to "Bouygues Telecom",
        "20888" to "Bouygues Telecom",
        // DOM-TOM
        "34001" to "Orange",
        "34002" to "SFR",
        "34003" to "Dauphin Telecom",
        "34004" to "Free",
        "34008" to "SFR",
        "34020" to "Digicel",
        "64700" to "Orange",
        "64702" to "Free",
        "64710" to "SFR",
        "74201" to "Orange",
        "30801" to "SPM Telecom",
        // Canada (302)
        "302720" to "Rogers",
        "302610" to "Bell",
        "302220" to "Telus",
        "302500" to "Vidéotron",
        "302490" to "Freedom Mobile",
        "302780" to "SaskTel",
        "302270" to "Eastlink",
        "302370" to "Tbaytel",
        "302620" to "Ice Wireless",
        "302940" to "Sogetel",
        "302770" to "Xplore",
        // Suisse (228)
        "22801" to "Swisscom",
        "22802" to "Sunrise",
        "22803" to "Salt",
        // Belgique (206)
        "20601" to "Proximus",
        "20605" to "Telenet",
        "20610" to "Orange",
        "20620" to "BASE",
        // Allemagne (262)
        "26201" to "Telekom",
        "26202" to "Vodafone",
        "26203" to "O2",
        "26207" to "O2",
        "26223" to "1&1",
        // Espagne (214)
        "21401" to "Vodafone",
        "21403" to "Orange",
        "21404" to "Yoigo",
        "21407" to "Movistar",
        // Italie (222)
        "22201" to "TIM",
        "22210" to "Vodafone",
        "22250" to "Iliad",
        "22288" to "WindTre",
        // Portugal (268)
        "26801" to "Vodafone",
        "26803" to "NOS",
        "26806" to "MEO",
        // Royaume-Uni (234)
        "23410" to "O2",
        "23415" to "Vodafone",
        "23420" to "Three",
        "23430" to "EE",
        // États-Unis (310/311/312)
        "310260" to "T-Mobile",
        "310410" to "AT&T",
        "311480" to "Verizon",
        // Japon (440)
        "44010" to "NTT Docomo",
        "44020" to "SoftBank",
        "44050" to "KDDI",
        "44011" to "Rakuten Mobile",
        // Australie (505)
        "50501" to "Telstra",
        "50502" to "Optus",
        "50503" to "Vodafone"
    )

    private const val NUM_PATTERN = """(\d{1,3}(?:[\s\u00A0,.]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?)"""

    private val speedValueRegex = Regex(
        """$NUM_PATTERN\s*(mb/s|mbps|mbit/s|kb/s|kbps|kbit/s|gb/s|gbps)""",
        RegexOption.IGNORE_CASE
    )
    private val msValueRegex = Regex(
        """$NUM_PATTERN\s*ms\b""",
        RegexOption.IGNORE_CASE
    )
    private val standaloneNumberRegex = Regex("""^\s*$NUM_PATTERN\s*$""")
    private val inlineNumberRegex = Regex(NUM_PATTERN)
    private val simpleInlineNumberRegex = Regex("""(\d+(?:[.,]\d+)?)""")
    private val unitOnlyLineRegex = Regex(
        """^\s*(mb/s|mbps|mbit/s|kb/s|kbps|kbit/s|gb/s|gbps|ms|%)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val bandRegex = Regex(
        """\b((?:[Bn]\d{1,3}|Bl)(?:\s*\+\s*(?:[Bn]\d{1,3}|Bl))*)\b"""
    )
    private val cellIdRegex = Regex(
        """\b(?:eNB|gNB|eNodeB|gNodeB|Cell\s*ID|Cell|CID|PCI)\s*[:=#]?\s*(\d{2,10}(?::\d{1,3})?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val plmnRegex = Regex("""\b(\d{3})[\s:-]?(\d{2,3})\b""")
    private val ooklaRatePromptRegex = Regex(
        """(?:quelle note donneriez-vous|how would you rate|¿cómo calificarías a|come valuti|como avalia|wie würden sie .* bewerten)\s+([A-Za-zÀ-ÿ0-9&\s-]{2,24})[?？]?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): ParsedSpeedtestData {
        if (rawText.isBlank()) return ParsedSpeedtestData()

        val normalizedRaw = rawText
            .replace(Regex("""([A-Za-zÀ-ÿ])(5G|4G|3G)\b""", RegexOption.IGNORE_CASE), "$1 $2")
            .replace(Regex("""\b(\d{1,3})\s+(\d{2})\s*ms\b""", RegexOption.IGNORE_CASE), "$1.$2 ms")

        val lines = normalizedRaw
            .lines()
            .map {
                it.trim()
                    .replace(Regex("""(\d)\s*([.,])\s*(\d)"""), "$1$2$3")
                    .replace(Regex("""(\d)[.,]+(?=\s|$)"""), "$1")
            }
            .filter { it.isNotEmpty() }

        var downloadMbps: Double? = null
        var downloadFromChartMax = false
        var uploadMbps: Double? = null
        var uploadFromChartMax = false
        var pingMs: Double? = null
        var jitterMs: Double? = null
        var jitterFromLoaded = false

        val speedCandidates = mutableListOf<Double>()
        val speedCandidateLines = mutableListOf<String>()

        // Pré-passe : si une ligne est un nombre seul suivi d'une ligne contenant uniquement une unité
        // (ex: "86,7" puis "Mbps"), on les fusionne virtuellement pour la passe 1.
        val mergedLines = buildList {
            var idx = 0
            while (idx < lines.size) {
                val cur = lines[idx]
                if (idx + 1 < lines.size &&
                    standaloneNumberRegex.matches(cur) &&
                    unitOnlyLineRegex.matches(lines[idx + 1])
                ) {
                    add("$cur ${lines[idx + 1]}")
                    idx += 2
                } else {
                    add(cur)
                    idx += 1
                }
            }
        }

        // Passe 1 : valeurs avec unité explicite (ex: "482,5 Mb/s", "21 ms")
        for (i in mergedLines.indices) {
            val line = mergedLines[i]
            val lower = line.lowercase(Locale.ROOT)
            if (isServerInfoLine(lower)) continue

            val prevLower = if (i > 0) mergedLines[i - 1].lowercase(Locale.ROOT) else ""
            val prev2Lower = if (i > 1) mergedLines[i - 2].lowercase(Locale.ROOT) else ""

            val speedMatches = speedValueRegex.findAll(line).toList()
            for (match in speedMatches) {
                val mbps = convertToMbps(
                    rawNumber = match.groupValues[1],
                    unit = match.groupValues[2]
                ) ?: continue
                speedCandidates.add(mbps)
                speedCandidateLines.add(lower)

                val isDl = containsAny(lower, downloadKeywords) ||
                    containsAny(prevLower, downloadKeywords) ||
                    (isAvgOrMaxLine(prevLower) && containsAny(prev2Lower, downloadKeywords))
                val isUl = containsAny(lower, uploadKeywords) ||
                    containsAny(prevLower, uploadKeywords) ||
                    (isAvgOrMaxLine(prevLower) && containsAny(prev2Lower, uploadKeywords))

                val isChartMax = Regex("""\bmax\s+\d""").containsMatchIn(lower)
                when {
                    isDl && (downloadMbps == null || (downloadFromChartMax && !isChartMax)) -> {
                        downloadMbps = mbps
                        downloadFromChartMax = isChartMax
                    }
                    isUl && (uploadMbps == null || (uploadFromChartMax && !isChartMax)) -> {
                        uploadMbps = mbps
                        uploadFromChartMax = isChartMax
                    }
                }
            }

            // Plusieurs valeurs ms possibles sur la même ligne (ex: "Moy: 25 ms / Gig: 7 ms")
            for (msMatch in msValueRegex.findAll(line)) {
                val ms = parseNumber(msMatch.groupValues[1]) ?: continue
                val beforeMatch = lower.substring(0, msMatch.range.first)
                val segStart = maxOf(beforeMatch.lastIndexOf('/'), beforeMatch.lastIndexOf('|'), 0)
                val localLabel = beforeMatch.substring(segStart)

                val isLoadedOnCurrentLine = lower.contains("charg") || lower.contains("loaded")
                val isLoadedOnPrevLine = prevLower.contains("charg") || prevLower.contains("loaded")
                when {
                    containsAny(localLabel, jitterKeywords) -> {
                        if (jitterMs == null ||
                            (jitterFromLoaded && !isLoadedOnCurrentLine) ||
                            localLabel.contains("repos") || localLabel.contains("idle") ||
                            prevLower.contains("repos") || prevLower.contains("idle")
                        ) {
                            jitterMs = ms
                            jitterFromLoaded = isLoadedOnCurrentLine
                        }
                    }
                    containsAny(localLabel, pingKeywords) -> {
                        if (pingMs == null) pingMs = ms
                    }
                    containsAny(prevLower, jitterKeywords) ||
                        (isAvgOrMaxLine(localLabel) && containsAny(prevLower, jitterKeywords)) -> {
                        val isLoaded = isLoadedOnCurrentLine || isLoadedOnPrevLine
                        if (jitterMs == null ||
                            (jitterFromLoaded && !isLoaded) ||
                            prevLower.contains("repos") || prevLower.contains("idle")
                        ) {
                            jitterMs = ms
                            jitterFromLoaded = isLoaded
                        }
                    }
                    containsAny(prevLower, pingKeywords) ||
                        (isAvgOrMaxLine(localLabel) && containsAny(prevLower, pingKeywords)) ||
                        (isAvgOrMaxLine(prevLower) && containsAny(prev2Lower, pingKeywords)) -> {
                        if (pingMs == null) pingMs = ms
                    }
                }
            }
        }

        // Passe 2 : valeurs sans unité collée (Ookla Speedtest, Cellular FR)
        if (downloadMbps == null || uploadMbps == null) {
            val dlIndex = lines.indexOfFirst { containsAny(it.lowercase(Locale.ROOT), downloadKeywords) }
            val ulIndex = lines.indexOfFirst { containsAny(it.lowercase(Locale.ROOT), uploadKeywords) }

            if (dlIndex >= 0 && ulIndex >= 0 && (ulIndex == dlIndex || ulIndex == dlIndex + 1)) {
                val followingNumbers = findNumbersAcrossLinesAfter(lines, ulIndex, maxScan = 5, limit = 2)
                if (downloadMbps == null && followingNumbers.isNotEmpty()) {
                    downloadMbps = followingNumbers[0]
                }
                if (uploadMbps == null && followingNumbers.size >= 2) {
                    uploadMbps = followingNumbers[1]
                }
            } else {
                if (downloadMbps == null && dlIndex >= 0) {
                    downloadMbps = findSpeedAfterKeyword(lines, dlIndex, downloadKeywords)
                }
                if (uploadMbps == null && ulIndex >= 0) {
                    uploadMbps = findSpeedAfterKeyword(lines, ulIndex, uploadKeywords)
                }
            }
        }

        if (pingMs == null) {
            val pingIndex = lines.indexOfFirst { containsAny(it.lowercase(Locale.ROOT), pingKeywords) }
            if (pingIndex >= 0) {
                val pingLineTail = lines[pingIndex].let { line ->
                    val lower = line.lowercase(Locale.ROOT)
                    val kwEnd = pingKeywords.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 }?.plus(kw.length) }.minOrNull() ?: 0
                    line.substring(kwEnd)
                }
                val nextJitterIdx = ((pingIndex + 1)..lines.lastIndex)
                    .firstOrNull { containsAny(lines[it].lowercase(Locale.ROOT), jitterKeywords) }
                    ?: (lines.lastIndex + 1)
                val msScanEnd = minOf(lines.lastIndex, pingIndex + 6, nextJitterIdx - 1)
                pingMs = msValueRegex.find(pingLineTail)?.let { parseNumber(it.groupValues[1]) }
                    ?: (if (pingIndex > 0) {
                        msValueRegex.find(lines[pingIndex - 1])?.let { parseNumber(it.groupValues[1]) }
                    } else null)
                    ?: (if (pingIndex + 1 <= msScanEnd) {
                        (pingIndex + 1..msScanEnd)
                            .firstNotNullOfOrNull { idx -> msValueRegex.find(lines[idx])?.let { parseNumber(it.groupValues[1]) } }
                    } else null)
                    ?: findNumberAfterKeyword(lines, pingIndex, pingKeywords, maxValue = 5000.0, allowThousandsSpace = false)
            }
        }
        if (jitterMs == null) {
            val jitterIndex = lines.indexOfFirst { containsAny(it.lowercase(Locale.ROOT), jitterKeywords) }
            if (jitterIndex >= 0) {
                val jitterLineTail = lines[jitterIndex].let { line ->
                    val lower = line.lowercase(Locale.ROOT)
                    val kwEnd = jitterKeywords.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 }?.plus(kw.length) }.minOrNull() ?: 0
                    line.substring(kwEnd)
                }
                jitterMs = msValueRegex.find(jitterLineTail)?.let { parseNumber(it.groupValues[1]) }
                    ?: (jitterIndex + 1..minOf(lines.lastIndex, jitterIndex + 6))
                        .firstNotNullOfOrNull { idx -> msValueRegex.find(lines[idx])?.let { parseNumber(it.groupValues[1]) } }
                    ?: findNumberAfterKeyword(lines, jitterIndex, jitterKeywords, maxValue = 5000.0, allowThousandsSpace = false)
            }
        }

        // Fallback générique pour nPerf / captures où Moy: et Max: apparaissent dans l'ordre DL puis UL
        if (downloadMbps == null && speedCandidates.isNotEmpty()) {
            downloadMbps = speedCandidates.first()
        }
        if (uploadMbps == null && speedCandidates.size >= 2) {
            val isColumnGrouped = speedCandidateLines.size >= 4 &&
                speedCandidateLines[0].contains("max") &&
                speedCandidateLines[1].contains("moy")
            val candidateIndex = if (isColumnGrouped) 2 else 1
            val candidate = speedCandidates[candidateIndex]
            if (candidate != downloadMbps || speedCandidates.count { it == candidate } >= 2) {
                uploadMbps = candidate
            }
        }

        val fullLower = normalizedRaw.lowercase(Locale.ROOT)
        val hasIdleJitterLabel = fullLower.contains("gigue au repos") || fullLower.contains("idle jitter")
        if (hasIdleJitterLabel) {
            // Sur SignalQuest, ML Kit lit souvent "Gigue au repos" dans la colonne gauche puis "65 ms"
            // tout seul après les deux lignes "XXX ms gigue YYY ms" de la colonne droite.
            val standaloneAfterLoadedJitter = mergedLines.indices.firstNotNullOfOrNull { idx ->
                val cur = mergedLines[idx]
                val prev = if (idx > 0) mergedLines[idx - 1].lowercase(Locale.ROOT) else ""
                val isStandaloneMs = Regex("""^\s*$NUM_PATTERN\s*ms\s*$""", RegexOption.IGNORE_CASE).matches(cur)
                if (isStandaloneMs && containsAny(prev, jitterKeywords)) {
                    msValueRegex.find(cur)?.let { parseNumber(it.groupValues[1]) }
                } else {
                    null
                }
            }
            if (standaloneAfterLoadedJitter != null) {
                jitterMs = standaloneAfterLoadedJitter
            }
        }

        val operatorName = detectOperator(normalizedRaw, fullLower)
        val networkName = detectNetworkName(normalizedRaw)
        val networkTech = detectNetworkTech(normalizedRaw, fullLower)
        val band = bandRegex.find(normalizedRaw)?.groupValues?.getOrNull(1)
            ?.replace(" ", "")
            ?.replace("Bl", "B1")
        val cellIdentifier = cellIdRegex.find(normalizedRaw)?.groupValues?.getOrNull(1)

        return ParsedSpeedtestData(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            pingMs = pingMs,
            jitterMs = jitterMs,
            operatorName = operatorName,
            networkName = networkName,
            networkTech = networkTech,
            band = band,
            cellIdentifier = cellIdentifier
        )
    }

    private fun findSpeedAfterKeyword(
        lines: List<String>,
        labelIndex: Int,
        keywords: List<String>
    ): Double? {
        // Ne pas confondre "DL chargée 153 ms" avec un débit en Mbps
        val labelLineLower = lines[labelIndex].lowercase(Locale.ROOT)
        if (msValueRegex.containsMatchIn(labelLineLower) && !speedValueRegex.containsMatchIn(labelLineLower)) {
            return null
        }
        val value = findNumberAfterKeyword(lines, labelIndex, keywords) ?: return null
        val end = minOf(lines.lastIndex, labelIndex + 5)
        for (i in (labelIndex + 1)..end) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (i > labelIndex + 1 && (containsAny(lower, downloadKeywords) || containsAny(lower, uploadKeywords))) {
                break
            }
            when {
                lower.contains("gb/s") || lower.contains("gbps") -> return value * 1000.0
                lower.contains("kb/s") || lower.contains("kbps") || lower.contains("kbit/s") -> return value / 1000.0
                lower.contains("mb/s") || lower.contains("mbps") || lower.contains("mbit/s") -> return value
            }
        }
        return value
    }

    private fun findNumberAfterKeyword(
        lines: List<String>,
        labelIndex: Int,
        keywords: List<String>,
        maxValue: Double = 100_000.0,
        allowThousandsSpace: Boolean = true
    ): Double? {
        val line = lines[labelIndex]
        val lower = line.lowercase(Locale.ROOT)
        val keywordEnd = keywords
            .mapNotNull { kw ->
                val idx = lower.indexOf(kw)
                if (idx >= 0) idx + kw.length else null
            }
            .minOrNull() ?: 0

        val tail = line.substring(keywordEnd)
        val regex = if (allowThousandsSpace) inlineNumberRegex else simpleInlineNumberRegex
        val sameLineMatch = regex.find(tail)
        if (sameLineMatch != null) {
            val value = parseNumber(sameLineMatch.groupValues[1])
            if (value != null && value <= maxValue) return value
        }
        return findStandaloneNumbersAfter(lines, labelIndex, maxScan = 3, limit = 1, maxValue = maxValue).firstOrNull()
    }

    private fun findNumbersAcrossLinesAfter(
        lines: List<String>,
        startIndex: Int,
        maxScan: Int,
        limit: Int,
        maxValue: Double = 100_000.0
    ): List<Double> {
        val found = mutableListOf<Double>()
        val end = minOf(lines.size - 1, startIndex + maxScan)
        for (i in (startIndex + 1)..end) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)
            if (containsAny(lower, downloadKeywords) ||
                containsAny(lower, uploadKeywords) ||
                containsAny(lower, pingKeywords) ||
                containsAny(lower, jitterKeywords)
            ) {
                break
            }
            if (unitOnlyLineRegex.matches(line)) continue
            val standalone = standaloneNumberRegex.matchEntire(line)
            if (standalone != null) {
                val value = parseNumber(standalone.groupValues[1])
                if (value != null && value <= maxValue) {
                    found.add(value)
                    if (found.size >= limit) break
                    continue
                }
            }
            // Cas où 2 nombres sont sur la même ligne (ex: "225 4,17" ou "86,7   1,35")
            val lineNumbers = simpleInlineNumberRegex.findAll(line)
                .mapNotNull { parseNumber(it.groupValues[1]) }
                .filter { it <= maxValue }
                .toList()
            if (lineNumbers.isNotEmpty()) {
                for (num in lineNumbers) {
                    found.add(num)
                    if (found.size >= limit) break
                }
                if (found.size >= limit) break
            } else if (found.isNotEmpty()) {
                break
            }
        }
        return found
    }

    private fun findStandaloneNumbersAfter(
        lines: List<String>,
        startIndex: Int,
        maxScan: Int,
        limit: Int,
        maxValue: Double = 100_000.0
    ): List<Double> {
        val found = mutableListOf<Double>()
        val end = minOf(lines.size - 1, startIndex + maxScan)
        for (i in (startIndex + 1)..end) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)
            if (containsAny(lower, downloadKeywords) ||
                containsAny(lower, uploadKeywords) ||
                containsAny(lower, pingKeywords) ||
                containsAny(lower, jitterKeywords)
            ) {
                break
            }
            if (unitOnlyLineRegex.matches(line)) continue
            val standalone = standaloneNumberRegex.matchEntire(line)
            if (standalone != null) {
                val value = parseNumber(standalone.groupValues[1])
                if (value != null && value <= maxValue) {
                    found.add(value)
                    if (found.size >= limit) break
                }
            } else if (found.isNotEmpty()) {
                break
            }
        }
        return found
    }

    private fun detectOperator(rawText: String, fullLower: String): String? {
        // 1. Phrase explicite d'Ookla : "Quelle note donneriez-vous Orange?" / "How would you rate Verizon?"
        ooklaRatePromptRegex.find(rawText)?.groupValues?.getOrNull(1)?.let { candidate ->
            val trimmed = candidate.trim()
            val candLower = trimmed.lowercase(Locale.ROOT)
            knownOperators.firstOrNull { (_, aliases) ->
                aliases.any { candLower.contains(it) }
            }?.let { return it.first }
            if (trimmed.isNotEmpty()) return trimmed
        }

        // 2. Sur nPerf, l'opérateur est sous "Opérateur" (prioritaire sur Réseau / PLMN)
        detectNperfOperatorUnderOperateur(rawText)?.let { return it }

        // 3. Premier opérateur apparaissant hors lignes serveur (et ligne suivant "Serveur" / logo)
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nonServerLines = buildList {
            var skipNext = false
            for (line in rawLines) {
                val lower = line.lowercase(Locale.ROOT)
                if (skipNext) {
                    skipNext = false
                    continue
                }
                if (lower == "serveur" || lower == "server") {
                    skipNext = true
                    continue
                }
                if (!isServerInfoLine(lower)) {
                    add(line)
                }
            }
        }
        val nonServerLower = nonServerLines.joinToString("\n").lowercase(Locale.ROOT)

        var bestMatchIndex = Int.MAX_VALUE
        var bestOperator: String? = null
        for ((displayName, aliases) in knownOperators) {
            for (alias in aliases) {
                val pattern = Regex("""\b${Regex.escape(alias)}\b""")
                val match = pattern.find(nonServerLower)
                if (match != null && match.range.first < bestMatchIndex) {
                    bestMatchIndex = match.range.first
                    bestOperator = displayName
                }
            }
        }
        if (bestOperator != null) return bestOperator

        // 4. Fallback par code PLMN (MCC+MNC)
        for (match in plmnRegex.findAll(rawText)) {
            val normalized = "${match.groupValues[1]}${match.groupValues[2]}"
            operatorByPlmn[normalized]?.let { return it }
        }

        return null
    }

    private fun detectNetworkName(rawText: String): String? {
        detectNperfNetworkUnderReseau(rawText)?.let { return it }
        for (match in plmnRegex.findAll(rawText)) {
            val normalized = "${match.groupValues[1]}${match.groupValues[2]}"
            operatorByPlmn[normalized]?.let { return it }
        }
        return null
    }

    private fun detectNperfOperatorUnderOperateur(rawText: String): String? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val opLabels = listOf("opérateur", "operateur", "operator")
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (opLabels.any { lower == it || lower.endsWith(" $it") }) {
                for (offset in 1..3) {
                    val candidate = lines.getOrNull(i + offset) ?: break
                    matchKnownOperator(candidate.lowercase(Locale.ROOT))?.let { return it }
                }
            }
        }
        return null
    }

    private fun detectNperfNetworkUnderReseau(rawText: String): String? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val reseauLabels = listOf("réseau", "reseau", "network")
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (reseauLabels.any { lower == it || lower.endsWith(" $it") }) {
                // Cas colonne : la valeur est juste en dessous de "Réseau"
                if (i + 1 < lines.size) {
                    matchKnownOperator(lines[i + 1].lowercase(Locale.ROOT))?.let { return it }
                }
                // Cas ligne : "Connexion", "Opérateur", "Réseau" puis 3 valeurs en dessous -> la 3e est sous Réseau
                if (i >= 2 &&
                    (lines[i - 1].lowercase(Locale.ROOT).contains("opérateur") ||
                        lines[i - 1].lowercase(Locale.ROOT).contains("operateur") ||
                        lines[i - 1].lowercase(Locale.ROOT).contains("operator"))
                ) {
                    for (offset in 1..4) {
                        val candidateIdx = i + offset
                        if (candidateIdx < lines.size) {
                            matchKnownOperator(lines[candidateIdx].lowercase(Locale.ROOT))?.let { found ->
                                // Si on a d'abord croisé un MVNO (ex: Lebara) avant SFR, on continue jusqu'au dernier
                                if (offset >= 3) return found
                            }
                        }
                    }
                    // Fallback : dernier opérateur connu dans les 4 lignes suivant "Réseau"
                    val lastOp = (1..4)
                        .mapNotNull { offset -> lines.getOrNull(i + offset)?.lowercase(Locale.ROOT) }
                        .mapNotNull { matchKnownOperator(it) }
                        .lastOrNull()
                    if (lastOp != null) return lastOp
                }
            }
        }
        return null
    }

    private fun matchKnownOperator(lowerText: String): String? {
        for ((displayName, aliases) in knownOperators) {
            for (alias in aliases) {
                val pattern = Regex("""\b${Regex.escape(alias)}\b""")
                if (pattern.containsMatchIn(lowerText)) return displayName
            }
        }
        return null
    }

    private fun isServerInfoLine(lower: String): Boolean {
        return lower.contains("anycast") ||
            lower.contains("serveur") ||
            lower.contains("server") ||
            Regex("""(?:^|[\s-])(?:10|25|40|100)\s*gb(?:/s|ps)\b""").containsMatchIn(lower)
    }

    private fun isAvgOrMaxLine(lower: String): Boolean {
        return lower.contains("max") ||
            lower.contains("moy") ||
            lower.contains("avg") ||
            lower.contains("min") ||
            lower.contains("pic")
    }

    private fun detectNetworkTech(rawText: String, fullLower: String): String? {
        val withoutAnchor = rawText.replace(
            Regex("""\b(?:ancre\s*4g|4g\s*anchor)\b""", RegexOption.IGNORE_CASE),
            " "
        )
        val hasNrBand = Regex("""\bn\d{1,3}\b""").containsMatchIn(rawText)
        val hasLteBandOrAnchor = Regex("""\bB\d{1,3}\b|\bancre\s*4g\b|\b4g\s*anchor\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(rawText)

        return when {
            Regex("""\b5g\s*[:(\s-]*sa\s*\)?|\bnr\s*[:(\s-]*sa\s*\)?""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "5G SA"
            Regex("""\b5g\s*[:(\s-]*nsa\s*\)?|\bnr\s*[:(\s-]*nsa\s*\)?""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "5G NSA"
            Regex("""\b5g\b|\bnr\b""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "5G"
            hasNrBand && hasLteBandOrAnchor -> "5G NSA"
            hasNrBand -> "5G"
            Regex("""\b4g\+|\blte-?a\b""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "4G+"
            Regex("""\b4g\b|\blte\b""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "4G"
            Regex("""\b3g\+|\bhspa\b|\b3g\b""", RegexOption.IGNORE_CASE).containsMatchIn(withoutAnchor) -> "3G"
            fullLower.contains("wi-fi") || Regex("""\bwifi\b""").containsMatchIn(fullLower) -> "Wi-Fi"
            else -> null
        }
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            if (keyword.length <= 2) {
                Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(text)
            } else {
                text.contains(keyword)
            }
        }
    }

    private fun convertToMbps(rawNumber: String, unit: String): Double? {
        val value = parseNumber(rawNumber) ?: return null
        return when (unit.lowercase(Locale.ROOT)) {
            "gb/s", "gbps" -> value * 1000.0
            "kb/s", "kbps", "kbit/s" -> value / 1000.0
            else -> value
        }
    }

    private fun parseNumber(raw: String): Double? {
        val compact = raw.replace(Regex("""[\s\u00A0]"""), "")
        val normalized = when {
            compact.contains(',') && compact.contains('.') -> {
                // Les deux séparateurs sont présents : le dernier est le séparateur décimal
                if (compact.lastIndexOf(',') > compact.lastIndexOf('.')) {
                    compact.replace(".", "").replace(',', '.')
                } else {
                    compact.replace(",", "")
                }
            }
            compact.contains(',') -> compact.replace(',', '.')
            else -> compact
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
}
