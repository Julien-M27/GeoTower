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

    @Test
    fun legacyManifestFileWithoutStatusIsStillUploadable() {
        val decoded = SignalQuestUploadManifestCodec.decode(
            """
            {
              "uploadId": "upload-legacy",
              "siteId": "12345",
              "operator": "SFR",
              "description": "",
              "createdAtMillis": 123,
              "files": [
                {
                  "sourceFileName": "source_0.jpg",
                  "sourceMimeType": "image/jpeg",
                  "sourceSizeBytes": 42
                }
              ],
              "stripExifBeforeUpload": false
            }
            """.trimIndent()
        )

        val uploadFile = decoded.files.single()
        assertEquals(SignalQuestUploadFileStatus.PENDING, SignalQuestUploadFileStatus.normalized(uploadFile.status))
        assertTrue(SignalQuestUploadFileStatus.shouldUpload(uploadFile.status))
        assertFalse(SignalQuestUploadFileStatus.countsAsFinished(uploadFile.status))
    }

    @Test
    fun retryStatusIsUploadableButNotFinished() {
        assertTrue(SignalQuestUploadFileStatus.shouldUpload(SignalQuestUploadFileStatus.RETRY))
        assertFalse(SignalQuestUploadFileStatus.countsAsFinished(SignalQuestUploadFileStatus.RETRY))
        assertFalse(SignalQuestUploadFileStatus.countsAsUploaded(SignalQuestUploadFileStatus.RETRY))
    }

    @Test
    fun terminalStatusesAreNotUploadedAgain() {
        assertFalse(SignalQuestUploadFileStatus.shouldUpload(SignalQuestUploadFileStatus.UPLOADED))
        assertFalse(SignalQuestUploadFileStatus.shouldUpload(SignalQuestUploadFileStatus.AWAITING_VALIDATION))
        assertFalse(SignalQuestUploadFileStatus.shouldUpload(SignalQuestUploadFileStatus.FAILED_PERMANENT))

        assertTrue(SignalQuestUploadFileStatus.countsAsFinished(SignalQuestUploadFileStatus.UPLOADED))
        assertTrue(SignalQuestUploadFileStatus.countsAsFinished(SignalQuestUploadFileStatus.AWAITING_VALIDATION))
        assertTrue(SignalQuestUploadFileStatus.countsAsFinished(SignalQuestUploadFileStatus.FAILED_PERMANENT))

        assertTrue(SignalQuestUploadFileStatus.countsAsUploaded(SignalQuestUploadFileStatus.UPLOADED))
        assertTrue(SignalQuestUploadFileStatus.countsAsUploaded(SignalQuestUploadFileStatus.AWAITING_VALIDATION))
        assertFalse(SignalQuestUploadFileStatus.countsAsUploaded(SignalQuestUploadFileStatus.FAILED_PERMANENT))
    }
}
