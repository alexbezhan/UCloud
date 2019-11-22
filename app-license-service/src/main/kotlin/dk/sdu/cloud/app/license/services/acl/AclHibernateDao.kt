package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.service.db.*
import java.io.Serializable
import javax.persistence.*

@javax.persistence.Entity
@Table(name = "permissions")
data class PermissionEntry(
    @get:EmbeddedId
    var key: Key
) {
    companion object : HibernateEntity<PermissionEntry>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(name = "entity") var userEntity: String,
        @get:Enumerated(EnumType.STRING) @Column(name = "entity_type") var entityType: EntityType,
        @get:Column(name = "server_id") var serverId: String,
        @get:Enumerated(EnumType.STRING) var permission: AccessRight
    ): Serializable
}


class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        licenseId: String,
        accessEntity: UserEntity,
        permission: AccessRight
    ): Boolean {
        return when (permission) {
            AccessRight.READ -> {
                session.criteria<PermissionEntry>{
                    allOf(
                        (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal accessEntity.id) and
                            (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type) and
                            ((entity[PermissionEntry::key][PermissionEntry.Key::permission] equal AccessRight.READ) or
                                    (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal AccessRight.READ_WRITE))
                    )
                }.list().isNotEmpty()
            }
            AccessRight.READ_WRITE -> {
                session.criteria<PermissionEntry> {
                    allOf(
                        (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal accessEntity.id) and
                                (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type) and
                                (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal AccessRight.READ_WRITE)
                    )
                }.list().isNotEmpty()
            }
        }
    }

    override fun updatePermissions(
        session: HibernateSession,
        licenseId: String,
        userEntity: UserEntity,
        permissions: AccessRight
    ) {
        val permissionEntry = PermissionEntry(
            PermissionEntry.Key(
                userEntity.id,
                userEntity.type,
                licenseId,
                permissions
            )
        )

        session.saveOrUpdate(permissionEntry)
    }

    override fun revokePermission(
        session: HibernateSession,
        licenseId: String,
        userEntity: UserEntity
    ) {
        session.deleteCriteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal licenseId) and
                    (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal userEntity.id) and
                    (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal userEntity.type)
        }.executeUpdate()
    }

    override fun listAcl(
        session: HibernateSession,
        licenseId: String
    ): List<EntityWithPermission> {
        return session
            .criteria<PermissionEntry> {
                entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal licenseId
            }
            .list()
            .map {
                EntityWithPermission(UserEntity(it.key.userEntity, it.key.entityType), it.key.permission)
            }
    }
}
