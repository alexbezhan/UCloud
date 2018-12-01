package dk.sdu.cloud.support.services

import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.call.receive
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

        val postResult = httpClient.call(hook) {
            method = HttpMethod.Post
            body = TextContent(defaultMapper.writeValueAsString(SlackMessage(message)), ContentType.Application.Json)
        }

        val status = postResult.response.status
        if (!status.isSuccess()) {
            log.warn("unsuccessful message from slack ($status)")
            runCatching { log.warn(postResult.receive()) }
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}