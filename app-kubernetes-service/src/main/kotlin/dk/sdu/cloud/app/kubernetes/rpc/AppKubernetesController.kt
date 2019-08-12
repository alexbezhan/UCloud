package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.app.orchestrator.api.InternalStdStreamsResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class AppKubernetesController(
    private val podService: PodService,
    private val vncService: VncService,
    private val webService: WebService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppKubernetesDescriptions.cleanup) {
            podService.cleanup(request.id)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.follow) {
            val (log, nextLine) = podService.retrieveLogs(
                request.job.id,
                request.stdoutLineStart,
                request.stdoutMaxLines
            )

            ok(InternalStdStreamsResponse(log, nextLine, "", 0))
        }

        implement(AppKubernetesDescriptions.jobVerified) {
            val sharedFileSystemMountsAreSupported =
                request.sharedFileSystemMounts.all { it.sharedFileSystem.backend == "kubernetes" }

            if (!sharedFileSystemMountsAreSupported) {
                throw RPCException(
                    "A file system mount was attempted which this backend does not support",
                    HttpStatusCode.BadRequest
                )
            }

            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) // Not supported
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
            podService.create(request)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.queryInternalVncParameters) {
            ok(vncService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.queryInternalWebParameters) {
            ok(webService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.cancel) {
            podService.cancel(request.verifiedJob)
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
