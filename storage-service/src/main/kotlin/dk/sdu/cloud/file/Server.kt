package dk.sdu.cloud.file

import com.sun.jna.Platform
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.http.MetadataController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.migration.ImportFavorites
import dk.sdu.cloud.file.migration.ImportShares
import dk.sdu.cloud.file.migration.PermissionMigration
import dk.sdu.cloud.file.migration.WorkspaceMigration
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.ACLWorker
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.MetadataRecoveryService
import dk.sdu.cloud.file.services.WSFileSessionService
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File
import kotlin.system.exitProcess

class Server(
    private val config: StorageConfiguration,
    private val cephConfig: CephConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        log.info("Creating core services")

        require(Platform.isLinux() || micro.developmentModeEnabled) {
            "This service is only able to run on GNU/Linux in production mode"
        }

        // FS root
        val fsRootFile =
            File("/mnt/cephfs/" + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        // Authorization
        val homeFolderService = HomeFolderService(client)
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val metadataDao = MetadataDao()
        val metadataService = MetadataService(db, metadataDao)
        val newAclService = AclService(metadataService, homeFolderService, client)

        val processRunner = LinuxFSRunnerFactory(micro.backgroundScope)
        val fs = LinuxFS(fsRootFile, newAclService)
        val aclService = ACLWorker(newAclService)
        val sensitivityService = FileSensitivityService(fs)
        val coreFileSystem =
            CoreFileSystemService(fs, sensitivityService, wsClient, micro.backgroundScope, metadataService)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(processRunner, coreFileSystem)
        val indexingService = IndexingService<LinuxFSRunner>(newAclService)

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        log.info("Core services constructed!")

        if (micro.commandLineArguments.contains("--migrate-workspaces")) {
            try {
                WorkspaceMigration(fsRootFile, false).runMigration()
            } catch (ex: Throwable) {
                log.error(ex.stackTraceToString())
                exitProcess(1)
            }
            exitProcess(0)
        }

        if (micro.commandLineArguments.contains("--migrate-permissions")) {
            try {
                PermissionMigration(db, metadataDao).runMigration()
            } catch (ex: Throwable) {
                log.error(ex.stackTraceToString())
                exitProcess(1)
            }
            exitProcess(0)
        }

        if (micro.commandLineArguments.contains("--migrate-shares")) {
            try {
                ImportShares(db, metadataDao).runMigration()
            } catch (ex: Throwable) {
                log.error(ex.stackTraceToString())
                exitProcess(1)
            }
            exitProcess(0)
        }

        if (micro.commandLineArguments.contains("--migrate-favorites")) {
            try {
                ImportFavorites(db, metadataDao).runMigration()
            } catch (ex: Throwable) {
                log.error(ex.stackTraceToString())
                exitProcess(1)
            }
            exitProcess(0)
        }

        UserProcessor(
            streams,
            fsRootFile,
            homeFolderService
        ).init()

        val metadataRecovery = MetadataRecoveryService(
            micro.backgroundScope,
            DistributedLockBestEffortFactory(micro),
            coreFileSystem,
            processRunner,
            db,
            metadataDao
        )

        metadataRecovery.startProcessing()

        val tokenValidation =
            micro.tokenValidation as? TokenValidationJWT ?: throw IllegalStateException("JWT token validation required")

        // HTTP
        with(micro.server) {
            configureControllers(
                ActionController(
                    commandRunnerForCalls,
                    coreFileSystem,
                    sensitivityService,
                    fileLookupService
                ),

                LookupController(
                    commandRunnerForCalls,
                    fileLookupService,
                    homeFolderService
                ),

                FileSecurityController(
                    commandRunnerForCalls,
                    aclService,
                    sensitivityService,
                    config.filePermissionAcl
                ),

                IndexingController(
                    processRunner,
                    indexingService
                ),

                SimpleDownloadController(
                    client,
                    commandRunnerForCalls,
                    coreFileSystem,
                    tokenValidation,
                    fileLookupService
                ),

                MultiPartUploadController(
                    client,
                    commandRunnerForCalls,
                    coreFileSystem,
                    sensitivityService,
                    micro.backgroundScope
                ),

                ExtractController(
                    client,
                    coreFileSystem,
                    commandRunnerForCalls,
                    sensitivityService,
                    micro.backgroundScope
                ),

                MetadataController(metadataService, metadataRecovery)
            )
        }

        startServices()
    }
}
