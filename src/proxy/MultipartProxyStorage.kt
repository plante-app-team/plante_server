package vegancheckteam.plante_server.multipart_proxy

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readAttributes
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.nowMillis

class MultipartProxyStorage(
    private val maxSizeBytes: Long,
    private val maxLifetimeMillis: Long,
    private val directory: Path,
) {
    init {
        if (directory.exists() && !directory.isDirectory()) {
            throw IllegalArgumentException("$directory is not a directory")
        }
        if (!directory.exists()) {
            directory.createDirectories()
        }
    }

    /**
     * Please delete the provided file when it's not needed anymore.
     */
    fun provideTempFile(): File? {
        sanitizeDirectory()
        if (!enoughSpace()) {
            Log.w("MultipartProxyStorage $directory", "No space left")
            return null
        }
        val file = directory.resolve(UUID.randomUUID().toString())
        return file.toFile()
    }

    private fun sanitizeDirectory() {
        for (file in directory.listDirectoryEntries()) {
            if (file.isDirectory()) {
                Log.e("MultipartProxyStorage $directory", "dir contains subdir: $file")
                val success = file.toFile().deleteRecursively()
                if (!success) {
                    Log.e("MultipartProxyStorage $directory", "could not delete subdir: $file")
                }
                continue
            }
            val createdAt = file.readAttributes<BasicFileAttributes>().creationTime().toMillis()
            val now = nowMillis()
            val timePassed = now - createdAt
            if (timePassed > maxLifetimeMillis) {
                Log.w("MultipartProxyStorage $directory", "deleting timed out file: $file")
                file.deleteExisting()
            }
        }
    }

    private fun enoughSpace(): Boolean {
        var totalSize = 0L
        for (file in directory.listDirectoryEntries()) {
            totalSize += file.fileSize()
        }
        return totalSize < maxSizeBytes
    }
}
