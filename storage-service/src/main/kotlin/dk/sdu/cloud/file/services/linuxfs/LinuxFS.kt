@file:Suppress("BlockingMethodInNonBlockingContext")

package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.XATTR_BIRTH
import dk.sdu.cloud.file.services.XATTR_ID
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.LinuxFS.Companion.PATH_MAX
import dk.sdu.cloud.file.services.mergeWith
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.task.api.TaskContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.math.min
import kotlin.streams.toList

class LinuxFS(
    fsRoot: File,
    private val aclService: AclService<*>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private val fsRoot = fsRoot.normalize().absoluteFile

    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) = ctx.submit {
        translateAndCheckFile(ctx, from)
        aclService.requirePermission(from, ctx.user, AccessRight.READ)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        copyPreAuthorized(ctx, from, to, writeConflictPolicy)
    }

    private suspend fun copyPreAuthorized(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val opts =
            if (writeConflictPolicy.allowsOverwrite()) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()

        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))

        if (writeConflictPolicy == WriteConflictPolicy.MERGE) {
            try {
                Files.copy(systemFrom.toPath(), systemTo.toPath(), *opts)
            } catch (e: DirectoryNotEmptyException) {
                systemFrom.listFiles()?.forEach {
                    copyPreAuthorized(
                        ctx,
                        Paths.get(from, it.name).toString(),
                        Paths.get(to, it.name).toString(),
                        writeConflictPolicy
                    )
                }
            }
        } else {
            Files.copy(systemFrom.toPath(), systemTo.toPath(), *opts)
        }
    }

    override suspend fun move(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) = ctx.submit {
        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))

        // We need write permission on from's parent to avoid being able to steal a file by changing the owner.
        aclService.requirePermission(from.parent(), ctx.user, AccessRight.WRITE)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        // We need to record some information from before the move
        val fromStat = stat(
            ctx,
            systemFrom,
            setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH, FileAttribute.FILE_TYPE),
            hasPerformedPermissionCheck = true
        )

        val targetType =
            runCatching {
                stat(
                    ctx,
                    systemTo,
                    setOf(FileAttribute.FILE_TYPE),
                    hasPerformedPermissionCheck = true
                )
            }.getOrNull()?.fileType

        if (targetType != null) {
            if (fromStat.fileType != targetType) {
                throw FSException.BadRequest("Target already exists and is not of same type as source.")
            }
            if (fromStat.fileType == targetType &&
                fromStat.fileType == FileType.DIRECTORY &&
                writeConflictPolicy == WriteConflictPolicy.OVERWRITE
            ) {
                throw FSException.BadRequest("Directory is not allowed to overwrite existing directory")
            }
        }

        movePreAuthorized(ctx, from, to, writeConflictPolicy)
    }

    private suspend fun movePreAuthorized(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))

        val opts =
            if (writeConflictPolicy.allowsOverwrite()) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()

        if (writeConflictPolicy == WriteConflictPolicy.MERGE) {
            if (systemFrom.isDirectory) {
                systemFrom.listFiles()?.forEach {
                    movePreAuthorized(
                        ctx,
                        Paths.get(from, it.name).toString(),
                        Paths.get(to, it.name).toString(),
                        writeConflictPolicy
                    )
                }
            } else {
                Files.createDirectories(systemTo.toPath().parent)
                Files.move(systemFrom.toPath(), systemTo.toPath(), *opts)
            }
        } else {
            Files.move(systemFrom.toPath(), systemTo.toPath(), *opts)
        }
    }

    override suspend fun listDirectoryPaginated(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<FileAttribute>,
        sortBy: FileSortBy?,
        paginationRequest: NormalizedPaginationRequest?,
        order: SortOrder?,
        type: FileType?
    ): Page<FileRow> = ctx.submit {
        aclService.requirePermission(directory, ctx.user, AccessRight.READ)

        val systemFiles = run {
            val file = File(translateAndCheckFile(ctx, directory))
            val requestedDirectory = file.takeIf { it.exists() } ?: throw FSException.NotFound()

            (requestedDirectory.listFiles() ?: throw FSException.PermissionException())
                .toList()
                .filter { fileInDirectory ->
                    when (type) {
                        FileType.DIRECTORY -> fileInDirectory.isDirectory
                        FileType.FILE -> fileInDirectory.isFile
                        null, FileType.LINK -> true
                    }
                }
        }.filter { !Files.isSymbolicLink(it.toPath()) }

        val min =
            if (paginationRequest == null) 0
            else min(systemFiles.size, paginationRequest.itemsPerPage * paginationRequest.page)
        val max =
            if (paginationRequest == null) systemFiles.size
            else min(systemFiles.size, min + paginationRequest.itemsPerPage)

        val page = if (sortBy != null && order != null) {
            // We must sort our files. We do this in two lookups!

            // The first lookup will retrieve just the path (this is cheap) and the attribute we need
            // (this might not be).

            // In the second lookup we use only the relevant files. We do this by performing the sorting after the
            // first step and gathering a list of files to be included in the result.

            val sortingAttribute = when (sortBy) {
                FileSortBy.TYPE -> FileAttribute.FILE_TYPE
                FileSortBy.PATH -> FileAttribute.PATH
                FileSortBy.CREATED_AT, FileSortBy.MODIFIED_AT -> FileAttribute.TIMESTAMPS
                FileSortBy.SIZE -> FileAttribute.SIZE
                FileSortBy.SENSITIVITY -> FileAttribute.SENSITIVITY
                null -> FileAttribute.PATH
            }

            val statsForSorting = stat(
                ctx,
                systemFiles,
                setOf(FileAttribute.PATH, sortingAttribute),
                hasPerformedPermissionCheck = true
            ).filterNotNull()

            val comparator = comparatorForFileRows(sortBy, order)

            val relevantFileRows = statsForSorting.sortedWith(comparator).subList(min, max)

            // Time for the second lookup. We will retrieve all attributes we don't already know about and merge them
            // with first lookup.
            val relevantFiles = relevantFileRows.map { File(translateAndCheckFile(ctx, it.path)) }

            val desiredMode = mode - setOf(sortingAttribute) + setOf(FileAttribute.PATH)

            val statsForRelevantRows = stat(
                ctx,
                relevantFiles,
                desiredMode,
                hasPerformedPermissionCheck = true
            ).filterNotNull().associateBy { it.path }

            val items = relevantFileRows.mapNotNull { rowWithSortingInfo ->
                val rowWithFullInfo = statsForRelevantRows[rowWithSortingInfo.path] ?: return@mapNotNull null
                rowWithSortingInfo.mergeWith(rowWithFullInfo)
            }

            Page(
                systemFiles.size,
                paginationRequest?.itemsPerPage ?: items.size,
                paginationRequest?.page ?: 0,
                items
            )
        } else {
            val items = stat(
                ctx,
                systemFiles,
                mode,
                hasPerformedPermissionCheck = true
            ).filterNotNull().subList(min, max)

            Page(
                items.size,
                paginationRequest?.itemsPerPage ?: items.size,
                paginationRequest?.page ?: 0,
                items
            )
        }

        page
    }

    private fun comparatorForFileRows(
        sortBy: FileSortBy,
        order: SortOrder
    ): Comparator<FileRow> {
        val naturalComparator: Comparator<FileRow> = when (sortBy) {
            FileSortBy.CREATED_AT -> Comparator.comparingLong { it.timestamps.created }

            FileSortBy.MODIFIED_AT -> Comparator.comparingLong { it.timestamps.modified }

            FileSortBy.TYPE -> Comparator.comparing<FileRow, String> {
                it.fileType.name
            }.thenComparing(Comparator.comparing<FileRow, String> {
                it.path.fileName().toLowerCase()
            })

            FileSortBy.PATH -> Comparator.comparing<FileRow, String> {
                it.path.fileName().toLowerCase()
            }

            FileSortBy.SIZE -> Comparator.comparingLong { it.size }

            // TODO This should be resolved before sorting
            FileSortBy.SENSITIVITY -> Comparator.comparing<FileRow, String> {
                (it.sensitivityLevel?.name?.toLowerCase()) ?: "inherit"
            }
        }

        return when (order) {
            SortOrder.ASCENDING -> naturalComparator
            SortOrder.DESCENDING -> naturalComparator.reversed()
        }
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFile: File,
        mode: Set<FileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): FileRow {
        return stat(ctx, listOf(systemFile), mode, hasPerformedPermissionCheck).first()
            ?: throw FSException.NotFound()
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFiles: List<File>,
        mode: Set<FileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): List<FileRow?> {
        // The 'shareLookup' contains a mapping between cloud paths and their ACL
        val shareLookup = if (FileAttribute.SHARES in mode) {
            aclService.listAcl(systemFiles.map { it.path.toCloudPath().normalize() })
        } else {
            emptyMap()
        }

        return systemFiles.map { systemFile ->
            try {
                if (!hasPerformedPermissionCheck) {
                    aclService.requirePermission(systemFile.path.toCloudPath(), ctx.user, AccessRight.READ)
                }

                var fileType: FileType? = null
                var unixMode: Int? = null
                var timestamps: Timestamps? = null
                var path: String? = null
                var inode: String? = null
                var size: Long? = null
                var shares: List<AccessEntry>? = null
                var sensitivityLevel: SensitivityLevel? = null

                val systemPath = try {
                    systemFile.toPath()
                } catch (ex: InvalidPathException) {
                    throw FSException.BadRequest()
                }

                val linkOpts = arrayOf(LinkOption.NOFOLLOW_LINKS)

                run {
                    // UNIX file attributes
                    val attributes = run {
                        val opts = ArrayList<String>()
                        if (FileAttribute.INODE in mode) opts.add("ino")
                        if (FileAttribute.CREATOR in mode) opts.add("uid")
                        if (FileAttribute.UNIX_MODE in mode) opts.add("mode")

                        if (opts.isEmpty()) {
                            null
                        } else {
                            Files.readAttributes(systemPath, "unix:${opts.joinToString(",")}", *linkOpts)
                        }
                    } ?: return@run

                    if (FileAttribute.INODE in mode) {
                        inode = runCatching { getExtendedAttributeInternal(systemFile, XATTR_ID) }.getOrNull()
                            ?: (attributes.getValue("ino") as Long).toString()
                    }

                    if (FileAttribute.UNIX_MODE in mode) unixMode = attributes["mode"] as Int
                }

                run {
                    // Basic file attributes and symlinks
                    val basicAttributes = run {
                        val opts = ArrayList<String>()

                        // We always add SIZE. This will make sure we always get a stat executed and thus throw if the
                        // file doesn't actually exist.
                        opts.add("size")

                        if (FileAttribute.TIMESTAMPS in mode) {
                            // Note we don't rely on the creationTime due to not being available in all file systems.
                            opts.addAll(listOf("lastAccessTime", "lastModifiedTime"))
                        }

                        if (FileAttribute.FILE_TYPE in mode) {
                            opts.add("isDirectory")
                        }

                        if (opts.isEmpty()) {
                            null
                        } else {
                            Files.readAttributes(systemPath, opts.joinToString(","), *linkOpts)
                        }
                    }

                    if (FileAttribute.PATH in mode) path = systemFile.absolutePath.toCloudPath()

                    if (basicAttributes != null) {
                        // We need to always ask if this file is a link to correctly resolve target file type.

                        if (FileAttribute.SIZE in mode) size = basicAttributes.getValue("size") as Long

                        if (FileAttribute.FILE_TYPE in mode) {
                            val isDirectory = basicAttributes.getValue("isDirectory") as Boolean

                            fileType = if (isDirectory) {
                                FileType.DIRECTORY
                            } else {
                                FileType.FILE
                            }
                        }

                        if (FileAttribute.TIMESTAMPS in mode) {
                            val lastAccess = basicAttributes.getValue("lastAccessTime") as FileTime
                            val lastModified = basicAttributes.getValue("lastModifiedTime") as FileTime

                            // The extended attribute is set by CoreFS
                            // Old setup would ignore errors. This is required for createLink to work
                            val creationTime =
                                runCatching {
                                    getExtendedAttributeInternal(systemFile, XATTR_BIRTH)?.toLongOrNull()
                                        ?.let { it * 1000 }
                                }.getOrNull() ?: lastModified.toMillis()

                            timestamps = Timestamps(
                                lastAccess.toMillis(),
                                creationTime,
                                lastModified.toMillis()
                            )
                        }
                    }
                }

                val realOwner = if (FileAttribute.OWNER in mode || FileAttribute.CREATOR in mode) {
                    val toCloudPath = systemFile.absolutePath.toCloudPath()
                    val realPath = toCloudPath.normalize()

                    val components = realPath.components()
                    when {
                        components.isEmpty() -> SERVICE_USER
                        components.first() != "home" -> SERVICE_USER
                        components.size < 2 -> SERVICE_USER
                        else -> // TODO This won't work for projects (?)
                            components[1]
                    }
                } else {
                    null
                }

                if (FileAttribute.SENSITIVITY in mode) {
                    // Old setup would ignore errors.
                    sensitivityLevel =
                        runCatching {
                            getExtendedAttributeInternal(
                                systemFile,
                                "sensitivity"
                            )?.let { SensitivityLevel.valueOf(it) }
                        }.getOrNull()
                }

                if (FileAttribute.SHARES in mode) {
                    val cloudPath = systemFile.path.toCloudPath()
                    shares = shareLookup.getOrDefault(cloudPath, emptyList()).map {
                        AccessEntry(it.username, it.permissions)
                    }
                }

                FileRow(
                    fileType,
                    unixMode,
                    realOwner,
                    "",
                    timestamps,
                    path,
                    inode,
                    size,
                    shares,
                    sensitivityLevel,
                    realOwner
                )
            } catch (ex: NoSuchFileException) {
                null
            }
        }
    }

    override suspend fun delete(
        ctx: LinuxFSRunner,
        path: String,
        task: TaskContext
    ) =
        ctx.submit {
            aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)
            aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

            val systemFile = File(translateAndCheckFile(ctx, path))

            if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
            traverseAndDelete(ctx, systemFile.toPath())
        }

    private fun delete(path: Path) {
        try {
            Files.delete(path)
        } catch (ex: NoSuchFileException) {
            log.debug("File at $path does not exists any more. Ignoring this error.")
        }
    }

    private suspend fun traverseAndDelete(
        ctx: LinuxFSRunner,
        path: Path
    ) {
        if (Files.isSymbolicLink(path)) {
            delete(path)
            return
        }

        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            path.toFile().listFiles()?.forEach {
                traverseAndDelete(ctx, it.toPath())
            }
        }

        delete(path)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ) = ctx.submit {
        log.debug("${ctx.user} is attempting to open $path")
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        if (ctx.outputStream == null) {
            val options = HashSet<OpenOption>()
            options.add(StandardOpenOption.TRUNCATE_EXISTING)
            options.add(StandardOpenOption.WRITE)
            if (!allowOverwrite) {
                options.add(StandardOpenOption.CREATE_NEW)
            } else {
                options.add(StandardOpenOption.CREATE)
            }

            try {
                val systemPath = systemFile.toPath()
                ctx.outputStream = Channels.newOutputStream(
                    Files.newByteChannel(systemPath, options, PosixFilePermissions.asFileAttribute(DEFAULT_FILE_MODE))
                )
                ctx.outputSystemFile = systemFile
            } catch (ex: FileAlreadyExistsException) {
                throw FSException.AlreadyExists()
            } catch (ex: java.nio.file.FileSystemException) {
                if (ex.message?.contains("Is a directory") == true) {
                    throw FSException.BadRequest("Upload target is a not a directory")
                } else {
                    throw ex
                }
            }
        } else {
            log.warn("openForWriting called twice without closing old file!")
            throw FSException.CriticalException("Internal error")
        }
    }

    override suspend fun write(
        ctx: LinuxFSRunner,
        writer: suspend (OutputStream) -> Unit
    ) = ctx.submit {
        // Note: This function has already checked permissions via openForWriting
        val stream = ctx.outputStream
        val file = ctx.outputSystemFile
        if (stream == null || file == null) {
            log.warn("write() called without openForWriting()!")
            throw FSException.CriticalException("Internal error")
        }

        try {
            runBlocking {
                writer(stream)
            }
        } finally {
            stream.close()
            ctx.outputStream = null
            ctx.outputSystemFile = null
        }
    }

    override suspend fun tree(
        ctx: LinuxFSRunner,
        path: String,
        mode: Set<FileAttribute>
    ): List<FileRow> = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        val systemFile = File(translateAndCheckFile(ctx, path))
        Files.walk(systemFile.toPath())
            .toList()
            .mapNotNull {
                if (Files.isSymbolicLink(it)) return@mapNotNull null

                stat(ctx, it.toFile(), mode, hasPerformedPermissionCheck = true)
            }
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ) = ctx.submit {
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)
        Files.createDirectory(systemFile.toPath(), PosixFilePermissions.asFileAttribute(DEFAULT_DIRECTORY_MODE))
        Unit
    }

    private fun getExtendedAttributeInternal(
        systemFile: File,
        attribute: String
    ): String? {
        return try {
            StandardCLib.getxattr(systemFile.absolutePath, "user.$attribute")
        } catch (ex: NativeException) {
            if (ex.statusCode == 61) return null
            if (ex.statusCode == 2) return null
            throw ex
        }
    }

    override suspend fun getExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ): String = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.READ)
        getExtendedAttributeInternal(File(translateAndCheckFile(ctx, path)), attribute) ?: throw FSException.NotFound()
    }

    override suspend fun setExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean
    ) = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        val status = StandardCLib.setxattr(
            translateAndCheckFile(ctx, path),
            "user.$attribute",
            value,
            allowOverwrite
        )

        if (status != 0) throw throwExceptionBasedOnStatus(status)

        Unit
    }

    override suspend fun listExtendedAttribute(ctx: LinuxFSRunner, path: String): List<String> = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.READ)
        StandardCLib.listxattr(translateAndCheckFile(ctx, path)).map { it.removePrefix("user.") }
    }

    override suspend fun deleteExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ) = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)
        StandardCLib.removexattr(translateAndCheckFile(ctx, path), "user.$attribute")
        Unit
    }

    override suspend fun stat(ctx: LinuxFSRunner, path: String, mode: Set<FileAttribute>): FileRow = ctx.submit {
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path, ctx.user, AccessRight.READ)
        stat(ctx, systemFile, mode, hasPerformedPermissionCheck = true)
    }

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String) = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        if (ctx.inputStream != null) {
            log.warn("openForReading() called without closing last stream")
            throw FSException.CriticalException("Internal error")
        }

        val systemFile = File(translateAndCheckFile(ctx, path))
        ctx.inputStream = FileChannel.open(systemFile.toPath(), StandardOpenOption.READ)
        ctx.inputSystemFile = systemFile
    }

    override suspend fun <R> read(
        ctx: LinuxFSRunner,
        range: LongRange?,
        consumer: suspend (InputStream) -> R
    ): R = ctx.submit {
        // Note: This function has already checked permissions via openForReading

        val stream = ctx.inputStream
        val file = ctx.inputSystemFile
        if (stream == null || file == null) {
            log.warn("read() called without calling openForReading()")
            throw FSException.CriticalException("Internal error")
        }

        val convertedToStream: InputStream = if (range != null) {
            stream.position(range.first)
            CappedInputStream(Channels.newInputStream(stream), range.last - range.first)
        } else {
            Channels.newInputStream(stream)
        }

        try {
            consumer(convertedToStream)
        } finally {
            convertedToStream.close()
            ctx.inputSystemFile = null
            ctx.inputStream = null
        }
    }

    override suspend fun onFileCreated(ctx: LinuxFSRunner, path: String) {
        Chown.setOwner(File(translateAndCheckFile(ctx, path)).toPath(), LINUX_FS_USER_UID, LINUX_FS_USER_UID)
    }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot.normalize().absolutePath).removePrefix("/")).normalize()
    }

    private fun translateAndCheckFile(
        ctx: LinuxFSRunner,
        internalPath: String,
        isDirectory: Boolean = false
    ): String {
        return translateAndCheckFile(fsRoot, internalPath, isDirectory, ctx.user == SERVICE_USER)
    }

    companion object : Loggable {
        override val log = logger()

        // Setting this to 4096 is too big for us to save files from workspaces. We want to leave a bit of buffer room.
        const val PATH_MAX = 3700

        val DEFAULT_FILE_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE
        )

        val DEFAULT_DIRECTORY_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE
        )
    }
}

fun translateAndCheckFile(
    fsRoot: File,
    internalPath: String,
    isDirectory: Boolean = false,
    isServiceUser: Boolean = false
): String {
    val root = (if (!isServiceUser) File(fsRoot, "home") else fsRoot).absolutePath.normalize().removeSuffix("/") + "/"
    val systemFile = File(fsRoot, internalPath)
    val path = systemFile
        .normalize()
        .absolutePath
        .let { it + (if (isDirectory) "/" else "") }

    if (Files.isSymbolicLink(systemFile.toPath())) {
        // We do not allow symlinks. Delete them if we detect them.
        systemFile.delete()
    }

    if (!path.startsWith(root) && path.removeSuffix("/") != root.removeSuffix("/")) {
        throw FSException.BadRequest("path is not in user-root")
    }

    if (path.contains("\n")) throw FSException.BadRequest("Path cannot contain new-lines")

    if (path.length >= PATH_MAX) {
        throw FSException.BadRequest("Path is too long ${path.length} '$path'")
    }

    return path
}
