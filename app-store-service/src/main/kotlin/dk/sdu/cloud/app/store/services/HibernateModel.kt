package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.*

/**
 * Updated in:
 *
 * - V4__Tools.sql
 */
@Entity
@Table(name = "tools")
data class ToolEntity(
    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Type(type = JSONB_TYPE)
    var tool: NormalizedToolDescription,

    @Column(length = 1024 * 64)
    var originalDocument: String,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
) {
    companion object : HibernateEntity<ToolEntity>, WithId<EmbeddedNameAndVersion>
}

@Entity
@Table(name = "favorited_by")
class FavoriteApplicationEntity(
    var applicationName: String,

    var applicationVersion: String,

    @Column(name = "the_user")
    var user: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<FavoriteApplicationEntity>, WithId<Long>
}

@Entity
@Table(name = "application_tags")
class TagEntity(
    var applicationName: String,

    var applicationVersion: String,

    @Column(name = "tag")
    var tag: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<TagEntity>, WithId<Long>
}

/**
 * Updated in:
 *
 * - V3__Applications.sql
 * - V4__Tools.sql
 */
@Entity
@Table(name = "applications")
class ApplicationEntity(
    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Type(type = JSONB_TYPE)
    var authors: List<String>,

    var title: String,

    @Column(length = 1024 * 64)
    var description: String,

    var website: String?,

    @Type(type = JSONB_TYPE)
    var tags: List<String>,

    @Type(type = JSONB_TYPE)
    var application: ApplicationInvocationDescription,

    @Column(name = "tool_name")
    var toolName: String,

    @Column(name = "tool_version")
    var toolVersion: String,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
) {
    companion object : HibernateEntity<ApplicationEntity>, WithId<EmbeddedNameAndVersion>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

