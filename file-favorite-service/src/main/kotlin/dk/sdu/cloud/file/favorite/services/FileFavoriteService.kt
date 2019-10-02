package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.indexing.api.AddSubscriptionRequest
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.RemoveSubscriptionRequest
import dk.sdu.cloud.indexing.api.ReverseLookupFilesRequest
import dk.sdu.cloud.indexing.api.Subscriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class FileFavoriteService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: FileFavoriteDAO<DBSession>,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun toggleFavorite(
        files: List<String>,
        user: SecurityPrincipalToken,
        userCloud: AuthenticatedClient,
        audit: ToggleFavoriteAudit? = null
    ): List<String> {
        // Note: This function must ensure that the user has the correct privileges to the file!
        val failures = ArrayList<String>()
        val newFileIds = HashSet<String>()
        val removedFileIds = HashSet<String>()
        db.withTransaction { session ->
            files.forEachIndexed { index, path ->
                try {
                    val fileId =
                        FileDescriptions.stat.call(
                            StatRequest(path, attributes = "${StorageFileAttribute.fileId}"),
                            userCloud
                        ).orThrow().fileId

                    val favorite = dao.isFavorite(session, user, fileId)

                    val fileAudit = audit?.files?.get(index)
                    if (fileAudit != null) {
                        fileAudit.fileId = fileId
                        fileAudit.newStatus = !favorite
                    }

                    if (favorite) {
                        dao.delete(session, user, fileId)
                        removedFileIds.add(fileId)
                    } else {
                        dao.insert(session, user, fileId)
                        newFileIds.add(fileId)
                    }
                } catch (e: RPCException) {
                    failures.add(path)
                }
            }
        }

        if (newFileIds.isNotEmpty()) {
            Subscriptions.addSubscription.call(
                AddSubscriptionRequest(newFileIds),
                serviceClient
            )
        }

        if (removedFileIds.isNotEmpty()) {
            Subscriptions.removeSubscription.call(
                RemoveSubscriptionRequest(removedFileIds),
                serviceClient
            )
        }

        return failures
    }

    fun getFavoriteStatus(files: List<StorageFile>, user: SecurityPrincipalToken): Map<String, Boolean> =
        db.withTransaction { dao.bulkIsFavorite(it, files, user) }

    suspend fun listAll(
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipalToken
    ): Page<StorageFile> {
        val fileIds = db.withTransaction {
            dao.listAll(it, pagination, user)
        }

        if (fileIds.items.isEmpty()) return Page(0, 0, 0, emptyList())

        val lookupResponse = LookupDescriptions.reverseLookupFiles.call(
            ReverseLookupFilesRequest(fileIds.items),
            serviceClient
        ).orThrow()

        run {
            // Delete unknown files (files that did not appear in the reverse lookup)
            val unknownFiles = HashSet<String>()
            for ((index, file) in lookupResponse.files.withIndex()) {
                val fileId = fileIds.items[index]
                if (file == null) {
                    unknownFiles.add(fileId)
                }
            }

            if (unknownFiles.isNotEmpty()) {
                log.info("The following files no longer exist: $unknownFiles")
                db.withTransaction { session -> dao.deleteById(session, unknownFiles) }
            }
        }

        // TODO It might be necessary for us to verify knowledge of these files.
        // But given we need to do a stat to get it into the database it should be fine.
        return Page(
            fileIds.itemsInTotal,
            fileIds.itemsPerPage,
            fileIds.pageNumber,
            lookupResponse.files.filterNotNull()
        )
    }

    fun deleteById(fileIds: Set<String>) {
        db.withTransaction {
            dao.deleteById(it, fileIds)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
