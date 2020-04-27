package dk.sdu.cloud.kubernetes.monitor.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class ExampleRequest(val message: String)
data class ExampleResponse(val echo: String)

object KubernetesMonitorDescriptions : CallDescriptionContainer("kubernetes.monitor") {
    val baseContext = "/api/kubernetes/monitor"

    val example = call<ExampleRequest, ExampleResponse, CommonErrorMessage>("example") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"example"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
