package fr.geotower.data.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalQuestUploadRulesTest {

    @Test
    fun rejectsNonImageMimeTypes() {
        assertTrue(SignalQuestUploadRules.isAcceptedMimeType("image/jpeg"))
        assertTrue(SignalQuestUploadRules.isAcceptedMimeType("IMAGE/WEBP"))
        assertFalse(SignalQuestUploadRules.isAcceptedMimeType("application/pdf"))
        assertFalse(SignalQuestUploadRules.isAcceptedMimeType(null))
    }

    @Test
    fun rejectsOversizedSourceFiles() {
        assertTrue(SignalQuestUploadRules.isAcceptedSourceSize(1L))
        assertTrue(SignalQuestUploadRules.isAcceptedSourceSize(SignalQuestUploadRules.MAX_SOURCE_BYTES))
        assertFalse(SignalQuestUploadRules.isAcceptedSourceSize(0L))
        assertFalse(SignalQuestUploadRules.isAcceptedSourceSize(SignalQuestUploadRules.MAX_SOURCE_BYTES + 1L))
        assertFalse(SignalQuestUploadRules.isAcceptedSourceSize(null))
    }

    @Test
    fun calculatesPowerOfTwoSampleSizeForLargeImages() {
        assertEquals(1, SignalQuestUploadRules.calculateInSampleSize(2048, 1536))
        assertEquals(2, SignalQuestUploadRules.calculateInSampleSize(6000, 4000))
        assertEquals(4, SignalQuestUploadRules.calculateInSampleSize(12000, 9000))
    }

    @Test
    fun draftStorePreservesUrisWithCommas() {
        val uri = "content://media/external/images/media/a,b?token=1%2C2"
        val draftId = SignalQuestUploadDraftStore.put(listOf(uri))

        assertEquals(listOf(uri), SignalQuestUploadDraftStore.take(draftId))
        assertEquals(emptyList<String>(), SignalQuestUploadDraftStore.take(draftId))
    }

    @Test
    fun manifestCodecRoundTripsUploadMetadata() {
        val manifest = SignalQuestUploadManifest(
            uploadId = "upload-1",
            siteId = "12345",
            operator = "SFR",
            description = "north side",
            createdAtMillis = 123L,
            files = listOf(
                SignalQuestUploadFile(
                    sourceFileName = "source_0.jpg",
                    sourceMimeType = "image/jpeg",
                    sourceSizeBytes = 42L
                )
            ),
            stripExifBeforeUpload = true
        )

        val decoded = SignalQuestUploadManifestCodec.decode(
            SignalQuestUploadManifestCodec.encode(manifest)
        )

        assertEquals(manifest, decoded)
    }
}
