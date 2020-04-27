package dk.sdu.cloud.auth.http

import com.fasterxml.jackson.module.kotlin.readValue
import com.warrenstrange.googleauth.GoogleAuthenticator
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AnswerChallengeRequest
import dk.sdu.cloud.auth.api.Create2FACredentialsResponse
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.api.TwoFactorStatusResponse
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.QRService
import dk.sdu.cloud.auth.services.TOTPService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.TwoFactorDAO
import dk.sdu.cloud.auth.services.TwoFactorHibernateDAO
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.services.WSTOTPService
import dk.sdu.cloud.auth.services.ZXingQRService
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwoFactorAuthControllerTest {
    private val passwordHashingService = PasswordHashingService()

    private data class TestContext(
        val loginResponder: LoginResponder<HibernateSession>,
        val twoFactorChallengeService: TwoFactorChallengeService<HibernateSession>,

        val twoFactorDAO: TwoFactorDAO<HibernateSession>,
        val userDAO: UserDAO<HibernateSession>,
        val totpService: TOTPService,
        val qrService: QRService,

        val db: DBSessionFactory<HibernateSession>
    )

    private suspend fun TestContext.withUser(
        principal: Principal = Person.ByPassword(
            "user",
            Role.USER,
            null,
            "user",
            "user",
            null,
            null,
            password = ByteArray(64),
            salt = ByteArray(64),
            twoFactorAuthentication = false,
            serviceLicenseAgreement = 0
        )
    ): Principal {
        db.withTransaction { userDAO.insert(it, principal) }
        return principal
    }

    // TODO Refactor this code.
    private fun runTest(
        twoFactorDAO: TwoFactorDAO<HibernateSession> = TwoFactorHibernateDAO(),
        userDAO: UserDAO<HibernateSession> = UserHibernateDAO(
            passwordHashingService,
            twoFactorDAO
        ),
        totpService: TOTPService = WSTOTPService(),
        qrService: QRService = ZXingQRService(),

        twoFactorChallengeService: (HibernateSessionFactory) -> TwoFactorChallengeService<HibernateSession> = { db ->
            TwoFactorChallengeService(
                db,
                twoFactorDAO,
                userDAO,
                totpService,
                qrService
            )
        },

        consumer: suspend TestApplicationEngine.(TestContext) -> Unit
    ) {
        lateinit var twoFactorService: TwoFactorChallengeService<HibernateSession>
        lateinit var loginResponder: LoginResponder<HibernateSession>

        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                twoFactorService = twoFactorChallengeService(micro.hibernateDatabase)
                loginResponder = LoginResponder(mockk(relaxed = true), twoFactorService)
                listOf(TwoFactorAuthController(twoFactorService, loginResponder))
            },
            test = {
                val ctx = TestContext(
                    loginResponder,
                    twoFactorService,
                    twoFactorDAO,
                    userDAO,
                    totpService,
                    qrService,
                    micro.hibernateDatabase
                )
                runBlocking {
                    engine.consumer(ctx)
                }
            }
        )
    }

    @Test
    fun `test creating credentials`() {
        runTest { ctx ->
            val user = ctx.withUser()

            val parsedResponse = create2FACredentials(user)

            // These are all rather hard to verify. Just ensure they aren't obviously invalid
            assertTrue(parsedResponse.challengeId.length > 1)
            assertTrue(parsedResponse.otpAuthUri.length > 1)
            assertTrue(parsedResponse.qrCodeB64Data.length > 1)
        }
    }

    @Test
    fun `test creating credentials with service user`() {
        runTest { ctx ->
            val user = ctx.withUser(ServicePrincipal("_service", Role.SERVICE))
            val resp = handleRequest(HttpMethod.Post, "/auth/2fa") { setUser(user.id, user.role) }.response

            assertEquals(HttpStatusCode.Unauthorized, resp.status())
        }
    }

    @Test
    fun `test create and answer via JSON`() {
        runTest { ctx ->
            val user = ctx.withUser()

            assertFalse(getStatus(user).connected)

            val twoFactorResp = create2FACredentials(user)

            val auth = GoogleAuthenticator()
            val totp = auth.getTotpPassword(twoFactorResp.secret)

            run {
                val resp = handleRequest(HttpMethod.Post, "/auth/2fa/challenge") {
                    setUser(user.id)
                    setBody(
                        defaultMapper.writeValueAsString(
                            AnswerChallengeRequest(
                                twoFactorResp.challengeId,
                                totp
                            )
                        )
                    )
                }.response

                assertEquals(HttpStatusCode.NoContent, resp.status())
            }

            assertTrue(getStatus(user).connected)
        }
    }

    @Test
    fun `test create and answer via JSON - Bad code`() {
        runTest { ctx ->
            val user = ctx.withUser()

            assertFalse(getStatus(user).connected)

            val twoFactorResp = create2FACredentials(user)

            val auth = GoogleAuthenticator()
            val totp = auth.getTotpPassword(twoFactorResp.secret)

            run {
                val resp = handleRequest(HttpMethod.Post, "/auth/2fa/challenge") {
                    setUser(user.id)
                    setBody(
                        defaultMapper.writeValueAsString(
                            AnswerChallengeRequest(
                                twoFactorResp.challengeId,
                                totp + 1
                            )
                        )
                    )
                }.response

                assertEquals(HttpStatusCode.Unauthorized, resp.status())
            }

            assertFalse(getStatus(user).connected)
        }
    }

    private fun TestApplicationEngine.getStatus(user: Principal): TwoFactorStatusResponse {
        val resp = handleRequest(HttpMethod.Get, "/auth/2fa/status") { setUser(user.id) }.response
        assertEquals(HttpStatusCode.OK, resp.status())
        return defaultMapper.readValue(resp.content!!)
    }

    private fun TestApplicationEngine.create2FACredentials(user: Principal): Create2FACredentialsResponse {
        val resp = handleRequest(HttpMethod.Post, "/auth/2fa") { setUser(user.id) }.response
        assertEquals(HttpStatusCode.OK, resp.status())
        return defaultMapper.readValue(resp.content!!)
    }
}
