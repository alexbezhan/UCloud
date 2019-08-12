package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareState

data class ShareRelationQuery(val username: String, val sharedByMe: Boolean)

/**
 * Provides an interface to the [Share] data layer
 *
 * All methods in this interface will throw [ShareException] when appropriate.
 *
 * Authorization is performed by this interface, however, only at the "share" level. Any file system restrictions are
 * expected by the layers above. For example: Clients should do authorization to make sure the user can create a share
 * for a given file.
 */
interface ShareDAO<Session> {
    fun create(
        session: Session,
        owner: String,
        sharedWith: String,
        path: String,
        initialRights: Set<AccessRight>,
        fileId: String,
        ownerToken: String
    ): Long

    fun findById(
        session: Session,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare

    fun findAllByPath(
        session: Session,
        auth: AuthRequirements,
        path: String
    ): List<InternalShare>

    fun findAllByFileId(
        session: Session,
        auth: AuthRequirements,
        fileId: String
    ): List<InternalShare>

    fun list(
        session: Session,
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): ListSharesResponse

    fun listSharedToMe(
        session: Session,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalShare>

    fun updateShare(
        session: Session,
        auth: AuthRequirements,
        shareId: Long,
        recipientToken: String? = null,
        state: ShareState? = null,
        rights: Set<AccessRight>? = null,
        path: String? = null,
        ownerToken: String? = null
    ): InternalShare

    fun deleteShare(
        session: Session,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare

    fun onFilesMoved(
        session: Session,
        events: List<StorageEvent.Moved>
    ): List<InternalShare>

    fun findAllByFileIds(
        session: Session,
        fileIds: List<String>,
        includeShares: Boolean = true
    ): List<InternalShare>

    fun deleteAllByShareId(
        session: Session,
        shareIds: List<Long>
    )

    fun listAll(session: Session): Sequence<InternalShare>
}

data class ListSharesResponse(
    val allSharesForPage: List<InternalShare>,
    val groupCount: Int
)

/**
 * Authorization requirements for a Share action.
 *
 * @param user The username of a user. A value of null indicates that this action is being performed by the service. It
 *             will as a result always pass validation.
 *
 * @param requireRole The role to require of the [user].
 */
data class AuthRequirements(
    val user: String? = null,
    val requireRole: ShareRole? = null
)

enum class ShareRole {
    PARTICIPANT,
    OWNER,
    RECIPIENT
}
