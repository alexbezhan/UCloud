package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ListRequest
import dk.sdu.cloud.file.gateway.api.FavoriteGWDescriptions
import dk.sdu.cloud.file.gateway.api.StorageFileWithMetadata
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.mapItems

class FavoriteController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FavoriteGWDescriptions.list) {
            val userCloud = userCloudService.createUserCloud(ctx as HttpCall)

            val pageOfFiles = FileFavoriteDescriptions.list
                .call(
                    ListRequest(request.itemsPerPage, request.page),
                    userCloud
                )
                .orThrow()
                .mapItems { StorageFileWithMetadata(it, true) }

            ok(pageOfFiles) // TODO This won't work if we get more resources in here
        }

        return@with
    }
}
