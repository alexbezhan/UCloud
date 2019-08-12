package dk.sdu.cloud.support.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.call.receive
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.slf4j.Logger

private data class SlackMessage(val text: String)

class SlackNotifier(
    val hook: String
) : TicketNotifier {
    private val httpClient = HttpClient()

    override suspend fun onTicket(ticket: Ticket) {
        val message = """
            New ticket via SDUCloud:

            *User information:*
              - *Username:* ${ticket.principal.username}
              - *Role:* ${ticket.principal.role}
              - *Real name:* ${ticket.principal.firstName} ${ticket.principal.lastName}

            *Technical info:*
              - *Request ID (Audit):* ${ticket.requestId}
              - *User agent:* ${ticket.userAgent}

            The following message was attached:

        """.trimIndent() + ticket.message.lines().joinToString("\n") { "> $it" }


        var retries = 0
        while (true) {
            retries++
            if (retries == 3) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
            }
            val postResult = try {
                httpClient.call(hook) {
                    method = HttpMethod.Post
                    body = TextContent(
                        defaultMapper.writeValueAsString(SlackMessage(message)),
                        ContentType.Application.Json
                    )
                }
            } catch (ex: Exception) {
                when (ex) {
                    is java.net.ConnectException -> {
                        log.debug("Java.net.Connect Exception caught : ${ex.message}")

                    }
                    is io.ktor.client.engine.cio.ConnectException -> {
                        log.debug("Cio ConnectException caught : ${ex.message}")
                    }
                }
                continue

            }
            val status = postResult.response.status
            if (!status.isSuccess()) {
                log.warn("unsuccessful message from slack ($status)")
                runCatching { log.warn(postResult.receive()) }
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
            return
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
