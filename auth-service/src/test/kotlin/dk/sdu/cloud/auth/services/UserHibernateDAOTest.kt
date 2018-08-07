package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.PublicPerson
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.every
import io.mockk.mockk
import org.hibernate.NonUniqueObjectException
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserHibernateDAOTest{

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `insert, find and delete`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val email = "test@testmail.com"
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                val userHibernate = UserHibernateDAO()
                userHibernate.insert(session, person)
                assertEquals(email, userHibernate.findById(session, email).id)
                userHibernate.delete(session, email)
                assertNull(userHibernate.findByIdOrNull(session, email))
            }
        }
    }

    @Test
    fun `insert 2 and list all`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val email = "test@testmail.com"
                val email2 = "anotherEmail@test.com"
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                val person2 = PersonUtils.createUserByPassword(
                    "McFirstName McMiddle",
                    "McLastname",
                    email2,
                    Role.USER,
                    "Password1234"
                )

                val userHibernate = UserHibernateDAO()
                userHibernate.insert(session, person)
                userHibernate.insert(session, person2)

                val listOfAll = userHibernate.listAll(session)

                assertEquals(2, listOfAll.size)
                assertEquals(email, listOfAll[0].id)
                assertEquals(email2, listOfAll[1].id)

            }
        }
    }

    @Test (expected = NonUniqueObjectException::class)
    fun `insert 2 with same email`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val email = "test@testmail.com"
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                val person2 = PersonUtils.createUserByPassword(
                    "McFirstName McMiddle",
                    "McLastname",
                    email,
                    Role.ADMIN,
                    "Password1234"
                )
                val userHibernate = UserHibernateDAO()
                userHibernate.insert(session, person)
                userHibernate.insert(session, person2)

            }
        }
    }

    @Test (expected = UserException.NotFound::class)
    fun `delete non existing user`() {
        withDatabase { db ->
            val session = db.openSession()
            val userHibernate = UserHibernateDAO()
            userHibernate.delete(session, "test@testmail.com")
        }
    }

    @Test
    fun `insert WAYF`() {
        withDatabase { db ->

            val auth = mockk<Auth>()

            db.withTransaction { session ->

                val userDao = UserHibernateDAO()
                every { auth.authenticated } returns true
                every { auth.attributes } answers {
                    val h = HashMap<String, List<String>>(10)
                    h.put(AttributeURIs.EduPersonTargetedId, listOf("hello"))
                    h.put("gn", listOf("Firstname"))
                    h.put("sn", listOf("Lastname"))
                    h.put("schacHomeOrganization", listOf("SDU"))
                    h
                }

                val person = PersonUtils.createUserByWAYF(auth)

                userDao.insert(session, person)

                assertEquals("SDU", person.organizationId)

            }
        }
    }

    @Test
    fun `Create Service Entity`() {
        val date = Date()
        val entity = ServiceEntity("_id", Role.SERVICE, date, date)
        assertEquals(date, entity.createdAt)
        assertEquals(date, entity.modifiedAt)

        val principal = entity.toModel()
        assertEquals(ServicePrincipal("_id", Role.SERVICE), principal)
        val backToEntity = principal.toEntity()
        assertTrue(backToEntity.createdAt > entity.createdAt)
        assertTrue(backToEntity.modifiedAt > entity.modifiedAt)
        assertEquals(backToEntity.id, entity.id)
        assertEquals(backToEntity.role, entity.role)

    }

    @Test
    fun `Create Person Entity and use hashcode and equals test`() {
        val date = Date()
        val person1 = PersonEntityByPassword(
            "id",
            Role.USER,
            date,
            date,
            "title",
            "firstname",
            "lastname",
            "phone",
            "orcid",
            ByteArray(2),
            ByteArray(4)
        )
        val person2 = PersonEntityByPassword(
            "id",
            Role.USER,
            date,
            date,
            "title",
            "firstname",
            "lastname",
            "phone",
            "orcid",
            ByteArray(2),
            ByteArray(4)
        )

        assertTrue(person1.equals(person2))

        val hashedPerson = person1.hashCode()
        val hashedPerson2 = person2.hashCode()
        //Same values, so should be same hash
        assertEquals(hashedPerson, hashedPerson2)

    }

    @Test
    fun `create Person by WAYF Entity and transform to model`() {
        val entity = PersonEntityByWAYF(
            "id",
            Role.USER,
            Date(),
            Date(),
            "title",
            "Firstname",
            "lastname",
            "phone",
            "orcid",
            "orgid"
        )
        val model = entity.toModel()
        val backToEntity = model.toEntity()

        assertEquals(entity.id, backToEntity.id)
        assertTrue(entity.createdAt < backToEntity.createdAt)
        assertTrue(entity.modifiedAt < backToEntity.modifiedAt)
    }
}