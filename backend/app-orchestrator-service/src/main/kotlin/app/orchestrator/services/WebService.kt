package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersRequest
import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersResponse
import dk.sdu.cloud.app.orchestrator.api.QueryWebParametersResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class WebService(
    private val computationBackendService: ComputationBackendService,
    private val db: DBContext,
    private val jobDao: JobQueryService,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun queryWebParameters(jobId: String, requestedBy: String): QueryInternalWebParametersResponse {
        val (job) = jobDao.find(db, listOf(jobId), requestedBy).single()
        if (job.owner != requestedBy) throw RPCException("Not found", HttpStatusCode.NotFound)

        val backend = computationBackendService.getAndVerifyByName(job.backend)
        return backend.queryInternalWebParameters.call(
            QueryInternalWebParametersRequest(job),
            serviceClient
        ).orThrow()
    }
}

fun QueryInternalWebParametersResponse.exportForEndUser(): QueryWebParametersResponse =
    QueryWebParametersResponse(path)
