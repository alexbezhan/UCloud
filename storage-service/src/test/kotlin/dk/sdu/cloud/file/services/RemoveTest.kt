package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.file.util.createDummyFS
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.micro.BackgroundScope
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import kotlin.test.*

class RemoveTest : WithBackgroundScope() {
    private fun createService(
        root: String,
        emitter: StorageEventProducer = mockk(relaxed = true)
    ): Pair<LinuxFSRunnerFactory, CoreFileSystemService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        return Pair(
            runner,
            CoreFileSystemService(fs, emitter, fileSensitivityService, ClientMock.authenticatedClient, backgroundScope)
        )
    }

    @Test
    fun testSimpleRemove() {
        EventServiceMock.reset()
        val emitter = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), backgroundScope, {})

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        runner.withBlockingContext("user1") {
            service.delete(it, "/home/user1/folder")
        }
        val existingFolder = File(fsRoot, "home/user1/folder")
        assertFalse(existingFolder.exists())

        // The function returns immediately. We want to wait for those events to have been emitted.
        // This is not a fool proof way of doing it. But we have no way of waiting for tasks
        Thread.sleep(100)

        val events = EventServiceMock.messagesForTopic(StorageEvents.events)

        events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/a" }
        events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/b" }
        events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/c" }
        events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder" }
    }

    @Test(expected = FSException.NotFound::class)
    fun testNonExistingPathRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.produce(any<StorageEvent>()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        //Folder should not exists
        val nonExistingFolder = File(fsRoot, "home/user1/fold")
        assertFalse(nonExistingFolder.exists())
        runner.withBlockingContext("user1") { service.delete(it, "/home/user1/fold") }
    }
}
