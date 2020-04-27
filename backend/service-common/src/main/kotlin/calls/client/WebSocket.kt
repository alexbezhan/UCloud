package dk.sdu.cloud.calls.client

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.calls.WSRequest
import dk.sdu.cloud.calls.websocket
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.ClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.webSocketRawSession
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.http.fullPath
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class OutgoingWSCall : OutgoingCall {
    override val attributes = AttributeContainer()

    companion object : OutgoingCallCompanion<OutgoingWSCall> {
        override val klass = OutgoingWSCall::class
        override val attributes = AttributeContainer()

        internal val SUBSCRIPTION_HANDLER_KEY = AttributeKey<suspend (Any) -> Unit>("ws-subscription-handler")
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
class OutgoingWSRequestInterceptor : OutgoingRequestInterceptor<OutgoingWSCall, OutgoingWSCall.Companion> {
    override val companion = OutgoingWSCall
    private val connectionPool = WSConnectionPool()
    private val streamId = AtomicInteger()
    private val sampleCounter = AtomicInteger(0)

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): OutgoingWSCall {
        return OutgoingWSCall()
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingWSCall
    ): IngoingCallResponse<S, E> {
        val callId = Random.nextInt(10000) // Non unique ID for logging
        val start = System.currentTimeMillis()
        val shortRequestMessage = request.toString().take(100)
        val streamId = streamId.getAndIncrement().toString()
        val shouldSample = sampleCounter.incrementAndGet() % SAMPLE_FREQUENCY == 0

        val url = run {
            val targetHost = ctx.attributes.outgoingTargetHost
            val host = targetHost.host.removeSuffix("/")

            // For some reason ktor's websocket client does not currently work when pointed at WSS, but works fine
            // when redirected from WS to WSS.
            val port = targetHost.takeIf { it.port != 443 }?.port ?: 80
            val scheme = "ws"

            val path = call.websocket.path.removePrefix("/")

            "$scheme://$host:$port/$path"
        }

        run {
            val requestDebug = "[$callId] -> ${call.fullName}: $shortRequestMessage"
            if (shouldSample) log.info(requestDebug)
            else log.debug(requestDebug)
        }

        val session = connectionPool.retrieveConnection(url)
        val wsRequest = WSRequest(
            call.fullName,
            streamId,
            request,
            bearer = ctx.attributes.outgoingAuthToken,
            causedBy = ctx.causedBy,
            project = ctx.project
        )
        val writer = defaultMapper.writerFor(
            defaultMapper.typeFactory.constructParametricType(
                WSRequest::class.java,
                defaultMapper.typeFactory.constructType(call.requestType)
            )
        )
        val subscription = session.subscribe(streamId)
        val text = writer.writeValueAsString(wsRequest)
        session.underlyingSession.outgoing.send(Frame.Text(text))

        val handler = ctx.attributes.getOrNull(OutgoingWSCall.SUBSCRIPTION_HANDLER_KEY)

        val response = coroutineScope {
            lateinit var response: WSMessage.Response<Any?>
            val channel = processMessagesFromStream(call, subscription, streamId)

            try {
                while (!channel.isClosedForReceive) {
                    val message = channel.receive()
                    if (message is WSMessage.Response) {
                        val responseDebug =
                            "[$callId] <- ${call.fullName} RESPONSE ${System.currentTimeMillis() - start}ms"

                        if (message.status in 400..599 || shouldSample) log.info(responseDebug)
                        else log.debug(responseDebug)

                        response = message
                        session.unsubscribe(streamId)
                        break
                    } else if (message is WSMessage.Message && handler != null) {
                        log.debug("[$callId] <- ${call.fullName} MESSAGE ${System.currentTimeMillis() - start}ms")
                        handler(message.payload!!)
                    }
                }
            } catch (ex: Throwable) {
                runCatching {
                    // Make sure the underlying session is also closed. Otherwise we risk that the connection
                    // pool won't renew this session.
                    session.underlyingSession.close(cause = ex)
                }

                if (ex is ClosedReceiveChannelException || ex is CancellationException) {
                    // Do nothing. It is expected that the channel will close down.
                    log.info("Channel was closed")
                    response = WSMessage.Response(streamId, null, HttpStatusCode.BadGateway.value)
                } else {
                    throw ex
                }
            }

            response
        }

        @Suppress("UNCHECKED_CAST")
        return when (response.status) {
            in 100..399 -> IngoingCallResponse.Ok(response.payload as S, HttpStatusCode.fromValue(response.status))
            else -> IngoingCallResponse.Error(response.payload as E?, HttpStatusCode.fromValue(response.status))
        }
    }

    private fun CoroutineScope.processMessagesFromStream(
        call: CallDescription<*, *, *>,
        channel: ReceiveChannel<WSRawMessage>,
        streamId: String
    ): ReceiveChannel<WSMessage<Any?>> = produce {
        val successReader = defaultMapper.readerFor(call.successType)
        val errorReader = defaultMapper.readerFor(call.errorType)

        for (message in channel) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val result = when (message.type) {
                WSMessage.MESSAGE_TYPE -> {
                    val payload = successReader.readValue<Any>(message.payload)
                    WSMessage.Message<Any>(streamId, payload)
                }

                WSMessage.RESPONSE_TYPE -> {
                    val reader = when (message.status) {
                        in 100..399 -> successReader
                        else -> errorReader
                    }

                    val payload = try {
                        reader.readValue<Any>(message.payload)
                    } catch (ex: Throwable) {
                        if (message.status in 100..399) throw ex
                        null
                    }

                    WSMessage.Response<Any?>(streamId, payload, message.status)
                }
                else -> null
            }

            if (result != null) {
                send(result as WSMessage<Any?>)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val SAMPLE_FREQUENCY = 100
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
internal class WSClientSession constructor(
    val underlyingSession: ClientWebSocketSession
) {
    private val channels = HashMap<String, Channel<WSRawMessage>>()
    private val mutex = Mutex()

    suspend fun subscribe(streamId: String): ReceiveChannel<WSRawMessage> {
        mutex.withLock {
            val channel = Channel<WSRawMessage>(Channel.UNLIMITED)
            channels[streamId] = channel
            return channel
        }
    }

    suspend fun unsubscribe(streamId: String) {
        mutex.withLock {
            channels.remove(streamId)?.cancel()
        }
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                while (true) {
                    val frame = underlyingSession.incoming.receive()

                    if (frame is Frame.Text) {
                        val text = frame.readText()

                        @Suppress("BlockingMethodInNonBlockingContext") val frameNode = defaultMapper.readTree(text)
                        val type = frameNode[TYPE_PROPERTY]
                            ?.takeIf { !it.isNull && it.isTextual }
                            ?.textValue() ?: continue

                        val status = frameNode[WSMessage.STATUS_FIELD]
                            ?.takeIf { !it.isNull && it.isIntegralNumber }
                            ?.intValue() ?: -1

                        val incomingStreamId = frameNode[WSMessage.STREAM_ID_FIELD]
                            ?.takeIf { !it.isNull && it.isTextual }
                            ?.textValue() ?: continue

                        val payload = frameNode[WSMessage.PAYLOAD_FIELD]

                        mutex.withLock {
                            val channel = channels[incomingStreamId] ?: return@withLock

                            if (!channel.isClosedForSend) {
                                try {
                                    channel.send(WSRawMessage(type, status, incomingStreamId, payload))
                                } catch (ex: CancellationException) {
                                    // Ignored
                                }
                            } else {
                                unsubscribe(incomingStreamId)
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                if (ex is ClosedReceiveChannelException || ex.cause is ClosedReceiveChannelException) {
                    mutex.withLock {
                        val emptyTree = defaultMapper.readTree("{}")

                        channels.forEach { (streamId, channel) ->
                            if (!channel.isClosedForSend) {
                                channel.send(WSRawMessage(WSMessage.RESPONSE_TYPE, 499, streamId, emptyTree))
                            }

                            channel.cancel()
                        }

                        channels.clear()
                    }
                } else {
                    log.info("Exception while processing client messages!")
                    log.info(ex.stackTraceToString())
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

internal data class WSRawMessage(
    val type: String,
    val status: Int,
    val streamId: String,
    val payload: JsonNode
)

@UseExperimental(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
internal class WSConnectionPool {
    private val connectionPool = HashMap<String, WSClientSession>()
    private val mutex = Mutex()
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun retrieveConnection(
        location: String
    ): WSClientSession {
        val existing = connectionPool[location]
        if (existing != null) {
            if (existing.underlyingSession.outgoing.isClosedForSend ||
                existing.underlyingSession.incoming.isClosedForReceive
            ) {
                connectionPool.remove(location)
            } else {
                return existing
            }
        }

        mutex.withLock {
            val existingAfterLock = connectionPool[location]
            if (existingAfterLock != null) return existingAfterLock

            val url = URLBuilder(location).build()
            log.info("Building new websocket connection to $url")
            val session = client.webSocketRawSession(
                host = url.host,
                port = url.port,
                path = url.fullPath
            )

            val wrappedSession = WSClientSession(session)
            connectionPool[location] = wrappedSession
            wrappedSession.startProcessing(GlobalScope)
            return wrappedSession
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.subscribe(
    request: R,
    authenticatedClient: AuthenticatedClient,
    handler: suspend (S) -> Unit
): IngoingCallResponse<S, E> {
    require(authenticatedClient.companion == OutgoingWSCall) {
        "subscribe call not supported for backend '${authenticatedClient.companion}'"
    }

    @Suppress("UNCHECKED_CAST")
    return authenticatedClient.client.call(
        this,
        request,
        authenticatedClient.companion as OutgoingCallCompanion<OutgoingCall>,
        beforeFilters = { ctx ->
            authenticatedClient.authenticator(ctx)

            ctx.attributes[OutgoingWSCall.SUBSCRIPTION_HANDLER_KEY] = {
                handler(it as S)
            }
        }
    )
}
