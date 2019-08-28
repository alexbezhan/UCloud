package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.SimpleDuration
import kotlin.math.max

data class VerifiedJob(
    /**
     * A copy of the [Application] that this job is/will be running.
     */
    val application: Application,

    /**
     * A (optional) name for this job
     */
    val name: String? = null,

    /**
     * A complete list of files gathered from input_directory and input_file parameters.
     */
    val files: List<ValidatedFileForUpload>,

    /**
     * A unique ID for this job.
     */
    val id: String,

    /**
     * The real username of the user who created this job.
     *
     * If this is started by a project proxy user this will point to the real user (as indicated by the
     * extendedByChain).
     */
    val owner: String,

    /**
     * The number of nodes requested.
     */
    val nodes: Int,

    /**
     * The number of tasks per node requested.
     */
    val tasksPerNode: Int,

    /**
     * The maximum amount of time this job can run for.
     */
    val maxTime: SimpleDuration,

    /**
     * The input values for this job.
     */
    val jobInput: VerifiedJobInput,

    /**
     * The backend on which to run this job.
     */
    val backend: String,

    /**
     * The job's current state.
     */
    val currentState: JobState,

    /**
     * A status message.
     */
    val status: String,

    /**
     * The state of the job when the job entered the failed state
     */
    var failedState: JobState?,

    /**
     * The collection to put the results in. This defaults to [Application.metadata.title].
     */
    val archiveInCollection: String,

    /**
     * The workspace to use for files.
     */
    val workspace: String? = null,

    /**
     * Timestamp for when this job was created.
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp for when this job was last updated.
     */
    val modifiedAt: Long = System.currentTimeMillis(),

    /**
     * A complete list of mounts
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("mounts")
    val _mounts: List<ValidatedFileForUpload>? = null,

    /**
     * Timestamp for when this job started execution.
     */
    val startedAt: Long? = null,

    /**
     * Milliseconds left of job from the job is started, null if the job is not started
     */
    val timeLeft: Long? = if (startedAt != null) {
        max((startedAt + maxTime.toMillis()) - System.currentTimeMillis(), 0)
    } else {
        null
    },

    /**
     * The username of the user who initiated the job.
     *
     * If this is started by a project proxy user then this will point to the username of that proxy user.
     */
    val user: String = owner,

    /**
     * The project that this job belongs to.
     *
     * If this project is started by a project proxy user then this property will point to that project. If this is not
     * started by a project proxy user then this will be null.
     */
    val project: String? = null,

    /**
     * A list of shared file systems to be mounted inside of the container.
     *
     * A backend is allowed to reject a shared file system mount if it does not support mounting it. This should
     * happen early, for example, by comparing the backend against a whitelist of supported backends.
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("sharedFileSystemMounts")
    val _sharedFileSystemMounts: List<SharedFileSystemMount>? = null,

    /**
     * A list of peers that this application is requesting networking with.
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("peers")
    val _peers: List<ApplicationPeer>? = null
) {
    @get:JsonIgnore
    val mounts: List<ValidatedFileForUpload>
        get() = _mounts ?: emptyList()

    @get:JsonIgnore
    val peers: List<ApplicationPeer>
        get() = _peers ?: emptyList()

    @get:JsonIgnore
    val sharedFileSystemMounts: List<SharedFileSystemMount>
        get() = _sharedFileSystemMounts ?: emptyList()

    override fun toString() = "VerifiedJob(${application.metadata.name}@${application.metadata.version})"
}
