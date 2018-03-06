package dk.sdu.cloud.storage.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.net.URI

// Contains the shared model (used as part of interface) of this service

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}

class StoragePath(
    path: String,
    @get:JsonIgnore val host: String = "",
    val name: String = path.substringAfterLast('/')
) {
    // Remove trailing '/' (avoid doing so if the entire path is '/')
    val path = if (path.length > 1) path.removeSuffix("/") else path

    fun pushRelative(relativeURI: String): StoragePath {
        return StoragePath(URI("$path/$relativeURI").normalize().path, host)
    }

    fun push(vararg components: String): StoragePath = pushRelative(components.joinToString("/"))

    fun pop(): StoragePath = StoragePath(URI(path).resolve(".").normalize().path, host)
}

/*
@JsonSerialize(using = StoragePath.Companion.Serializer::class)
class StoragePath private constructor(private val uri: URI) {
    val host get() = uri.host
    val path get() = uri.path
    val name get() = components.last()

    init {
        uri.host!!
        if (uri.scheme != "storage") throw IllegalArgumentException("invalid scheme")
    }

    val components: List<String>
        get() = uri.path.split('/')

    fun pushRelative(relativeURI: String): StoragePath {
        return StoragePath(URI(uri.scheme, uri.host, URI(uri.path + '/' + relativeURI).normalize().path,
                null, null))
    }

    fun push(vararg components: String): StoragePath = pushRelative(components.joinToString("/"))

    fun pop(): StoragePath = StoragePath(uri.resolve(".").normalize())

    override fun toString(): String = uri.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StoragePath

        if (uri != other.uri) return false

        return true
    }

    override fun hashCode(): Int = uri.hashCode()

    companion object {
        object Serializer : StdSerializer<StoragePath>(StoragePath::class.java) {
            override fun serialize(value: StoragePath, gen: JsonGenerator, provider: SerializerProvider) {
                gen.writeStartObject()
                gen.writeStringField("uri", value.uri.toString())
                gen.writeStringField("host", value.uri.host)
                gen.writeStringField("path", value.uri.path)
                gen.writeStringField("name", value.name)
                gen.writeEndObject()
            }
        }

        fun internalCreateFromHostAndAbsolutePath(host: String, path: String): StoragePath =
                StoragePath(URI("storage", host, path, null, null).normalize())

        fun fromURI(uri: URI) = StoragePath(uri)
        fun fromURI(uriAsString: String) = StoragePath(URI(uriAsString))
    }
}
*/

enum class AccessRight {
    NONE,
    READ,
    READ_WRITE,
    OWN
}

data class MetadataEntry(val key: String, val value: String)
typealias Metadata = List<MetadataEntry>

data class AccessEntry(val entity: Entity, val right: AccessRight)
typealias AccessControlList = List<AccessEntry>

enum class FileType {
    FILE,
    DIRECTORY
}

data class StorageFile(
    val type: FileType,
    val path: StoragePath,
    val createdAt: Long,
    val modifiedAt: Long,
    val size: Int,
    val acl: List<AccessEntry>,
    val favorited: Boolean,
    val sensitivityLevel: SensitivityLevel
)

enum class SensitivityLevel {
    OPEN_ACCESS,
    CONFIDENTIAL,
    SENSITIVE
}

data class FileStat(
    val path: StoragePath,
    val createdAtUnixMs: Long,
    val modifiedAtUnixMs: Long,
    val ownerName: String,
    val sizeInBytes: Long,
    val systemDefinedChecksum: String
)

enum class ArchiveType {
    TAR,
    TAR_GZ,
    ZIP
}
