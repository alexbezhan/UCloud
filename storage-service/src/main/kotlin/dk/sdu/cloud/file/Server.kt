package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.http.WorkspaceController
import dk.sdu.cloud.file.processors.ScanProcessor
import dk.sdu.cloud.file.processors.StorageProcessor
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.linuxfs.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.H2_DIALECT
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File
import kotlin.system.exitProcess

class Server(
    private val config: StorageConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        supportReverseInH2(micro)

        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        log.info("Creating core services")

        Chown.isDevMode = micro.developmentModeEnabled

        // FS root
        val fsRootFile = File("/mnt/cephfs/").takeIf { it.exists() }
            ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        // Authorization
        val homeFolderService = HomeFolderService(client)
        val aclDao = AclHibernateDao()
        val newAclService =
            AclService(micro.hibernateDatabase, aclDao, homeFolderService, linuxFSRealPathSupplier(fsRootFile))

        // Low level FS
        val processRunner = LinuxFSRunnerFactory()
        val fs = LinuxFS(fsRootFile, newAclService)

        // High level FS
        val storageEventProducer =
            StorageEventProducer(streams.createProducer(StorageEvents.events), micro.backgroundScope) {
                log.warn("Caught exception while emitting a storage event!")
                log.warn(it.stackTraceToString())
                stop()
            }

        // Metadata services
        val aclService = ACLWorker(newAclService)
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)

        // High level FS
        val coreFileSystem =
            CoreFileSystemService(fs, storageEventProducer, sensitivityService, wsClient, micro.backgroundScope)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(processRunner, coreFileSystem)
        val indexingService = IndexingService(
            processRunner,
            coreFileSystem,
            storageEventProducer,
            newAclService,
            micro.eventStreamService
        )
        val fileScanner = FileScanner(processRunner, coreFileSystem, storageEventProducer, micro.backgroundScope)
        val workspaceService = WorkspaceService(fsRootFile, fileScanner, newAclService, coreFileSystem, processRunner)

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        log.info("Core services constructed!")

        UserProcessor(
            streams,
            fileScanner
        ).init()

        StorageProcessor(streams, newAclService).init()

        ScanProcessor(streams, indexingService).init()

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
                    coreFileSystem,
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
                    processRunner,
                    coreFileSystem,
                    tokenValidation,
                    fileLookupService
                ),

                MultiPartUploadController(
                    client,
                    processRunner,
                    coreFileSystem,
                    sensitivityService,
                    micro.backgroundScope
                ),

                ExtractController(
                    client,
                    coreFileSystem,
                    fileLookupService,
                    processRunner,
                    sensitivityService,
                    micro.backgroundScope
                ),

                WorkspaceController(workspaceService)
            )
        }

        startServices()
    }

    private fun supportReverseInH2(micro: Micro) {
        val config =
            micro.configuration.requestChunkAtOrNull<HibernateFeature.Feature.Config>(*HibernateFeature.CONFIG_PATH)
                ?: return

        if (config.dialect == H2_DIALECT || config.profile == HibernateFeature.Feature.Profile.TEST_H2 ||
            config.profile == HibernateFeature.Feature.Profile.PERSISTENT_H2
        ) {
            // Add database 'polyfill' for postgres reverse function.
            log.info("Adding the H2 polyfill")
            micro.hibernateDatabase.withTransaction { session ->
                session.createNativeQuery(
                    "CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ " +
                            "String reverse(String s) { return new StringBuilder(s).reverse().toString(); } " +
                            "\$\$;"
                ).executeUpdate()
            }
        }

        micro.hibernateDatabase.withTransaction { session ->
            try {
                session.createNativeQuery("select REVERSE('foo')").list().first().toString()
            } catch (ex: Throwable) {
                log.error("Could not reverse string in database!")
                exitProcess(1)
            }
        }
    }
}
