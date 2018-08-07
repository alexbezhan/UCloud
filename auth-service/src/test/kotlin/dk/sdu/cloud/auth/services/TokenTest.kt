package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dk.sdu.cloud.auth.api.RequestAndRefreshToken
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.*


class TokenTest{

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `create and register test`() {
        withDatabase { db ->
            val email = "test@testmail.com"
            db.withTransaction { session ->

                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )

                val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                val userDao = UserHibernateDAO()
                val refreshTokenDao = RefreshTokenHibernateDAO()
                val tokenService = TokenService(
                    db,
                    userDao,
                    refreshTokenDao,
                    jwtAlg,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true)
                )

                val jwtParser = JWT.require(jwtAlg).build()

                val result = tokenService.createAndRegisterTokenFor(person)
                val parsedJwt = jwtParser.verify(result.accessToken)
                assertEquals(email, parsedJwt.subject)

                assertTrue(result.refreshToken.isNotEmpty())
            }
        }
    }

    @Test
    fun `process SAML auth test - User not found in system, create new WAYF `() {
        withAuthMock {
            withDatabase { db ->
                val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                val userDao = UserHibernateDAO()
                val refreshTokenDao = RefreshTokenHibernateDAO()
                val tokenService = TokenService(
                    db,
                    userDao,
                    refreshTokenDao,
                    jwtAlg,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true)
                )
                val auth = mockk<Auth>()
                every { auth.authenticated } returns true
                every { auth.attributes } answers {
                    val h = HashMap<String, List<String>>(10)
                    h.put(AttributeURIs.EduPersonTargetedId, listOf("hello"))
                    h.put("gn", listOf("Firstname"))
                    h.put("sn", listOf("Lastname"))
                    h.put("schacHomeOrganization", listOf("SDU"))
                    h

                }
                val result = tokenService.processSAMLAuthentication(auth)
                assertEquals("SDU", result?.organizationId)
                assertEquals("Firstname", result?.firstNames)
                assertEquals("Lastname", result?.lastName)

            }
        }
    }

    @Test
    fun `process SAML auth test - missing EDU Person Target ID`() {
        withDatabase { db ->
            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()
            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
            val auth = mockk<Auth>()
            every { auth.authenticated } returns true
            every { auth.attributes} returns HashMap(1)
            assertNull(tokenService.processSAMLAuthentication(auth))

        }
    }

    @Test
    fun `process SAML auth test - not authenticated`() {
        withDatabase { db ->
            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()
            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
            val auth = mockk<Auth>()
            every { auth.authenticated } returns false
            assertNull(tokenService.processSAMLAuthentication(auth))

        }
    }

    @Test
    fun `request one time token test`() {
        withAuthMock {
            withDatabase { db ->
                val email = "test@testmail.com"
                val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                val userDao = UserHibernateDAO()
                val refreshTokenDao = RefreshTokenHibernateDAO()
                val tokenService = TokenService(
                    db,
                    userDao,
                    refreshTokenDao,
                    jwtAlg,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true)
                )
                db.withTransaction { session ->
                    val person = PersonUtils.createUserByPassword(
                        "FirstName Middle",
                        "Lastname",
                        email,
                        Role.ADMIN,
                        "ThisIsMyPassword"
                    )
                    userDao.insert(session, person)
                }

                val jwtParser = JWT.require(jwtAlg).build()

                val result = tokenService.requestOneTimeToken(email, "Audience")

                val parsedJwt = jwtParser.verify(result.accessToken)
                assertEquals(email, parsedJwt.subject)

                assertTrue(result.jti.isNotEmpty())

            }
        }
    }

    @Test
    fun `refresh test`() {
        withDatabase { db ->
            val email = "test@testmail.com"
            val token = "token"

            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()
            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )

            db.withTransaction { session ->
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                userDao.insert(session, person)

                val refreshTAU = RefreshTokenAndUser(email, token)
                val refreshHibernateTAU = RefreshTokenHibernateDAO()
                refreshHibernateTAU.insert(session, refreshTAU)

            }
            val result = tokenService.refresh(token)

            val jwtParser = JWT.require(jwtAlg).build()

            val parsedJwt = jwtParser.verify(result.accessToken)
            assertEquals(email, parsedJwt.subject)

            assertTrue(result.accessToken.isNotEmpty())
        }
    }

    @Test (expected = TokenService.RefreshTokenException.InvalidToken::class)
    fun `refresh test - not valid token`() {
        withDatabase { db ->
            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()
            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
            tokenService.refresh("not a token")
        }
    }

    @Test
    fun `logout test `() {
        withDatabase { db ->
            val email = "test@testmail.com"
            val token = "token"

            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()

            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )

            db.withTransaction { session ->
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                userDao.insert(session, person)

                val refreshTAU = RefreshTokenAndUser(email, token)
                refreshTokenDao.insert(session, refreshTAU)
            }

            tokenService.logout(token)

        }
    }

    @Test (expected = TokenService.RefreshTokenException.InvalidToken::class)
    fun `logout test - not valid token`() {
        withDatabase { db ->
            val jwtAlg = JWTAlgorithm.HMAC256("foobar")
            val userDao = UserHibernateDAO()
            val refreshTokenDao = RefreshTokenHibernateDAO()
            val tokenService = TokenService(
                db,
                userDao,
                refreshTokenDao,
                jwtAlg,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
            tokenService.logout("not a token")
        }
    }
}