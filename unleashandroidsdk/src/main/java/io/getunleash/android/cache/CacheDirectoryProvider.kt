package io.getunleash.android.cache

import android.content.Context
import io.getunleash.android.backup.LocalStorageConfig
import io.getunleash.android.util.LoggerWrapper
import java.io.File

class CacheDirectoryProvider(
    private val config: LocalStorageConfig,
    private val context: Context,
    private val runtime: Runtime = Runtime.getRuntime()
) {

    companion object {
        private const val TAG = "CacheDirProvider"
    }
    fun getCacheDirectory(tempDirName: String, deleteOnShutdown: Boolean = false): File {
        val tempStorageDir: File = config.dir?.let { File(it) } ?: context.cacheDir
        val tempDir = File(tempStorageDir, tempDirName)
        if (!createDirectoryIfNotExists(tempDir)) {
            LoggerWrapper.w(TAG, "Failed to create directory ${tempDir.absolutePath}")
        } else {
            if (deleteOnShutdown) addShutdownHook(tempDir)
        }
        return tempDir
    }

    private fun createDirectoryIfNotExists(file: File): Boolean {
        if (file.exists()) {
            LoggerWrapper.d(TAG, "Directory ${file.absolutePath} already exists")
            return true
        }
        if (file.mkdirs()) {
            LoggerWrapper.d(TAG, "Created directory ${file.absolutePath}")
            return true
        }
        LoggerWrapper.w(TAG, "Failed to create directory ${file.absolutePath}")
        return false
    }

    private fun addShutdownHook(file: File) {
        runtime.addShutdownHook(DeleteFileShutdownHook(file))
    }

    private class DeleteFileShutdownHook(file: File) : Thread(Runnable {
        file.deleteRecursively()
    })
}
