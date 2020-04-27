package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.ApplicationPeer
import dk.sdu.cloud.calls.RPCException
import io.fabric8.kubernetes.api.model.HostAlias
import io.ktor.http.HttpStatusCode

class HostAliasesService(private val k8: K8Dependencies) {
    fun findAliasesForPeers(peers: Collection<ApplicationPeer>): List<HostAlias> {
        return peers.flatMap { findAliasesForRunningJob(it.jobId, it.name) }
    }

    private fun findAliasesForRunningJob(jobId: String, name: String): List<HostAlias> {
        if (!name.matches(hostNameRegex)) throw RPCException("Bad hostname specified", HttpStatusCode.BadRequest)


        return k8.nameAllocator.listPods(jobId)
            .map {
                val ipAddress = it.status.podIP
                val rank = it.metadata.labels[K8NameAllocator.RANK_LABEL]!!.toInt()

                val hostnames = if (rank == 0) listOf(name, "$name-0") else listOf("$name-$rank")

                HostAlias(
                    hostnames,
                    ipAddress
                )
            }
    }

    companion object {
        private val hostNameRegex =
            Regex(
                "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
                        "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$"
            )
    }
}
