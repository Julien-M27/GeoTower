package fr.geotower.utils

enum class AppUiMode(val storageKey: String) {
    Auto("auto"),
    OneUi("one_ui"),
    Material("material");

    fun usesOneUi(isSamsungDevice: Boolean = DeviceProfile.isSamsungDevice): Boolean {
        return when (this) {
            Auto -> isSamsungDevice
            OneUi -> true
            Material -> false
        }
    }

    companion object {
        fun fromStorageKey(storageKey: String?): AppUiMode {
            return values().firstOrNull { it.storageKey == storageKey } ?: Auto
        }

        fun fromOneUiEnabled(enabled: Boolean): AppUiMode {
            return if (enabled) OneUi else Material
        }
    }
}
