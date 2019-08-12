package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.VerifyFileKnowledgeResponse
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class FileLookupTest {
    @Test
    fun `test file lookup`() {
        val mirco = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val fileLookUp = FileLookupService(cloud)

        ClientMock.mockCallSuccess(
            AuthDescriptions.tokenExtension,
            TokenExtensionResponse("token", null, null)
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.stat,
            StorageFile(
                FileType.FILE,
                "path/to/file",
                1234567,
                12345678,
                "user",
                1234,
                listOf(AccessEntry("entity", true, setOf(AccessRight.EXECUTE))),
                SensitivityLevel.PRIVATE,
                false,
                emptySet(),
                "1"
            )
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.verifyFileKnowledge,
            VerifyFileKnowledgeResponse(listOf(true))
        )

        val result = runBlocking {
            fileLookUp.lookupFile("path/to/file", "token", TestUsers.user.username, null)
        }

        assertEquals("1", result.fileId)
    }

    @Test (expected = RPCException::class)
    fun `test file verify Failed`() {
        val mirco = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val fileLookUp = FileLookupService(cloud)

        ClientMock.mockCallSuccess(
            AuthDescriptions.tokenExtension,
            TokenExtensionResponse("token", null, null)
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.verifyFileKnowledge,
            VerifyFileKnowledgeResponse(listOf(false))
        )
        runBlocking {
            fileLookUp.lookupFile("path", "userToken", TestUsers.user.username, null)
        }
    }
}
