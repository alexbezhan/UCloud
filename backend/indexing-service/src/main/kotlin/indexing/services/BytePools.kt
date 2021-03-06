package dk.sdu.cloud.indexing.services

import io.ktor.utils.io.pool.*
import java.util.*

val DefaultByteArrayPool = ByteArrayPool()

class ByteArrayPool : DefaultPool<ByteArray>(128) {
    override fun produceInstance(): ByteArray = ByteArray(1024 * 64)
    override fun clearInstance(instance: ByteArray): ByteArray {
        Arrays.fill(instance, 0)
        return instance
    }
}
