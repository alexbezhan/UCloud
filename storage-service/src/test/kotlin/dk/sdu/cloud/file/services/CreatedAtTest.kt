package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.createdAt
import dk.sdu.cloud.file.api.modifiedAt
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.micro.BackgroundScope
import io.mockk.mockk
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.*

class CreatedAtTest : WithBackgroundScope() {
    val user = "user"

    data class TestContext(
        val runner: LinuxFSRunnerFactory,
        val fs: LowLevelFileSystemInterface<LinuxFSRunner>,
        val coreFs: CoreFileSystemService<LinuxFSRunner>,
        val lookupService: FileLookupService<LinuxFSRunner>
    )

    private fun initTest(root: File): TestContext {
        val (runner, fs) = linuxFSWithRelaxedMocks(root.absolutePath)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        val coreFs =
            CoreFileSystemService(
                fs,
                StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), backgroundScope, {}),
                fileSensitivityService,
                ClientMock.authenticatedClient,
                backgroundScope
            )
        val fileLookupService = FileLookupService(runner, coreFs)

        return TestContext(runner, fs, coreFs, fileLookupService)
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()

    @Test
    fun `test that file creation is roughly right`() {
        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir(user) {

                }
            }

            runner.withBlockingContext(user) { ctx ->
                coreFs.write(ctx, "/home/$user/foo", WriteConflictPolicy.REJECT) {
                    write("Hello, World!".toByteArray())
                }

                val stat = lookupService.stat(ctx, "/home/$user/foo")
                assertThatInstance(stat.createdAt) { System.currentTimeMillis() - it < 1000 }
            }
        }
    }

    @Test
    fun `test that created at doesn't change after rename`() {
        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir(user) {

                }
            }

            runner.withBlockingContext(user) { ctx ->
                val path = "/home/$user/foo"
                val newPath = path + "2"
                coreFs.write(ctx, path, WriteConflictPolicy.REJECT) {
                    write("Hello, World!".toByteArray())
                }

                val stat = lookupService.stat(ctx, path)
                assertThatInstance(stat.createdAt) { System.currentTimeMillis() - it < 1000 }

                // Ensure that timestamp is no longer equal to old timestamp
                delay(1000)

                coreFs.move(ctx, path, newPath, WriteConflictPolicy.REJECT)

                val stat2 = lookupService.stat(ctx, newPath)
                assertEquals(stat.createdAt, stat2.createdAt)
            }
        }
    }

    @Test
    fun `test that modification update timestamps correctly`() {
        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir(user) {

                }
            }

            runner.withBlockingContext(user) { ctx ->
                val path = "/home/$user/foo"
                coreFs.write(ctx, path, WriteConflictPolicy.REJECT) {
                    write("Hello, World!".toByteArray())
                }

                val stat = lookupService.stat(ctx, path)
                assertThatInstance(stat.createdAt) { System.currentTimeMillis() - it < 1000 }

                // Ensure that timestamp is no longer equal to old timestamp
                delay(1000)

                coreFs.write(ctx, path, WriteConflictPolicy.OVERWRITE) {
                    write("New file!".toByteArray())
                }

                val stat2 = lookupService.stat(ctx, path)
                assertEquals(stat.createdAt, stat2.createdAt)
                assertNotEquals(stat.modifiedAt, stat2.modifiedAt)
                assertThatInstance(stat2.modifiedAt) { it > stat.modifiedAt }
            }
        }
    }
}
