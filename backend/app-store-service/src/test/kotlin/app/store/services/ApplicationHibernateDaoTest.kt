package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.app.store.util.withTool
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.hibernate.exception.GenericJDBCException
import org.junit.Test
import java.lang.Exception
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationHibernateDaoTest {
    private val user = TestUsers.user

    @Test
    fun `create, find, update test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, normAppDesc)

                run {
                    // Load from page
                    val hits = appDAO.findAllByName(it, user, "name", NormalizedPaginationRequest(10, 0))
                    val loadedApp = hits.items.first().metadata.description

                    assertEquals("app description", loadedApp)
                    assertEquals(1, hits.itemsInTotal)
                }

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }

                appDAO.updateDescription(it, user, "name", "2.2", null, listOf("New Authors"))

                run {
                    // Load from specific version after another update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("New Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test
    fun `test find by name and version user`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, normAppDesc)


                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `test find by name and version user - notfound`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }
            }
        }
    }

    @Test
    fun `test creating different versions`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val version1 = normAppDesc.withNameAndVersion("app", "v1")
                val version2 = normAppDesc.withNameAndVersion("app", "v2")

                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, version1)
                Thread.sleep(1000) // Wait a bit to make sure they get different createdAt
                appDAO.create(it, user, version2)

                val allListed = appDAO.listLatestVersion(it, user, NormalizedPaginationRequest(10, 0))
                assertEquals(1, allListed.itemsInTotal)
                assertThatPropertyEquals(allListed.items.single(), { it.metadata.version }, version2.metadata.version)
            }
        }
    }

    @Test
    fun `search test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val applicationA = normAppDesc.withNameAndVersionAndTitle("name1", "1", "AAA")
                val applicationB = normAppDesc.withNameAndVersionAndTitle("name2", "1", "BBB")

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, applicationA)
                appDAO.create(it, user, applicationB)

                run {
                    val searchResult = appDAO.search(it, user, "A", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AAA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "notPossible", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }
                //Spacing searches
                run {
                    val searchResult = appDAO.search(it, user, "AA   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                run {
                    val searchResult = appDAO.search(it, user, "   AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                run {
                    val searchResult =
                        appDAO.search(it, user, "multiple one found AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }

                run {
                    val searchResult =
                        appDAO.search(it, user, "   AA  A Extra    spacing   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA BB", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                    assertEquals(applicationB.metadata.name, searchResult.items.last().metadata.name)

                }

                run {
                    val searchResult = appDAO.search(it, user, "  ", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }

                //multiversion search
                val applicationANewVersion = normAppDesc.withNameAndVersionAndTitle("name1", "2", "AAA")
                appDAO.create(it, user, applicationANewVersion)

                run {
                    val searchResult = appDAO.search(it, user, "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationANewVersion.metadata.title, searchResult.items.first().metadata.title)
                    assertEquals(applicationANewVersion.metadata.version, searchResult.items.first().metadata.version)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA BB", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals(applicationANewVersion.metadata.title, searchResult.items.first().metadata.title)
                    assertEquals(applicationANewVersion.metadata.version, searchResult.items.first().metadata.version)
                    assertEquals(applicationB.metadata.title, searchResult.items.last().metadata.title)
                    assertEquals(applicationB.metadata.version, searchResult.items.last().metadata.version)
                }
            }
        }
    }

    @Test(expected = ApplicationException.AlreadyExists::class)
    fun `Create - already exists - test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, user, normAppDesc)

            }
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `Create - Not Allowed - test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, TestUsers.user5, normAppDesc)

            }
        }
    }

    @Test(expected = ApplicationException.BadToolReference::class)
    fun `Create - bad tool - test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO(), AclHibernateDao())
                appDAO.create(it, user, normAppDesc)
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `Find by name - not found - test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO(), AclHibernateDao())
                appDAO.findByNameAndVersion(it, user, "name", "version")
            }
        }
    }

    //@Ignore // Code only works in postgres
    @Test
    fun `tagSearch test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                val commonTag = "common"
                val appA = normAppDesc.withNameAndVersionAndTitle("A", "1", "Atitle")
                val appB = normAppDesc.withNameAndVersionAndTitle("B", "1", "Btitle")

                appDAO.create(it, user, appA)
                appDAO.create(it, user, appB)

                appDAO.createTags(it, user, appA.metadata.name, listOf(commonTag, "A1", "A2"))
                appDAO.createTags(it, user, appB.metadata.name, listOf(commonTag, "B1", "B2"))

                run {
                    // Search for no hits
                    val hits = appDAO.searchTags(it, user, listOf("tag20"), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }

                run {
                    // Search for one hit tag
                    val hits = appDAO.searchTags(it, user, listOf("A1"), NormalizedPaginationRequest(10, 0))

                    val result = hits.items.single().metadata

                    assertEquals(1, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, result.name)
                    assertEquals(appA.metadata.version, result.version)
                }

                run {
                    // Search for multiple hit tag
                    val hits = appDAO.searchTags(it, user, listOf(commonTag), NormalizedPaginationRequest(10, 0))

                    assertEquals(2, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, hits.items[0].metadata.name)
                    assertEquals(appB.metadata.name, hits.items[1].metadata.name)
                }

                run {
                    // Search for empty tag. Should be empty since it is not a wildcard search
                    val hits = appDAO.searchTags(it, user, listOf(""), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }
            }
        }
    }

    @Test
    fun `Favorite test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                val userA = TestUsers.user.copy(username = "userA")
                val userB = TestUsers.user.copy(username = "userB")

                val aVersion1 = normAppDesc.withNameAndVersion("A", "v1")
                val aVersion2 = normAppDesc.withNameAndVersion("A", "v2")
                val bVersion1 = normAppDesc.withNameAndVersion("B", "v1")

                appDAO.create(it, user, aVersion1)
                Thread.sleep(100) // Ensure different createdAt
                appDAO.create(it, user, aVersion2)
                appDAO.create(it, user, bVersion1)

                listOf(userA, userB).forEach { currentUser ->
                    run {
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(0, favorites.itemsInTotal)
                    }

                    run {
                        appDAO.toggleFavorite(it, currentUser, aVersion1.metadata.name, aVersion1.metadata.version)
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(1, favorites.itemsInTotal)
                    }

                    run {
                        appDAO.toggleFavorite(it, currentUser, aVersion2.metadata.name, aVersion2.metadata.version)
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(2, favorites.itemsInTotal)
                    }
                }
            }
        }
    }

    @Test(expected = ApplicationException.BadApplication::class)
    fun `Favorite test - Not an app`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                appDAO.toggleFavorite(it, user, "App1", "1.4")
            }
        }
    }

    @Test
    fun `create and delete tags`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                val appA = normAppDesc.withNameAndVersion("A", "1")

                appDAO.create(it, user, appA)
                appDAO.createTags(it, user, appA.metadata.name, listOf("A1", "A2"))
                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, "A2")
                    val nottag = appDAO.findTag(it, appA.metadata.name, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNull(nottag)

                    val tags = appDAO.findTagsForApp(it, appA.metadata.name)
                    assertEquals(2, tags.size)
                    assertEquals("A1", tags.first().tag)
                    assertEquals("A2", tags.last().tag)
                }

                appDAO.createTags(it, user, appA.metadata.name, listOf("A3"))

                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, "A2")
                    val tag3 = appDAO.findTag(it, appA.metadata.name, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNotNull(tag3)
                }

                appDAO.deleteTags(it, user, appA.metadata.name, listOf("A1", "A3"))
                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, "A2")
                    val tag3 = appDAO.findTag(it, appA.metadata.name, "A3")

                    assertNull(tag1)
                    assertNotNull(tag2)
                    assertNull(tag3)
                }
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `create tag for invalid app`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.createTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `delete tag for invalid app`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)
                appDAO.deleteTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test
    fun `find latest by tool`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val toolDao = ToolHibernateDAO()
        val aclDao = AclHibernateDao()
        val appDao = ApplicationHibernateDAO(toolDao, aclDao)
        val t1 = "tool1"
        val t2 = "tool2"
        val version = "1"

        runBlocking {
            db.withTransaction { session ->
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t1, version)))
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t2, version)))

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "1").withTool(t1, version))
                Thread.sleep(250)
                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "2").withTool(t1, version))

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("b", "1").withTool(t2, version))
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, t1, PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 1 }
                assertThatInstance(page) { it.items.single().metadata.name == "a" }
                assertThatInstance(page) { it.items.single().metadata.version == "2" }
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, t2, PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 1 }
                assertThatInstance(page) { it.items.single().metadata.name == "b" }
                assertThatInstance(page) { it.items.single().metadata.version == "1" }
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, "tool3", PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 0 }
            }
        }
    }

    @Test
    fun `Find by supported file ext test CC only`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

                try {
                    appDAO.findBySupportedFileExtension(
                        it,
                        TestUsers.admin,
                        setOf("kt")
                    )
                } catch (ex: Exception) {
                    //Do nothing
                }
            }
        }
    }

    @Test
    fun `Prepare page for user - no user test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )
                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )
                val page = appDAO.findLatestByTool(
                    it,
                    TestUsers.admin,
                    normToolDesc.info.name,
                    NormalizedPaginationRequest(10, 0)
                )

                val preparedPage = appDAO.preparePageForUser(it, null, page)

                assertEquals(1, preparedPage.itemsInTotal)
            }
        }
    }

    @Test
    fun `Create and Delete Logo test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                runBlocking {
                    val logo = appDAO.fetchLogo(it, "name")
                    assertNull(logo)
                }

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

                runBlocking {
                    val logo = appDAO.fetchLogo(it, "name")
                    assertNull(logo)
                }

                appDAO.createLogo(it, TestUsers.admin, "name", ByteArray(1024))

                runBlocking {
                    val logo = appDAO.fetchLogo(it, "name")
                    assertNotNull(logo)
                }

                appDAO.clearLogo(it, TestUsers.admin, "name")

                runBlocking {
                    val logo = appDAO.fetchLogo(it, "name")
                    assertNull(logo)
                }
            }
        }
    }

    @Test
    fun `Create Logo - forbidden`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

                try {
                    appDAO.createLogo(it, TestUsers.user, "name", ByteArray(1024))
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 403) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Delete Logo - forbidden`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

                try {
                    appDAO.clearLogo(it, TestUsers.user, "name")
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 403) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Create Logo - NotFound`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                try {
                    appDAO.createLogo(it, TestUsers.user, "name", ByteArray(1024))
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 404) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Delete Logo - NotFound`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                try {
                    appDAO.clearLogo(it, TestUsers.user, "name")
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 404) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Find all by ID test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anothername", "1.1")
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc
                )

                appDAO.getAllApps(it, TestUsers.admin).forEach { app -> println(app.id) }

                val ids1 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    listOf(EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(1, ids1.size)

                val ids2 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    listOf(EmbeddedNameAndVersion("name", "2.2"), EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(2, ids2.size)

                val ids3 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    listOf(EmbeddedNameAndVersion("name", "WRONG"), EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(1, ids3.size)
            }
        }
    }

    @Test
    fun `find all by IDs - no ids given`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appDAO = ApplicationHibernateDAO(toolDAO, aclDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anotherName", "1.1")
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc
                )

                val results = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    emptyList(),
                    NormalizedPaginationRequest(10, 0)
                )

                assertTrue(results.isEmpty())
            }
        }
    }
}
