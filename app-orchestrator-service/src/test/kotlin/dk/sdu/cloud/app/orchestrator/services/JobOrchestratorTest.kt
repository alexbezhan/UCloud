package dk.sdu.cloud.app.orchestrator.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.orchestrator.utils.normTool
import dk.sdu.cloud.app.orchestrator.utils.normToolDesc
import dk.sdu.cloud.app.orchestrator.utils.startJobRequest
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class JobOrchestratorTest {

    private val decodedJWT = mockk<DecodedJWT>(relaxed = true).also {
        every { it.subject } returns "user"
    }

    private val client = ClientMock.authenticatedClient
    private lateinit var backend: NamedComputationBackendDescriptions
    private lateinit var orchestrator: JobOrchestrator<HibernateSession>
    private lateinit var streamFollowService: StreamFollowService<HibernateSession>

    @BeforeTest
    fun init() {
        OrchestrationScope.reset()
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        val toolDao = mockk<ToolStoreService>()
        val appDao = mockk<AppStoreService>()
        val jobDao = JobHibernateDao(appDao, toolDao, tokenValidation)
        val backendName = "backend"
        val compBackend = ComputationBackendService(listOf(ApplicationBackend(backendName)), true)

        coEvery { appDao.findByNameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version) } returns normAppDesc
        coEvery { toolDao.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version) } returns normTool

        val jobFileService = JobFileService(client)
        val orchestrator = JobOrchestrator(
            client,
            EventServiceMock.createProducer(AccountingEvents.jobCompleted),
            db,
            JobVerificationService(appDao, toolDao, tokenValidation, backendName, SharedMountVerificationService()),
            compBackend,
            jobFileService,
            jobDao,
            backendName
        )

        backend = compBackend.getAndVerifyByName(backendName)
        ClientMock.mockCallSuccess(
            backend.jobVerified,
            Unit
        )

        ClientMock.mockCallSuccess(
            backend.jobPrepared,
            Unit
        )

        ClientMock.mockCallSuccess(
            backend.cleanup,
            Unit
        )

        this.orchestrator = orchestrator
        this.streamFollowService = StreamFollowService(jobFileService, client, compBackend, db, jobDao)
    }

    fun setup(): JobOrchestrator<HibernateSession> = orchestrator

    @Test
    fun `orchestrator start job, handle proposed state, lookup test `() = runBlocking {
        val orchestrator = setup()

        val returnedID =
            orchestrator.startJob(
                startJobRequest,
                decodedJWT,
                client
            )

        orchestrator.handleProposedStateChange(
            JobStateChange(returnedID, JobState.PREPARED),
            "newStatus",
            TestUsers.user
        )

        // Same state for branch check
        orchestrator.handleProposedStateChange(
            JobStateChange(returnedID, JobState.FAILURE),
            "newFAILStatus",
            TestUsers.user
        )

        orchestrator.handleProposedStateChange(
            JobStateChange(returnedID, JobState.FAILURE),
            "newStatus",
            TestUsers.user
        )

        val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
        assertEquals("newFAILStatus", job.status)

        orchestrator.handleAddStatus(returnedID, "Status Is FAIL", TestUsers.user)

        val jobAfterStatusChange = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
        assertEquals("Status Is FAIL", jobAfterStatusChange.status)

        // Checking bad transition - Prepared -> Validated not legal
        try {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.VALIDATED),
                "newerStatus",
                TestUsers.user
            )
        } catch (ex: JobException.BadStateTransition) {
            println("Caught Expected Exception")
        }

        orchestrator.removeExpiredJobs()
    }

    @Test
    fun `orchestrator handle job complete fail and success test`() {
        val orchestrator = setup()

        //Success
        runBlocking {
            val returnedID = run {
                orchestrator.startJob(
                    startJobRequest,
                    decodedJWT,
                    client
                )
            }

            run {
                orchestrator.handleJobComplete(
                    returnedID,
                    SimpleDuration(1, 0, 0),
                    true,
                    TestUsers.user
                )
            }

            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.SUCCESS, job.currentState)
        }

        //failed
        runBlocking {
            val returnedID = run {
                orchestrator.startJob(
                    startJobRequest,
                    decodedJWT,
                    client
                )
            }

            run {
                orchestrator.handleJobComplete(
                    returnedID,
                    SimpleDuration(1, 0, 0),
                    false,
                    TestUsers.user
                )
            }

            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.FAILURE, job.currentState)
        }
    }

    @Test
    fun `handle incoming files`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/")
        )

        ClientMock.mockCallSuccess(
            MultiPartUploadDescriptions.simpleUpload,
            Unit
        )
        ClientMock.mockCallSuccess(
            FileDescriptions.extract,
            Unit
        )

        runBlocking {
            orchestrator.handleIncomingFile(
                returnedID,
                TestUsers.user,
                "path/to/file",
                1234,
                ByteReadChannel.Empty,
                true
            )
        }
    }

    @Test
    fun `followStreams test`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/")
        )

        ClientMock.mockCallSuccess(
            backend.follow,
            InternalStdStreamsResponse("stdout", 10, "stderr", 10)
        )
        runBlocking {
            val result =
                streamFollowService.followStreams(FollowStdStreamsRequest(returnedID, 0, 0, 0, 0), decodedJWT.subject)
            assertEquals("stdout", result.stdout)
            assertEquals("stderr", result.stderr)
            assertEquals(10, result.stderrNextLine)
        }
    }

    @Test(expected = JobException.BadStateTransition::class)
    fun `test with job exception`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.VALIDATED),
                null,
                TestUsers.user
            )
        }
    }

    @Test(expected = JobException.NotFound::class)
    fun `test with job exception2`() {
        val orchestrator = setup()

        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange("lalala", JobState.VALIDATED),
                null,
                TestUsers.user
            )
        }
    }

    @Test
    fun `Handle cancel of successful job test`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        runBlocking {
            assertEquals(JobState.VALIDATED, orchestrator.lookupOwnJob(returnedID, TestUsers.user).currentState)

            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.SUCCESS),
                null,
                TestUsers.user
            )
        }
        runBlocking {
            assertEquals(JobState.SUCCESS, orchestrator.lookupOwnJob(returnedID, TestUsers.user).currentState)

            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.CANCELING),
                null,
                TestUsers.user
            )
        }

        runBlocking {
            assertEquals(JobState.SUCCESS, orchestrator.lookupOwnJob(returnedID, TestUsers.user).currentState)
        }
    }
}
