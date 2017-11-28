package org.esciencecloud.abc.services

import io.ktor.routing.Routing
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.HostInfo
import org.esciencecloud.abc.RPCConfiguration
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.HPCStreams
import org.esciencecloud.abc.processors.MyJobs
import org.esciencecloud.storage.Result

class HPCStore(private val hostname: String, private val port: Int, private val rpc: RPCConfiguration) {
    private var server: KafkaRPCServer? = null
    private val hostInfo = HostInfo(hostname, port)
    private val endpoints = ArrayList<KafkaRPCEndpoint<Any, Any>>()

    private lateinit var streams: KafkaStreams

    private val slurmIdToInternalId = endpoint {
        KafkaRPCEndpoint.simpleEndpoint<Long, String>(
                root = "/slurm",
                table = HPCStreams.SlurmIdToJobId.name,
                keyParser = { KafkaRPCEndpoint.resultFromNullable(it.toLongOrNull()) }
        )
    }

    private val jobToApp = endpoint {
        KafkaRPCEndpoint.simpleEndpoint<String, HPCAppEvent.Pending>(
                root = "/job",
                table = HPCStreams.JobIdToApp.name
        )
    }

    private val jobToStatus = endpoint {
        KafkaRPCEndpoint.simpleEndpoint<String, HPCAppEvent>(
                root = "/status",
                table = HPCStreams.JobIdToStatus.name
        )
    }

    private val recentJobsByUser = endpoint {
        KafkaRPCEndpoint.simpleEndpoint<String, MyJobs>(
                root = "/recent",
                table = HPCStreams.RecentlyCompletedJobs.name
        )
    }

    fun init(streams: KafkaStreams, routing: Routing) {
        if (server != null) throw IllegalStateException("RPC Server already started!")
        this.streams = streams

        val server = KafkaRPCServer(hostname, port, endpoints, streams, rpc.secretToken)
        server.configureExisting(routing)
        this.server = server
    }

    suspend fun querySlurmIdToInternal(slurmId: Long, allowRetries: Boolean = true): Result<String> =
            slurmIdToInternalId.query(streams, hostInfo, rpc.secretToken, slurmId, allowRetries)

    suspend fun queryJobIdToApp(jobId: String, allowRetries: Boolean = true): Result<HPCAppEvent.Pending> =
            jobToApp.query(streams, hostInfo, rpc.secretToken, jobId, allowRetries)

    suspend fun queryJobIdToStatus(jobId: String, allowRetries: Boolean = true): Result<HPCAppEvent> =
            jobToStatus.query(streams, hostInfo, rpc.secretToken, jobId, allowRetries)

    suspend fun queryRecentJobsByUser(user: String, allowRetries: Boolean = true): Result<MyJobs> =
            recentJobsByUser.query(streams, hostInfo, rpc.secretToken, user, allowRetries)

    private inline fun <K : Any, V : Any> endpoint(producer: () -> KafkaRPCEndpoint<K, V>): KafkaRPCEndpoint<K, V> {
        val endpoint = producer()
        @Suppress("UNCHECKED_CAST")
        endpoints.add(endpoint as KafkaRPCEndpoint<Any, Any>)
        return endpoint
    }
}
