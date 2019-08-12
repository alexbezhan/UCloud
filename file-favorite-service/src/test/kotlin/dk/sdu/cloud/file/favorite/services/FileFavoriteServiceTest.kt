package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.favorite.storageFile
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.withDatabase
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileFavoriteServiceTest {
    fun fileStatMock() {
        ClientMock.mockCall(FileDescriptions.stat) { req ->
            val file = when (req.path) {
                "/home/user/1" -> storageFile
                "/home/user/2" -> storageFile.copy(pathOrNull = "/home/user/2", fileIdOrNull = "fileId2")
                "/home/user/3" -> storageFile.copy(pathOrNull = "/home/user/3", fileIdOrNull = "fileId3")
                else -> null
            }
            if (file != null) {
                TestCallResult.Ok(file)
            } else
                TestCallResult.Error(CommonErrorMessage("null file"), HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `test toggle, check, untoggle favorite`() {
        withDatabase { db ->
            val user = TestUsers.user
            val micro = initializeMicro()
            val cloud = ClientMock.authenticatedClient
            val dao = FileFavoriteHibernateDAO()
            val service = FileFavoriteService(db, dao, cloud)

            fileStatMock()

            runBlocking {
                val failures = service.toggleFavorite(
                    listOf("/home/user/1", "/home/user/2", "/home/user/3"),
                    user.createToken(),
                    cloud
                )
                assertTrue(failures.isEmpty())
            }
            val favorites = service.getFavoriteStatus(
                listOf(
                    storageFile,
                    storageFile.copy(pathOrNull = "/home/user/2", fileIdOrNull = "fileId2"),
                    storageFile.copy(pathOrNull = "/home/user/4", fileIdOrNull = "fileId4")
                ),
                user.createToken()
            )

            assertTrue(favorites["fileId"]!!)
            assertTrue(favorites["fileId2"]!!)
            assertFalse(favorites["fileId4"]!!)

            runBlocking {
                val failures = service.toggleFavorite(
                    listOf("/home/user/1", "/home/user/2", "/home/user/3"),
                    user.createToken(),
                    cloud
                )
                assertTrue(failures.isEmpty())
            }

            val favorites2 = service.getFavoriteStatus(
                listOf(
                    storageFile,
                    storageFile.copy(pathOrNull = "/home/user/2", fileIdOrNull = "fileId2"),
                    storageFile.copy(pathOrNull = "/home/user/4", fileIdOrNull = "fileId4")
                ),
                user.createToken()
            )

            assertFalse(favorites2["fileId"]!!)
            assertFalse(favorites2["fileId2"]!!)
            assertFalse(favorites2["fileId4"]!!)

        }
    }

    @Test
    fun `test toggle - stat failed`() {
        withDatabase { db ->
            val user = TestUsers.user
            val micro = initializeMicro()
            val cloud = ClientMock.authenticatedClient
            val dao = FileFavoriteHibernateDAO()
            val service = FileFavoriteService(db, dao, cloud)

            fileStatMock()

            runBlocking {
                val failures = service.toggleFavorite(
                    listOf("/home/user/1", "/home/user/4", "/home/user/5"),
                    user.createToken(),
                    cloud
                )
                assertEquals("/home/user/4", failures.first())
                assertEquals("/home/user/5", failures.last())
            }

            val favorites = service.getFavoriteStatus(
                listOf(
                    storageFile,
                    storageFile.copy(pathOrNull = "/home/user/2", fileIdOrNull = "fileId2")
                ),
                user.createToken()
            )

            assertTrue(favorites["fileId"]!!)
            assertFalse(favorites["fileId2"]!!)
        }
    }

    @Test
    fun `Simple Entity test`() {
        val entity = FavoriteEntity("fileId", "username")
        assertEquals("fileId", entity.fileId)
        assertEquals("username", entity.username)
    }
}


fun SecurityPrincipal.createToken(): SecurityPrincipalToken = SecurityPrincipalToken(
    this,
    listOf(SecurityScope.ALL_WRITE),
    0,
    Long.MAX_VALUE,
    null
)
