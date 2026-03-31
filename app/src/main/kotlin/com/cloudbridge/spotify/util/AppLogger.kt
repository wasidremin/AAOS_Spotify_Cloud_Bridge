package com.cloudbridge.spotify.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralised application logger for the Spotify Cloud Bridge.
 *
 * When enabled via [setEnabled], every call to [d], [i], [w], [e]
 * writes a timestamped line to an internal log file **in addition** to
 * the standard Android [Log]. When disabled, only [Log] is used (the
 * default Android behaviour).
 *
 * Log files are stored in the app-private `files/logs/` directory.
 * The current file is `cloudbridge.log`; older files are kept as
 * `cloudbridge_<timestamp>.log` after rotation.
 *
 * Thread-safety: writes are buffered in a [ConcurrentLinkedQueue] and
 * flushed periodically or on demand via [flush], avoiding I/O on every
 * single log call.
 */
object AppLogger {

    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L  // 5 MB
    private const val MAX_LOG_FILES = 5
    private const val FLUSH_THRESHOLD = 50 // flush to disk every N entries
    private const val LOG_DIR = "logs"
    private const val LOG_FILE_NAME = "cloudbridge.log"

    @Volatile
    var enabled: Boolean = false
        private set

    private var logDir: File? = null
    private val buffer = ConcurrentLinkedQueue<String>()
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    /**
     * Initialise the logger with the app context. Must be called once
     * from [SpotifyCloudBridgeApp.onCreate].
     */
    fun init(context: Context) {
        logDir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
    }

    /** Turn file logging on or off at runtime. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            appendToFile("--- Logging enabled at ${timestamp()} ---")
        } else {
            appendToFile("--- Logging disabled at ${timestamp()} ---")
            flushSync()
        }
    }

    // ── Public log methods ───────────────────────────────────────────

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        if (enabled) bufferLine("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        if (enabled) bufferLine("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        if (enabled) {
            bufferLine("W", tag, message)
            throwable?.let { bufferLine("W", tag, Log.getStackTraceString(it)) }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        if (enabled) {
            bufferLine("E", tag, message)
            throwable?.let { bufferLine("E", tag, Log.getStackTraceString(it)) }
        }
    }

    // ── File management ──────────────────────────────────────────────

    /** Flush the in-memory buffer to disk (call from a background thread). */
    suspend fun flush() = withContext(Dispatchers.IO) { flushSync() }

    /** Return all log files sorted newest-first. */
    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Export all log files into a single combined file suitable for
     * copying to USB or sharing via intent.
     *
     * @return the combined export [File], or `null` if no logs exist.
     */
    suspend fun exportLogs(context: Context): File? = withContext(Dispatchers.IO) {
        flushSync()
        val files = getLogFiles()
        if (files.isEmpty()) return@withContext null

        val exportFile = File(context.cacheDir, "cloudbridge_logs_export.txt")
        exportFile.bufferedWriter().use { writer ->
            writer.appendLine("═══════════════════════════════════════════════════════════════")
            writer.appendLine(" Cloud-Bridge Log Export — ${timestamp()}")
            writer.appendLine("═══════════════════════════════════════════════════════════════")
            writer.appendLine()
            for (file in files) {
                writer.appendLine("── ${file.name} (${file.length() / 1024} KB) ──")
                file.bufferedReader().use { reader ->
                    reader.copyTo(writer)
                }
                writer.appendLine()
            }
        }
        exportFile
    }

    /** Delete all log files. */
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        buffer.clear()
        logDir?.listFiles { f -> f.extension == "log" }?.forEach { it.delete() }
    }

    /** Total size of all log files in bytes. */
    fun totalLogSizeBytes(): Long =
        getLogFiles().sumOf { it.length() }

    // ── Internals ────────────────────────────────────────────────────

    private fun bufferLine(level: String, tag: String, message: String) {
        val line = "${timestamp()} $level/$tag: $message"
        buffer.add(line)
        if (buffer.size >= FLUSH_THRESHOLD) {
            // Best-effort flush; if another thread is already flushing this
            // is a no-op because ConcurrentLinkedQueue.poll is thread-safe.
            flushSync()
        }
    }

    private fun appendToFile(line: String) {
        val dir = logDir ?: return
        try {
            val file = File(dir, LOG_FILE_NAME)
            file.appendText(line + "\n")
        } catch (_: Exception) {
            // Swallow — logging must never crash the app
        }
    }

    @Synchronized
    private fun flushSync() {
        val dir = logDir ?: return
        val file = File(dir, LOG_FILE_NAME)
        try {
            file.appendText(buildString {
                var entry = buffer.poll()
                while (entry != null) {
                    appendLine(entry)
                    entry = buffer.poll()
                }
            })
            rotateIfNeeded(file)
        } catch (_: Exception) {
            // Swallow
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (file.length() < MAX_LOG_FILE_SIZE) return
        val dir = logDir ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        file.renameTo(File(dir, "cloudbridge_$stamp.log"))
        // Prune oldest files beyond the cap
        val files = dir.listFiles { f -> f.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
    }

    private fun timestamp(): String =
        dateFormat.get()?.format(Date()) ?: Date().toString()
}
