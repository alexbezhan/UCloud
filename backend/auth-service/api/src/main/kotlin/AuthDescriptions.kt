package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class LoginRequest(
    val username: String?,
    val service: String?
)

data class OneTimeAccessToken(val accessToken: String, val jti: String)
data class RequestOneTimeToken(val audience: String)
data class ClaimOneTimeToken(val jti: String)

data class Session(
    val ipAddress: String,
    val userAgent: String,
    val createdAt: Long
)

data class ListUserSessionsRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListUserSessionsResponse = Page<Session>

data class TokenExtensionRequest(
    /**
     * A valid JWT for the security principal extension is requested
     */
    val validJWT: String,

    /**
     * A list of [SecurityScope]s that this request requires.
     *
     * It is not possible to ask for all.
     */
    val requestedScopes: List<String>,

    /**
     * How many ms the new token should be valid for.
     *
     * It is not possible to extend this deadline. Currently the maximum deadline is configured to be 24 hours.
     */
    val expiresIn: Long,

    /**
     * Should this extension allow the token to be refreshed?
     *
     * This will happen through a refresh token passed via [OptionalAuthenticationTokens.refreshToken].
     */
    val allowRefreshes: Boolean = false
) {
    override fun toString(): String =
        "TokenExtensionRequest(" +
                "requestedScopes = $requestedScopes, " +
                "expiresIn = $expiresIn, " +
                "allowRefreshes = $allowRefreshes" +
                ")"
}

typealias TokenExtensionResponse = OptionalAuthenticationTokens

data class TokenExtensionAudit(
    val requestedBy: String,
    val username: String?,
    val role: Role?,
    val requestedScopes: List<String>,
    val expiresIn: Long,
    val allowRefreshes: Boolean
)

data class BulkInvalidateRequest(val tokens: List<String>)
typealias BulkInvalidateResponse = Unit

object AuthDescriptions : CallDescriptionContainer("auth") {
    const val baseContext = "/auth"

    val refresh = call<Unit, AccessToken, CommonErrorMessage>("refresh") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"refresh"
            }
        }
    }

    val webRefresh = call<Unit, AccessTokenAndCsrf, CommonErrorMessage>("webRefresh") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"refresh"
                +"web"
            }
        }
    }

    val bulkInvalidate = call<BulkInvalidateRequest, BulkInvalidateResponse, CommonErrorMessage>("bulkInvalidate") {
        audit<Unit>()

        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"logout"
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val logout = call<Unit, Unit, Unit>("logout") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"logout"
            }
        }
    }

    val webLogout = call<Unit, Unit, CommonErrorMessage>("webLogout") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"logout"
                +"web"
            }
        }
    }

    val claim = call<ClaimOneTimeToken, Unit, Unit>("claim") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"claim"
                +boundTo(ClaimOneTimeToken::jti)
            }
        }
    }

    val requestOneTimeTokenWithAudience =
        call<RequestOneTimeToken, OneTimeAccessToken, Unit>("requestOneTimeTokenWithAudience") {
            auth {
                roles = Roles.PUBLIC
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post
                path {
                    using(baseContext)
                    +"request"
                }

                params {
                    +boundTo(RequestOneTimeToken::audience)
                }
            }
        }

    val tokenExtension = call<TokenExtensionRequest, TokenExtensionResponse, CommonErrorMessage>("tokenExtension") {
        audit<TokenExtensionAudit>()
        auth {
            roles = setOf(Role.USER, Role.SERVICE, Role.ADMIN)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"extend"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val passwordLogin = call<Unit, Unit, CommonErrorMessage>("passwordLogin") {
        audit<LoginRequest>()

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"login"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val listUserSessions =
        call<ListUserSessionsRequest, ListUserSessionsResponse, CommonErrorMessage>("listUserSessions") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"sessions"
                }

                params {
                    +boundTo(ListUserSessionsRequest::itemsPerPage)
                    +boundTo(ListUserSessionsRequest::page)
                }
            }
        }

    val invalidateSessions = call<Unit, Unit, CommonErrorMessage>("invalidateSessions") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"sessions"
            }
        }
    }
}

