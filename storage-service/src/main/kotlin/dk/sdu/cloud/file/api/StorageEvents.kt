package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.service.TYPE_PROPERTY
import java.util.*

/**
 * Represents an event which has occurred inside of the storage system
 *
 * Each file has a unique identifier (implementation dependant, for CephFS this is the inode). Additionally every
 * event contains the canonical path. SDUCloud doesn't support hard-links. As a result we can be certain that each
 * file has exactly one canonical path at any point in time.
 *
 * The effects of each event is guaranteed to have taken place internally in the system when they are emitted.
 *
 * __Note:__ Since events are emitted and consumed asynchronously you cannot be certain that the file is present at
 * the [StorageEvent.path] or that the file even exists, since multiple new events may have occurred when the event
 * is consumed.
 *
 * __Note:__ The events are emitted on a best-effort basis. It is entirely possible that these events can be out-of-sync
 * with the real system. As a result clients should be able to handle event sequences that are technically
 * impossible to occur. For example, a create event might be missed and jump straight to a moved or deleted event.
 *
 * __Note:__ Regular scans are performed to ensure the events and FS are in-sync. When inconsistencies are detected new
 * events will be emitted to make the events consistent with the FS.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StorageEvent.CreatedOrRefreshed::class, name = "created"),
    JsonSubTypes.Type(value = StorageEvent.Deleted::class, name = "deleted"),
    JsonSubTypes.Type(value = StorageEvent.SensitivityUpdated::class, name = "sensitivity"),
    JsonSubTypes.Type(value = StorageEvent.Moved::class, name = "moved"),
    JsonSubTypes.Type(value = StorageEvent.Invalidated::class, name = "invalidated")
)
sealed class StorageEvent {
    /**
     * Internal timestamp for when the event occurred
     *
     * Format is milliseconds since unix epoch
     *
     * __Note:__ This is different from the time the event is emitted. In case of events emitted due to an
     * out-of-sync event stream the timestamps may differ significantly.
     *
     * __Note:__ These timestamps are best-effort.
     */
    abstract val timestamp: Long

    abstract val eventCausedBy: String?

    abstract val file: StorageFile?

    /**
     * Emitted when a file has been created or a full-refresh of the file is deemed necessary.
     *
     * It is safe for clients to overwrite their previous entry at indexed by [id]. None of the old attributes are
     * guaranteed to be the same. For example, it is perfectly valid for the system not to emit a [Moved] event and just
     * send a new [CreatedOrRefreshed] event with a new [path]. This will, for example, occur when inconsistencies are
     * detected.
     */
    data class CreatedOrRefreshed(
        override val file: StorageFile,
        override val timestamp: Long,
        override val eventCausedBy: String? = null
    ) : StorageEvent()

    /**
     * Emitted when the sensitivity level of a file has changed
     */
    data class SensitivityUpdated(
        override val file: StorageFile,
        override val timestamp: Long,
        override val eventCausedBy: String? = null
    ) : StorageEvent()

    /**
     * Emitted when a file has been deleted
     */
    data class Deleted(
        override val file: StorageFile,
        override val timestamp: Long,
        override val eventCausedBy: String? = null
    ) : StorageEvent()

    /**
     * Emitted when a file is moved from one location to another
     *
     * __Note:__ The path in this case refers to the _new_ path.
     */
    data class Moved(
        val oldPath: String,
        override val file: StorageFile,
        override val timestamp: Long,
        override val eventCausedBy: String? = null
    ) : StorageEvent()

    /**
     * Indicates that the events have been out-of-sync with the FS for all children of [path]
     *
     * Clients should invalidate their caches for all paths starting with [path] (including [path]).
     *
     * __Note:__ The [id], [owner] and [timestamp] are all best-effort approximates and should not be relied upon
     *
     * __Note:__ The [id] should _never_ be relied upon for this event time.
     */
    data class Invalidated(
        val path: String,
        override val timestamp: Long,
        override val eventCausedBy: String? = null
    ) : StorageEvent() {
        override val file: StorageFile? = null
    }
}

data class Timestamps(val accessed: Long, val created: Long, val modified: Long)

typealias StorageEventStream = EventStream<StorageEvent>

object StorageEvents : EventStreamContainer() {
    /**
     * A list of storage events. Keyed by the file ID
     */
    val events = stream<StorageEvent>("storage-events", { it.file?.fileIdOrNull ?: UUID.randomUUID().toString() })
}
