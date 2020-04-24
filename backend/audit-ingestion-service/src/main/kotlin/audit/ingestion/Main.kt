package dk.sdu.cloud.audit.ingestion

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.audit.ingestion.api.AuditIngestionServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AuditIngestionServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}