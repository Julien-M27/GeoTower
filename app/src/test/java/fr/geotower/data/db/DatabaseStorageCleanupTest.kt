package fr.geotower.data.db

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseStorageCleanupTest {

    @Test
    fun clearTransientArtifacts_keepsActiveDatabaseAndRemovesAllTransientFiles() {
        val directory = Files.createTempDirectory("geotower-db-cleanup").toFile()
        try {
            val active = directory.resolve("geotower_fr.db")
            active.writeText("active")
            listOf(
                ".download",
                ".backup",
                ".localbuild",
            ).forEach { suffix ->
                directory.resolve("geotower_fr.db$suffix").writeText("temporary")
                directory.resolve("geotower_fr.db$suffix-wal").writeText("temporary")
                directory.resolve("geotower_fr.db$suffix-shm").writeText("temporary")
                directory.resolve("geotower_fr.db$suffix-journal").writeText("temporary")
            }

            assertTrue(DatabaseStorageCleanup.clearTransientArtifacts(directory, listOf("geotower_fr.db")))

            assertTrue(active.isFile)
            assertFalse(directory.resolve("geotower_fr.db.download").exists())
            assertFalse(directory.resolve("geotower_fr.db.backup").exists())
            assertFalse(directory.resolve("geotower_fr.db.localbuild").exists())
            assertFalse(directory.resolve("geotower_fr.db.localbuild-wal").exists())
            assertFalse(directory.resolve("geotower_fr.db.localbuild-shm").exists())
            assertFalse(directory.resolve("geotower_fr.db.localbuild-journal").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun clearLocalBuildWorkspace_removesSourcesAndStaging() {
        val directory = Files.createTempDirectory("geotower-build-cleanup").toFile()
        try {
            directory.resolve("sup_data.zip").writeText("source")
            directory.resolve("staging_mobile.db").writeText("staging")
            directory.resolve("arcep").mkdirs()
            directory.resolve("arcep/sites.csv").writeText("source")

            assertTrue(DatabaseStorageCleanup.clearLocalBuildWorkspace(directory))
            assertFalse(directory.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
