package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.relativize
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.retryWithCatch
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.task.api.MeasuredSpeedInteger
import dk.sdu.cloud.task.api.Progress
import dk.sdu.cloud.task.api.SimpleSpeed
import dk.sdu.cloud.task.api.runTask
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.random.Random

const val XATTR_BIRTH = "birth"
const val XATTR_ID = "fid"

class CoreFileSystemService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val wsServiceClient: AuthenticatedClient,
    private val backgroundScope: BackgroundScope
) {
    internal data class NewFileData(
        val fileId: String
    )

    private suspend fun handlePotentialFileCreation(
        ctx: Ctx,
        path: String,
        preAllocatedCreation: Long? = null,
        preAllocatedFileId: String? = null
    ): NewFileData? {
        // Note: We don't unwrap as this is expected to fail due to it already being present.
        val timestamp = runCatching {
            fs.setExtendedAttribute(
                ctx,
                path,
                XATTR_BIRTH,
                ((preAllocatedCreation ?: System.currentTimeMillis()) / 1000).toString(),
                allowOverwrite = false
            )
        }

        if (timestamp.isSuccess) {
            val newFileId = preAllocatedFileId ?: UUID.randomUUID().toString()
            val newFile = runCatching {
                fs.setExtendedAttribute(
                    ctx,
                    path,
                    XATTR_ID,
                    newFileId,
                    allowOverwrite = false
                )
            }

            return if (newFile.isSuccess) {
                fs.onFileCreated(ctx, path)
                NewFileData(newFileId)
            } else {
                null
            }
        }
        return null
    }

    suspend fun write(
        ctx: Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: suspend OutputStream.() -> Unit
    ): String {
        val normalizedPath = path.normalize()
        val targetPath =
            renameAccordingToPolicy(ctx, normalizedPath, conflictPolicy)

        fs.openForWriting(ctx, targetPath, conflictPolicy.allowsOverwrite())
        handlePotentialFileCreation(ctx, targetPath)
        fs.write(ctx, writer)
        return targetPath
    }

    suspend fun <R> read(
        ctx: Ctx,
        path: String,
        range: LongRange? = null,
        consumer: suspend InputStream.() -> R
    ): R {
        fs.openForReading(ctx, path)
        return fs.read(ctx, range, consumer)
    }

    suspend fun copy(
        ctx: Ctx,
        from: String,
        to: String,
        sensitivityLevel: SensitivityLevel,
        conflictPolicy: WriteConflictPolicy
    ): String {
        val normalizedFrom = from.normalize()
        val fromStat = stat(ctx, from, setOf(FileAttribute.FILE_TYPE, FileAttribute.SIZE))
        if (fromStat.fileType != FileType.DIRECTORY) {
            runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
                status = "Copying file from '$from' to '$to'"

                val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
                fs.copy(ctx, from, targetPath, conflictPolicy, this)
                handlePotentialFileCreation(ctx, targetPath)
                setSensitivity(ctx, targetPath, sensitivityLevel)

                return targetPath
            }
        } else {
            runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
                status = "Copying files from '$from' to '$to'"
                val filesPerSecond = MeasuredSpeedInteger("Files copied per second", "Files/s")
                this.speeds = listOf(filesPerSecond)

                val newRoot = renameAccordingToPolicy(ctx, to, conflictPolicy).normalize()
                status = "Copying files from '$from' to '$newRoot'"
                if (!exists(ctx, newRoot)) {
                    makeDirectory(ctx, newRoot)
                }

                val tree = tree(ctx, from, setOf(FileAttribute.PATH, FileAttribute.SIZE))
                val progress = Progress("Number of files", 0, tree.size)
                this.progress = progress

                tree.forEach { currentFile ->
                    val currentPath = currentFile.path.normalize()
                    val relativeFile = relativize(normalizedFrom, currentPath)

                    writeln("Copying file '$relativeFile' (${currentFile.size} bytes)")
                    retryWithCatch(
                        retryDelayInMs = 0L,
                        exceptionFilter = { it is FSException.AlreadyExists },
                        body = {
                            val desired = joinPath(newRoot, relativeFile).normalize()
                            if (desired == newRoot) return@forEach
                            val targetPath = renameAccordingToPolicy(ctx, desired, conflictPolicy)
                            fs.copy(ctx, currentPath, targetPath, conflictPolicy, this)

                            handlePotentialFileCreation(ctx, targetPath)
                        }
                    )

                    progress.current++
                    filesPerSecond.increment(1)
                }

                setSensitivity(ctx, newRoot, sensitivityLevel)
                return newRoot
            }
        }
    }

    suspend fun delete(
        ctx: Ctx,
        path: String
    ) {
        fs.delete(ctx, path)
    }

    suspend fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FileRow {
        return fs.stat(ctx, path, mode)
    }

    suspend fun statOrNull(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FileRow? {
        return try {
            stat(ctx, path, mode)
        } catch (ex: FSException.NotFound) {
            null
        }
    }

    suspend fun listDirectory(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>,
        type: FileType? = null
    ): List<FileRow> {
        return fs.listDirectory(ctx, path, mode, type = type)
    }

    suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>,
        sortBy: FileSortBy,
        order: SortOrder,
        paginationRequest: NormalizedPaginationRequest? = null,
        type: FileType? = null
    ): Page<FileRow> {
        return fs.listDirectoryPaginated(
            ctx,
            path,
            mode,
            sortBy,
            paginationRequest,
            order,
            type = type
        )
    }

    suspend fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): List<FileRow> {
        return fs.tree(ctx, path, mode)
    }

    suspend fun makeDirectory(
        ctx: Ctx,
        path: String
    ) {
        fs.makeDirectory(ctx, path)
        handlePotentialFileCreation(ctx, path)
    }

    suspend fun move(
        ctx: Ctx,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ): String {
        val targetPath = renameAccordingToPolicy(ctx, to, writeConflictPolicy)
        fs.move(ctx, from, targetPath, writeConflictPolicy)
        return targetPath
    }

    suspend fun exists(
        ctx: Ctx,
        path: String
    ): Boolean {
        return try {
            stat(ctx, path, setOf(FileAttribute.PATH))
            true
        } catch (ex: FSException.NotFound) {
            false
        }
    }

    suspend fun renameAccordingToPolicy(
        ctx: Ctx,
        desiredTargetPath: String,
        conflictPolicy: WriteConflictPolicy
    ): String {
        if (conflictPolicy == WriteConflictPolicy.OVERWRITE) return desiredTargetPath

        // Performance: This will cause a lot of stats, on items in the same folder, for the most part we could
        // simply ls a common root and cache it. This should be a lot more efficient.
        val targetExists = exists(ctx, desiredTargetPath)
        return when (conflictPolicy) {
            WriteConflictPolicy.OVERWRITE -> desiredTargetPath

            WriteConflictPolicy.MERGE -> desiredTargetPath

            WriteConflictPolicy.RENAME -> {
                if (targetExists) findFreeNameForNewFile(ctx, desiredTargetPath)
                else desiredTargetPath
            }

            WriteConflictPolicy.REJECT -> {
                if (targetExists) throw FSException.AlreadyExists()
                else desiredTargetPath
            }
        }
    }

    suspend fun dummyTask(ctx: Ctx) {
        val range = 0 until 100

        runTask(wsServiceClient, backgroundScope, "Storage Test", ctx.user, updateFrequencyMs = 50) {
            val progress = Progress("Progress", 0, range.last)
            val taskSpeed = SimpleSpeed("Speeed!", 0.0, "Foo")
            val tasksPerSecond = MeasuredSpeedInteger("Tasks", "T/s")

            speeds = listOf(taskSpeed, tasksPerSecond)
            this.progress = progress

            for (iteration in range) {
                status = "Working on step $iteration"
                if (iteration % 10 == 0) {
                    taskSpeed.speed = Random.nextDouble()
                }
                progress.current = iteration
                writeln("Started work on $iteration")
                delay(100)
                tasksPerSecond.increment(1)
                writeln("Work on $iteration complete!")
            }
        }
    }

    private val duplicateNamingRegex = Regex("""\((\d+)\)""")
    private suspend fun findFreeNameForNewFile(ctx: Ctx, desiredPath: String): String {
        fun findFileNameNoExtension(fileName: String): String {
            return fileName.substringBefore('.')
        }

        fun findExtension(fileName: String): String {
            if (!fileName.contains(".")) return ""
            return '.' + fileName.substringAfter('.', missingDelimiterValue = "")
        }

        val fileName = desiredPath.fileName()
        val desiredWithoutExtension = findFileNameNoExtension(fileName)
        val extension = findExtension(fileName)

        val parentPath = desiredPath.parent()
        val listDirectory = listDirectory(ctx, parentPath, setOf(FileAttribute.PATH))
        val paths = listDirectory.map { it.path }
        val names = listDirectory.map { it.path.fileName() }

        return if (!paths.contains(desiredPath)) {
            desiredPath
        } else {
            val namesMappedAsIndices = names.mapNotNull {
                val nameWithoutExtension = findFileNameNoExtension(it)
                val nameWithoutPrefix = nameWithoutExtension.substringAfter(desiredWithoutExtension)
                val myExtension = findExtension(it)

                if (extension != myExtension) return@mapNotNull null

                if (nameWithoutPrefix.isEmpty()) {
                    0 // We have an exact match on the file name
                } else {
                    val match = duplicateNamingRegex.matchEntire(nameWithoutPrefix)
                    if (match == null) {
                        null // The file name doesn't match at all, i.e., the file doesn't collide with our desired name
                    } else {
                        match.groupValues.getOrNull(1)?.toIntOrNull()
                    }
                }
            }

            if (namesMappedAsIndices.isEmpty()) {
                desiredPath
            } else {
                val currentMax = namesMappedAsIndices.max() ?: 0
                "$parentPath/$desiredWithoutExtension(${currentMax + 1})$extension"
            }
        }
    }

    private suspend fun setSensitivity(ctx: Ctx, targetPath: String, sensitivityLevel: SensitivityLevel) {
        val newSensitivity = stat(ctx, targetPath, setOf(FileAttribute.SENSITIVITY))
        if (sensitivityLevel != newSensitivity.sensitivityLevel) {
            sensitivityService.setSensitivityLevel(
                ctx,
                targetPath,
                sensitivityLevel
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
