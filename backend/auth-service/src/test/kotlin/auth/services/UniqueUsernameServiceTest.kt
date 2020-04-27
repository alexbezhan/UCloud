package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.Ignore

class UniqueUsernameServiceTest {
    private lateinit var service: UniqueUsernameService<HibernateSession>
    private lateinit var userDao: UserDAO<HibernateSession>
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var personService: PersonService
    private lateinit var personTemplate: Person.ByPassword

    @BeforeTest
    fun initTests() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val passwordHashingService = PasswordHashingService()
        db = micro.hibernateDatabase
        userDao = UserHibernateDAO(passwordHashingService, TwoFactorHibernateDAO())
        service = UniqueUsernameService(db, userDao)
        personService = PersonService(passwordHashingService, service)

        personTemplate = personService.createUserByPassword(
            "Dan",
            "Thrane",
            "dthrane@foo.dkl",
            Role.ADMIN,
            "password",
            "dthrane@foo.dkl"
        )
    }

    @Test
    fun `generate a single username`(): Unit = runBlocking {
        val prefix = "DanThrane"
        val id = service.generateUniqueName(prefix)
        db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
        assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
    }

    @Test
    fun `generate 1000 usernames`(): Unit = runBlocking {
        val prefix = "DanThrane"
        repeat(1000) {
            val id = service.generateUniqueName(prefix)
            println(id)
            db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
            assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
        }
    }

    @Ignore
    @Test
    fun `generate 11000 usernames`(): Unit = runBlocking {
        val prefix = "DanThrane"
        repeat(11000) {
            val id = service.generateUniqueName(prefix)
            println(id)
            db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
            assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
        }
    }
}
