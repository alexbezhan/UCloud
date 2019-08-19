package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ParsedApplicationParameter
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.hibernate.ScrollMode
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.slf4j.Logger
import java.util.*
import javax.persistence.*
import javax.persistence.criteria.Predicate
import kotlin.collections.ArrayList

@Entity
@Table(name = "job_information")
data class JobInformationEntity(
    @Id
    @NaturalId
    val systemId: String,

    var owner: String,

    var name: String?,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "name", column = Column(name = "application_name")),
        AttributeOverride(name = "version", column = Column(name = "application_version"))
    )
    var application: EmbeddedNameAndVersion,

    var status: String,

    @Enumerated(EnumType.STRING)
    var state: JobState,

    @Enumerated(EnumType.STRING)
    var failedState: JobState?,

    var nodes: Int,

    var tasksPerNode: Int,

    @Type(
        type = JSONB_MAP_TYPE,
        parameters = [
            Parameter(
                name = JSONB_MAP_PARAM_KEY_TYPE,
                value = "java.lang.String"
            ),
            Parameter(
                name = JSONB_MAP_PARAM_VALUE_TYPE,
                value = "dk.sdu.cloud.app.store.api.ParsedApplicationParameter"
            )
        ]
    )
    var parameters: Map<String, ParsedApplicationParameter?>,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload"
            )
        ]
    )
    var files: List<ValidatedFileForUpload>,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload"
            )
        ]
    )
    var mounts: List<ValidatedFileForUpload>,

    var maxTimeMinutes: Int,

    var maxTimeSeconds: Int,

    var backendName: String,

    @Column(length = 4096)
    var accessToken: String,

    @Column(length = 1024)
    var archiveInCollection: String,

    @Column(length = 1024)
    var workspace: String?,

    var maxTimeHours: Int,

    var startedAt: Date?,

    override var modifiedAt: Date,

    override var createdAt: Date,

    @Column(length = 1024)
    var username: String?,

    @Column(length = 1024)
    var project: String?,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMount"
            )
        ]
    )
    var sharedFileSystemMounts: List<SharedFileSystemMount>?,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ApplicationPeer"
            )
        ]
    )
    var peers: List<ApplicationPeer>?
) : WithTimestamps {

    companion object : HibernateEntity<JobInformationEntity>, WithId<String>
}

class JobHibernateDao(
    private val appStoreService: AppStoreService,
    private val toolStoreService: ToolStoreService,
    private val tokenValidation: TokenValidation<*>
) : JobDao<HibernateSession> {
    override fun create(session: HibernateSession, jobWithToken: VerifiedJobWithAccessToken) {
        val (job, token) = jobWithToken

        val entity = JobInformationEntity(
            job.id,
            job.owner,
            job.name,
            job.application.metadata.toEmbedded(),
            "Verified",
            job.currentState,
            job.failedState,
            job.nodes,
            job.tasksPerNode,
            job.jobInput.asMap(),
            job.files,
            job.mounts,
            job.maxTime.minutes,
            job.maxTime.seconds,
            job.backend,
            token,
            job.archiveInCollection,
            job.workspace,
            job.maxTime.hours,
            null,
            Date(System.currentTimeMillis()),
            Date(System.currentTimeMillis()),
            job.user,
            job.project,
            job.sharedFileSystemMounts,
            job.peers
        )

        session.save(entity)
    }

    override fun updateStatus(session: HibernateSession, systemId: String, status: String) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::status], status)
                criteria.set(entity[JobInformationEntity::modifiedAt], Date(System.currentTimeMillis()))
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override fun updateStateAndStatus(session: HibernateSession, systemId: String, state: JobState, status: String?, failedState: JobState?) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::modifiedAt], Date(System.currentTimeMillis()))
                criteria.set(entity[JobInformationEntity::state], state)
                criteria.set(entity[JobInformationEntity::failedState], failedState)
                if (status != null) {
                    criteria.set(entity[JobInformationEntity::status], status)
                }

                if (state == JobState.RUNNING) {
                    criteria.set(entity[JobInformationEntity::startedAt], Date(System.currentTimeMillis()))
                }
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override fun updateWorkspace(session: HibernateSession, systemId: String, workspace: String) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::workspace], workspace)
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override suspend fun findOrNull(
        session: HibernateSession,
        systemId: String,
        owner: SecurityPrincipalToken?
    ): VerifiedJobWithAccessToken? {
        return JobInformationEntity[session, systemId]
            ?.takeIf {
                owner == null ||
                        it.owner == owner.realUsername() ||
                        (it.project == owner.projectOrNull() && it.state.isFinal())
            }
            ?.toModel(resolveTool = true)
    }

    override suspend fun findJobsCreatedBefore(
        session: HibernateSession,
        timestamp: Long
    ): Sequence<VerifiedJobWithAccessToken> {
        return sequence {
            val scroller = session
                .criteria<JobInformationEntity> {
                    entity[JobInformationEntity::createdAt] lessThan Date(timestamp) and
                            (entity[JobInformationEntity::state] notEqual JobState.SUCCESS) and
                            (entity[JobInformationEntity::state] notEqual JobState.FAILURE)
                }
                .scroll(ScrollMode.FORWARD_ONLY)

            while (scroller.next()) {
                val next = scroller.get(0) as JobInformationEntity
                val value = runBlocking { next.toModel(resolveTool = true) }
                if (value != null) yield(value!!)
            }
        }
    }

    override suspend fun list(
        session: HibernateSession,
        owner: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        order: SortOrder,
        sortBy: JobSortBy,
        minTimestamp: Long?,
        maxTimestamp: Long?,
        filter: JobState?
    ): Page<VerifiedJobWithAccessToken> {
        return session.paginatedCriteria<JobInformationEntity>(
            pagination,
            orderBy = {
                val field = when (sortBy) {
                    JobSortBy.NAME -> JobInformationEntity::name
                    JobSortBy.STATE -> JobInformationEntity::state
                    JobSortBy.APPLICATION -> JobInformationEntity::application
                    JobSortBy.STARTED_AT -> JobInformationEntity::startedAt
                    JobSortBy.LAST_UPDATE -> JobInformationEntity::modifiedAt
                    JobSortBy.CREATED_AT -> JobInformationEntity::createdAt
                }

                when (order) {
                    SortOrder.ASCENDING -> listOf(ascending(entity[field]))
                    SortOrder.DESCENDING -> listOf(descending(entity[field]))
                }
            },
            predicate = {
                val canViewAsOwner = entity[JobInformationEntity::owner] equal owner.realUsername()

                val project = owner.projectOrNull()
                val canViewAsPartOfProject =
                    if (project == null) {
                        literal(false).toPredicate()
                    } else {
                        allOf(
                            entity[JobInformationEntity::project] equal project,
                            anyOf(
                                entity[JobInformationEntity::state] equal JobState.FAILURE,
                                entity[JobInformationEntity::state] equal JobState.SUCCESS
                            )
                        )
                    }

                // Time ranges
                val lowerTime = entity[JobInformationEntity::createdAt] greaterThanEquals Date(minTimestamp ?: 0)
                val upperTime = entity[JobInformationEntity::createdAt] lessThanEquals Date(maxTimestamp ?: Date().time)
                val matchesLowerFilter = literal(minTimestamp == null).toPredicate() or lowerTime
                val matchesUpperFilter = literal(maxTimestamp == null).toPredicate() or upperTime

                // AppState filter
                val appState = entity[JobInformationEntity::state] equal (filter ?: JobState.VALIDATED)
                val appStateFilter = literal(filter == null).toPredicate() or appState

                allOf(
                    matchesLowerFilter,
                    matchesUpperFilter,
                    appStateFilter,
                    anyOf(
                        canViewAsOwner,
                        canViewAsPartOfProject
                    )
                )
            }
        ).mapItemsNotNull { it.toModel() }
    }

    private inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R> {
        val newItems = items.mapNotNull(mapper)
        return Page(
            itemsInTotal,
            itemsPerPage,
            pageNumber,
            newItems
        )
    }

    private suspend fun JobInformationEntity.toModel(
        resolveTool: Boolean = false
    ): VerifiedJobWithAccessToken? {
        val withoutTool = VerifiedJobWithAccessToken(
            VerifiedJob(
                appStoreService.findByNameAndVersion(application.name, application.version)
                    ?: return null, // return null in case application no longer exists (issue #915)
                name,
                files,
                systemId,
                owner,
                nodes,
                tasksPerNode,
                SimpleDuration(maxTimeHours, maxTimeMinutes, maxTimeSeconds),
                VerifiedJobInput(parameters),
                backendName,
                state,
                status,
                failedState,
                archiveInCollection,
                tokenValidation.validateAndDecodeOrNull(accessToken)?.principal?.uid
                    ?: Long.MAX_VALUE, // TODO This is a safe value to map to, but we shouldn't just map it to long max
                workspace,
                createdAt = createdAt.time,
                modifiedAt = modifiedAt.time,
                _mounts = mounts,
                startedAt = startedAt?.time,
                user = username ?: owner,
                project = project,
                _sharedFileSystemMounts = sharedFileSystemMounts,
                _peers = peers
            ),
            accessToken
        )

        if (!resolveTool) return withoutTool

        val toolReference = withoutTool.job.application.invocation.tool
        val tool =
            toolStoreService.findByNameAndVersion(toolReference.name, toolReference.version)

        return withoutTool.copy(
            job = withoutTool.job.copy(
                application = withoutTool.job.application.copy(
                    invocation = withoutTool.job.application.invocation.copy(
                        tool = ToolReference(toolReference.name, toolReference.version, tool)
                    )
                )
            )
        )
    }

    private fun NameAndVersion.toEmbedded(): EmbeddedNameAndVersion = EmbeddedNameAndVersion(name, version)

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
