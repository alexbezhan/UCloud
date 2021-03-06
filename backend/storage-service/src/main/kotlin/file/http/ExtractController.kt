package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.services.BulkUploader
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

class ExtractController<Ctx : FSUserContext>(
    private val serviceCloud: AuthenticatedClient,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val backgroundScope: BackgroundScope
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileDescriptions.extract) {
            audit(SingleFileAudit(request))
            val user = ctx.securityPrincipal.username

            val extractor = when {
                request.path.endsWith(".tar.gz") -> BulkUploader.fromFormat("tgz", commandRunnerFactory.type)
                request.path.endsWith(".zip") -> BulkUploader.fromFormat("zip", commandRunnerFactory.type)
                else -> null
            } ?: return@implement error(
                CommonErrorMessage("Unsupported format"),
                HttpStatusCode.BadRequest
            )

            backgroundScope.launch {
                commandRunnerFactory.withCtx(this@implement, user) { readContext ->
                    coreFs.read(readContext, request.path) {
                        val fileInputStream = this

                        extractor.upload(
                            serviceCloud,
                            coreFs,
                            { commandRunnerFactory.createContext(this@implement, user) },
                            request.path.parent(),
                            WriteConflictPolicy.RENAME,
                            fileInputStream,
                            null,
                            request.path.fileName(),
                            backgroundScope
                        )
                    }

                    if (request.removeOriginalArchive == true) {
                        coreFs.delete(readContext, request.path)
                    }
                }
            }

            ok(Unit, HttpStatusCode.Accepted)
        }
    }
}
