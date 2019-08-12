package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.orchestrator.api.AppOrchestratorServiceDescription
import dk.sdu.cloud.app.orchestrator.api.ApplicationBackend
import dk.sdu.cloud.micro.*

data class Configuration(
    val backends: List<ApplicationBackend> = emptyList(),
    val defaultBackend: String = "abacus"
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppOrchestratorServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkOrNull("app") ?: Configuration()

    Server(micro, config).start()
}
