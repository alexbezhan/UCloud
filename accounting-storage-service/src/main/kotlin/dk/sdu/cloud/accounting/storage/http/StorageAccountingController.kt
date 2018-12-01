package dk.sdu.cloud.accounting.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.BuildReportResponse
import dk.sdu.cloud.accounting.storage.api.StorageAccountingDescriptions
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class StorageAccountingController(
    private val storageAccountingService: StorageAccountingService
) : Controller {
    override val baseContext: String = StorageAccountingDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(StorageAccountingDescriptions.buildReport) { req ->
            if (call.securityPrincipal.username != "_accounting") {
                return@implement(error(CommonErrorMessage("User Not Allowed"), HttpStatusCode.Unauthorized))
            }
            else {
                val storageUsed = storageAccountingService
                return@implement(ok(BuildReportResponse(storageUsed.calculateUsage(homeDirectory(req.user), req.user))))
            }
        }
    }
}