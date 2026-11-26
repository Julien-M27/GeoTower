package fr.geotower.data.build

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pont JNI vers le moteur de generation natif Rust (`libgeotower_builder.so`).
 */
object RustDbBuilder {
    private val loaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("geotower_builder")
            true
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean = loaded

    interface ProgressCallback {
        fun onProgress(phaseOrdinal: Int, percent: Int, detail: String?, processed: Long)
        /** Retourne 1 si annule, 0 sinon (bloque tant qu'en pause). */
        fun checkControl(): Int
    }

    data class Config(
        val packMobile: Boolean,
        val packRadio: Boolean,
        val allowedServiceMask: Int,
        val dataZip: File,
        val refZip: File?,
        val observatoireCsv: File?,
        val arcepFiles: List<File>,
        val builtMobileFile: File,
        val stagingMobileFile: File,
        val builtRadioFile: File,
        val stagingRadioFile: File,
        val version: String,
        val zipVersion: String,
        val quarterlyVersion: String?,
        val totalRamBytes: Long,
        val communes: Map<String, String>,
        val departments: Map<String, DepartmentReferenceRow>,
    )

    fun build(config: Config, callback: ProgressCallback): String? {
        if (!loaded) return "Bibliothèque native Rust (geotower_builder) indisponible"
        val json = JSONObject().apply {
            put("packMobile", config.packMobile)
            put("packRadio", config.packRadio)
            put("allowedServiceMask", config.allowedServiceMask)
            put("dataZipPath", config.dataZip.absolutePath)
            put("refZipPath", config.refZip?.takeIf { it.exists() }?.absolutePath ?: JSONObject.NULL)
            put("observatoireCsvPath", config.observatoireCsv?.takeIf { it.exists() }?.absolutePath ?: JSONObject.NULL)
            put("arcepCsvPaths", JSONArray(config.arcepFiles.map { it.absolutePath }))
            put("builtMobilePath", config.builtMobileFile.absolutePath)
            put("stagingMobilePath", config.stagingMobileFile.absolutePath)
            put("builtRadioPath", config.builtRadioFile.absolutePath)
            put("stagingRadioPath", config.stagingRadioFile.absolutePath)
            put("version", config.version)
            put("zipVersion", config.zipVersion)
            put("quarterlyVersion", config.quarterlyVersion ?: JSONObject.NULL)
            put("totalRamBytes", config.totalRamBytes)
            put("communes", JSONObject(config.communes))
            val deptsObj = JSONObject()
            for ((code, row) in config.departments) {
                deptsObj.put(
                    code,
                    JSONObject().apply {
                        put("code", row.code)
                        put("name", row.name)
                        put("regionCode", row.regionCode)
                        put("areaKm2", row.areaKm2 ?: JSONObject.NULL)
                        put("population", row.population ?: JSONObject.NULL)
                        put("populationYear", row.populationYear ?: JSONObject.NULL)
                    },
                )
            }
            put("departments", deptsObj)
        }
        val error = nativeBuildDatabases(json.toString(), callback)
        return error.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    private external fun nativeBuildDatabases(configJson: String, callback: ProgressCallback): String
}
