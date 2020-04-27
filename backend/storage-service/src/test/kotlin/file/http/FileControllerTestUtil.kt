package dk.sdu.cloud.file.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.LookupFileInDirectoryRequest
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.util.createDummyFS
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestContext
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.util.*

data class FileControllerContext(
    val client: RpcClient,
    val authenticatedClient: AuthenticatedClient,
    val fsRoot: String,
    val runner: LinuxFSRunnerFactory,
    val fs: LinuxFS,
    val coreFs: CoreFileSystemService<LinuxFSRunner>,
    val lookupService: FileLookupService<LinuxFSRunner>
)

fun KtorApplicationTestSetupContext.configureServerWithFileController(
    scope: BackgroundScope,
    fsRootInitializer: () -> File = { createDummyFS() },
    fileUpdateAclWhitelist: Set<String> = emptySet(),
    additional: (FileControllerContext) -> List<Controller> = { emptyList() }
): List<Controller> {
    val fsRoot = fsRootInitializer()
    val (runner, fs, aclService) = linuxFSWithRelaxedMocks(fsRoot.absolutePath, scope)
    micro.install(HibernateFeature)
    val coreFs =
        CoreFileSystemService(fs, ClientMock.authenticatedClient, scope, mockedMetadataService)

    val homeFolderService = mockk<HomeFolderService>()
    val callRunner = CommandRunnerFactoryForCalls(runner, WSFileSessionService(runner))
    coEvery { homeFolderService.findHomeFolder(any()) } coAnswers { homeDirectory(it.invocation.args.first() as String) }

    val ctx = FileControllerContext(
        client = micro.client,
        authenticatedClient = AuthenticatedClient(micro.client, OutgoingHttpCall) {},
        fsRoot = fsRoot.absolutePath,
        runner = runner,
        fs = fs,
        coreFs = coreFs,
        lookupService = FileLookupService(runner, coreFs)
    )

    with(ctx) {
        micro.server.configureControllers(
            ActionController(
                callRunner,
                coreFs,
                lookupService
            ),

            LookupController(
                callRunner,
                lookupService,
                homeFolderService
            ),

            FileSecurityController(
                callRunner,
                aclService,
                coreFs,
                fileUpdateAclWhitelist
            )
        )
    }

    return additional(ctx)
}

private val mapper = jacksonObjectMapper()

fun TestApplicationEngine.call(
    method: HttpMethod,
    url: String,
    params: Map<String, String> = emptyMap(),
    rawBody: String? = null,
    jsonBody: Any? = null,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    val fullUrl =
        "$url?" + params.entries.joinToString("&") { entry -> "${entry.key}=${entry.value}" }

    return handleRequest(method, fullUrl) {
        addHeader("Job-Id", UUID.randomUUID().toString())
        addHeader("X-No-Load", "true")
        setUser(user, role)

        if (rawBody != null) {
            setBody(rawBody)
            assert(jsonBody == null)
        } else if (jsonBody != null) {
            setBody(mapper.writeValueAsBytes(jsonBody))
        }
    }.response
}

fun TestApplicationEngine.move(
    path: String,
    newPath: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/move",
        mapOf("path" to path, "newPath" to newPath),
        user = user,
        role = role
    )
}

@Deprecated("Replaced with new testing strategy")
fun TestApplicationEngine.stat(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Get, "/api/files/stat",
        mapOf("path" to path),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.makeDir(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/directory",
        rawBody = """{ "path": "$path" }""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.delete(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Delete,
        "/api/files",
        rawBody = """{ "path": "$path" }""",
        user = user,
        role = role
    )
}

@Deprecated("Replaced with new testing")
fun TestApplicationEngine.listDir(
    path: String,
    user: String = "user1",
    role: Role = Role.USER,
    attributes: Set<StorageFileAttribute>? = null,
    sortBy: FileSortBy? = null
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files",
        params = run {
            val attribMap = if (attributes != null) {
                mapOf("attributes" to attributes.joinToString(",") { it.name })
            } else {
                emptyMap()
            }

            val sortByMap = if (sortBy != null) {
                mapOf("sortBy" to sortBy.name)
            } else {
                emptyMap()
            }

            mapOf("path" to path) + attribMap + sortByMap
        },
        user = user,
        role = role
    )
}

fun TestApplicationEngine.lookupFileInDirectory(
    path: String,
    itemsPerPage: Int,
    sortBy: FileSortBy,
    order: SortOrder,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files/lookup",
        params = mapOf(
            "path" to path,
            "itemsPerPage" to itemsPerPage.toString(),
            "sortBy" to sortBy.toString(),
            "order" to order.toString()
        ),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.copy(
    path: String,
    newPath: String,
    conflictPolicy: WriteConflictPolicy,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/copy",
        params = mapOf("path" to path, "newPath" to newPath, "policy" to conflictPolicy.toString()),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.extract(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/extract",
        rawBody = """{ "path" : "$path"}""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.findHome(
    username: String,
    user: String = "user1",
    role: Role = Role.ADMIN
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files/homeFolder",
        params = mapOf(
            "username" to username
        ),
        user = user,
        role = role
    )
}

fun KtorApplicationTestContext.updateAcl(
    request: UpdateAclRequest,
    user: SecurityPrincipal
): TestApplicationCall {
    return sendJson(
        HttpMethod.Post,
        "/api/files/update-acl",
        request,
        user
    )
}

fun KtorApplicationTestContext.stat(
    request: StatRequest,
    user: SecurityPrincipal = TestUsers.user
): TestApplicationCall {
    return sendRequest(
        HttpMethod.Get,
        "/api/files/stat",
        user,
        mapOf("path" to request.path) +
                (if (request.attributes != null) mapOf("attributes" to request.attributes!!) else emptyMap()),
        configure = {
            addHeader("X-No-Load", "true")
        }
    )
}

fun KtorApplicationTestContext.lookup(
    request: LookupFileInDirectoryRequest,
    user: SecurityPrincipal = TestUsers.user
): TestApplicationCall {
    return sendRequest(
        HttpMethod.Get,
        "/api/files/lookup",
        user,
        mapOf<String, String?>(
            "path" to request.path,
            "itemsPerPage" to request.itemsPerPage.toString(),
            "sortBy" to request.sortBy.toString(),
            "order" to request.order.toString(),
            "attributes" to request.attributes
        ).removeNullValues(),
        configure = {
            addHeader("X-No-Load", "true")
        }
    )
}

fun KtorApplicationTestContext.listDirectory(
    request: ListDirectoryRequest,
    user: SecurityPrincipal = TestUsers.user
): TestApplicationCall {
    return sendRequest(
        HttpMethod.Get,
        "/api/files",
        user,
        mapOf<String, String?>(
            "path" to request.path,
            "itemsPerPage" to request.itemsPerPage?.toString(),
            "page" to request.page?.toString(),
            "order" to request.order?.toString(),
            "sortBy" to request.sortBy?.toString(),
            "attributes" to request.attributes
        ).removeNullValues(),
        configure = {
            addHeader("X-No-Load", "true")
        }
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, String?>.removeNullValues(): Map<String, String> {
    return filterValues { it != null } as Map<String, String>
}

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    val token = TokenValidationMock.createTokenForUser(username, role)
    addHeader(HttpHeaders.Authorization, "Bearer $token")
}

