package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AppStoreService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val applicationDAO: ApplicationDAO<DBSession>,
    private val toolDao: ToolDAO<DBSession>
) {
    fun toggleFavorite(securityPrincipal: SecurityPrincipal, name: String, version: String) {
        db.withTransaction { session ->
            applicationDAO.toggleFavorite(
                session,
                securityPrincipal,
                name,
                version
            )
        }
    }

    fun retrieveFavorites(
        securityPrincipal: SecurityPrincipal,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> = db.withTransaction { session ->
        applicationDAO.retrieveFavorites(
            session,
            securityPrincipal,
            request.normalize()
        )
    }

    fun searchTags(
        securityPrincipal: SecurityPrincipal,
        tags: List<String>,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.searchTags(
                session,
                securityPrincipal,
                tags,
                normalizedPaginationRequest
            )
        }

    fun searchApps(
        securityPrincipal: SecurityPrincipal,
        query: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.search(
                session,
                securityPrincipal,
                query,
                normalizedPaginationRequest
            )
        }

    fun findByNameAndVersion(
        securityPrincipal: SecurityPrincipal,
        name: String,
        version: String
    ): ApplicationWithFavoriteAndTags {
        db.withTransaction { session ->
            val result = applicationDAO.findByNameAndVersionForUser(
                session,
                securityPrincipal,
                name,
                version
            )

            val toolRef = result.invocation.tool
            val tool = toolDao.findByNameAndVersion(session, securityPrincipal, toolRef.name, toolRef.version)

            return result.copy(
                invocation = result.invocation.copy(
                    tool = ToolReference(
                        toolRef.name,
                        toolRef.version,
                        tool
                    )
                )
            )
        }
    }

    fun findByName(
        securityPrincipal: SecurityPrincipal,
        name: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction {
            applicationDAO.findAllByName(
                it,
                securityPrincipal,
                name,
                normalizedPaginationRequest
            )
        }

    fun listAll(
        securityPrincipal: SecurityPrincipal,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.listLatestVersion(
                session,
                securityPrincipal,
                normalizedPaginationRequest
            )

        }

    fun create(securityPrincipal: SecurityPrincipal, application: Application, content: String) {
        db.withTransaction { session ->
            applicationDAO.create(session, securityPrincipal, application, content)
        }
    }

    fun createTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.createTags(session, user, applicationName, tags)
        }
    }

    fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.deleteTags(session, user, applicationName, tags)
        }
    }

    fun findLatestByTool(
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        return db.withTransaction { session ->
            applicationDAO.findLatestByTool(session, user, tool, paging)
        }
    }

    fun advancedSearch(
        user: SecurityPrincipal,
        name: String?,
        version: String?,
        tags: List<String>?,
        description: String?,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationWithFavoriteAndTags> {
        return db.withTransaction { session ->
            applicationDAO.advancedSearch(session, user, name, version, tags, description, paging)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

}
