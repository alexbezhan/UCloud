package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.JobSortBy
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.SortOrder

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface JobDao<Session> {
    fun create(
        session: Session,
        jobWithToken: VerifiedJobWithAccessToken
    )

    fun updateStateAndStatus(
        session: Session,
        systemId: String,
        state: JobState,
        status: String? = null,
        failedState: JobState? = null
    )

    fun updateStatus(
        session: Session,
        systemId: String,
        status: String
    )

    fun updateWorkspace(
        session: Session,
        systemId: String,
        workspace: String
    )

    suspend fun find(
        session: Session,
        systemIds: List<String>,
        owner: SecurityPrincipalToken? = null
    ): List<VerifiedJobWithAccessToken>

    suspend fun list(
        session: Session,
        owner: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        order: SortOrder = SortOrder.DESCENDING,
        sortBy: JobSortBy = JobSortBy.STARTED_AT,
        minTimestamp: Long? = null,
        maxTimestamp: Long? = null,
        filter: JobState? = null,
        application: String? = null,
        version: String? = null
    ): Page<VerifiedJobWithAccessToken>

    suspend fun findJobsCreatedBefore(
        session: Session,
        timestamp: Long
    ): Sequence<VerifiedJobWithAccessToken>
}

suspend fun <Session> JobDao<Session>.findOrNull(
    session: Session,
    systemId: String,
    owner: SecurityPrincipalToken? = null
): VerifiedJobWithAccessToken? {
    return find(session, listOf(systemId), owner).filter { it.job.id == systemId }.singleOrNull()
}
