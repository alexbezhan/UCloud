package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.storage.api.StorageServiceDescription

val SERVICE_USER = "_${StorageServiceDescription.name}"

data class StorageConfiguration(
    val filePermissionAcl: Set<String> = emptySet()
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        init(StorageServiceDescription, args)
        installDefaultFeatures()
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(BackgroundScopeFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration(),
        micro
    ).start()
}
