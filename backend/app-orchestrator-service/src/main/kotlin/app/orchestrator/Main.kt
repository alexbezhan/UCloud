package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.orchestrator.api.AppOrchestratorServiceDescription
import dk.sdu.cloud.app.orchestrator.api.ApplicationBackend
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class Configuration(
    val backends: List<ApplicationBackend> = listOf(ApplicationBackend("kubernetes")),
    val defaultBackend: String = "kubernetes"
)

object AppOrchestratorService : Service {
    override val description = AppOrchestratorServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(BackgroundScopeFeature)
        micro.install(RefreshingJWTCloudFeature)

        val config = micro.configuration.requestChunkOrNull("app") ?: Configuration()
        return Server(micro, config)
    }
}

fun main(args: Array<String>) {
    AppOrchestratorService.runAsStandalone(args)
}
