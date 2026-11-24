package fr.geotower.data.speedtest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object SpeedtestRepository {

    fun observeAll(context: Context): Flow<List<SpeedtestEntryEntity>> {
        return SpeedtestDatabase.getDatabase(context).speedtestDao().observeAll()
    }

    fun observeById(context: Context, id: String): Flow<SpeedtestEntryEntity?> {
        return SpeedtestDatabase.getDatabase(context).speedtestDao().observeById(id)
    }

    suspend fun getById(context: Context, id: String): SpeedtestEntryEntity? {
        return SpeedtestDatabase.getDatabase(context).speedtestDao().getById(id)
    }

    suspend fun getAllOnce(context: Context): List<SpeedtestEntryEntity> {
        return SpeedtestDatabase.getDatabase(context).speedtestDao().getAllOnce()
    }

    fun imageFile(context: Context, imageFileName: String): File {
        return SpeedtestImageAnalyzer.imageFile(context, imageFileName)
    }

    /**
     * Importe une liste d'images (depuis le sélecteur Android ou un partage `ACTION_SEND*`),
     * extrait EXIF + OCR hors-ligne et enregistre une entrée par image.
     */
    suspend fun importImages(context: Context, uris: List<Uri>): List<SpeedtestEntryEntity> =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val dao = SpeedtestDatabase.getDatabase(appContext).speedtestDao()
            val imported = mutableListOf<SpeedtestEntryEntity>()

            for (uri in uris) {
                val fileName = SpeedtestImageAnalyzer.copyImageToInternalStorage(appContext, uri)
                    ?: continue
                val file = imageFile(appContext, fileName)
                val meta = SpeedtestImageAnalyzer.analyzeStoredImage(appContext, file)
                val now = System.currentTimeMillis()
                val entity = SpeedtestEntryEntity(
                    id = UUID.randomUUID().toString(),
                    imageFileName = fileName,
                    testedAtMillis = meta.testedAtMillis,
                    createdAtMillis = now,
                    downloadMbps = meta.parsed.downloadMbps,
                    uploadMbps = meta.parsed.uploadMbps,
                    pingMs = meta.parsed.pingMs,
                    jitterMs = meta.parsed.jitterMs,
                    operatorName = meta.parsed.operatorName,
                    networkTech = meta.parsed.networkTech,
                    band = meta.parsed.band,
                    cellIdentifier = meta.parsed.cellIdentifier,
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    ocrRawText = meta.rawOcrText
                )
                dao.upsert(entity)
                imported.add(entity)
            }

            imported
        }

    suspend fun upsert(context: Context, entry: SpeedtestEntryEntity) {
        SpeedtestDatabase.getDatabase(context).speedtestDao().upsert(entry)
    }

    suspend fun reanalyzeOcr(context: Context, entry: SpeedtestEntryEntity): SpeedtestEntryEntity =
        withContext(Dispatchers.IO) {
            val file = imageFile(context, entry.imageFileName)
            if (!file.exists()) return@withContext entry
            val meta = SpeedtestImageAnalyzer.analyzeStoredImage(context, file)
            val updated = entry.copy(
                downloadMbps = meta.parsed.downloadMbps ?: entry.downloadMbps,
                uploadMbps = meta.parsed.uploadMbps ?: entry.uploadMbps,
                pingMs = meta.parsed.pingMs ?: entry.pingMs,
                jitterMs = meta.parsed.jitterMs ?: entry.jitterMs,
                operatorName = meta.parsed.operatorName ?: entry.operatorName,
                networkTech = meta.parsed.networkTech ?: entry.networkTech,
                band = meta.parsed.band ?: entry.band,
                cellIdentifier = meta.parsed.cellIdentifier ?: entry.cellIdentifier,
                latitude = entry.latitude ?: meta.latitude,
                longitude = entry.longitude ?: meta.longitude,
                ocrRawText = meta.rawOcrText ?: entry.ocrRawText
            )
            SpeedtestDatabase.getDatabase(context).speedtestDao().upsert(updated)
            updated
        }

    suspend fun delete(context: Context, entry: SpeedtestEntryEntity) = withContext(Dispatchers.IO) {
        SpeedtestDatabase.getDatabase(context).speedtestDao().deleteById(entry.id)
        val file = imageFile(context, entry.imageFileName)
        runCatching { if (file.exists()) file.delete() }
    }
}
