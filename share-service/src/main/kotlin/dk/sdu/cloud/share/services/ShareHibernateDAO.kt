package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.share.api.ShareState
import org.hibernate.ScrollMode
import java.util.*
import javax.persistence.*
import javax.persistence.criteria.Predicate

private const val COL_FILE_ID = "file_id"
private const val COL_SHARED_WITH = "shared_with"
private const val COL_PATH = "path"

@Entity
@Table(
    name = "shares",
    uniqueConstraints = [
        UniqueConstraint(columnNames = [COL_SHARED_WITH, COL_PATH])
    ],
    indexes = [
        Index(columnList = COL_FILE_ID)
    ]
)
data class ShareEntity(
    var owner: String,

    @Column(name = COL_SHARED_WITH)
    var sharedWith: String,

    @Column(name = COL_PATH, length = 4096)
    var path: String,

    @Column(length = 4096)
    var filename: String,

    var rights: Int,

    @Enumerated(EnumType.ORDINAL)
    var state: ShareState,

    @Column(name = COL_FILE_ID)
    var fileId: String,

    @Id
    @GeneratedValue
    var id: Long = 0,

    var ownerToken: String,

    var recipientToken: String? = null,

    @Temporal(TemporalType.TIMESTAMP)
    override var createdAt: Date = Date(System.currentTimeMillis()),

    @Temporal(TemporalType.TIMESTAMP)
    override var modifiedAt: Date = Date(System.currentTimeMillis())
) : WithTimestamps {
    companion object : HibernateEntity<ShareEntity>, WithId<Long>
}

class ShareHibernateDAO : ShareDAO<HibernateSession> {
    override fun create(
        session: HibernateSession,
        owner: String,
        sharedWith: String,
        path: String,
        initialRights: Set<AccessRight>,
        fileId: String,
        ownerToken: String
    ): Long {
        if (owner == sharedWith) throw ShareException.BadRequest("Cannot share file with yourself")

        val exists = session.criteria<ShareEntity> {
            allOf(
                entity[ShareEntity::path] equal literal(path),
                entity[ShareEntity::sharedWith] equal literal(sharedWith)
            )
        }.uniqueResult() != null

        if (exists) {
            throw ShareException.DuplicateException()
        }

        return session.save(
            ShareEntity(
                owner = owner,
                sharedWith = sharedWith,
                path = path,
                filename = path.fileName(),
                state = ShareState.REQUEST_SENT,
                rights = initialRights.asInt(),
                fileId = fileId,
                ownerToken = ownerToken
            )
        ) as Long
    }

    override fun findAllByFileId(
        session: HibernateSession,
        auth: AuthRequirements,
        fileId: String
    ): List<InternalShare> {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(auth),
                entity[ShareEntity::fileId] equal fileId
            )
        }.list().map { it.toModel() }
    }

    override fun findById(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare {
        return shareById(session, auth, shareId).toModel()
    }

    override fun findAllByPath(
        session: HibernateSession,
        auth: AuthRequirements,
        path: String
    ): List<InternalShare> {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(auth),
                entity[ShareEntity::path] equal literal(path)
            )
        }.list()
            .map { it.toModel() }
            .takeIf { it.isNotEmpty() }
            ?: throw ShareException.NotFound()
    }

    override fun list(
        session: HibernateSession,
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery,
        paging: NormalizedPaginationRequest
    ): ListSharesResponse {
        val itemsInTotal = countShareGroups(session, auth, shareRelation)
        val items = findShareGroups(session, auth, paging, shareRelation)

        return ListSharesResponse(
            items,
            itemsInTotal.toInt()
        )
    }

    override fun listSharedToMe(
        session: HibernateSession,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalShare> {
        return session.paginatedCriteria<ShareEntity>(paging) {
            (entity[ShareEntity::sharedWith] equal user) and (entity[ShareEntity::state] equal ShareState.ACCEPTED)
        }.mapItems { it.toModel() }
    }

    private fun countShareGroups(
        session: HibernateSession,
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery
    ): Long {
        return session.countWithPredicate<ShareEntity>(
            distinct = true,
            selection = { entity[ShareEntity::path] },
            predicate = { findSharesBy(auth, shareRelation) }
        )
    }

    private fun findShareGroups(
        session: HibernateSession,
        auth: AuthRequirements,
        paging: NormalizedPaginationRequest,
        shareRelation: ShareRelationQuery
    ): List<InternalShare> {
        // We first find the share groups (by path)
        val distinctPaths = session.createCriteriaBuilder<String, ShareEntity>().run {
            with(criteria) {
                select(entity[ShareEntity::path])
                distinct(true)
                where(findSharesBy(auth, shareRelation))
                orderBy(ascending(entity[ShareEntity::path]))
            }
        }.createQuery(session).paginatedList(paging)

        // We then retrieve all shares for each group
        return session
            .criteria<ShareEntity>(
                orderBy = {
                    listOf(
                        ascending(entity[ShareEntity::state]),
                        ascending(entity[ShareEntity::filename])
                    )
                },
                predicate = {
                    allOf(
                        entity[ShareEntity::path] isInCollection distinctPaths,
                        findSharesBy(auth, shareRelation)
                    )
                }
            )
            .list()
            .map { it.toModel() }
    }

    override fun updateShare(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long,
        recipientToken: String?,
        state: ShareState?,
        rights: Set<AccessRight>?,
        path: String?,
        ownerToken: String?
    ): InternalShare {
        if (path == null && recipientToken == null && state == null && rights == null) {
            throw ShareException.InternalError("Nothing to update")
        }

        val share = shareById(session, auth, shareId)
        if (path != null) {
            share.path = path
            share.filename = path.fileName()
        }
        if (recipientToken != null) share.recipientToken = recipientToken
        if (ownerToken != null) share.ownerToken = ownerToken
        if (state != null) share.state = state
        if (rights != null) share.rights = rights.asInt()

        session.save(share)
        return share.toModel()
    }

    override fun deleteShare(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare {
        val entity = shareById(session, auth, shareId)
        session.delete(entity)
        return entity.toModel()
    }

    override fun onFilesMoved(session: HibernateSession, events: List<StorageEvent.Moved>): List<InternalShare> {
        val shares = internalFindAllByFileId(session, events.map { it.file.fileId })
        if (shares.isNotEmpty()) {
            val eventsByFileId = events.associateBy { it.file.fileId }
            shares.forEach { share ->
                val event = eventsByFileId[share.fileId] ?: return@forEach
                share.path = event.file.path
                share.filename = event.file.path.fileName()
                session.save(share)
            }
        }
        session.flush()
        return shares.map { it.toModel() }
    }

    override fun findAllByFileIds(
        session: HibernateSession,
        fileIds: List<String>,
        includeShares: Boolean
    ): List<InternalShare> {
        return internalFindAllByFileId(session, fileIds, includeShares).map { it.toModel() }
    }

    override fun deleteAllByShareId(session: HibernateSession, shareIds: List<Long>) {
        session.deleteCriteria<ShareEntity> { entity[ShareEntity::id] isInCollection shareIds }.executeUpdate()
    }

    override fun listAll(session: HibernateSession): Sequence<InternalShare> = sequence {
        session.createQuery("from ShareEntity").scroll(ScrollMode.FORWARD_ONLY).use { iterator ->
            while (iterator.next()) {
                val entity = iterator.get(0) as? ShareEntity ?: break
                yield(entity.toModel())
            }
        }
    }

    private fun internalFindAllByFileId(
        session: HibernateSession,
        fileIds: List<String>,
        includeShares: Boolean = true
    ): List<ShareEntity> {
        if (fileIds.size > 250) throw IllegalArgumentException("fileIds.size > 250")
        return session.criteria<ShareEntity> {
            allOf(*ArrayList<Predicate>().apply {
                if (includeShares) add(entity[ShareEntity::fileId] isInCollection fileIds)
            }.toTypedArray())
        }.list()
    }

    private fun shareById(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): ShareEntity {
        return session.criteria<ShareEntity> {
            allOf(isAuthorized(auth), entity[ShareEntity::id] equal shareId)
        }.uniqueResult() ?: throw ShareException.NotFound()
    }

    private fun CriteriaBuilderContext<*, ShareEntity>.isAuthorized(
        auth: AuthRequirements
    ): Predicate {
        if (auth.user == null || auth.requireRole == null) return literal(true).toPredicate()

        val isOwner = entity[ShareEntity::owner] equal literal(auth.user)
        val isSharedWith = entity[ShareEntity::sharedWith] equal literal(auth.user)

        return when (auth.requireRole) {
            ShareRole.PARTICIPANT -> anyOf(isOwner, isSharedWith)
            ShareRole.OWNER -> isOwner
            ShareRole.RECIPIENT -> isSharedWith
        }
    }

    private fun CriteriaBuilderContext<*, ShareEntity>.findSharesBy(
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery
    ): Predicate {
        val predicates = arrayListOf(isAuthorized(auth))
        if (shareRelation.sharedByMe) {
            predicates.add(entity[ShareEntity::owner] equal shareRelation.username)
        } else {
            predicates.add(entity[ShareEntity::sharedWith] equal shareRelation.username)
        }
        return allOf(*predicates.toTypedArray())
    }

    private fun ShareEntity.toModel(): InternalShare =
        InternalShare(
            id = id,
            owner = owner,
            sharedWith = sharedWith,
            state = state,
            path = path,
            rights = rights.asEnumSet(),
            fileId = fileId,
            ownerToken = ownerToken,
            recipientToken = recipientToken,
            createdAt = createdAt.time,
            modifiedAt = modifiedAt.time
        )
}

