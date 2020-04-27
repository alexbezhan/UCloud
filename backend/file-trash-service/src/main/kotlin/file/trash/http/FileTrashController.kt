package dk.sdu.cloud.file.trash.http

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.trash.api.FileTrashDescriptions
import dk.sdu.cloud.file.trash.services.TrashService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class FileTrashController(
    private val wsServiceCloud: AuthenticatedClient,
    private val trashService: TrashService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileTrashDescriptions.trash) {
            ok(
                trashService.moveFilesToTrash(
                    request.files,
                    ctx.securityPrincipal.username,
                    userCloudWs()
                ),
                HttpStatusCode.Accepted
            )
        }

        implement(FileTrashDescriptions.clear) {
            ok(
                trashService.emptyTrash(
                    ctx.securityPrincipal.username,
                    userCloudWs(),
                    request.trashPath
                ),
                HttpStatusCode.Accepted
            )
        }
    }

    private fun CallHandler<*, *, *>.userCloudWs(): AuthenticatedClient {
        return wsServiceCloud.withoutAuthentication().bearerAuth(ctx.bearer!!)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
