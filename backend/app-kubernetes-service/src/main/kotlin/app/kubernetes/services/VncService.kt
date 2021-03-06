package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.orchestrator.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.ktor.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.webSocket

class VncService(
    private val tunnelManager: TunnelManager
) {
    private val jobIdToJob = HashMap<String, VerifiedJob>()

    fun install(routing: Route): Unit = with(routing) {
        webSocket("${AppKubernetesDescriptions.baseContext}/vnc/{id}", protocol = "binary") {
            val requestId = call.parameters["id"]
            log.info("Incoming VNC connection for application $requestId")
            val id = requestId ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            val tunnel = createTunnel(id)

            tunnel.use {
                runWSProxy(tunnel)
            }
        }
        return@with
    }

    fun queryParameters(job: VerifiedJob): QueryInternalVncParametersResponse {
        jobIdToJob[job.id] = job
        return QueryInternalVncParametersResponse(
            "${AppKubernetesDescriptions.baseContext}/vnc/${job.id}",
            job.application.invocation.vnc?.password
        )
    }

    private suspend fun createTunnel(incomingId: String): Tunnel {
        val jobId = incomingId // Slightly less secure, but should work for prototype
        val job = jobIdToJob[jobId] ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val port = job.application.invocation.vnc?.port ?: 5900
        return tunnelManager.createOrUseExistingTunnel(jobId, port, job.url)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
