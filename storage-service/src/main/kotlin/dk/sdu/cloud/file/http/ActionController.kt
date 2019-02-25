package dk.sdu.cloud.file.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAnnotationService
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.util.CallResult
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class ActionController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val annotationService: FileAnnotationService<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FileDescriptions.createDirectory) {
            if (ctx.securityPrincipal.role in Roles.PRIVILEDGED && request.owner != null) {
                val owner = request.owner!!
                commandRunnerFactory.withCtxAndTimeout(this, user = owner) {
                    coreFs.makeDirectory(it, request.path)
                    sensitivityService.setSensitivityLevel(it, request.path, request.sensitivity, null)
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            } else {
                commandRunnerFactory.withCtxAndTimeout(this) {
                    coreFs.makeDirectory(it, request.path)
                    sensitivityService.setSensitivityLevel(
                        it,
                        request.path,
                        request.sensitivity,
                        ctx.securityPrincipal.username
                    )
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            }
        }

        implement(FileDescriptions.deleteFile) {
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtxAndTimeout(this) {
                val stat = coreFs.stat(it, request.path, setOf(FileAttribute.INODE))
                coreFs.delete(it, request.path)

                audit(SingleFileAudit(stat.inode, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.move) {
            audit(SingleFileAudit(null, request))
            commandRunnerFactory.withCtxAndTimeout(this) {
                val stat = coreFs.stat(it, request.path, setOf(FileAttribute.INODE))
                coreFs.move(it, request.path, request.newPath, request.policy ?: WriteConflictPolicy.OVERWRITE)

                audit(SingleFileAudit(stat.inode, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.copy) {
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtxAndTimeout(this) {
                val stat = coreFs.stat(it, request.path, setOf(FileAttribute.INODE))
                coreFs.copy(it, request.path, request.newPath, request.policy ?: WriteConflictPolicy.OVERWRITE)
                audit(SingleFileAudit(stat.inode, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.annotate) {
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtx(this, user = request.proxyUser) {
                val stat = coreFs.stat(it, request.path, setOf(FileAttribute.INODE))
                annotationService.annotateFiles(it, request.path, request.annotatedWith)
                audit(SingleFileAudit(stat.inode, request))
                ok(Unit)
            }
        }

        implement(FileDescriptions.createLink) {
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val created = coreFs.createSymbolicLink(ctx, request.linkTargetPath, request.linkPath)
                audit(SingleFileAudit(coreFs.stat(ctx, request.linkPath, setOf(FileAttribute.INODE)).inode, request))

                ok(fileLookupService.stat(ctx, created.path))
            }
        }
    }
}