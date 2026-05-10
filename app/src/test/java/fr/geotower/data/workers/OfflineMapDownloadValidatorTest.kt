package fr.geotower.data.workers

import fr.geotower.data.models.OfflineMapDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OfflineMapDownloadValidatorTest {

    @Test
    fun validCatalogEntryRequiresHttpsAllowedHostAndSafeFilename() {
        val valid = mapDto(
            mapUrl = "https://download.mapsforge.org/maps/v5/europe/france/alsace.map",
            mapFilename = "alsace.map"
        )

        assertTrue(OfflineMapDownloadValidator.isValidCatalogEntry(valid))
    }

    @Test
    fun httpMapUrlIsRejected() {
        val invalid = mapDto(
            mapUrl = "http://download.mapsforge.org/maps/v5/europe/france/alsace.map",
            mapFilename = "alsace.map"
        )

        assertFalse(OfflineMapDownloadValidator.isValidCatalogEntry(invalid))
    }

    @Test
    fun pathTraversalFilenameIsRejected() {
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("../alsace.map"))
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("..\\alsace.map"))
    }

    @Test
    fun nonMapFilenamesAreRejected() {
        assertTrue(OfflineMapDownloadValidator.isSafeMapFilename("france.map"))
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("evil.txt"))
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("file.map.download"))
    }

    @Test
    fun absoluteFilenameIsRejected() {
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("/tmp/alsace.map"))
        assertFalse(OfflineMapDownloadValidator.isSafeMapFilename("C:\\tmp\\alsace.map"))
    }

    @Test
    fun safeMapFileStaysInsideMapsDirectory() {
        val mapsDir = Files.createTempDirectory("geotower-maps").toFile()
        try {
            val file = OfflineMapDownloadValidator.safeMapFile(mapsDir, "alsace.map")

            assertNotNull(file)
            assertTrue(file!!.canonicalPath.startsWith(mapsDir.canonicalPath))
        } finally {
            mapsDir.deleteRecursively()
        }
    }

    @Test
    fun listSafeMapFilesReturnsOnlyValidMapFilesDirectlyUnderMapsDirectory() {
        val rootDir = Files.createTempDirectory("geotower-map-list").toFile()
        val mapsDir = rootDir.resolve("maps").apply { mkdirs() }
        val otherDir = rootDir.resolve("other").apply { mkdirs() }
        val validMap = mapsDir.resolve("france.map")
        try {
            validMap.writeText("map")
            mapsDir.resolve("evil.txt").writeText("text")
            mapsDir.resolve("file.map.download").writeText("partial")
            mapsDir.resolve("nested.map").mkdir()
            otherDir.resolve("outside.map").writeText("map")

            val safeFiles = OfflineMapDownloadValidator.listSafeMapFiles(mapsDir)

            assertEquals(listOf(validMap.canonicalFile), safeFiles)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun deleteAllSafeMapFilesDeletesOnlyValidMapFiles() {
        val mapsDir = Files.createTempDirectory("geotower-map-delete").toFile()
        val validMap = mapsDir.resolve("france.map")
        val textFile = mapsDir.resolve("evil.txt")
        val tempFile = mapsDir.resolve("file.map.download")
        val nestedMapDir = mapsDir.resolve("nested.map")
        try {
            validMap.writeText("map")
            textFile.writeText("text")
            tempFile.writeText("partial")
            nestedMapDir.mkdir()

            val deletedCount = OfflineMapDownloadValidator.deleteAllSafeMapFiles(mapsDir)

            assertEquals(1, deletedCount)
            assertFalse(validMap.exists())
            assertTrue(textFile.exists())
            assertTrue(tempFile.exists())
            assertTrue(nestedMapDir.exists())
        } finally {
            mapsDir.deleteRecursively()
        }
    }

    @Test
    fun downloadedMapRequiresExpectedLengthAndSha256WhenProvided() {
        val mapsDir = Files.createTempDirectory("geotower-maps").toFile()
        val mapFile = mapsDir.resolve("alsace.map")
        try {
            mapFile.writeText("abc")

            assertTrue(
                OfflineMapDownloadValidator.isValidDownloadedMap(
                    file = mapFile,
                    expectedContentLength = 3,
                    expectedSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                )
            )
            assertFalse(
                OfflineMapDownloadValidator.isValidDownloadedMap(
                    file = mapFile,
                    expectedContentLength = 4,
                    expectedSha256 = null
                )
            )
            assertFalse(
                OfflineMapDownloadValidator.isValidDownloadedMap(
                    file = mapFile,
                    expectedContentLength = 3,
                    expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                )
            )
        } finally {
            mapsDir.deleteRecursively()
        }
    }

    private fun mapDto(
        mapUrl: String,
        mapFilename: String,
        sha256: String? = null
    ): OfflineMapDto {
        return OfflineMapDto(
            id = "alsace",
            name = "Alsace",
            description = "Region",
            mapUrl = mapUrl,
            estimatedSizeMb = 85,
            mapFilename = mapFilename,
            sha256 = sha256
        )
    }
}
