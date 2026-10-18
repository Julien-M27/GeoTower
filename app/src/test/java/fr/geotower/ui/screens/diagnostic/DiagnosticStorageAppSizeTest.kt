package fr.geotower.ui.screens.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticStorageAppSizeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun calculateApkFilesSizeWithNullAndMissingFilesReturnsZero() {
        assertEquals(0L, calculateApkFilesSize(null, null))
        assertEquals(0L, calculateApkFilesSize("non_existent_file.apk", emptyArray<String>()))
        assertEquals(0L, calculateApkFilesSize(null, arrayOf("missing_split.apk")))
    }

    @Test
    fun calculateApkFilesSizeSumsBaseAndSplitApks() {
        val baseApk = tempFolder.newFile("base.apk").apply {
            writeBytes(ByteArray(1024))
        }
        val split1 = tempFolder.newFile("split_config.arm64_v8a.apk").apply {
            writeBytes(ByteArray(512))
        }
        val split2 = tempFolder.newFile("split_config.xxhdpi.apk").apply {
            writeBytes(ByteArray(256))
        }

        val total = calculateApkFilesSize(
            baseApk.absolutePath,
            arrayOf(split1.absolutePath, split2.absolutePath)
        )
        assertEquals(1024L + 512L + 256L, total)
    }

    @Test
    fun calculateApkFilesSizeIgnoresDirectories() {
        val dir = tempFolder.newFolder("fake_apk_dir")
        val total = calculateApkFilesSize(dir.absolutePath, null)
        assertEquals(0L, total)
    }
}
