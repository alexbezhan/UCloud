package dk.sdu.cloud.storage.services.ext.irods

object SDUCloudTrustManager {
    private var loaded = false
    val trustManager by lazy {
        ChainedTrustManager.chainDefaultWithStoreFrom(
            SDUCloudTrustManager::class.java.classLoader.getResourceAsStream("sdu-cloud-store.jks"),
            "dataispublic".toCharArray()
        )
    }

    fun ensureLoaded() {
        if (loaded) return

        trustManager.setAsDefault()
        loaded = true
    }
}