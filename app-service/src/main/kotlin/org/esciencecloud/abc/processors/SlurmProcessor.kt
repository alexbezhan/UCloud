package org.esciencecloud.abc.processors

import kotlinx.coroutines.experimental.runBlocking
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.internalError
import org.esciencecloud.abc.services.*
import org.esciencecloud.abc.services.ssh.SSHConnectionPool
import org.esciencecloud.abc.services.ssh.scpDownload
import org.esciencecloud.abc.services.ssh.stat
import org.esciencecloud.abc.util.BashEscaper
import org.esciencecloud.abc.util.BashEscaper.safeBashArgument
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.ext.PermissionException
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.model.StoragePath
import org.irods.jargon.core.exception.FileNotFoundException
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Processes events from slurm
 */
class SlurmProcessor(
        private val interactiveStore: HPCStore,
        private val sshPool: SSHConnectionPool,
        private val storageConnectionFactory: StorageConnectionFactory,
        private val slurmAgent: SlurmPollAgent,
        private val streamService: HPCStreamService
) {
    companion object {
        private val log = LoggerFactory.getLogger(SlurmProcessor::class.java)
    }

    suspend fun handle(event: SlurmEvent): Pair<String, HPCAppEvent> =
            when (event) {
                is SlurmEventBegan -> {
                    val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()
                    val appEvent = HPCAppEvent.Started(event.jobId)

                    Pair(key, appEvent)
                }

                is SlurmEventEnded -> handleEndedEvent(event)

                else -> throw IllegalStateException()
            }

    private suspend fun handleEndedEvent(event: SlurmEventEnded): Pair<String, HPCAppEvent.Ended> {
        // Some of these queries _must_ resolve. Because of this we throw if they do not resolve.
        // The queries should have built-in retries, so if the instances have not yet replayed the data we will
        // give them a chance to do so before crashing. TODO We might have to tweak this slightly for more events.
        val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()
        val pendingEvent = interactiveStore.queryJobIdToApp(key).orThrow()

        val appParameters = pendingEvent.originalRequest.event.parameters
        val app = with(pendingEvent.originalRequest.event.application) {
            ApplicationDAO.findByNameAndVersion(name, version)
        }!!

        // TODO We need to be able to resolve this in a uniform manner. Since we will need these in multiple places.
        // There will also be some logic in handling optional parameters.
        val outputs = app.parameters
                .filterIsInstance<ApplicationParameter.OutputFile>()
                .map { it.map(appParameters[it.name]) }

        val storage = with(pendingEvent.originalRequest.header.performedFor) {
            storageConnectionFactory.createForAccount(username, password)
        }.capture() ?: return Pair(key, HPCAppEvent.UnsuccessfullyCompleted(Error.invalidAuthentication()))

        return try {
            sshPool.use {
                log.info("Handling Slurm ended event! $key ${event.jobId}")
                // Transfer output files
                for (transfer in outputs) {
                    val workingDirectory = URI(pendingEvent.workingDirectory)
                    val source = workingDirectory.resolve(transfer.source)

                    if (!source.path.startsWith(workingDirectory.path)) {
                        log.warn("File ${transfer.source} did not resolve to be within working directory " +
                                "($source versus $workingDirectory). Skipping this file")
                        continue
                    }

                    // TODO Do we just automatically zip up if the output file is a directory?
                    val sourceFile = stat(source.path)
                    if (sourceFile == null) {
                        log.info("Could not find output file at: ${source.path}. Skipping file")
                        continue
                    }

                    if (sourceFile.isDir) {
                        val zipPath = source.path + ".zip"
                        val (status, output) = execWithOutputAsText("zip -r " +
                                BashEscaper.safeBashArgument(zipPath) + " " +
                                BashEscaper.safeBashArgument(source.path))

                        if (status != 0) {
                            log.warn("Unable to create zip archive of output!")
                            log.warn("Path: ${source.path}")
                            log.warn("Status: $status")
                            log.warn("Output: $output")

                            return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted(Error.internalError()))
                        }

                        TODO("Handle directory uploads")
                    } else {
                        var permissionDenied = false
                        scpDownload(source.path) {
                            try {
                                storage.files.put(StoragePath.fromURI(transfer.destination), it)
                            } catch (ex: FileNotFoundException) {
                                permissionDenied = true
                            } catch (ex: PermissionException) {
                                permissionDenied = true
                            }
                        }

                        if (permissionDenied) return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted(
                                Error.permissionDenied("Could not transfer file to ${transfer.destination}")
                        ))
                    }
                }

                // TODO Crashing after deletion but before we send event will cause a lot of problems. We should split
                // this into two.
                execWithOutputAsText("rm -rf ${safeBashArgument(pendingEvent.jobDirectory)}")

                Pair(key, HPCAppEvent.SuccessfullyCompleted(event.jobId))
            }
        } finally {
            storage.close()
        }
    }

    fun init() {
        slurmAgent.addListener {
            runBlocking {
                val (key, event) = handle(it)
                streamService.appEventsProducer.emit(key, event)
            }
        }
    }
}