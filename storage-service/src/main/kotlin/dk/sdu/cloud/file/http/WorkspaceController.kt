package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.api.WorkspaceDescriptions
import dk.sdu.cloud.file.api.WorkspaceMode
import dk.sdu.cloud.file.api.Workspaces
import dk.sdu.cloud.file.services.WorkspaceService
import dk.sdu.cloud.service.Controller

class WorkspaceController(
    private val workspaceService: WorkspaceService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(WorkspaceDescriptions.create) {
            val response = workspaceService.create(
                request.username,
                request.mounts,
                request.allowFailures,
                request.createSymbolicLinkAt,
                request.mode ?: WorkspaceMode.COPY_FILES
            )

            ok(response)
        }

        implement(WorkspaceDescriptions.delete) {
            workspaceService.delete(request.workspaceId)
            ok(Workspaces.Delete.Response)
        }

        implement(WorkspaceDescriptions.transfer) {
            workspaceService.requestTransfer(
                request.workspaceId,
                request.transferGlobs,
                request.destination,
                request.replaceExisting,
                request.deleteWorkspace
            )

            ok(Workspaces.Transfer.Response(emptyList()))
        }

        return@with
    }
}
